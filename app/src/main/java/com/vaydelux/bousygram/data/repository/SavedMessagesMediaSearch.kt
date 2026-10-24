package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicInteger

/**
 * Search for videos in a chat with a progressive contract: every successful TDLib filter
 * can publish a partial page immediately, while the final callback provides the merged page
 * and pagination cursor. This lets the UI render the first videos without waiting for the
 * slowest filter request.
 */
internal fun SavedMessagesRepository.searchMediaProgressiveInternal(
    chatId: Long,
    limit: Int,
    fromMessageId: Long?,
    topicId: Int? = null,
    onUpdate: (items: List<MediaItem>, nextFromMessageId: Long, error: String?, isFinal: Boolean) -> Unit
) {
    val plan = createMediaSearchPlan(limit)
    val pageLimit = plan.pageLimit
    val documentLimit = plan.documentLimit
    val results = mutableListOf<MediaItem>()
    val pendingRequests = AtomicInteger(4)
    val lock = Any()
    val publishLock = Any()
    var firstError: String? = null
    var finalDelivered = false
    var updateSequence = 0L
    var latestPublishedSequence = 0L

    fun publish(
        sequence: Long,
        items: List<MediaItem>,
        nextFromMessageId: Long,
        error: String?,
        isFinal: Boolean
    ) {
        synchronized(publishLock) {
            // TDLib callbacks may complete on different native callback threads. Ignore an older
            // partial snapshot if a later filter has already published newer results.
            if (sequence <= latestPublishedSequence) return
            latestPublishedSequence = sequence
            onUpdate(items, nextFromMessageId, error, isFinal)
        }
    }

    fun deliverFinalOrFallback(snapshot: List<MediaItem>, error: String?) {
        if (snapshot.isNotEmpty()) {
            val finalItems = snapshot.take(pageLimit)
            val sequence = synchronized(lock) { ++updateSequence }
            publish(sequence, finalItems, finalItems.minOfOrNull { it.messageId } ?: 0L, null, true)
            return
        }

        // TDLib's message search index may be temporarily incomplete after login or sync.
        // Fall back to a bounded chat-history scan only when all indexed filters returned no
        // playable videos; normal results never wait for history scanning.
        loadMediaFromHistoryInternal(chatId, fromMessageId, pageLimit, topicId) { historyItems ->
            val finalItems = historyItems
                .filter { it.type == MediaType.Video && it.fileId != null }
                .dedupeAndSort()
                .take(pageLimit)
            val sequence = synchronized(lock) { ++updateSequence }
            publish(
                sequence,
                finalItems,
                finalItems.minOfOrNull { it.messageId } ?: 0L,
                error.takeIf { finalItems.isEmpty() },
                true
            )
        }
    }

    fun collect(items: List<MediaItem>?, error: String?) {
        var snapshot: List<MediaItem> = emptyList()
        var aggregatedError: String? = null
        var isFinal = false
        var shouldFallback = false
        var sequence = 0L
        synchronized(lock) {
            if (finalDelivered) return
            if (items != null) results.addAll(items)
            if (!error.isNullOrBlank() && firstError == null) firstError = error
            snapshot = results
                .asSequence()
                .filter { it.type == MediaType.Video && it.fileId != null }
                .toList()
                .dedupeAndSort()
                .take(pageLimit)
            // A filter can fail while another filter still returns usable videos. Do not let a
            // non-fatal per-filter error leave an error banner over valid media results.
            aggregatedError = firstError.takeIf { snapshot.isEmpty() }
            isFinal = pendingRequests.decrementAndGet() == 0
            if (isFinal) {
                finalDelivered = true
                shouldFallback = snapshot.isEmpty()
            }
            sequence = ++updateSequence
        }

        if (isFinal) {
            if (shouldFallback) {
                // Send one empty non-final result so callers know the search requests completed,
                // but preserve loading state until the fallback result has been resolved.
                publish(sequence, snapshot, 0L, aggregatedError, false)
                deliverFinalOrFallback(snapshot, aggregatedError)
            } else {
                publish(sequence, snapshot, snapshot.minOfOrNull { it.messageId } ?: 0L, aggregatedError, true)
            }
        } else {
            // Emit an update even when the current filter has no videos. This lets the UI start
            // showing progress immediately and schedule the noncritical chat-source catalog,
            // rather than holding all side work until the final filter responds.
            publish(sequence, snapshot, 0L, aggregatedError, false)
        }
    }

    // Do not query PhotoAndVideo: this app's library is video-only and that filter duplicates
    // MessageVideo results while adding photos that the UI discards. Document searches inspect
    // more messages because they can also match ordinary files before the video documents.
    searchWithFilterInternal(chatId, pageLimit, fromMessageId, TdApi.SearchMessagesFilterVideo(), topicId) { items, error ->
        collect(items, error)
    }
    searchWithFilterInternal(chatId, documentLimit, fromMessageId, TdApi.SearchMessagesFilterDocument(), topicId) { items, error ->
        collect(items, error)
    }
    searchWithFilterInternal(chatId, pageLimit, fromMessageId, TdApi.SearchMessagesFilterVideoNote(), topicId) { items, error ->
        collect(items, error)
    }
    searchWithFilterInternal(chatId, pageLimit, fromMessageId, TdApi.SearchMessagesFilterAnimation(), topicId) { items, error ->
        collect(items, error)
    }
}

