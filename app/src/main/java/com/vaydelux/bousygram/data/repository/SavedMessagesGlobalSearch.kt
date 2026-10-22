package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.drinkless.tdlib.TdApi

data class GlobalSearchResult(
    val items: List<MediaItem>,
    val nextOffset: String,
    val chatTitles: Map<Long, String>
)

fun SavedMessagesRepository.searchGlobalVideos(
    query: String,
    offset: String,
    limit: Int = 40,
    onResult: (GlobalSearchResult?, String?) -> Unit
) {
    if (query.isBlank()) {
        onResult(GlobalSearchResult(emptyList(), "", emptyMap()), null)
        return
    }
    client.send(
        TdApi.SearchMessages(
            null,
            query,
            offset,
            limit.coerceIn(1, 100),
            TdApi.SearchMessagesFilterVideo(),
            null,
            0,
            0
        )
    ) { result ->
        when (result) {
            is TdApi.FoundMessages -> {
                val items = result.messages
                    ?.mapNotNull { mapMessageToMediaInternal(it) }
                    ?.filter { it.type == MediaType.Video && it.fileId != null }
                    ?: emptyList()
                val chatIds = items.map { it.chatId }.distinct()
                resolveChatTitles(chatIds, emptyMap()) { titles ->
                    onResult(
                        GlobalSearchResult(
                            items = items,
                            nextOffset = result.nextOffset.orEmpty(),
                            chatTitles = titles
                        ),
                        null
                    )
                }
            }
            is TdApi.Error -> onResult(null, result.message)
            else -> onResult(null, "Search failed")
        }
    }
}

fun SavedMessagesRepository.searchChatVideos(
    chatId: Long,
    query: String,
    fromMessageId: Long,
    limit: Int = 40,
    onResult: (List<MediaItem>, Long, String?) -> Unit
) {
    if (query.isBlank()) {
        onResult(emptyList(), 0L, null)
        return
    }
    client.send(
        TdApi.SearchChatMessages(
            chatId,
            null,
            query,
            null,
            fromMessageId,
            0,
            limit.coerceIn(1, 100),
            TdApi.SearchMessagesFilterVideo()
        )
    ) { result ->
        when (result) {
            is TdApi.FoundChatMessages -> {
                val items = result.messages
                    ?.mapNotNull { mapMessageToMediaInternal(it) }
                    ?.filter { it.type == MediaType.Video && it.fileId != null }
                    ?: emptyList()
                onResult(items, result.nextFromMessageId, null)
            }
            is TdApi.Error -> onResult(emptyList(), 0L, result.message)
            else -> onResult(emptyList(), 0L, "Chat search failed")
        }
    }
}

private fun SavedMessagesRepository.resolveChatTitles(
    chatIds: List<Long>,
    accumulated: Map<Long, String>,
    onDone: (Map<Long, String>) -> Unit
) {
    if (chatIds.isEmpty()) {
        onDone(accumulated)
        return
    }
    val chatId = chatIds.first()
    val remaining = chatIds.drop(1)
    if (accumulated.containsKey(chatId)) {
        resolveChatTitles(remaining, accumulated, onDone)
        return
    }
    client.send(TdApi.GetChat(chatId)) { result ->
        val title = when (result) {
            is TdApi.Chat -> result.title
            else -> "Chat"
        }
        resolveChatTitles(remaining, accumulated + (chatId to title), onDone)
    }
}
