package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.MediaItem
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicInteger

private const val HISTORY_PAGE_SIZE = 100
private const val HISTORY_MAX_PAGES = 12

internal fun SavedMessagesRepository.searchMediaInternal(
    chatId: Long,
    limit: Int,
    fromMessageId: Long?,
    onResult: (List<MediaItem>, String?) ->(Unit)
) {
    val perFilterLimit = (limit * 3).coerceAtMost(100)
    val results = mutableListOf<MediaItem>()
    val pendingRequests = AtomicInteger(5)
    val lock = Any()
    var error: String? = null

    fun deliverResults() {
        val merged = results.dedupeAndSort()
        val taken = merged.take(limit)
        if (taken.isEmpty()) {
            // FALLBACK: if local message index is missing/stale, scan chat history pages directly.
            loadMediaFromHistoryInternal(chatId, fromMessageId, limit) { historyItems ->
                onResult(historyItems, error)
            }
        } else {
            onResult(taken, error)
        }
    }

    fun collect(items: List<MediaItem>?, err: String?) {
        synchronized(lock) {
            if (items != null) {
                results.addAll(items)
            }
            if (err != null && error == null) {
                error = err
            }
            if (pendingRequests.decrementAndGet() == 0) {
                deliverResults()
            }
        }
    }

    searchWithFilterInternal(chatId, perFilterLimit, fromMessageId, TdApi.SearchMessagesFilterVideo()) { items, err ->
        collect(items, err)
    }
    searchWithFilterInternal(chatId, perFilterLimit, fromMessageId, TdApi.SearchMessagesFilterDocument()) { items, err ->
        collect(items, err)
    }
    searchWithFilterInternal(chatId, perFilterLimit, fromMessageId, TdApi.SearchMessagesFilterVideoNote()) { items, err ->
        collect(items, err)
    }
    searchWithFilterInternal(chatId, perFilterLimit, fromMessageId, TdApi.SearchMessagesFilterAnimation()) { items, err ->
        collect(items, err)
    }
    searchWithFilterInternal(chatId, perFilterLimit, fromMessageId, TdApi.SearchMessagesFilterPhotoAndVideo()) { items, err ->
        collect(items, err)
    }
}

private fun SavedMessagesRepository.loadMediaFromHistoryInternal(
    chatId: Long,
    fromMessageId: Long?,
    limit: Int,
    onResult: (List<MediaItem>) -> Unit
) {
    val collected = mutableListOf<MediaItem>()

    fun requestPage(anchorMessageId: Long, page: Int) {
        if (collected.size >= limit || page >= HISTORY_MAX_PAGES) {
            onResult(collected.dedupeAndSort().take(limit))
            return
        }

        val offset = if (anchorMessageId == 0L) 0 else 1
        client.send(
            TdApi.GetChatHistory(chatId, anchorMessageId, offset, HISTORY_PAGE_SIZE, false)
        ) { histResult ->
            when (histResult) {
                is TdApi.Messages -> {
                    val messages = histResult.messages.orEmpty()
                    if (messages.isEmpty()) {
                        onResult(collected.dedupeAndSort().take(limit))
                        return@send
                    }

                    val pageItems = messages
                        .mapNotNull { mapMessageToMediaInternal(it) }
                        .filter { it.type == com.vaydelux.bousygram.domain.model.MediaType.Video }
                    collected += pageItems

                    val nextAnchor = messages.lastOrNull()?.id ?: 0L
                    if (nextAnchor == 0L || messages.size < HISTORY_PAGE_SIZE) {
                        onResult(collected.dedupeAndSort().take(limit))
                    } else {
                        requestPage(nextAnchor, page + 1)
                    }
                }
                else -> onResult(collected.dedupeAndSort().take(limit))
            }
        }
    }

    requestPage(fromMessageId ?: 0L, 0)
}


internal fun SavedMessagesRepository.searchWithFilterInternal(
    chatId: Long,
    limit: Int,
    fromMessageId: Long?,
    filter: TdApi.SearchMessagesFilter,
    onItems: (List<MediaItem>, String?) -> Unit
) {
    // TDLib requires SearchChatMessages.offset to be <= 0.
    val offset = 0
    val query = TdApi.SearchChatMessages(
        chatId,
        null,
        "",
        null,
        fromMessageId ?: 0,
        offset,
        limit,
        filter
    )
    client.send(query) { result ->
        when (result) {
            is TdApi.FoundChatMessages -> {
                val items = result.messages?.mapNotNull(::mapMessageToMediaInternal).orEmpty()
                onItems(items, null)
            }
            is TdApi.Error -> onItems(emptyList(), result.message)
            else -> onItems(emptyList(), "Unknown response from TDLib")
        }
    }
}