internal fun SavedMessagesRepository.searchMediaInternal(
    chatId: Long,
    limit: Int,
    fromMessageId: Long?,
    onResult: (List<MediaItem>, String?) -> Unit
) {
    var delivered = false
    searchMediaProgressiveInternal(chatId, limit, fromMessageId) { items, _, error, isFinal ->
        if (isFinal && !delivered) {
            delivered = true
            onResult(items, error)
        }
    }
}

private fun SavedMessagesRepository.loadMediaFromHistoryInternal(
    chatId: Long,
    fromMessageId: Long?,
    limit: Int,
    topicId: Int? = null,
    onResult: (List<MediaItem>) -> Unit
) {
    val collected = mutableListOf<MediaItem>()
    val isSpecificTopic = topicId != null && topicId != 0

    fun requestPage(anchorMessageId: Long, page: Int) {
        if (collected.size >= limit || page >= HISTORY_MAX_PAGES) {
            onResult(collected.dedupeAndSort().take(limit))
            return
        }

        val offset = if (anchorMessageId == 0L) 0 else 1
        val isSpecificTopic = topicId != null && topicId != 0
        val request: TdApi.Function<TdApi.Messages> = if (topicId != null && topicId != 0) {
            TdApi.GetForumTopicHistory(chatId, topicId, anchorMessageId, offset, HISTORY_PAGE_SIZE)
        } else {
            TdApi.GetChatHistory(chatId, anchorMessageId, offset, HISTORY_PAGE_SIZE, false)
        }

        client.send(request) { histResult ->
            when (histResult) {
                is TdApi.Messages -> {
                    val messages = histResult.messages.orEmpty()
                    if (messages.isEmpty()) {
                        onResult(collected.dedupeAndSort().take(limit))
                        return@send
                    }

                    val pageItems = messages
                        .mapNotNull { mapMessageToMediaInternal(it, topicId?.toLong()) }
                        .filter { it.type == MediaType.Video && it.fileId != null }
                        .filter {
                            if (topicId == null || topicId == 0) true
                            else it.messageThreadId == topicId.toLong() ||
                                (topicId == 1 && (it.messageThreadId == null || it.messageThreadId == 0L || it.messageThreadId == 1L))
                        }
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
    topicId: Int? = null,
    onItems: (List<MediaItem>, String?) -> Unit
) {
    val topic = if (topicId != null && topicId != 0) TdApi.MessageTopicForum(topicId) else null
    // TDLib requires SearchChatMessages.offset to be <= 0.
    val query = TdApi.SearchChatMessages(
        chatId,
        topic,
        "",
        null,
        fromMessageId ?: 0,
        0,
        limit.coerceIn(1, 100),
        filter
    )
    client.send(query) { result ->
        when (result) {
            is TdApi.FoundChatMessages -> {
                val items = result.messages?.mapNotNull {
                    mapMessageToMediaInternal(it, topicId?.toLong())
                }.orEmpty()
                onItems(items, null)
            }
            is TdApi.Error -> onItems(emptyList(), result.message)
            else -> onItems(emptyList(), "Unknown response from TDLib")
        }
    }
}

private const val HISTORY_PAGE_SIZE = 100
private const val HISTORY_MAX_PAGES = 12
