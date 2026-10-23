package com.vaydelux.bousygram.data.repository

import android.util.Log
import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import org.drinkless.tdlib.TdApi

/**
 * Loads Forum Topics (channels) for a supergroup in forum mode.
 * Maps TDLib ForumTopics into Discord/Slack-style channels.
 */
internal fun SavedMessagesRepository.loadForumTopicsInternal(
    chatId: Long,
    query: String = "",
    onResult: (List<ForumTopicChannel>, String?) -> Unit
) {
    val request = TdApi.GetForumTopics(
        chatId,
        query,
        0, // offsetDate
        0L, // offsetMessageId
        0, // offsetForumTopicId
        100 // limit
    )

    client.send(request) { result ->
        when (result) {
            is TdApi.ForumTopics -> {
                val channels = result.topics.orEmpty().map { topic ->
                    val info = topic.info
                    ForumTopicChannel(
                        id = info.forumTopicId,
                        chatId = chatId,
                        name = info.name.ifBlank { if (info.isGeneral) "general" else "topic-${info.forumTopicId}" },
                        iconColor = info.icon?.color ?: 0x6FB9F0,
                        iconCustomEmojiId = info.icon?.customEmojiId ?: 0L,
                        isPinned = topic.isPinned,
                        isClosed = info.isClosed,
                        isGeneral = info.isGeneral,
                        unreadCount = topic.unreadCount,
                        totalMessages = topic.unreadCount
                    )
                }.sortedWith(
                    compareByDescending<ForumTopicChannel> { it.isGeneral }
                        .thenByDescending { it.isPinned }
                        .thenBy { it.name.lowercase() }
                )
                Log.d(SavedMessagesRepository.TAG, "Loaded ${channels.size} forum topics for chat $chatId")
                onResult(channels, null)
            }
            is TdApi.Error -> {
                Log.w(SavedMessagesRepository.TAG, "GetForumTopics failed for chat $chatId: ${result.message}")
                onResult(emptyList(), result.message)
            }
            else -> onResult(emptyList(), "Unknown response loading forum topics")
        }
    }
}
