package com.vaydelux.mediaforge.players

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.CaptionFormat
import com.vaydelux.mediaforge.core.MediaForgePlaybackTransport
import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import com.vaydelux.mediaforge.core.StreamCandidate
import com.vaydelux.mediaforge.core.StreamFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Seekable Android FD for LibVLC backed exclusively by MediaForge's guarded
 * HTTPS range requests. Supports *progressive* MP4/WebM only, not HLS/DASH.
 *
 * This is experimental: OEM Android FUSE support, codec behavior and seek
 * latency need ARM32/ARM64 device qualification before production enablement.
 * The underlying FD is never a file:// path or remote URL.
 */
class MediaForgeGuardedProgressiveSource private constructor(
    val descriptor: ParcelFileDescriptor,
    val localSubtitle: File?,
    private val callback: GuardedRangeCallback,
    private val worker: HandlerThread
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        callback.stop()
        runCatching { descriptor.close() }
        worker.quitSafely()
        localSubtitle?.delete()
    }

    companion object {
        fun supported(stream: StreamCandidate): Boolean =
            stream.format == StreamFormat.MP4 || stream.format == StreamFormat.WEBM

        /**
         * Suspends without blocking Compose. Servers must implement byte ranges
         * with a stable known length. Otherwise the user can use Media3/WebView.
         */
        suspend fun open(
            context: Context,
            stream: StreamCandidate,
            caption: CaptionCandidate? = null
        ): MediaForgeGuardedProgressiveSource = withContext(Dispatchers.IO) {
            require(supported(stream)) { "LibVLC guarded FD requires MP4 or WebM" }
            require(MediaForgeUrlPolicy.safeHttpsUrl(stream.url) != null) {
                "LibVLC guarded FD requires HTTPS"
            }
            val client = MediaForgePlaybackTransport.client.newBuilder()
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
            val identity = inspectLength(client, stream.url)
            coroutineContext.ensureActive()
            val subtitle = if (caption != null) fetchSubtitle(context, client, caption) else null
            val callback = GuardedRangeCallback(client, stream.url, identity)
            val thread = HandlerThread("mediaforge-vlc-range-io")
            var descriptor: ParcelFileDescriptor? = null
            try {
                thread.start()
                val storage = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                descriptor = storage.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY,
                    callback,
                    Handler(thread.looper)
                )
                coroutineContext.ensureActive()
                MediaForgeGuardedProgressiveSource(
                    descriptor, subtitle, callback, thread
                )
            } catch (error: Exception) {
                callback.stop()
                runCatching { descriptor?.close() }
                thread.quitSafely()
                subtitle?.delete()
                throw error
            }
        }

        private fun inspectLength(client: OkHttpClient, url: String): RemoteRangeIdentity {
            client.newCall(
                Request.Builder().url(url)
                    // A ranged FD maps raw bytes at exact offsets. Disallow
                    // HTTP transparent gzip/br transformations at the wire.
                    .header("Accept-Encoding", "identity")
                    .header("Range", "bytes=0-0").get().build()
            ).execute().use { response ->
                if (response.code != 206) throw IOException("Server does not support byte ranges")
                if (!isIdentityByteRangeResponse(
                        response.header("Content-Encoding"), response.header("Content-Length"), 1
                    )
                ) throw IOException("Byte-range response is encoded or has an incorrect length")
                val range = parseContentRange(response.header("Content-Range"))
                    ?: throw IOException("Missing stable content range")
                if (range.first != 0L || range.second != 0L || range.third <= 0L)
                    throw IOException("Invalid initial content range")
                return RemoteRangeIdentity(
                    length = range.third,
                    strongEtag = parseStrongEtag(response.header("ETag"))
                )
            }
        }

        private fun fetchSubtitle(
            context: Context,
            client: OkHttpClient,
            subtitle: CaptionCandidate
        ): File? {
            if (subtitle.format !in setOf(CaptionFormat.SRT, CaptionFormat.VTT) ||
                MediaForgeUrlPolicy.safeHttpsUrl(subtitle.url) == null
            ) return null
            return runCatching {
                val bytes = client.newCall(
                    Request.Builder().url(subtitle.url).get().build()
                ).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Subtitle unavailable")
                    if (response.header("Content-Type").orEmpty().contains("text/html", true))
                        throw IOException("Unexpected subtitle content")
                    val input = response.body?.byteStream() ?: throw IOException("No subtitle body")
                    input.use { stream ->
                        val out = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (out.size() <= 512 * 1024) {
                            val allowed = minOf(buffer.size, 512 * 1024 + 1 - out.size())
                            if (allowed <= 0) break
                            val count = stream.read(buffer, 0, allowed)
                            if (count == -1) break
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    }
                }
                if (bytes.isEmpty() || bytes.size > 512 * 1024)
                    throw IOException("Subtitle exceeds bounded limit")
                val ext = if (subtitle.format == CaptionFormat.SRT) ".srt" else ".vtt"
                File.createTempFile("mediaforge-subtitle-", ext, context.cacheDir).also {
                    try {
                        it.writeBytes(bytes)
                    } catch (error: Exception) {
                        it.delete()
                        throw error
                    }
                }
            }.getOrNull()
        }
    }
}

