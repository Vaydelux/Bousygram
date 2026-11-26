package com.vaydelux.bousygram.core.cast

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

/** Debug in-process live HLS adapter for synthetic media. No network,
 * arbitrary URLs, filesystem paths, public endpoint or private Telegram
 * content. A playlist lease remains held until Media3 closes the response.
 * Only an advertised retained segment can be reopened at a bounded offset.
 * Read completion is telemetry, never a playback acknowledgement. */
@UnstableApi
internal class RollingHlsLiveLocalTransport(private val store: RollingHlsLiveFileStore) {
    private val authority = UUID.randomUUID().toString()
    private val playlists = AtomicInteger()
    private val media = AtomicInteger()
    private val newest = AtomicLong()
    private val endList = AtomicBoolean()
    data class Diagnostics(val playlists: Int, val completedMediaReads: Int,
                           val newestCompletedSequence: Long, val endListRead: Boolean)

    fun diagnostics() = Diagnostics(playlists.get(), media.get(), newest.get(), endList.get())
    fun uri(): Uri = Uri.Builder().scheme(SCHEME).authority(authority).appendPath("index.m3u8").build()
    fun factory(): DataSource.Factory = DataSource.Factory { Source() }

    private inner class Source : BaseDataSource(false) {
        private var uri: Uri? = null
        private var playlist: RollingHlsLiveFileStore.Playlist? = null
        private var lease: RollingHlsLiveFileStore.Lease? = null
        private var memory: ByteArray? = null
        private var cursor = 0
        private var remaining = 0L
        private var started = false
        private var failed = false
        private var finalPlaylist = false
        private var wholeResource = false

        override fun getUri(): Uri? = uri

        override fun open(dataSpec: DataSpec): Long {
            check(uri == null && !started)
            val requested = dataSpec.uri
            if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET || dataSpec.httpBody != null ||
                dataSpec.position < 0 || requested.scheme != SCHEME ||
                requested.encodedAuthority != authority || requested.encodedQuery != null ||
                requested.encodedFragment != null || !store.available())
                throw IOException("Live local resource refused")
            transferInitializing(dataSpec)
            try {
                val length = dataSpec.length.takeUnless { it == C.LENGTH_UNSET.toLong() }
                if (length != null && length <= 0) throw IOException("Invalid live range")
                when (val path = requested.encodedPath) {
                    "/index.m3u8", "/init.mp4" -> {
                        // The initialization bytes are immutable for this authority.
                        // A mutable playlist must still be fetched as a whole.
                        if (path == "/index.m3u8" && dataSpec.position != 0L)
                            throw IOException("Invalid playlist offset")
                        memory = if (path == "/index.m3u8") {
                            val response = store.openPlaylist() ?: throw IOException("Live playlist unavailable")
                            playlist = response
                            val text = response.text() ?: throw IOException("Live playlist revoked")
                            finalPlaylist = text.endsWith("#EXT-X-ENDLIST\n")
                            text.toByteArray(Charsets.UTF_8)
                        } else store.initialization() ?: throw IOException("Live initialization unavailable")
                        val size = checkNotNull(memory).size.toLong()
                        if (dataSpec.position !in 0L until size) throw IOException("Invalid metadata offset")
                        cursor = dataSpec.position.toInt()
                        val available = size - dataSpec.position
                        if (path == "/index.m3u8") {
                            if (length != null && length != available) throw IOException("Playlist range refused")
                            remaining = available
                        } else {
                            remaining = length ?: available
                            if (remaining !in 1L..available) throw IOException("Initialization range refused")
                        }
                    }
                    else -> {
                        if (path == null || !path.startsWith("/segment/") || !path.endsWith(".m4s") ||
                            path.length !in 14..32) throw IOException("Invalid live resource")
                        val text = path.substring(9, path.length - 4)
                        if (text.firstOrNull() == '0' || text.any { it !in '0'..'9' })
                            throw IOException("Invalid segment sequence")
                        val sequence = text.toLongOrNull()?.takeIf { it in 1L until Long.MAX_VALUE }
                            ?: throw IOException("Invalid segment sequence")
                        val opened = store.open(sequence, dataSpec.position, length)
                            ?: throw IOException("Live segment unavailable")
                        lease = opened
                        remaining = opened.length
                        wholeResource = dataSpec.position == 0L && length == null
                    }
                }
                if (remaining <= 0 || !store.available()) throw IOException("Live resource revoked")
                uri = requested
                started = true
                transferStarted(dataSpec)
                return remaining
            } catch (error: Exception) {
                failed = true
                try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                throw error
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset <= buffer.size - length)
            if (length == 0) return 0
            try {
                if (!started || failed || !store.available()) throw IOException("Live resource revoked")
                if (remaining == 0L) return C.RESULT_END_OF_INPUT
                val count = minOf(remaining, length.toLong(), 32L * 1024).toInt()
                val data = memory
                val n = if (data != null) {
                    data.copyInto(buffer, offset, cursor, cursor + count)
                    cursor += count
                    count
                } else checkNotNull(lease).read(buffer, offset, count)
                if (n !in 1..count || !store.available()) throw IOException("Live resource read failed")
                remaining -= n
                bytesTransferred(n)
                return n
            } catch (error: Exception) {
                failed = true
                buffer.fill(0, offset, offset + length)
                throw error
            }
        }

        override fun close() {
            val complete = started && !failed && remaining == 0L && store.available()
            val seq = lease?.sequence
            val wasPlaylist = playlist != null
            try {
                try { lease?.close() } finally { playlist?.close() }
                if (complete && store.available()) {
                    if (wasPlaylist) {
                        playlists.incrementAndGet()
                        if (finalPlaylist) endList.set(true)
                    } else if (seq != null && wholeResource) {
                        media.incrementAndGet()
                        newest.updateAndGet { maxOf(it, seq) }
                    }
                }
            } finally {
                lease = null
                playlist = null
                memory?.fill(0)
                memory = null
                uri = null
                cursor = 0
                remaining = 0
                failed = false
                finalPlaylist = false
                wholeResource = false
                if (started) { started = false; transferEnded() }
            }
        }
    }

    companion object { private const val SCHEME = "bousygram-live" }
}
