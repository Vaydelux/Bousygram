package com.vaydelux.bousygram.presentation.media.vm

import com.vaydelux.bousygram.domain.model.MediaItem

/** Cursor-independent bounded viewport for Telegram metadata; never retains video bytes. */
internal object TelegramMediaWindow {
    const val LOW_RAM_MAX_ITEMS = 120
    const val STANDARD_MAX_ITEMS = 240

    data class Merge(val items: List<MediaItem>, val removedFromFront: Int)

    fun itemLimit(lowMemory: Boolean): Int =
        if (lowMemory) LOW_RAM_MAX_ITEMS else STANDARD_MAX_ITEMS


    /**
     * A live Telegram message arrives at the newest edge, not the older page
     * currently being fetched. Do not let years of live updates grow Compose
     * state beyond the same ARM32 catalog budget as ordinary pagination.
     *
     * When the user has paged past the retained recent window, do not inject a
     * new item at the top and displace the visible older card/focus. A refresh
     * returns to recent items. If dropping an old tail on the recent window,
     * move the next-page cursor to the last item actually retained so later
     * Load More will not skip the discarded tail.
     */
    data class LiveInsert(
        val items: List<MediaItem>,
        val nextFromMessageId: Long,
        val hasMore: Boolean,
        val visible: Boolean
    )

    fun prependLatest(
        current: List<MediaItem>,
        incoming: MediaItem,
        maxItems: Int,
        discardedFromFront: Int,
        nextFromMessageId: Long,
        hasMore: Boolean
    ): LiveInsert {
        require(maxItems > 0)
        val index = current.indexOfFirst {
            it.chatId == incoming.chatId && it.messageId == incoming.messageId
        }
        if (index >= 0) {
            val updated = current.toMutableList().also { it[index] = incoming }
            return LiveInsert(updated, nextFromMessageId, hasMore, visible = true)
        }
        // We are browsing old retained pages, so preserve screen/focus.
        if (discardedFromFront > 0) {
            return LiveInsert(current, nextFromMessageId, hasMore, visible = false)
        }
        val merged = ArrayList<MediaItem>(minOf(maxItems, current.size + 1))
        merged.add(incoming)
        merged.addAll(current.take((maxItems - 1).coerceAtLeast(0)))
        val droppedTail = current.size + 1 > maxItems
        return LiveInsert(
            items = merged,
            nextFromMessageId = if (droppedTail) merged.last().messageId else nextFromMessageId,
            hasMore = hasMore || droppedTail,
            visible = true
        )
    }

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
