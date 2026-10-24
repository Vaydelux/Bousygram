package com.vaydelux.bousygram.core.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.PlaybackCacheState
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkPurpose
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkRequest
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkResult
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.math.min

/**
 * Direct in-process Media3 [DataSource] for ExoPlayer.
 *
 * Reads byte ranges directly from [TelegramChunkScheduler] and TDLib without an intermediate
 * loopback HTTP socket, token validation, or cleartext network exceptions.
 * Access is authorized by verifying that the requested fileId is tied to an active [PlaybackSession].
 */
@OptIn(UnstableApi::class)
class TdLibDataSource : BaseDataSource(/* isNetwork = */ true) {

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = TdLibDataSource()
    }

    private var currentDataSpec: DataSpec? = null
    private var uri: Uri? = null
    private var fileId: Int = 0
    private var totalSize: Long = 0L
    private var bytesRemaining: Long = 0L
    private var currentPosition: Long = 0L

    @Volatile
    private var opened: Boolean = false

    @Volatile
    private var isClosed: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        isClosed = false

        val fileIdParam = dataSpec.uri.getQueryParameter("fileId")?.toIntOrNull()
            ?: dataSpec.uri.host?.toIntOrNull()
            ?: dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Invalid TDLib URI: ${dataSpec.uri}")

        fileId = fileIdParam

        // Direct data source access needs no token, but must be tied to an active playback session.
        if (!PlaybackSessionController.isFileActive(fileId)) {
            throw IOException("Direct TDLib data source access denied: fileId $fileId is not tied to an active playback session")
        }

        val sizeParam = dataSpec.uri.getQueryParameter("size")?.toLongOrNull() ?: 0L
        totalSize = if (sizeParam > 0L) sizeParam else TelegramChunkScheduler.fileSize(fileId)
        if (totalSize <= 0L) {
            totalSize = LocalStreamingServer.resolveFileSize(fileId)
        }
        if (totalSize > 0L) {
            TelegramChunkScheduler.noteFileSize(fileId, totalSize)
        }

        currentPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (totalSize > 0L) {
            (totalSize - dataSpec.position).coerceAtLeast(0L)
        } else {
            C.LENGTH_UNSET.toLong()
        }

        // Notify scheduler of streaming start with the same priority as the HTTP path
        TelegramChunkScheduler.markState(fileId, PlaybackCacheState.Streaming)
        TelegramChunkScheduler.openPlayback(fileId, totalSize)

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (isClosed || !opened) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            min(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }
        if (bytesToRead <= 0) return C.RESULT_END_OF_INPUT

        var attempt = 0
        val maxAttempts = if (com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular) 90 else 75
        var readyBytes: ByteArray? = null

        while (attempt < maxAttempts && opened && !isClosed) {
            val chunk = runBlocking {
                TelegramChunkScheduler.fetchChunk(
                    TelegramChunkRequest(
                        fileId = fileId,
                        offset = currentPosition,
                        limit = bytesToRead,
                        priority = STREAM_PRIORITY, // 32 = TDLib maximum streaming priority (same as HTTP path)
                        purpose = TelegramChunkPurpose.Streaming
                    )
                )
            }

            when (chunk) {
                is TelegramChunkResult.Ready -> {
                    readyBytes = chunk.bytes
                    break
                }
                is TelegramChunkResult.FatalFailure -> {
                    throw IOException("TDLib direct read fatal failure: ${chunk.message}")
                }
                is TelegramChunkResult.RetryableFailure -> {
                    // Retry with backoff
                }
                TelegramChunkResult.Pending -> {
                    // Range pending; retry with backoff
                }
            }

            if (attempt == 0) {
                TelegramChunkScheduler.markState(fileId, PlaybackCacheState.Streaming)
            }
            attempt++
            try {
                Thread.sleep(if (attempt < 5) 150L else 350L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("TDLib direct read interrupted", e)
            }
        }

        if (isClosed || !opened) {
            throw IOException("TDLib direct read aborted")
        }

        val bytes = readyBytes
            ?: throw IOException("Timed out waiting for TDLib chunk at offset $currentPosition (fileId=$fileId)")

        if (bytes.isEmpty()) {
            return if (bytesRemaining == C.LENGTH_UNSET.toLong() || bytesRemaining == 0L) {
                C.RESULT_END_OF_INPUT
            } else {
                throw IOException("Unexpected empty chunk from TDLib at offset $currentPosition")
            }
        }

        // Notify telemetry of first byte delivery
        PlaybackTelemetryRecorder.notifyFirstByte(fileId)

        // Support short reads when TDLib chunk has fewer bytes than requested
        val bytesCopied = min(bytesToRead, bytes.size)
        System.arraycopy(bytes, 0, target, offset, bytesCopied)
        currentPosition += bytesCopied
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesCopied
        }

        bytesTransferred(bytesCopied)
        return bytesCopied
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        if (opened) {
            opened = false
            isClosed = true
            currentDataSpec = null
            // Seek-cancel behavior: cancel active downloads/prefetch at the abandoned offset
            TelegramChunkScheduler.onClientDisconnected(fileId, currentPosition)
            transferEnded()
        }
    }

    companion object {
        const val SCHEME = "tdlib"
        const val STREAM_PRIORITY = 32

        fun isTdLibUri(uri: Uri?): Boolean =
            uri?.scheme.equals(SCHEME, ignoreCase = true)

        fun isDirectPlayable(uri: Uri?): Boolean {
            if (uri == null) return false
            if (uri.scheme.equals(SCHEME, ignoreCase = true)) return true
            if ((uri.host == "127.0.0.1" || uri.host == "localhost") && uri.getQueryParameter("fileId") != null) return true
            return false
        }

        fun buildUri(fileId: Int, size: Long, fileName: String? = null): Uri {
            val builder = Uri.Builder()
                .scheme(SCHEME)
                .authority(fileId.toString())
                .appendQueryParameter("fileId", fileId.toString())
                .appendQueryParameter("size", size.toString())
            if (!fileName.isNullOrBlank()) {
                builder.appendQueryParameter("name", fileName)
            }
            return builder.build()
        }
    }
}
