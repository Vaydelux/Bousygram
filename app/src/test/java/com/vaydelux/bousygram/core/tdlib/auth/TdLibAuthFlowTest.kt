package com.vaydelux.bousygram.core.tdlib.auth

import com.vaydelux.bousygram.core.tdlib.client.TdLibFallbackEngine
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TdLibAuthFlowTest {
    private fun send(query: TdApi.Function<*>): TdApi.Object {
        val latch = CountDownLatch(1)
        var result: TdApi.Object? = null
        TdLibFallbackEngine.send(query, { response -> result = response; latch.countDown() }, null, null)
        assertTrue(latch.await(2, TimeUnit.SECONDS))
        return result ?: error("Fallback did not return a result")
    }

    private fun assertUnavailable(result: TdApi.Object) {
        assertTrue(result is TdApi.Error)
        assertEquals(TdLibFallbackEngine.NATIVE_UNAVAILABLE_CODE, (result as TdApi.Error).code)
        assertEquals(TdLibFallbackEngine.NATIVE_UNAVAILABLE_MESSAGE, result.message)
    }

    @Test
    fun authenticationInputsNeverAuthorize() {
        assertUnavailable(send(TdApi.RequestQrCodeAuthentication(longArrayOf())))
        assertUnavailable(send(TdApi.SetAuthenticationPhoneNumber("+15551234567", null)))
        assertUnavailable(send(TdApi.CheckAuthenticationCode("22222")))
        assertUnavailable(send(TdApi.CheckAuthenticationPassword("password")))
        assertUnavailable(send(TdApi.LogOut()))
    }

    @Test
    fun accountAndChatReadsNeverReturnFabricatedData() {
        assertUnavailable(send(TdApi.GetMe()))
        assertUnavailable(send(TdApi.GetChat(777000L)))
        assertUnavailable(send(TdApi.GetChats(TdApi.ChatListMain(), 10)))
        assertUnavailable(send(TdApi.SearchMessages(TdApi.ChatListMain(), "", "", 10, TdApi.SearchMessagesFilterVideo(), null, 0, 0)))
    }

    @Test
    fun mutationsAndFilesNeverReportSuccess() {
        assertUnavailable(send(TdApi.DeleteMessages(1L, longArrayOf(1L), true)))
        assertUnavailable(send(TdApi.OpenChat(1L)))
        assertUnavailable(send(TdApi.DownloadFile(1, 1, 0, 0, true)))
        assertUnavailable(send(TdApi.OptimizeStorage(0L, -1, -1, -1, emptyArray(), longArrayOf(), longArrayOf(), false, 0)))
    }

    @Test
    fun unsupportedSynchronousCallsFailClosed() {
        assertUnavailable(TdLibFallbackEngine.execute(TdApi.GetMe()))
    }
}
