package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.drinkless.tdlib.TdApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    sendCatalogRequest(
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
                resolveChatTitles(chatIds) { titles ->
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
    sendCatalogRequest(
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
    onDone: (Map<Long, String>) -> Unit
) {
    catalogScope.launch {
        val titles = mutableMapOf<Long, String>()
        // Labels are optional. Bound the entire enrichment pass so a stale chat
        // does not delay an otherwise usable page of search results.
        withTimeoutOrNull(1_200L) {
            for (batch in chatIds.chunked(8)) {
                batch.map { id ->
                    async { id to (requestCatalog(TdApi.GetChat(id)) as? TdApi.Chat)?.title }
                }.awaitAll().forEach { (id, title) ->
                    if (!title.isNullOrBlank()) titles[id] = title
                }
            }
        }
        if (owner?.let(AccountSession::isCurrent) == true) onDone(titles)
    }
}
