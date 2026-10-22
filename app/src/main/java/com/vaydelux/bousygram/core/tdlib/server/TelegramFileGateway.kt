package com.vaydelux.bousygram.core.tdlib.server

import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume

internal data class TelegramGatewayFile(
    val fileId: Int,
    val localPath: String,
    val isDownloadingCompleted: Boolean,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long,
    val isDownloadingActive: Boolean = false
)

internal sealed class TelegramGatewayResult<out T> {
    data class Success<T>(val value: T) : TelegramGatewayResult<T>()
    data class Failure(
        val code: Int,
        val message: String,
        val fatal: Boolean
    ) : TelegramGatewayResult<Nothing>()
}

internal interface TelegramFileGateway {
    suspend fun downloadFile(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Int,
        synchronous: Boolean = false
    ): TelegramGatewayResult<TelegramGatewayFile?>

    suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long

    suspend fun getFile(fileId: Int): TelegramGatewayFile?

    suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray?
}

internal object TelegramFileGatewayRegistry {
    @Volatile
    var current: TelegramFileGateway = TdLibTelegramFileGateway
        private set

    fun installForTests(gateway: TelegramFileGateway) {
        current = gateway
    }

    fun reset() {
        current = TdLibTelegramFileGateway
    }
}

internal object TdLibTelegramFileGateway : TelegramFileGateway {
    override suspend fun downloadFile(
        fileId: Int,
        priority: Int,
        offset: Long,
        limit: Int,
        synchronous: Boolean
    ): TelegramGatewayResult<TelegramGatewayFile?> =
        suspendCancellableCoroutine { continuation ->
            TdLibClient.client.send(
                TdApi.DownloadFile(
                    fileId,
                    priority,
                    offset.coerceAtLeast(0L),
                    limit.toLong(),
                    synchronous
                ),
                handler@{ response ->
                    if (!continuation.isActive) return@handler
                    when (response) {
                        is TdApi.File -> continuation.resume(TelegramGatewayResult.Success(response.toGatewayFile()))
                        is TdApi.Error -> {
                            TelegramLogWrapper.logError("DownloadFile", response)
                            continuation.resume(
                                TelegramGatewayResult.Failure(
                                    code = response.code,
                                    message = response.message,
                                    fatal = response.code == 400 || response.code == 404
                                )
                            )
                        }
                        else -> continuation.resume(TelegramGatewayResult.Success(null))
                    }
                }
            )
        }

    override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long =
        suspendCancellableCoroutine { continuation ->
            TdLibClient.client.send(
                TdApi.GetFileDownloadedPrefixSize(fileId, offset.coerceAtLeast(0L)),
                handler@{ response ->
                    if (!continuation.isActive) return@handler
                    val size = when (response) {
                        is TdApi.FileDownloadedPrefixSize -> response.size.coerceAtLeast(0L)
                        is TdApi.Error -> {
                            TelegramLogWrapper.logError("GetFileDownloadedPrefixSize", response)
                            0L
                        }
                        else -> 0L
                    }
                    continuation.resume(size)
                }
            )
        }

    override suspend fun getFile(fileId: Int): TelegramGatewayFile? =
        suspendCancellableCoroutine { continuation ->
            TdLibClient.client.send(
                TdApi.GetFile(fileId),
                handler@{ response ->
                    if (!continuation.isActive) return@handler
                    continuation.resume((response as? TdApi.File)?.toGatewayFile())
                }
            )
        }

    override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            TdLibClient.client.send(
                TdApi.ReadFilePart(fileId, offset.coerceAtLeast(0L), limit.toLong()),
                handler@{ response ->
                    if (!continuation.isActive) return@handler
                    when (response) {
                        is TdApi.Data -> continuation.resume(response.data?.takeIf { it.size == limit })
                        is TdApi.Error -> {
                            TelegramLogWrapper.logError("ReadFilePart", response)
                            continuation.resume(null)
                        }
                        else -> continuation.resume(null)
                    }
                }
            )
        }

    private fun TdApi.File.toGatewayFile(): TelegramGatewayFile =
        TelegramGatewayFile(
            fileId = id,
            localPath = local?.path.orEmpty(),
            isDownloadingCompleted = local?.isDownloadingCompleted == true,
            downloadOffset = local?.downloadOffset ?: 0L,
            downloadedPrefixSize = local?.downloadedPrefixSize ?: 0L,
            isDownloadingActive = local?.isDownloadingActive == true
        )
}
