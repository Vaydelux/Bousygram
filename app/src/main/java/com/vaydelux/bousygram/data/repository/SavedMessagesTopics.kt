package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import kotlinx.coroutines.Job
import org.drinkless.tdlib.TdApi

/** Delivers progressive real topic pages; clients can cancel stale requests. */
internal fun SavedMessagesRepository.loadForumTopicsInternal(
    chatId: Long,
    query: String = "",
    onUpdate: (List<ForumTopicChannel>, String?, Boolean) -> Unit
): () -> Unit {
    var pageRequest: Job? = null
    val loader = ForumTopicLoader(chatId, query, { request, callback ->
        pageRequest = sendCatalogRequest(request, onResult = callback)
    }, onUpdate)
    loader.load()
    return {
        loader.cancel()
        pageRequest?.cancel()
    }
}

/** A page cursor is owned by this account's loader, not persisted across accounts. */
internal class ForumTopicPageControl(
    val loadMore: () -> Unit,
    val cancel: () -> Unit
)

/**
 * Bounded, explicit continuation for large forums. The first 40 topics appear
 * before the user requests another 100, even in forums with thousands of topics.
 * All later pages are still tied to the current repository/account session.
 */
internal fun SavedMessagesRepository.loadForumTopicsPagedInternal(
    chatId: Long,
    query: String = "",
    onHasMore: (Boolean) -> Unit,
    onUpdate: (List<ForumTopicChannel>, String?, Boolean) -> Unit
): ForumTopicPageControl {
    var pageRequest: Job? = null
    var pageStartNanos = TelegramCatalogTiming.nowNanos()
    var firstPage = true
    val loader = ForumTopicLoader(
        chatId = chatId, query = query,
        send = { request, callback ->
            pageStartNanos = TelegramCatalogTiming.nowNanos()
            pageRequest = sendCatalogRequest(request, onResult = callback)
        },
        onUpdate = { topics, error, isFinal ->
            if (isFinal) {
                TelegramCatalogTiming.record(
                    if (firstPage) TelegramCatalogStage.TOPIC_FIRST_PAGE
                        else TelegramCatalogStage.TOPIC_NEXT_PAGE,
                    pageStartNanos, topics.size,
                    when {
                        error != null -> TelegramCatalogOutcome.PARTIAL
                        topics.isEmpty() -> TelegramCatalogOutcome.EMPTY
                        else -> TelegramCatalogOutcome.READY
                    }
                )
                firstPage = false
            }
            onUpdate(topics, error, isFinal)
        },
        autoAdvance = false,
        onHasMore = onHasMore
    )
    loader.load()
    return ForumTopicPageControl(
        loadMore = { loader.loadNextPage() },
        cancel = {
            loader.cancel()
            pageRequest?.cancel()
        }
    )
}

internal class ForumTopicLoader(
    private val chatId: Long,
    private val query: String,
    private val send: (TdApi.GetForumTopics, (TdApi.Object?) -> Unit) -> Unit,
    private val autoAdvance: Boolean = true,
    private val onHasMore: (Boolean) -> Unit = {},
    private val onUpdate: (List<ForumTopicChannel>, String?, Boolean) -> Unit
) {
    private data class Cursor(val date: Int = 0, val messageId: Long = 0L, val topicId: Int = 0)
    private val topics = linkedMapOf<Int, TdApi.ForumTopic>()
    private val requested = mutableSetOf<Cursor>()
    private var nextCursor: Cursor? = null
    private var inFlight = false
    @Volatile private var cancelled = false

    fun load() = loadPage(Cursor())
    fun cancel() { cancelled = true }

    /** Does nothing for an in-flight page, exhausted cursor or cancelled request. */
    fun loadNextPage() {
        if (autoAdvance || cancelled || inFlight) return
        nextCursor?.let(::loadPage)
    }

    private fun loadPage(cursor: Cursor) {
        if (cancelled || inFlight) return
        if (!requested.add(cursor)) {
            nextCursor = null
            onHasMore(false)
            onUpdate(snapshot(), "Telegram returned a repeated topic page. Retry to refresh the list.", true)
            return
        }
        inFlight = true
        val limit = if (requested.size == 1) 40 else 100
        try {
            send(TdApi.GetForumTopics(chatId, query, cursor.date, cursor.messageId, cursor.topicId, limit)) { result ->
                if (cancelled) return@send
                inFlight = false
                when (result) {
                    is TdApi.ForumTopics -> {
                        result.topics.orEmpty().forEach { topics[it.info.forumTopicId] = it }
                        val next = Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId)
                        val finished = next == Cursor()
                        // A server that repeats a cursor must never spin on the
                        // same page or silently lose previously loaded topics.
                        if (!finished && (next == cursor || next in requested)) {
                            nextCursor = null
                            onHasMore(false)
                            onUpdate(snapshot(), "Telegram returned a repeated topic page. Retry to refresh the list.", true)
                            return@send
                        }
                        nextCursor = if (finished) null else next
                        onHasMore(!finished)
                        // Manual mode completes this *page* without reporting
                        // that the entire forum is exhausted.
                        onUpdate(snapshot(), null, finished || !autoAdvance)
                        if (!autoAdvance && !finished) return@send
                        if (!finished && !cancelled) loadPage(next)
                    }
                    is TdApi.Error -> {
                        requested.remove(cursor)
                        nextCursor = cursor
                        onHasMore(!autoAdvance)
                        onUpdate(snapshot(), result.message.take(180), true)
                    }
                    else -> {
                        requested.remove(cursor)
                        nextCursor = cursor
                        onHasMore(!autoAdvance)
                        onUpdate(snapshot(), "Unable to load Telegram topics. Please retry.", true)
                    }
                }
            }
        } catch (failure: Exception) {
            inFlight = false
            requested.remove(cursor)
            nextCursor = cursor
            if (!cancelled) {
                onHasMore(!autoAdvance)
                onUpdate(snapshot(), "Telegram topic request failed. Please retry.", true)
            }
        }
    }

    private fun snapshot(): List<ForumTopicChannel> = topics.values.map { topic ->
        val info = topic.info
        ForumTopicChannel(
            id = info.forumTopicId,
            chatId = chatId,
            name = info.name.ifBlank { if (info.isGeneral) "General" else "Unnamed topic" },
            iconColor = info.icon?.color ?: 0x6FB9F0,
            iconCustomEmojiId = info.icon?.customEmojiId ?: 0L,
            isPinned = topic.isPinned,
            isClosed = info.isClosed,
            isGeneral = info.isGeneral,
            unreadCount = topic.unreadCount.coerceAtLeast(0),
            order = topic.order
        )
    }.sortedByDescending { it.order }
}
