package com.vaydelux.bousygram.presentation.media.vm

import com.vaydelux.bousygram.domain.model.MediaItem

/**
 * Pure guards for account-scoped catalog pagination and in-folder searching.
 * Kept free of Android dependencies so boundary cases can be covered in JVM tests.
 */
internal fun canCacheCatalogPage(itemCount: Int, limit: Int): Boolean =
    itemCount in 1..limit

/** Null must mean "do not search", never an implicit global Telegram search. */
internal fun quickSearchScope(
    isSavedMessages: Boolean,
    savedMessagesChatId: Long?,
    selectedChatId: Long?
): Long? = (if (isSavedMessages) savedMessagesChatId else selectedChatId)
    ?.takeIf { it != 0L }

/** Continue/Resume must not surface a recently played video from a different source. */
internal fun canResumeInSource(
    isSavedMessages: Boolean,
    savedMessagesChatId: Long?,
    selectedChatId: Long?,
    lastPlayedChatId: Long?
): Boolean {
    val sourceId = quickSearchScope(isSavedMessages, savedMessagesChatId, selectedChatId) ?: return false
    return lastPlayedChatId != null && sourceId == lastPlayedChatId
}

/** An error/partial callback cannot advance a cursor or declare end-of-list. */
internal fun resolvedPageCursor(current: Long, returned: Long, isFinal: Boolean, error: String?): Long =
    if (isFinal && error == null) returned else current

internal fun freshCatalogTimestamp(savedAtMs: Long, nowMs: Long, ttlMs: Long): Boolean =
    ttlMs >= 0L && nowMs >= savedAtMs && nowMs - savedAtMs <= ttlMs

/** Saved Messages must never display media from another Telegram chat or a global recent index. */
internal fun savedMessagesOnly(items: List<MediaItem>, savedMessagesChatId: Long?): List<MediaItem> {
    val id = savedMessagesChatId?.takeIf { it != 0L } ?: return emptyList()
    return items.filter { it.chatId == id }
}


/** Retry unavailable thumbnails only after a bounded interval; honor clock resets. */
internal fun allowThumbnailRetry(lastFailureAtMs: Long?, nowMs: Long, cooldownMs: Long): Boolean =
    lastFailureAtMs == null || nowMs < lastFailureAtMs ||
        nowMs - lastFailureAtMs >= cooldownMs

/** A previously focused card may have been evicted by the bounded history window.
 * Restore D-pad focus to a real visible item rather than an unattached requester.
 */
internal fun resolveRestorableMediaKey(
    previousKey: String?,
    visible: List<MediaItem>,
    discardedCount: Int
): String? {
    if (previousKey == null || visible.isEmpty()) return null
    if (visible.any { "${it.chatId}:${it.messageId}" == previousKey }) return previousKey
    return if (discardedCount > 0) visible.first().let { "${it.chatId}:${it.messageId}" }
        else null
}