/**
 * An Android ProxyFileDescriptor requires a byte-for-byte, seekable remote
 * representation. Compressed HTTP entity content or a mismatched declared
 * range length must fail closed, even on otherwise valid HTTP 206 responses.
 *
 * Missing Content-Length is allowed for HTTP chunked transfer. The reader
 * independently enforces the exact expected byte count and its memory cap.
 */
internal fun isIdentityByteRangeResponse(
    contentEncoding: String?,
    contentLength: String?,
    expectedBytes: Int
): Boolean {
    if (expectedBytes <= 0) return false
    if (contentEncoding != null &&
        !contentEncoding.trim().equals("identity", ignoreCase = true)
    ) return false
    if (contentLength != null) {
        val declared = contentLength.trim().toLongOrNull() ?: return false
        if (declared != expectedBytes.toLong()) return false
    }
    return true
}

internal fun parseContentRange(value: String?): Triple<Long, Long, Long>? {
    val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)
        .matchEntire(value?.trim() ?: return null) ?: return null
    return runCatching {
        Triple(
            match.groupValues[1].toLong(),
            match.groupValues[2].toLong(),
            match.groupValues[3].toLong()
        )
    }.getOrNull()
}

/**
 * A strong ETag gives every subsequent 206 range read a stable representation.
 * Weak ETags cannot be used with If-Range. Sources without an ETag still require
 * identical Content-Range totals but cannot prove byte-level version identity.
 */
internal data class RemoteRangeIdentity(val length: Long, val strongEtag: String?)

internal fun parseStrongEtag(header: String?): String? {
    val value = header?.trim() ?: return null
    if (value.length !in 2..512 || !value.startsWith('"') || !value.endsWith('"')) return null
    // RFC entity-tag opaque bytes exclude ASCII whitespace, controls and quotes.
    if (value.substring(1, value.length - 1).any {
            it.code != 33 && it.code !in 35..126 && it.code !in 128..255
        }) return null
    return value
}

internal fun isConsistentRangeResponse(
    identity: RemoteRangeIdentity,
    start: Long,
    end: Long,
    contentRange: String?,
    returnedEtag: String?
): Boolean {
    val range = parseContentRange(contentRange) ?: return false
    if (range.first != start || range.second != end || range.third != identity.length) return false
    // Some servers omit ETag from 206 responses; If-Range is sent when available.
    // A conflicting returned tag is nevertheless always a hard failure.
    if (identity.strongEtag != null && returnedEtag != null &&
        parseStrongEtag(returnedEtag) != identity.strongEtag
    ) return false
    return true
}

internal const val MEDIAFORGE_RANGE_WINDOW_BYTES = 256L * 1024L
internal const val MEDIAFORGE_MAX_CACHED_WINDOWS = 2

/**
 * A tiny LRU of decoded byte-range responses, intentionally owned by the
 * StorageManager callback thread (no shared mutable cache across VLC threads).
 * Container parsers often alternate between the MP4 head and tail index;
 * keeping both windows avoids repeat network fetches with a 512-KiB ceiling.
 */
internal class MediaForgeRangeWindowCache {
    private val windows = LinkedHashMap<Long, ByteArray>(
        MEDIAFORGE_MAX_CACHED_WINDOWS, 0.75f, true
    )

