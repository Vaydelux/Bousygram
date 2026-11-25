package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import com.vaydelux.bousygram.core.tdlib.server.TelegramFileMutationFence
import com.vaydelux.bousygram.core.tdlib.server.SharedTelegramFileMutationFence

/**
 * Explicit owner-bound native request adapter. Debug-only, no automatic caller.
 * No re-resolution or cancellation: remapped IDs and ambiguous responses fail
 * closed. Only the driver may issue a bounded request after storage admission.
 */
internal class AccountBoundCastFileGateway(private val claim: TelegramFileMutationFence.Claim) : RollingHlsOwnedWindowDriver.Gateway {
    private fun permitted(owner: AccountSnapshot, fileId: Int): Boolean =
        claim.owner == owner && claim.fileId == fileId && claim.lock.isLocked &&
            SharedTelegramFileMutationFence.instance.isCurrent(claim)

    private fun statusOf(owner: AccountSnapshot, fileId: Int, response: TdApi.Object?):
        RollingHlsOwnedWindowDriver.Status? {
        val file = response as? TdApi.File ?: return null
        val local = file.local ?: return null
        if (!permitted(owner, fileId) || file.id != fileId || file.size <= 0L) return null
        return RollingHlsOwnedWindowDriver.Status(file.id, file.size, local.isDownloadingActive)
    }

    override suspend fun status(owner: AccountSnapshot, fileId: Int): RollingHlsOwnedWindowDriver.Status? {
        require(fileId > 0)
        if (!permitted(owner, fileId)) return null
        return statusOf(owner, fileId, TdLibClient.awaitAccountResponse(
            TdApi.GetFile(fileId), expectedOwner = owner, timeoutMs = 5_000L))
    }

    override suspend fun start(owner: AccountSnapshot, fileId: Int, offset: Long, limit: Int):
        RollingHlsOwnedWindowDriver.Status? {
        require(fileId > 0 && offset >= 0 && limit > 0 &&
            limit.toLong() <= RollingHlsDownloadOwnership.MAX_DOWNLOAD_BYTES &&
            offset <= Long.MAX_VALUE - limit)
        if (!permitted(owner, fileId)) return null
        return statusOf(owner, fileId, TdLibClient.awaitAccountResponse(
            TdApi.DownloadFile(fileId, 16, offset, limit.toLong(), false),
            expectedOwner = owner, timeoutMs = 5_000L))
    }

    override suspend fun read(owner: AccountSnapshot, fileId: Int, offset: Long, count: Int): ByteArray? {
        require(fileId > 0 && offset >= 0 && count in 1..RollingHlsDownloadOwnership.MAX_READ_BYTES &&
            offset <= Long.MAX_VALUE - count)
        if (!permitted(owner, fileId)) return null
        val response = TdLibClient.awaitAccountResponse(
            TdApi.ReadFilePart(fileId, offset, count.toLong()),
            expectedOwner = owner, timeoutMs = 5_000L) as? TdApi.Data ?: return null
        val bytes = response.data ?: return null
        if (!permitted(owner, fileId) || bytes.size !in 1..count) {
            bytes.fill(0)
            return null
        }
        return bytes
    }
}
