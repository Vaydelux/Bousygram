package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.core.tdlib.server.TelegramFileGateway
import com.vaydelux.bousygram.core.tdlib.server.TelegramGatewayFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Re-resolves a Telegram message before playback; a persisted fileId alone may be stale.
 * All TDLib lookups are bounded and recoverable, but cancellation of the player scope
 * must still propagate. No login, cache deletion, or stale global credentials are involved.
 */
internal class PlaybackFileRecovery(
    private val gateway: TelegramFileGateway,
    private val messageTimeoutMs: Long = 2_500L,
    private val fileTimeoutMs: Long = 1_000L
) {
    suspend fun refresh(chatId: Long, messageId: Long, originalFileId: Int): TelegramGatewayFile? {
        if (originalFileId <= 0) return null
        if (chatId != 0L && messageId != 0L) {
            val resolved = safeLookup(messageTimeoutMs) { gateway.reResolveFile(chatId, messageId) }
            if (resolved != null && resolved.fileId > 0) return resolved
        }
        return safeLookup(fileTimeoutMs) { gateway.getFile(originalFileId) }
            ?.takeIf { it.fileId == originalFileId }
    }

    private suspend fun <T> safeLookup(timeoutMs: Long, block: suspend () -> T): T? {
        return try {
            withTimeoutOrNull(timeoutMs) { block() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A transient TDLib error is not permission to strand a video on Preparing.
            null
        }
    }
}
