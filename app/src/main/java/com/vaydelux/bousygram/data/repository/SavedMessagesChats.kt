package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.SAVED_MESSAGES_TITLE
import com.vaydelux.bousygram.domain.model.VideoChatItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

internal suspend fun loadVideoChats(
    owner: AccountSnapshot,
    limit: Int,
    request: suspend (TdApi.Function<*>) -> TdApi.Object? = { TdLibClient.awaitAccountResponse(it, owner) },
    onHasMore: (Boolean) -> Unit = {},
    onPage: (List<VideoChatItem>) -> Unit
): String? = coroutineScope {
    // The caller owns publication on the main dispatcher. Each list advances independently,
    // so a slow archive never blocks the first main page.
    val mainSources = linkedMapOf<Long, VideoChatItem>()
    val archivedSources = linkedMapOf<Long, VideoChatItem>()

    fun publish() {
        val saved = mainSources.values.firstOrNull { it.isSavedMessages }
            ?: archivedSources.values.firstOrNull { it.isSavedMessages }
        onPage(buildList {
            saved?.let(::add)
            addAll(mainSources.values.filterNot { it.isSavedMessages })
            addAll(archivedSources.values.filterNot { it.isSavedMessages || it.chatId in mainSources })
        })
    }

    suspend fun boundedRequest(query: TdApi.Function<*>): TdApi.Object? =
        withTimeoutOrNull(CHAT_PICKER_REQUEST_TIMEOUT_MS) { request(query) }

    val main = async {
        loadChatList(owner, TdApi.ChatListMain(), limit, mainSources, ::boundedRequest, ::publish)
    }
    val archive = async {
        loadChatList(owner, TdApi.ChatListArchive(), limit, archivedSources, ::boundedRequest, ::publish)
    }
    val results = listOf(main, archive).awaitAll()
    // Do not infer directory exhaustion from the number of visible groups:
    // private chats may occupy most of the scanned Telegram dialog list.
    onHasMore(results.any { it.hasMore })
    results.mapNotNull { it.error }.firstOrNull()
}

private data class ChatListLoadResult(val error: String? = null, val hasMore: Boolean = false)

private suspend fun loadChatList(
    owner: AccountSnapshot,
    chatList: TdApi.ChatList,
    limit: Int,
    sources: MutableMap<Long, VideoChatItem>,
    request: suspend (TdApi.Function<*>) -> TdApi.Object?,
    onPage: () -> Unit
): ChatListLoadResult {
    val isArchived = chatList is TdApi.ChatListArchive
    var queryLimit = minOf(limit, CHAT_PICKER_FIRST_PAGE)
    var endOfList = false
    var publishedFirstBatch = false
    var unavailableDetails = false
    var mayHaveMore = false
    while (true) {
        val response = request(TdApi.GetChats(chatList, queryLimit))
        val ids = (response as? TdApi.Chats)?.chatIds
            ?: return ChatListLoadResult(directoryError(response), hasMore = true)
        val removed = sources.keys.retainAll(ids.toSet())
        val batches = ids.filterNot { it in sources }.chunked(CHAT_PICKER_DETAIL_BATCH)

        for ((index, batch) in batches.withIndex()) {
            val chats = coroutineScope {
                batch.map { chatId -> async { loadChatSource(owner, chatId, isArchived, request) } }.awaitAll()
            }
            chats.filterNotNull().forEach { sources[it.chatId] = it }
            // A single timed-out group/topic metadata request must not hide the
            // other successfully loaded channels and groups.
            if (chats.any { it == null }) unavailableDetails = true
            if (!publishedFirstBatch || index == batches.lastIndex) {
                publishedFirstBatch = true
                onPage()
            }
        }
        if (removed && batches.isEmpty()) onPage()

        if (ids.size >= limit || (endOfList && ids.size < queryLimit)) {
            mayHaveMore = ids.size >= limit && !endOfList
            break
        }
        if (ids.size == queryLimit) {
            queryLimit = minOf(limit, queryLimit + CHAT_PICKER_LOAD_BATCH)
            continue
        }

        // TDLib can return a short page while more dialogs are still available.
        when (val result = request(TdApi.LoadChats(chatList, CHAT_PICKER_LOAD_BATCH))) {
            is TdApi.Ok -> Unit
            is TdApi.Error -> {
                if (result.code != 404) return ChatListLoadResult(directoryError(result), hasMore = true)
                // A prefetch may have filled the list between GetChats and LoadChats.
                endOfList = true
            }
            else -> return ChatListLoadResult(directoryError(result), hasMore = true)
        }
    }
    return ChatListLoadResult(
        error = if (unavailableDetails) "Some Telegram chats could not be loaded. Retry to recover them." else null,
        hasMore = mayHaveMore || unavailableDetails
    )
}

private suspend fun loadChatSource(
    owner: AccountSnapshot,
    chatId: Long,
    isArchived: Boolean,
    request: suspend (TdApi.Function<*>) -> TdApi.Object?
): VideoChatItem? {
    val chat = request(TdApi.GetChat(chatId)) as? TdApi.Chat ?: return null
    val type = chat.type
    val kind = when (type) {
        is TdApi.ChatTypePrivate -> if (type.userId == owner.accountId) ChatSourceKind.SAVED_MESSAGES else ChatSourceKind.PRIVATE
        is TdApi.ChatTypeBasicGroup -> ChatSourceKind.GROUP
        is TdApi.ChatTypeSupergroup -> if (type.isChannel) ChatSourceKind.CHANNEL else ChatSourceKind.GROUP
        is TdApi.ChatTypeSecret -> ChatSourceKind.SECRET
        else -> ChatSourceKind.OTHER
    }
    val isSaved = kind == ChatSourceKind.SAVED_MESSAGES
    val isForum = if (type is TdApi.ChatTypeSupergroup && !type.isChannel) {
        val group = request(TdApi.GetSupergroup(type.supergroupId)) as? TdApi.Supergroup
            ?: return null
        group.isForum
    } else false
    return VideoChatItem(
        chatId = chatId,
        title = if (isSaved) SAVED_MESSAGES_TITLE else chat.title.orEmpty(),
        isSavedMessages = isSaved,
        isArchived = isArchived && !isSaved,
        kind = kind,
        isForum = isForum
    )
}

private fun directoryError(response: TdApi.Object?): String =
    (response as? TdApi.Error)?.message?.takeIf { it.isNotBlank() }
        ?: "Telegram took too long to load chats. Try again."

private const val CHAT_PICKER_FIRST_PAGE = 24
private const val CHAT_PICKER_LOAD_BATCH = 50
private const val CHAT_PICKER_DETAIL_BATCH = 12
private const val CHAT_PICKER_REQUEST_TIMEOUT_MS = 8_000L

internal fun SavedMessagesRepository.resolveSavedMessagesChatInternal(
    onResult: (Long?, String?) -> Unit
) {
    val cachedChatId = cachedSavedMessagesChatId
    if (cachedChatId != null) {
        onResult(cachedChatId, null)
        return
    }

    // Authorization already verified this account with GetMe.
    val userId = owner?.accountId
    if (userId == null) {
        onResult(null, "Sign in to Telegram to open Saved Messages.")
        return
    }
    sendCatalogRequest(TdApi.CreatePrivateChat(userId, false)) { result ->
        when (result) {
            is TdApi.Chat -> {
                cachedSavedMessagesChatId = result.id
                onResult(result.id, null)
            }
            is TdApi.Error -> onResult(null, result.message)
            else -> onResult(null, "Failed to open Saved Messages")
        }
    }
}
