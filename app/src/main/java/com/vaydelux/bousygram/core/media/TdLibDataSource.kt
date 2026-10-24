package com.vaydelux.bousygram.core.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
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
    private var opened: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)
        uri = dataSpec.uri

        val fileIdParam = dataSpec.uri.getQueryParameter("fileId")?.toIntOrNull()
            ?: dataSpec.uri.host?.toIntOrNull()
            ?: dataSpec.uri.lastPathSegment?.toIntOrNull()
            ?: throw IOException("Invalid TDLib URI: ${dataSpec.uri}")

        fileId = fileIdParam
        val sizeParam = dataSpec.uri.getQueryParameter("size")?.toLongOrNull() ?: 0L
        totalSize = if (sizeParam > 0L) sizeParam else TelegramChunkScheduler.fileSize(fileId)

        currentPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (totalSize > 0L) {
            (totalSize - dataSpec.position).coerceAtLeast(0L)
        } else {
            C.LENGTH_UNSET.toLong()
        }

        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            min(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val chunk = runBlocking {
            TelegramChunkScheduler.fetchChunk(
                TelegramChunkRequest(
                    fileId = fileId,
                    offset = currentPosition,
                    limit = bytesToRead,
                    priority = 32, // TDLib maximum streaming priority
                    purpose = TelegramChunkPurpose.Streaming
                )
            )
        }

        val bytes = when (chunk) {
            is TelegramChunkResult.Ready -> chunk.bytes
            is TelegramChunkResult.FatalFailure -> throw IOException("TDLib direct read fatal failure: ${chunk.message}")
            is TelegramChunkResult.RetryableFailure -> throw IOException("TDLib direct read retryable error: ${chunk.message}")
            TelegramChunkResult.Pending -> throw IOException("TDLib direct read timed out waiting for bytes")
        }

        if (bytes.isEmpty()) {
            return if (bytesRemaining == C.LENGTH_UNSET.toLong() || bytesRemaining == 0L) {
                C.RESULT_END_OF_INPUT
            } else {
                throw IOException("Unexpected empty chunk from TDLib at offset $currentPosition")
            }
        }

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
            currentDataSpec = null
            transferEnded()
        }
    }

    companion object {
        const val SCHEME = "tdlib"

        fun isTdLibUri(uri: Uri?): Boolean =
            uri?.scheme.equals(SCHEME, ignoreCase = true)

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
