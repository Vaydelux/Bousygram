package com.vaydelux.bousygram.data.repository

import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramCatalogRetryPolicyTest {
    @Test fun postLoginTemporaryFailuresRecoverWithinThreeAttempts() = runTest {
        var attempts = 0
        val response = retryTransientTelegramCatalogRequest(
            TdApi.GetChats(TdApi.ChatListMain(), 24)
        ) {
            attempts++
            if (attempts < 3) TdApi.Error(503, "Synchronizing")
            else TdApi.Chats(1, longArrayOf(77))
        }
        assertTrue(response is TdApi.Chats)
        assertEquals(3, attempts)
    }

    @Test fun authDenialAndEndOfListNeverRetry() = runTest {
        val function = TdApi.LoadChats(TdApi.ChatListMain(), 50)
        for (code in listOf(400, 401, 403, 404, 429)) {
            var calls = 0
            val response = retryTransientTelegramCatalogRequest(function) {
                calls++
                TdApi.Error(code, "Permanent or rate-limited")
            }
            assertEquals(code, (response as TdApi.Error).code)
            assertEquals("A permanent Telegram response must not be retried", 1, calls)
        }
        assertFalse(isTransientTelegramCatalogFailure(TdApi.Error(403, "Access denied")))
    }

    @Test fun failureStopsAfterBudgetWithoutPollingLoop() = runTest {
        var calls = 0
        val response = retryTransientTelegramCatalogRequest(
            TdApi.GetChats(TdApi.ChatListMain(), 24),
            attempts = 2
        ) {
            calls++
            TdApi.Error(500, "Temporary")
        }
        assertEquals(2, calls)
        assertTrue(response is TdApi.Error)
    }
}
