package com.vaydelux.bousygram.core.tdlib.server

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import kotlinx.coroutines.CancellationException
import org.drinkless.tdlib.TdApi

internal data class TelegramGatewayFile(
    val fileId: Int,
    val localPath: String,
    val isDownloadingCompleted: Boolean,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long,
    val isDownloadingActive: Boolean = false,
    val size: Long = 0L,
    val expectedSize: Long = 0L
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

    suspend fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean = false): Boolean = true

    suspend fun reResolveFile(chatId: Long, messageId: Long): TelegramGatewayFile? = null
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
    ): TelegramGatewayResult<TelegramGatewayFile?> {
        // All file requests are tied to the *same* verified account + native generation.
        // A native restart or logout cancels the suspension rather than leaving the
        // caller waiting on a response that TDLib will never deliver.
        val owner = AccountSession.current() ?: throw CancellationException("No active Telegram account")
        return downloadForOwner(owner, fileId, priority, offset, limit, synchronous)
    }

    internal suspend fun downloadForOwner(owner: AccountSnapshot, fileId: Int, priority: Int,
        offset: Long, limit: Int, synchronous: Boolean = false): TelegramGatewayResult<TelegramGatewayFile?> {
        val response = SharedTelegramFileMutationFence.instance.legacy(owner, fileId) {
            TdLibClient.awaitAccountResponse(
                TdApi.DownloadFile(fileId, priority, offset.coerceAtLeast(0L), limit.toLong(), synchronous),
                expectedOwner = owner, timeoutMs = if (synchronous) 45_000L else 15_000L
            ) ?: throw CancellationException("Uncertain Telegram download response")
        }
        return when (response) {
            is TdApi.File -> TelegramGatewayResult.Success(response.toGatewayFile())
            is TdApi.Error -> {
                TelegramLogWrapper.logError("DownloadFile", response)
                TelegramGatewayResult.Failure(
                    code = response.code,
                    message = response.message,
                    fatal = response.code == 400 || response.code == 404
                )
            }
            else -> TelegramGatewayResult.Success(null)
        }
    }

    override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long {
        val response = TdLibClient.awaitAccountResponse(
            TdApi.GetFileDownloadedPrefixSize(fileId, offset.coerceAtLeast(0L))
        )
        return when (response) {
            is TdApi.FileDownloadedPrefixSize -> response.size.coerceAtLeast(0L)
            is TdApi.Error -> {
                TelegramLogWrapper.logError("GetFileDownloadedPrefixSize", response)
                0L
            }
            else -> 0L
        }
    }

    override suspend fun getFile(fileId: Int): TelegramGatewayFile? {
        val response = TdLibClient.awaitAccountResponse(TdApi.GetFile(fileId))
        return (response as? TdApi.File)?.toGatewayFile()
    }

    override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? {
        val response = TdLibClient.awaitAccountResponse(
            TdApi.ReadFilePart(fileId, offset.coerceAtLeast(0L), limit.toLong()),
            timeoutMs = 20_000L
        )
        return when (response) {
            is TdApi.Data -> response.data?.takeIf { it.isNotEmpty() }
            is TdApi.Error -> {
                TelegramLogWrapper.logError("ReadFilePart", response)
                null
            }
            else -> null
        }
    }

    override suspend fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean): Boolean {
        val owner = AccountSession.current() ?: throw CancellationException("No active Telegram account")
        return cancelForOwner(owner, fileId, onlyIfPending)
    }

    internal suspend fun cancelForOwner(owner: AccountSnapshot, fileId: Int, onlyIfPending: Boolean): Boolean {
        val response = SharedTelegramFileMutationFence.instance.legacy(owner, fileId) {
            TdLibClient.awaitAccountResponse(TdApi.CancelDownloadFile(fileId, onlyIfPending),
                expectedOwner = owner) ?: throw CancellationException("Uncertain Telegram cancellation response")
        }
        return response is TdApi.Ok
    }

    internal suspend fun deleteForOwner(owner: AccountSnapshot, fileId: Int, protected: () -> Boolean): Boolean =
        SharedTelegramFileMutationFence.instance.maintenance(owner, fileId, protected) {
            val response = TdLibClient.awaitAccountResponse(TdApi.DeleteFile(fileId), expectedOwner = owner,
                timeoutMs = 4_000L) ?: throw CancellationException("Uncertain Telegram deletion response")
            response is TdApi.Ok
        } ?: false

    override suspend fun reResolveFile(chatId: Long, messageId: Long): TelegramGatewayFile? {
        // Capture once: GetMessage from account A must never be followed by GetFile in account B.
        val owner = com.vaydelux.bousygram.core.account.AccountSession.current() ?: return null
        val response = TdLibClient.awaitAccountResponse(
            TdApi.GetMessage(chatId, messageId), expectedOwner = owner
        )
        val message = response as? TdApi.Message ?: return null
        val targetFileId = when (val content = message.content) {
            is TdApi.MessageVideo -> content.video?.video?.id
            is TdApi.MessageVideoNote -> content.videoNote?.video?.id
            is TdApi.MessageAnimation -> content.animation?.animation?.id
            is TdApi.MessageDocument -> content.document?.document?.id
            else -> null
        }
        if (targetFileId == null || targetFileId <= 0) return null
        val fileResponse = TdLibClient.awaitAccountResponse(
            TdApi.GetFile(targetFileId), expectedOwner = owner
        )
        return (fileResponse as? TdApi.File)?.toGatewayFile()
    }

    internal fun TdApi.File.toGatewayFile(): TelegramGatewayFile =
        TelegramGatewayFile(
            fileId = id,
            localPath = local?.path.orEmpty(),
            isDownloadingCompleted = local?.isDownloadingCompleted == true,
            downloadOffset = local?.downloadOffset ?: 0L,
            downloadedPrefixSize = local?.downloadedPrefixSize ?: 0L,
            isDownloadingActive = local?.isDownloadingActive == true,
            size = size,
            expectedSize = expectedSize
        )
}
