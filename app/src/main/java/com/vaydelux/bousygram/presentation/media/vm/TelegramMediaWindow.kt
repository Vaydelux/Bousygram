package com.vaydelux.bousygram.presentation.media.vm

import com.vaydelux.bousygram.domain.model.MediaItem

/** Cursor-independent bounded viewport for Telegram metadata; never retains video bytes. */
internal object TelegramMediaWindow {
    const val LOW_RAM_MAX_ITEMS = 120
    const val STANDARD_MAX_ITEMS = 240

    data class Merge(val items: List<MediaItem>, val removedFromFront: Int)

    fun itemLimit(lowMemory: Boolean): Int =
        if (lowMemory) LOW_RAM_MAX_ITEMS else STANDARD_MAX_ITEMS

    /** Append older Telegram history, deduplicate by chat/message identity, evict the oldest
     * references from this in-memory traversal. TDLib's paging cursor remains untouched.
     */
    fun merge(existing: List<MediaItem>, incoming: List<MediaItem>, maxItems: Int): Merge {
        require(maxItems > 0)
        val unique = LinkedHashMap<Pair<Long, Long>, MediaItem>()
        existing.forEach { unique[it.chatId to it.messageId] = it }
        incoming.forEach { unique[it.chatId to it.messageId] = it }
        val discarded = (unique.size - maxItems).coerceAtLeast(0)
        return Merge(unique.values.drop(discarded), discarded)
    }
}

/** FIFO identity marker budget; caller owns synchronization if accessed across threads. */
internal fun rememberBoundedIdentity(keys: LinkedHashSet<String>, key: String, capacity: Int): Boolean {
    require(capacity > 0)
    val fresh = keys.add(key)
    while (keys.size > capacity) {
        val iterator = keys.iterator()
        if (iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
    return fresh
}
