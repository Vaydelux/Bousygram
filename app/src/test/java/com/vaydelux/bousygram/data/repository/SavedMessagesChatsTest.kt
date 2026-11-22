package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.VideoChatItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedMessagesChatsTest {
    private val owner = AccountSnapshot(accountId = 100, generation = 1)

    @Test
    fun showsMainGroupsBeforeArchiveTimeout() = runTest {
        val pages = mutableListOf<List<VideoChatItem>>()
        val result = async {
            loadVideoChats(owner, 10_000, request = { query ->
                when (query) {
                    is TdApi.GetChats -> {
                        if (query.chatList is TdApi.ChatListArchive) awaitCancellation()
                        check(query.limit in 1..50)
                        TdApi.Chats(1, longArrayOf(42))
                    }
                    is TdApi.GetChat -> groupChat(query.chatId)
                    is TdApi.LoadChats -> TdApi.Error(404, "All chats loaded")
                    else -> error("Unexpected directory query")
                }
            }, onPage = { pages.add(it) })
        }

        runCurrent()

        assertFalse(result.isCompleted)
        assertEquals(42L, pages.last().single().chatId)
        assertEquals(ChatSourceKind.GROUP, pages.last().single().kind)
        assertNotNull(result.await())
    }

    @Test
    fun shortPageStillLoadsMoreChatsAndPreservesArchivedForums() = runTest {
        val nextPage = CompletableDeferred<Unit>()
        val pages = mutableListOf<List<VideoChatItem>>()
        var mainPageLoaded = false
        val result = async {
            loadVideoChats(owner, 50, request = { query ->
                when (query) {
                    is TdApi.GetChats -> when {
                        query.chatList is TdApi.ChatListArchive -> TdApi.Chats(1, longArrayOf(43))
                        mainPageLoaded -> TdApi.Chats(2, longArrayOf(41, 42))
                        else -> TdApi.Chats(2, longArrayOf(41))
                    }
                    is TdApi.LoadChats -> {
                        if (query.chatList is TdApi.ChatListMain && !mainPageLoaded) {
                            nextPage.await()
                            mainPageLoaded = true
                            TdApi.Ok()
                        } else TdApi.Error(404, "All chats loaded")
                    }
                    is TdApi.GetChat -> groupChat(query.chatId).apply {
                        if (id == 43L) type = TdApi.ChatTypeSupergroup(43, false)
                    }
                    is TdApi.GetSupergroup -> TdApi.Supergroup().apply { isForum = true }
                    else -> error("Unexpected directory query")
                }
            }, onPage = { pages.add(it) })
        }

        runCurrent()

        assertEquals(listOf(41L, 43L), pages.last().map { it.chatId })
        nextPage.complete(Unit)
        assertNull(result.await())
        assertEquals(listOf(41L, 42L, 43L), pages.last().map { it.chatId })
        val forum = pages.last().last()
        assertEquals(true, forum.isArchived)
        assertEquals(true, forum.isForum)
    }

    @Test
    fun oneFailedChatDetailKeepsValidGroupsVisibleAndAllowsRetry() = runTest {
        val pages = mutableListOf<List<VideoChatItem>>()
        var hasMore = false
        val error = loadVideoChats(owner, 3, request = { query ->
            when (query) {
                is TdApi.GetChats -> if (query.chatList is TdApi.ChatListArchive)
                    TdApi.Chats(0, longArrayOf())
                else TdApi.Chats(3, longArrayOf(41, 42, 43))
                is TdApi.GetChat -> if (query.chatId == 42L) TdApi.Error(500, "Temporary")
                    else groupChat(query.chatId)
                is TdApi.LoadChats -> TdApi.Error(404, "All chats loaded")
                else -> error("Unexpected request")
            }
        }, onHasMore = { hasMore = it }, onPage = { pages += it })

        assertEquals(listOf(41L, 43L), pages.last().map { it.chatId })
        assertNotNull("Failed details should be retryable, not silently discarded", error)
        assertEquals(true, hasMore)
    }

    @Test
    fun reusesPreviouslyLoadedMetadataWhenExpandingSourceWindow() = runTest {
        var chatRequests = 0
        var hasMore = true
        val cached = listOf(
            VideoChatItem(chatId = 41, title = "Group 41", isSavedMessages = false, kind = ChatSourceKind.GROUP)
        )
        val pages = mutableListOf<List<VideoChatItem>>()
        val failure = loadVideoChats(owner, 5, request = { query ->
            when (query) {
                is TdApi.GetChats -> if (query.chatList is TdApi.ChatListArchive)
                    TdApi.Chats(0, longArrayOf()) else TdApi.Chats(2, longArrayOf(41, 43))
                is TdApi.GetChat -> {
                    chatRequests++
                    groupChat(query.chatId)
                }
                is TdApi.LoadChats -> TdApi.Error(404, "All chats loaded")
                else -> error("Unexpected request")
            }
        }, knownSources = cached, onHasMore = { hasMore = it }, onPage = { pages += it })

        assertNull(failure)
        assertEquals(listOf(41L, 43L), pages.last().map { it.chatId })
        assertEquals(1, chatRequests)
        assertFalse(hasMore)
    }

    @Test
    fun transientDialogAndForumMetadataErrorsRecoverWithoutUserRetry() = runTest {
        var mainRequests = 0
        var metadataRequests = 0
        val pages = mutableListOf<List<VideoChatItem>>()
        val error = loadVideoChats(owner, 10, request = { query ->
            when (query) {
                is TdApi.GetChats -> if (query.chatList is TdApi.ChatListArchive)
                    TdApi.Chats(0, longArrayOf()) else {
                    mainRequests++
                    if (mainRequests == 1) TdApi.Error(503, "TDLib synchronizing")
                    else TdApi.Chats(1, longArrayOf(202))
                }
                is TdApi.GetChat -> groupChat(query.chatId).apply {
                    type = TdApi.ChatTypeSupergroup(500, false)
                }
                is TdApi.GetSupergroup -> {
                    metadataRequests++
                    if (metadataRequests == 1) TdApi.Error(500, "Temporary")
                    else TdApi.Supergroup().apply { isForum = true }
                }
                is TdApi.LoadChats -> TdApi.Error(404, "All chats loaded")
                else -> error("unexpected")
            }
        }, onPage = { pages += it })

        assertNull(error)
        assertEquals(2, mainRequests)
        assertEquals(2, metadataRequests)
        assertEquals(202L, pages.last().single().chatId)
        assertEquals(true, pages.last().single().isForum)
        assertEquals(true, pages.last().single().forumMetadataResolved)
    }

    @Test
    fun failedForumLookupKeepsGroupVisibleAndRepairsClassificationOnRefresh() = runTest {
        var metadataReady = false
        var getChatCount = 0
        val request: suspend (TdApi.Function<*>) -> TdApi.Object? = { query ->
            when (query) {
                is TdApi.GetChats -> if (query.chatList is TdApi.ChatListArchive)
                    TdApi.Chats(0, longArrayOf())
                else TdApi.Chats(1, longArrayOf(303))
                is TdApi.GetChat -> {
                    getChatCount++
                    groupChat(query.chatId).apply { type = TdApi.ChatTypeSupergroup(600, false) }
                }
                is TdApi.GetSupergroup -> if (metadataReady)
                    TdApi.Supergroup().apply { isForum = true }
                else TdApi.Error(503, "Not ready")
                is TdApi.LoadChats -> TdApi.Error(404, "All chats loaded")
                else -> error("unexpected")
            }
        }
        var first = emptyList<VideoChatItem>()
        val initialError = loadVideoChats(owner, 5, request = request, onPage = { first = it })
        assertNotNull(initialError)
        assertEquals(303L, first.single().chatId)
        assertFalse(first.single().forumMetadataResolved)

        metadataReady = true
        var recovered = emptyList<VideoChatItem>()
        val recoveryError = loadVideoChats(
            owner, 5, request = request, knownSources = first,
            onPage = { recovered = it }
        )
        assertNull(recoveryError)
        assertEquals(2, getChatCount)
        assertEquals(true, recovered.single().forumMetadataResolved)
        assertEquals(true, recovered.single().isForum)
    }

    private fun groupChat(chatId: Long) = TdApi.Chat().apply {
        id = chatId
        title = "Group $chatId"
        type = TdApi.ChatTypeBasicGroup(chatId)
    }
}