    fun get(start: Long): ByteArray? = windows[start]

    fun put(start: Long, bytes: ByteArray) {
        require(start >= 0L && bytes.isNotEmpty() &&
            bytes.size <= MEDIAFORGE_RANGE_WINDOW_BYTES.toInt()
        )
        windows[start] = bytes
        while (windows.size > MEDIAFORGE_MAX_CACHED_WINDOWS) {
            val eldest = windows.entries.iterator()
            if (!eldest.hasNext()) break
            val key = eldest.next().key
            windows.remove(key)
        }
    }

    val retainedWindows: Int get() = windows.size
    val retainedBytes: Int get() = windows.values.sumOf { it.size }
}

internal fun alignedRangeWindowStart(position: Long, windowBytes: Long = MEDIAFORGE_RANGE_WINDOW_BYTES): Long {
    require(position >= 0L && windowBytes > 0L)
    return position - position % windowBytes
}

/**
 * All file reads happen on the Android StorageManager callback thread.
 * A single bounded 256-KiB window avoids dozens of GETs for tiny VLC reads.
 */
private class GuardedRangeCallback(
    private val client: OkHttpClient,
    private val url: String,
    private val identity: RemoteRangeIdentity
) : ProxyFileDescriptorCallback() {
    private val total = identity.length
    private val stopped = AtomicBoolean(false)
    @Volatile private var call: Call? = null
    private val rangeCache = MediaForgeRangeWindowCache()

    fun stop() {
        stopped.set(true)
        call?.cancel()
    }

    override fun onGetSize(): Long = total

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if (stopped.get()) throw ErrnoException("read", OsConstants.EBADF)
        if (offset < 0L || size < 0 || size > data.size)
            throw ErrnoException("read", OsConstants.EINVAL)
        if (offset >= total || size == 0) return 0
        val count = minOf(size.toLong(), total - offset).toInt()
        var copied = 0
        try {
            while (copied < count) {
                if (stopped.get()) throw IOException("File source closed")
                val position = offset + copied
                val start = alignedRangeWindowStart(position)
                val window = rangeCache.get(start) ?: loadWindow(start)
                val from = (position - start).toInt()
                val available = minOf(window.size - from, count - copied)
                if (available <= 0) throw IOException("Short byte-range response")
                System.arraycopy(window, from, data, copied, available)
                copied += available
            }
        } catch (_: IOException) {
            throw ErrnoException("read", OsConstants.EIO)
        }
        return copied
    }

    private fun loadWindow(start: Long): ByteArray {
        // The callback thread owns this cache. Only publish fully verified
        // 206 payloads, never partially downloaded or failed windows.
        val expected = minOf(MEDIAFORGE_RANGE_WINDOW_BYTES, total - start).toInt()
        if (expected <= 0) throw IOException("End of stream")
        val end = start + expected - 1L
        val requestBuilder = Request.Builder().url(url)
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$start-$end")
        identity.strongEtag?.let { requestBuilder.header("If-Range", it) }
        val request = requestBuilder.get().build()
        val activeCall = client.newCall(request)
        call = activeCall
        try {
            activeCall.execute().use { response ->
                if (response.code != 206) throw IOException("Server ignored byte-range request")
                if (!isIdentityByteRangeResponse(
                        response.header("Content-Encoding"), response.header("Content-Length"), expected
                    )
                ) throw IOException("Byte-range response is encoded or has an incorrect length")
                if (!isConsistentRangeResponse(
                        identity, start, end,
                        response.header("Content-Range"), response.header("ETag")
                    )
                ) throw IOException("Media version or byte range changed")
                val fresh = ByteArray(expected)
                val input = response.body?.byteStream() ?: throw IOException("No range body")
                input.use { stream ->
                    var read = 0
                    while (read < expected) {
                        if (stopped.get()) throw IOException("File source closed")
                        val n = stream.read(fresh, read, expected - read)
                        if (n == -1) throw IOException("Truncated range body")
                        read += n
                    }
                }
                rangeCache.put(start, fresh)
                return fresh
            }
        } finally {
            call = null
        }
    }

    override fun onRelease() { stop() }
}
