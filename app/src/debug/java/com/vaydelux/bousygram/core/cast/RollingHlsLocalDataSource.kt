package com.vaydelux.bousygram.core.cast

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/**
 * Debug finite receiver qualification only. No socket, HTTP fallback, URL
 * redirection, local file path or arbitrary range access. Media3 uses the
 * same account-bound delivery session as the private transport dispatcher.
 * A fully read unit records local transfer only, never playback completion.
 */
@UnstableApi
internal class RollingHlsLocalDataSource(
    private val session: RollingHlsDeliverySession
) : BaseDataSource(false) {
    private var uri: Uri? = null
    private var memory: ByteArray? = null
    private var transfer: RollingHlsDeliverySession.Transfer? = null
    private var position = 0
    private var remaining = 0L
    private var started = false
    private var failed = false

    override fun getUri(): Uri? = uri

    override fun open(dataSpec: DataSpec): Long {
        check(!started && uri == null)
        val requested = dataSpec.uri
        if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET || dataSpec.httpBody != null ||
            dataSpec.position != 0L || requested.scheme != SCHEME ||
            requested.encodedAuthority != session.resourceId ||
            requested.encodedQuery != "token=${session.readToken}" ||
            requested.encodedFragment != null || !session.active())
            throw IOException("Unsupported local receiver request")
        transferInitializing(dataSpec)
        try {
            when (val path = requested.encodedPath) {
                "/index.m3u8" -> memory = session.playlist(session.resourceId, session.readToken)
                    ?.toByteArray(Charsets.UTF_8) ?: throw IOException("Playlist unavailable")
                "/init.mp4" -> memory = session.initialization(session.resourceId, session.readToken)
                    ?: throw IOException("Initialization unavailable")
                else -> {
                    if (path == null || !path.startsWith("/segment/") || !path.endsWith(".m4s") ||
                        path.length !in 14..32) throw IOException("Unsupported local resource")
                    val seq = path.substring(9, path.length - 4)
                    transfer = session.open(session.resourceId, session.readToken, seq)
                        ?: throw IOException("Segment unavailable")
                }
            }
            remaining = memory?.size?.toLong() ?: checkNotNull(transfer).segment.size
            if (remaining <= 0 || (dataSpec.length != C.LENGTH_UNSET.toLong() && dataSpec.length != remaining) ||
                !session.active()) throw IOException("Unsupported local resource length")
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
            if (!started || failed || !session.active()) throw IOException("Local receiver revoked")
            if (remaining == 0L) return C.RESULT_END_OF_INPUT
            val count = minOf(length.toLong(), remaining, 32L * 1024).toInt()
            val data = memory
            val read = if (data != null) {
                data.copyInto(buffer, offset, position, position + count)
                position += count
                count
            } else checkNotNull(transfer).read(buffer, offset, count)
            if (read !in 1..count || !session.active()) throw IOException("Local receiver read failed")
            remaining -= read
            bytesTransferred(read)
            return read
        } catch (error: Exception) {
            failed = true
            buffer.fill(0, offset, offset + length)
            throw error
        }
    }

    override fun close() {
        val complete = started && !failed && remaining == 0L && session.active()
        try {
            transfer?.complete(complete)
        } finally {
            transfer = null
            memory?.fill(0)
            memory = null
            uri = null
            remaining = 0
            position = 0
            failed = false
            if (started) {
                started = false
                transferEnded()
            }
        }
    }

    companion object {
        private const val SCHEME = "bousygram-hls"
        fun playlistUri(session: RollingHlsDeliverySession): Uri =
            Uri.Builder().scheme(SCHEME).authority(session.resourceId)
                .appendPath("index.m3u8").appendQueryParameter("token", session.readToken).build()
        fun factory(session: RollingHlsDeliverySession): DataSource.Factory =
            DataSource.Factory { RollingHlsLocalDataSource(session) }
    }
}
