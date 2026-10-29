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
        }) { topics, error -> assertNull(error); items = topics }.load()
        assertEquals(2, requests.size)
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
        }) { result, error -> items = result; lastError = error }.load()
        assertEquals(2, requests)
        assertEquals(1, items.size)
        assertNotNull(lastError)
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
        }) { result, error -> items = result; lastError = error }.load()
        assertEquals(1, items.size)
        assertEquals("Temporary server failure", lastError)
    }
}
