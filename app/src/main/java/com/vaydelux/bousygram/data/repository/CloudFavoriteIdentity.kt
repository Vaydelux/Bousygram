package com.vaydelux.bousygram.data.repository

import org.drinkless.tdlib.TdApi

/**
 * Keys reconstructible from Telegram messages on any device, without a Favorites database.
 *
 * TDLib file IDs are process/account-local; only remote.uniqueId is stable across devices.
 * Channel origins and forwarded-source IDs help when the remote file ID isn't available.
 * No caption is modified and no private source identifier is uploaded as a sidecar message.
 */
internal fun cloudFavoriteIdentityKeys(message: TdApi.Message): Set<String> {
    val keys = LinkedHashSet<String>(3)
    if (message.chatId != 0L && message.id != 0L) {
        keys += "source:${message.chatId}:${message.id}"
    }

    val info = message.forwardInfo
    val lastForward = info?.source
    if (lastForward != null && lastForward.chatId != 0L && lastForward.messageId != 0L) {
        keys += "source:${lastForward.chatId}:${lastForward.messageId}"
    }
    val origin = info?.origin as? TdApi.MessageOriginChannel
    if (origin != null && origin.chatId != 0L && origin.messageId != 0L) {
        keys += "source:${origin.chatId}:${origin.messageId}"
    }

    val content = message.content
    val file: TdApi.File? = when (content) {
        is TdApi.MessageVideo -> content.video?.video
        is TdApi.MessageVideoNote -> content.videoNote?.video
        is TdApi.MessageAnimation -> content.animation?.animation
        is TdApi.MessageDocument -> content.document?.document
        is TdApi.MessageAudio -> content.audio?.audio
        is TdApi.MessagePhoto -> content.photo?.sizes?.maxByOrNull {
            it.width.toLong() * it.height.toLong()
        }?.photo
        else -> null
    }
    file?.remote?.uniqueId?.takeIf { it.isNotBlank() }?.let {
        keys += "remote:$it"
    }
    return keys
}

/** Only accept a confirmed cloud match; dates, captions, titles and file sizes are not identities. */
internal fun cloudFavoritesMatch(source: TdApi.Message, saved: TdApi.Message): Boolean {
    val sourceKeys = cloudFavoriteIdentityKeys(source)
    val savedKeys = cloudFavoriteIdentityKeys(saved)
    return sourceKeys.any { it in savedKeys }
}
