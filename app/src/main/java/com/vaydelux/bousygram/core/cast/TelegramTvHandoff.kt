package com.vaydelux.bousygram.core.cast

/**
 * Cross-device identity is a chat/message reference, not TDLib's local file ID.
 * No session files, credentials, stream URLs, titles or thumbnails are sent.
 * This is a transport-neutral contract; it does not authenticate a peer.
 */
data class TelegramTvHandoff(
    val chatId: Long,
    val messageId: Long,
    val positionMs: Long,
    val playRequested: Boolean
) {
    fun isValid(): Boolean = chatId != 0L && messageId > 0L &&
        positionMs in 0L..MAX_POSITION_MS

    companion object {
        const val MAX_POSITION_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * Only call after authenticated pairing and explicit receiver acceptance.
 * The resolver must use the TV's current account, verify the message and
 * return its own local file ID. The caller must bind resolution and player
 * launch to the same receiver account generation.
 */
object TelegramTvHandoffPolicy {
    suspend fun resolve(
        request: TelegramTvHandoff,
        pairedPeerConfirmed: Boolean,
        resolveLocalFileId: suspend (Long, Long) -> Int?
    ): ResolvedTelegramTvHandoff? {
        if (!pairedPeerConfirmed || !request.isValid()) return null
        val localFileId = resolveLocalFileId(request.chatId, request.messageId)
            ?.takeIf { it > 0 } ?: return null
        return ResolvedTelegramTvHandoff(request, localFileId)
    }
}

data class ResolvedTelegramTvHandoff internal constructor(
    val request: TelegramTvHandoff,
    val localFileId: Int
)
