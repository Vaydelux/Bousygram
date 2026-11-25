package com.vaydelux.bousygram.core.cast

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TelegramTvHandoffTest {
    @Test fun requiresPairingAndResolvesUsingTvAccount() = runBlocking {
        val request = TelegramTvHandoff(-100123L, 42L, 5000L, false)
        var calls = 0
        suspend fun resolve(chat: Long, message: Long): Int {
            calls++
            assertEquals(request.chatId, chat)
            assertEquals(request.messageId, message)
            return 987
        }
        assertNull(TelegramTvHandoffPolicy.resolve(request, false, ::resolve))
        assertEquals(0, calls)
        val result = TelegramTvHandoffPolicy.resolve(request, true, ::resolve)!!
        assertEquals(987, result.localFileId)
        assertEquals(request, result.request)
        assertFalse(result.request.playRequested)
        assertEquals(1, calls)
        assertNull(TelegramTvHandoffPolicy.resolve(request, true) { _, _ -> null })
        assertNull(TelegramTvHandoffPolicy.resolve(request, true) { _, _ -> -1 })
    }

    @Test fun rejectsInvalidReferencesBeforeResolution() = runBlocking {
        val valid = TelegramTvHandoff(-100L, 2L, 0L, true)
        for (request in listOf(valid.copy(chatId = 0), valid.copy(messageId = 0),
            valid.copy(positionMs = -1), valid.copy(positionMs = Long.MAX_VALUE))) {
            assertNull(TelegramTvHandoffPolicy.resolve(request, true) { _, _ ->
                error("Invalid reference reached account resolver")
            })
        }
    }
}
