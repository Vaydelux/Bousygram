package com.vaydelux.bousygram.data.repository

import android.util.Log
import com.vaydelux.bousygram.domain.model.MediaItem
import org.drinkless.tdlib.TdApi

internal fun List<MediaItem>.dedupeAndSort(): List<MediaItem> =
    distinctBy { it.messageId }.sortedByDescending { it.date }

internal fun SavedMessagesRepository.loadLatestMediaInternal(
    limit: Int,
    onResult: (List<MediaItem>, String?) -> Unit
) {
    val cached = cachedMeId
    if (cached != null) {
        loadSavedMessagesChatInternal(cached, limit, onResult)
        return
    }
    client.send(TdApi.GetMe()) { meResult ->
        when (meResult) {
            is TdApi.User -> {
                cachedMeId = meResult.id
                loadSavedMessagesChatInternal(meResult.id, limit, onResult)
            }
            is TdApi.Error -> {
                Log.e(SavedMessagesRepository.TAG, "Failed to get user: ${meResult.message}")
                onResult(emptyList(), meResult.message)
            }
            else -> {
                Log.e(SavedMessagesRepository.TAG, "Unexpected response from GetMe")
                onResult(emptyList(), "Unexpected response from TDLib")
            }
        }
    }
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
        client.send(TdApi.OpenChat(savedChatId)) {
            searchMediaInternal(savedChatId, limit, fromMessageId) { items, err ->
                val nextFromId = items.lastOrNull()?.messageId ?: 0L
                onResult(items, nextFromId, err)
            }
        }
    }
}

internal fun SavedMessagesRepository.loadChatMediaInternal(
    chatId: Long,
    limit: Int,
    onResult: (List<MediaItem>, String?) -> Unit
) {
    client.send(TdApi.OpenChat(chatId)) {
        searchMediaInternal(chatId, limit, null, onResult)
    }
}

internal fun SavedMessagesRepository.loadChatMediaPagedInternal(
    chatId: Long,
    limit: Int,
    fromMessageId: Long?,
    onResult: (List<MediaItem>, Long, String?) -> Unit
) {
    client.send(TdApi.OpenChat(chatId)) {
        searchMediaInternal(chatId, limit, fromMessageId) { items, err ->
            val nextFromId = items.lastOrNull()?.messageId ?: 0L
            onResult(items, nextFromId, err)
        }
    }
}
