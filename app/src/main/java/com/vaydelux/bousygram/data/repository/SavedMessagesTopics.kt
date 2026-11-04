package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import org.drinkless.tdlib.TdApi

/** Delivers progressive real topic pages; clients can cancel stale requests. */
internal fun SavedMessagesRepository.loadForumTopicsInternal(
    chatId: Long,
    query: String = "",
    onUpdate: (List<ForumTopicChannel>, String?, Boolean) -> Unit
): () -> Unit {
    val loader = ForumTopicLoader(chatId, query, { request, callback -> client.send(request, callback) }, onUpdate)
    loader.load()
    return loader::cancel
}

internal class ForumTopicLoader(
    private val chatId: Long,
    private val query: String,
    private val send: (TdApi.GetForumTopics, (TdApi.Object?) -> Unit) -> Unit,
    private val onUpdate: (List<ForumTopicChannel>, String?, Boolean) -> Unit
) {
    private data class Cursor(val date: Int = 0, val messageId: Long = 0L, val topicId: Int = 0)
    private val topics = linkedMapOf<Int, TdApi.ForumTopic>()
    private val requested = mutableSetOf<Cursor>()
    @Volatile private var cancelled = false

    fun load() = loadPage(Cursor())
    fun cancel() { cancelled = true }

    private fun loadPage(cursor: Cursor) {
        if (cancelled) return
        if (!requested.add(cursor)) {
            onUpdate(snapshot(), "Telegram returned a repeated topic page. Retry to refresh the list.", true)
            return
        }
        // Display the first smaller page promptly, then load the rest in the background.
        val limit = if (requested.size == 1) 40 else 100
        try {
            send(TdApi.GetForumTopics(chatId, query, cursor.date, cursor.messageId, cursor.topicId, limit)) { result ->
                if (cancelled) return@send
                when (result) {
                    is TdApi.ForumTopics -> {
                        result.topics.orEmpty().forEach { topics[it.info.forumTopicId] = it }
                        val next = Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId)
                        val finished = next == Cursor()
                        onUpdate(snapshot(), null, finished)
                        if (!finished && !cancelled) loadPage(next)
                    }
                    is TdApi.Error -> onUpdate(snapshot(), result.message, true)
                    else -> onUpdate(snapshot(), "Unable to load Telegram topics. Please retry.", true)
                }
            }
        } catch (failure: Exception) {
            if (!cancelled) onUpdate(snapshot(), "Telegram topic request failed. Please retry.", true)
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
