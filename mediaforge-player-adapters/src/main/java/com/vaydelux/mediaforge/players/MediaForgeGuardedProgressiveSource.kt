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
import java.io.File
import java.io.IOException
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
            val client = MediaForgePlaybackTransport.client
            val length = inspectLength(client, stream.url)
            coroutineContext.ensureActive()
            val subtitle = if (caption != null) fetchSubtitle(context, client, caption) else null
            val callback = GuardedRangeCallback(client, stream.url, length)
            val thread = HandlerThread("mediaforge-vlc-range-io")
            try {
                thread.start()
                val storage = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val descriptor = storage.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY,
                    callback,
                    Handler(thread.looper)
                )
                coroutineContext.ensureActive()
                MediaForgeGuardedProgressiveSource(descriptor, subtitle, callback, thread)
            } catch (error: Exception) {
                callback.stop()
                thread.quitSafely()
                subtitle?.delete()
                throw error
            }
        }

        private fun inspectLength(client: OkHttpClient, url: String): Long {
            client.newCall(
                Request.Builder().url(url).header("Range", "bytes=0-0").get().build()
            ).execute().use { response ->
                if (response.code != 206) throw IOException("Server does not support byte ranges")
                val range = parseContentRange(response.header("Content-Range"))
                    ?: throw IOException("Missing stable content range")
                if (range.first != 0L || range.second != 0L || range.third <= 0L)
                    throw IOException("Invalid initial content range")
                return range.third
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
                    input.use { it.readNBytes(512 * 1024 + 1) }
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

private fun parseContentRange(value: String?): Triple<Long, Long, Long>? {
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
 * All file reads happen on the Android StorageManager callback thread.
 * A single bounded 256-KiB window avoids dozens of GETs for tiny VLC reads.
 */
private class GuardedRangeCallback(
    private val client: OkHttpClient,
    private val url: String,
    private val total: Long
) : ProxyFileDescriptorCallback() {
    private val stopped = AtomicBoolean(false)
    @Volatile private var call: Call? = null
    private var windowStart = -1L
    private var window = ByteArray(0)

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
                if (position < windowStart || position >= windowStart + window.size) {
                    loadWindow(position)
                }
                val from = (position - windowStart).toInt()
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

    private fun loadWindow(position: Long) {
        val expected = minOf(256L * 1024L, total - position).toInt()
        if (expected <= 0) throw IOException("End of stream")
        val end = position + expected - 1L
        val request = Request.Builder().url(url)
            .header("Range", "bytes=$position-$end")
            .get().build()
        val activeCall = client.newCall(request)
        call = activeCall
        try {
            activeCall.execute().use { response ->
                if (response.code != 206) throw IOException("Server ignored byte-range request")
                val range = parseContentRange(response.header("Content-Range"))
                    ?: throw IOException("Invalid content range")
                if (range.first != position || range.second != end || range.third != total) {
                    throw IOException("Unexpected or changed byte range")
                }
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
                windowStart = position
                window = fresh
            }
        } finally {
            call = null
        }
    }

    override fun onRelease() { stop() }
}
