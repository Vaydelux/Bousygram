package com.vaydelux.bousygram.presentation.media.vm

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

/** An error/partial callback cannot advance a cursor or declare end-of-list. */
internal fun resolvedPageCursor(current: Long, returned: Long, isFinal: Boolean, error: String?): Long =
    if (isFinal && error == null) returned else current

internal fun freshCatalogTimestamp(savedAtMs: Long, nowMs: Long, ttlMs: Long): Boolean =
    ttlMs >= 0L && nowMs >= savedAtMs && nowMs - savedAtMs <= ttlMs
