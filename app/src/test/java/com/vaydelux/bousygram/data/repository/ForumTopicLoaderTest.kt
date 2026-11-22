package com.vaydelux.bousygram.data.repository

import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class ForumTopicLoaderTest {
    private fun topic(id: Int, orderValue: Long, unread: Int = 0): TdApi.ForumTopic = TdApi.ForumTopic().apply {
        info = TdApi.ForumTopicInfo().apply { forumTopicId = id; name = "Topic $id" }
        order = orderValue
        unreadCount = unread
    }

    @Test
    fun followsExplicitCursorEvenForShortPagesAndUsesTelegramOrder() {
        val requests = mutableListOf<TdApi.GetForumTopics>()
        var items = emptyList<ForumTopicChannel>()
        ForumTopicLoader(7L, "query", { request, reply ->
            requests += request
            reply(if (requests.size == 1) TdApi.ForumTopics(2, arrayOf(topic(1, 10, 3)), 45, 99L, 1)
            else TdApi.ForumTopics(2, arrayOf(topic(2, 20)), 0, 0L, 0))
        }) { topics, error, _ -> assertNull(error); items = topics }.load()
        assertEquals(2, requests.size)
        assertEquals(40, requests[0].limit)
        assertEquals(100, requests[1].limit)
        assertEquals(45, requests[1].offsetDate)
        assertEquals(99L, requests[1].offsetMessageId)
        assertEquals(1, requests[1].offsetForumTopicId)
        assertEquals(listOf(2, 1), items.map { it.id })
        assertEquals(3, items.last().unreadCount)
        assertNull("Unread count must not be presented as total messages", items.last().totalMessages)
    }

    @Test
    fun repeatedCursorStopsWithoutDiscardingValidTopics() {
        var requests = 0
        var lastError: String? = null
        var items = emptyList<ForumTopicChannel>()
        ForumTopicLoader(7L, "", { _, reply ->
            requests++
            reply(TdApi.ForumTopics(2, arrayOf(topic(1, 10)), 45, 99L, 1))
        }) { result, error, _ -> items = result; lastError = error }.load()
        assertEquals(2, requests)
        assertEquals(1, items.size)
        assertNotNull(lastError)
    }

    @Test
    fun partialTopicsAppearBeforeTheFinalPage() {
        val responses = mutableListOf<(TdApi.Object?) -> Unit>()
        val updates = mutableListOf<Pair<List<ForumTopicChannel>, Boolean>>()
        val loader = ForumTopicLoader(7L, "", { _, reply ->
            responses.add(reply)
        }) { items, error, isFinal ->
            assertNull(error)
            updates.add(items to isFinal)
        }
        loader.load()
        assertEquals(1, responses.size)
        responses[0](TdApi.ForumTopics(2, arrayOf(topic(1, 10)), 45, 99L, 1))
        assertEquals(listOf(1), updates.single().first.map { it.id })
        assertFalse(updates.single().second)
        assertEquals(2, responses.size)
        responses[1](TdApi.ForumTopics(2, arrayOf(topic(2, 20)), 0, 0L, 0))
        assertTrue(updates.last().second)
        assertEquals(listOf(2, 1), updates.last().first.map { it.id })
    }

    @Test
    fun manualPagingDoesNotFetchTheSecondPageUntilRequested() {
        val requests = mutableListOf<TdApi.GetForumTopics>()
        val responses = mutableListOf<(TdApi.Object?) -> Unit>()
        val updates = mutableListOf<Pair<List<ForumTopicChannel>, Boolean>>()
        var hasMore = false
        val loader = ForumTopicLoader(
            7L, "", { request, reply -> requests += request; responses += reply },
            { topics, error, done ->
                assertNull(error)
                updates += topics to done
            }, autoAdvance = false, onHasMore = { hasMore = it }
        )
        loader.load()
        assertEquals(1, requests.size)
        responses[0](TdApi.ForumTopics(2, arrayOf(topic(1, 20)), 20, 50L, 1))
        assertEquals(1, requests.size)
        assertEquals(listOf(1), updates.single().first.map { it.id })
        assertTrue("First page is complete, even when later pages remain", updates.single().second)
        assertTrue(hasMore)
        loader.loadNextPage()
        assertEquals(2, requests.size)
        assertEquals(20, requests[1].offsetDate)
        responses[1](TdApi.ForumTopics(2, arrayOf(topic(2, 10)), 0, 0L, 0))
        assertEquals(listOf(1, 2), updates.last().first.map { it.id })
        assertFalse(hasMore)
        loader.loadNextPage()
        assertEquals(2, requests.size)
    }

    @Test
    fun twoThousandTopicDirectoryDoesNotAutomaticallyScanBeyondFirstWindow() {
        val sent = mutableListOf<TdApi.GetForumTopics>()
        var visible = emptyList<ForumTopicChannel>()
        var hasMore = false
        val loader = ForumTopicLoader(
            21L, "", { req, reply ->
                sent += req
                val index = sent.size
                val count = if (index == 1) 40 else 100
                val start = if (index == 1) 1 else 41 + (index - 2) * 100
                val last = minOf(start + count - 1, 2_000)
                reply(TdApi.ForumTopics(
                    2_000,
                    (start..last).map { topic(it, (2_001 - it).toLong()) }.toTypedArray(),
                    if (last == 2_000) 0 else last,
                    if (last == 2_000) 0L else last.toLong(),
                    if (last == 2_000) 0 else last
                ))
            },
            { topics, error, done ->
                assertNull(error)
                assertTrue(done)
                visible = topics
            },
            autoAdvance = false,
            onHasMore = { hasMore = it }
        )
        loader.load()
        assertEquals(1, sent.size)
        assertEquals(40, visible.size)
        assertTrue(hasMore)
        loader.loadNextPage()
        assertEquals(2, sent.size)
        assertEquals(140, visible.size)
        assertTrue(hasMore)
        assertEquals(100, sent.last().limit)
    }

    @Test
    fun failedNextPageCanRetryExactCursorAndRetainsEarlierTopics() {
        val requests = mutableListOf<TdApi.GetForumTopics>()
        var visible = emptyList<ForumTopicChannel>()
        var message: String? = null
        var more = false
        val loader = ForumTopicLoader(
            7L, "", { request, reply ->
                requests += request
                reply(when (requests.size) {
                    1 -> TdApi.ForumTopics(2, arrayOf(topic(1, 20)), 40, 90L, 1)
                    2 -> TdApi.Error(503, "Temporary")
                    else -> TdApi.ForumTopics(2, arrayOf(topic(2, 10)), 0, 0L, 0)
                })
            },
            { topics, error, _ -> visible = topics; message = error },
            autoAdvance = false,
            onHasMore = { more = it }
        )
        loader.load()
        loader.loadNextPage()
        assertEquals("Temporary", message)
        assertEquals(listOf(1), visible.map { it.id })
        assertTrue(more)
        loader.loadNextPage()
        assertEquals(3, requests.size)
        assertEquals(requests[1].offsetDate, requests[2].offsetDate)
        assertEquals(requests[1].offsetMessageId, requests[2].offsetMessageId)
        assertNull(message)
        assertEquals(listOf(1, 2), visible.map { it.id })
        assertFalse(more)
    }

    @Test
    fun cancelledTopicRequestIgnoresLateCallbacks() {
        val replies = mutableListOf<(TdApi.Object?) -> Unit>()
        var updates = 0
        val loader = ForumTopicLoader(7L, "", { _, reply ->
            replies.add(reply)
        }) { _, _, _ -> updates++ }
        loader.load()
        loader.cancel()
        replies.single()(TdApi.ForumTopics(1, arrayOf(topic(1, 10)), 45, 99L, 1))
        assertEquals(0, updates)
        assertEquals(1, replies.size)
    }

    @Test
    fun laterPageFailurePreservesPreviouslyLoadedTopics() {
        var requests = 0
        var lastError: String? = null
        var items = emptyList<ForumTopicChannel>()
        ForumTopicLoader(7L, "", { _, reply ->
            requests++
            reply(if (requests == 1) TdApi.ForumTopics(2, arrayOf(topic(1, 10)), 45, 99L, 1)
            else TdApi.Error(500, "Temporary server failure"))
        }) { result, error, _ -> items = result; lastError = error }.load()
        assertEquals(1, items.size)
        assertEquals("Temporary server failure", lastError)
    }
}
