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
    knownSources: List<VideoChatItem> = emptyList(),
    onHasMore: (Boolean) -> Unit = {},
    onPage: (List<VideoChatItem>) -> Unit
): String? = coroutineScope {
    // The caller owns publication on the main dispatcher. Each list advances independently,
    // so a slow archive never blocks the first main page.
    // Reuse current-account metadata when expanding the directory instead of
    // reissuing GetChat/GetSupergroup for every already visible folder.
    val mainSources = LinkedHashMap<Long, VideoChatItem>().apply {
        knownSources.filterNot { it.isArchived }.forEach { put(it.chatId, it) }
    }
    val archivedSources = LinkedHashMap<Long, VideoChatItem>().apply {
        knownSources.filter { it.isArchived }.forEach { put(it.chatId, it) }
    }

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
    // Also publish on a cache hit where no new GetChat calls were needed.
    publish()
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
    // A partial forum classification stays eligible on the *next scan*.
    // Within this scan GetChats may expand/replay its prefix repeatedly;
    // requesting the same unresolved group's metadata on every replay
    // consumes retries and delays first visible content for large accounts.
    val attemptedMetadata = hashSetOf<Long>()
    while (true) {
        val response = retryTransientTelegramCatalogRequest(
            TdApi.GetChats(chatList, queryLimit), request = request
        )
        val ids = (response as? TdApi.Chats)?.chatIds
            ?: return ChatListLoadResult(directoryError(response), hasMore = true)
        // GetChats expands an accumulated prefix. Do not drop already resolved
        // metadata when the first small prefix is returned during a later scan.
        val removed = if (ids.size >= limit || (endOfList && ids.size < queryLimit)) {
            sources.keys.retainAll(ids.toSet())
        } else false
        val batches = ids.filter { id ->
            (sources[id] == null || sources[id]?.forumMetadataResolved == false) &&
                id !in attemptedMetadata
        }.chunked(CHAT_PICKER_DETAIL_BATCH)

        for ((index, batch) in batches.withIndex()) {
            attemptedMetadata.addAll(batch)
            val chats = coroutineScope {
                batch.map { chatId -> async { loadChatSource(owner, chatId, isArchived, request) } }.awaitAll()
            }
            chats.filterNotNull().forEach { sources[it.chatId] = it }
            // A single timed-out group/topic metadata request must not hide the
            // other successfully loaded channels and groups.
            if (chats.any { it == null || !it.forumMetadataResolved }) unavailableDetails = true
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
        when (val result = retryTransientTelegramCatalogRequest(
            TdApi.LoadChats(chatList, CHAT_PICKER_LOAD_BATCH), request = request
        )) {
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
        error = if (unavailableDetails) "Some Telegram group details are still syncing. Existing sources remain available." else null,
        hasMore = mayHaveMore || unavailableDetails
    )
}

private suspend fun loadChatSource(
    owner: AccountSnapshot,
    chatId: Long,
    isArchived: Boolean,
    request: suspend (TdApi.Function<*>) -> TdApi.Object?
): VideoChatItem? {
    val chat = retryTransientTelegramCatalogRequest(
        TdApi.GetChat(chatId), attempts = 2, request = request
    ) as? TdApi.Chat ?: return null
    val type = chat.type
    val kind = when (type) {
        is TdApi.ChatTypePrivate -> if (type.userId == owner.accountId) ChatSourceKind.SAVED_MESSAGES else ChatSourceKind.PRIVATE
        is TdApi.ChatTypeBasicGroup -> ChatSourceKind.GROUP
        is TdApi.ChatTypeSupergroup -> if (type.isChannel) ChatSourceKind.CHANNEL else ChatSourceKind.GROUP
        is TdApi.ChatTypeSecret -> ChatSourceKind.SECRET
        else -> ChatSourceKind.OTHER
    }
    val isSaved = kind == ChatSourceKind.SAVED_MESSAGES
    // Do not hide a valid Telegram group when the additional GetSupergroup
    // request times out during account synchronization. Mark classification
    // incomplete; the next refresh retries it rather than caching a false answer.
    val group = if (type is TdApi.ChatTypeSupergroup && !type.isChannel) {
        retryTransientTelegramCatalogRequest(
            TdApi.GetSupergroup(type.supergroupId), attempts = 2, request = request
        ) as? TdApi.Supergroup
    } else null
    val forumMetadataResolved = type !is TdApi.ChatTypeSupergroup ||
        type.isChannel || group != null
    val isForum = group?.isForum ?: false
    return VideoChatItem(
        chatId = chatId,
        title = if (isSaved) SAVED_MESSAGES_TITLE else chat.title.orEmpty(),
        isSavedMessages = isSaved,
        isArchived = isArchived && !isSaved,
        kind = kind,
        isForum = isForum,
        forumMetadataResolved = forumMetadataResolved
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
