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

    private fun groupChat(chatId: Long) = TdApi.Chat().apply {
        id = chatId
        title = "Group $chatId"
        type = TdApi.ChatTypeBasicGroup(chatId)
    }
}
