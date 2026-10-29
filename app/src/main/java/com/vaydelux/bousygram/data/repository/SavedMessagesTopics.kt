package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import org.drinkless.tdlib.TdApi

/** Emits real accumulated topic pages; TDLib can return fewer than the requested 100 topics. */
internal fun SavedMessagesRepository.loadForumTopicsInternal(
    chatId: Long,
    query: String = "",
    onResult: (List<ForumTopicChannel>, String?) -> Unit
) {
    ForumTopicLoader(chatId, query, { request, callback -> client.send(request, callback) }, onResult).load()
}

internal class ForumTopicLoader(
    private val chatId: Long,
    private val query: String,
    private val send: (TdApi.GetForumTopics, (TdApi.Object?) -> Unit) -> Unit,
    private val onResult: (List<ForumTopicChannel>, String?) -> Unit
) {
    private data class Cursor(val date: Int = 0, val messageId: Long = 0L, val topicId: Int = 0)
    private val topics = linkedMapOf<Int, TdApi.ForumTopic>()
    private val requested = mutableSetOf<Cursor>()

    fun load() = loadPage(Cursor())

    private fun loadPage(cursor: Cursor) {
        if (!requested.add(cursor)) {
            onResult(snapshot(), "Telegram returned a repeated topic page. Retry to refresh the list.")
            return
        }
        send(TdApi.GetForumTopics(chatId, query, cursor.date, cursor.messageId, cursor.topicId, 100)) { result ->
            when (result) {
                is TdApi.ForumTopics -> {
                    result.topics.orEmpty().forEach { topics[it.info.forumTopicId] = it }
                    onResult(snapshot(), null)
                    val next = Cursor(result.nextOffsetDate, result.nextOffsetMessageId, result.nextOffsetForumTopicId)
                    if (next != Cursor()) loadPage(next)
                }
                is TdApi.Error -> onResult(snapshot(), result.message)
                else -> onResult(snapshot(), "Unable to load Telegram topics. Please retry.")
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
