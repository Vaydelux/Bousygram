package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.MediaItem
import org.drinkless.tdlib.TdApi

internal fun List<MediaItem>.dedupeAndSort(): List<MediaItem> =
    distinctBy { it.messageId }.sortedByDescending { it.date }

internal fun SavedMessagesRepository.loadLatestMediaInternal(
    limit: Int,
    onResult: (List<MediaItem>, String?) -> Unit
) {
    loadLatestMediaPagedInternal(limit, null) { items, _, error -> onResult(items, error) }
}

internal fun SavedMessagesRepository.loadLatestMediaPagedInternal(
    limit: Int,
    fromMessageId: Long?,
    onResult: (List<MediaItem>, Long, String?) -> Unit
) {
    resolveSavedMessagesChatInternal { savedChatId: Long?, error: String? ->
        if (savedChatId == null) {
            onResult(emptyList(), 0L, error ?: "Saved Messages unavailable")
            return@resolveSavedMessagesChatInternal
        }
        // OpenChat is a UI side effect, not a prerequisite for SearchChatMessages.
        // Waiting for its callback can delay the first visible media batch indefinitely.
        client.send(TdApi.OpenChat(savedChatId)) { }
        searchMediaInternal(savedChatId, limit, fromMessageId) { items, err ->
            val nextFromId = items.lastOrNull()?.messageId ?: 0L
            onResult(items, nextFromId, err)
        }
    }
}


internal fun SavedMessagesRepository.loadLatestMediaPagedProgressiveInternal(
    limit: Int,
    fromMessageId: Long?,
    onUpdate: (List<MediaItem>, Long, String?, Boolean) -> Unit
) {
    resolveSavedMessagesChatInternal { savedChatId, error ->
        if (savedChatId == null) {
            onUpdate(emptyList(), 0L, error ?: "Saved Messages unavailable", true)
            return@resolveSavedMessagesChatInternal
        }
        client.send(TdApi.OpenChat(savedChatId)) { }
        searchMediaProgressiveInternal(savedChatId, limit, fromMessageId, null, onUpdate)
    }
}

internal fun SavedMessagesRepository.loadChatMediaPagedProgressiveInternal(
    chatId: Long,
    limit: Int,
    fromMessageId: Long?,
    topicId: Int? = null,
    onUpdate: (List<MediaItem>, Long, String?, Boolean) -> Unit
) {
    // Start indexed search now instead of waiting for the chat-open acknowledgement.
    client.send(TdApi.OpenChat(chatId)) { }
    searchMediaProgressiveInternal(chatId, limit, fromMessageId, topicId, onUpdate)
}

internal fun SavedMessagesRepository.loadChatMediaInternal(
    chatId: Long,
    limit: Int,
    onResult: (List<MediaItem>, String?) -> Unit
) {
    client.send(TdApi.OpenChat(chatId)) { }
    searchMediaInternal(chatId, limit, null, onResult)
}

internal fun SavedMessagesRepository.loadChatMediaPagedInternal(
    chatId: Long,
    limit: Int,
    fromMessageId: Long?,
    onResult: (List<MediaItem>, Long, String?) -> Unit
) {
    client.send(TdApi.OpenChat(chatId)) { }
    searchMediaInternal(chatId, limit, fromMessageId) { items, err ->
        val nextFromId = items.lastOrNull()?.messageId ?: 0L
        onResult(items, nextFromId, err)
    }
}
