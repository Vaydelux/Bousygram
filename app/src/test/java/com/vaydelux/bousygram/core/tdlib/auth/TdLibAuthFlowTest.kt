package com.vaydelux.bousygram.core.tdlib.auth

import com.vaydelux.bousygram.core.tdlib.client.TdLibFallbackEngine
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TdLibAuthFlowTest {

    @Before
    fun setUp() {
        val latch = CountDownLatch(1)
        TdLibFallbackEngine.send(
            TdApi.SetTdlibParameters(
                false, "/tmp/tdlib", "/tmp/files", byteArrayOf(),
                true, true, true, false, 2040, "b18441a1ff607e10a989891a5462e627",
                "en", "TestDevice", "Android", "1.0"
            ),
            { latch.countDown() },
            null,
            null
        )
        latch.await(2, TimeUnit.SECONDS)
    }

    @Test
    fun fakeAndDummyPhoneNumbersAreRejectedWithInvalidError() {
        val fakeNumbers = listOf("+1234567890", "+0000000000", "+1111111111", "invalid", "+0123456789")

        for (fake in fakeNumbers) {
            val latch = CountDownLatch(1)
            var response: TdApi.Object? = null
            TdLibFallbackEngine.send(
                TdApi.SetAuthenticationPhoneNumber(fake, null),
                { result ->
                    response = result
                    latch.countDown()
                },
                null,
                null
            )
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertTrue(response is TdApi.Error)
            assertEquals("PHONE_NUMBER_INVALID", (response as TdApi.Error).message)
        }

        // Verify state did NOT advance to WaitCode
        val stateLatch = CountDownLatch(1)
        var isWaitPhone = false
        TdLibFallbackEngine.send(
            TdApi.GetAuthorizationState(),
            { res ->
                isWaitPhone = res is TdApi.AuthorizationStateWaitPhoneNumber
                stateLatch.countDown()
            },
            null,
            null
        )
        assertTrue(stateLatch.await(2, TimeUnit.SECONDS))
        assertTrue("Auth state should still be WaitPhoneNumber", isWaitPhone)
    }

    @Test
    fun fakeCodeIsRejectedAndDoesNotAuthenticateUser() {
        // 1. Submit a valid phone number
        val phoneLatch = CountDownLatch(1)
        var phoneOk = false
        TdLibFallbackEngine.send(
            TdApi.SetAuthenticationPhoneNumber("+15551234567", null),
            { res ->
                phoneOk = res is TdApi.Ok
                phoneLatch.countDown()
            },
            null,
            null
        )
        assertTrue(phoneLatch.await(2, TimeUnit.SECONDS))
        assertTrue(phoneOk)

        // 2. Submit wrong/fake verification code
        val codeLatch = CountDownLatch(1)
        var isError = false
        var errorMessage = ""
        TdLibFallbackEngine.send(
            TdApi.CheckAuthenticationCode("99999"),
            { res ->
                if (res is TdApi.Error) {
                    isError = true
                    errorMessage = res.message
                }
                codeLatch.countDown()
            },
            null,
            null
        )
        assertTrue(codeLatch.await(2, TimeUnit.SECONDS))
        assertTrue("Fake code must be rejected with TdApi.Error", isError)
        assertEquals("PHONE_CODE_INVALID", errorMessage)

        // 3. Verify user is NOT authorized into the app
        val stateLatch = CountDownLatch(1)
        var isReady = false
        var isWaitCode = false
        TdLibFallbackEngine.send(
            TdApi.GetAuthorizationState(),
            { res ->
                isReady = res is TdApi.AuthorizationStateReady
                isWaitCode = res is TdApi.AuthorizationStateWaitCode
                stateLatch.countDown()
            },
            null,
            null
        )
        assertTrue(stateLatch.await(2, TimeUnit.SECONDS))
        assertFalse("Must not be authorized with fake code", isReady)
        assertTrue("Must remain in WaitCode", isWaitCode)
    }

    @Test
    fun repeatedWrongCodesTriggerFloodWait() {
        val phoneLatch = CountDownLatch(1)
        TdLibFallbackEngine.send(
            TdApi.SetAuthenticationPhoneNumber("+15551234567", null),
            { phoneLatch.countDown() },
            null,
            null
        )
        phoneLatch.await(2, TimeUnit.SECONDS)

        var lastErrorMessage = ""
        for (i in 1..4) {
            val codeLatch = CountDownLatch(1)
            TdLibFallbackEngine.send(
                TdApi.CheckAuthenticationCode("0000$i"),
                { res ->
                    if (res is TdApi.Error) lastErrorMessage = res.message
                    codeLatch.countDown()
                },
                null,
                null
            )
            codeLatch.await(2, TimeUnit.SECONDS)
        }

        assertTrue("Expected flood wait after repeated failures, got: $lastErrorMessage", lastErrorMessage.contains("FLOOD_WAIT"))
    }

    @Test
    fun correctVerificationCodeAuthorizesUser() {
        val phoneLatch = CountDownLatch(1)
        TdLibFallbackEngine.send(
            TdApi.SetAuthenticationPhoneNumber("+15551234567", null),
            { phoneLatch.countDown() },
            null,
            null
        )
        phoneLatch.await(2, TimeUnit.SECONDS)

        // Submit the designated valid code (22222)
        val codeLatch = CountDownLatch(1)
        var codeOk = false
        TdLibFallbackEngine.send(
            TdApi.CheckAuthenticationCode("22222"),
            { res ->
                codeOk = res is TdApi.Ok
                codeLatch.countDown()
            },
            null,
            null
        )
        assertTrue(codeLatch.await(2, TimeUnit.SECONDS))
        assertTrue("Valid code must return Ok", codeOk)

        // Verify state is Ready
        val stateLatch = CountDownLatch(1)
        var isReady = false
        TdLibFallbackEngine.send(
            TdApi.GetAuthorizationState(),
            { res ->
                isReady = res is TdApi.AuthorizationStateReady
                stateLatch.countDown()
            },
            null,
            null
        )
        assertTrue(stateLatch.await(2, TimeUnit.SECONDS))
        assertTrue("User must be authorized", isReady)
    }

    @Test
    fun qrAuthenticationGeneratesUniqueTokenEachTime() {
        val qrLatch1 = CountDownLatch(1)
        var link1: String? = null
        TdLibFallbackEngine.send(
            TdApi.RequestQrCodeAuthentication(longArrayOf()),
            { /* resultHandler */ },
            null,
            { update ->
                if (update is TdApi.UpdateAuthorizationState &&
                    update.authorizationState is TdApi.AuthorizationStateWaitOtherDeviceConfirmation
                ) {
                    link1 = (update.authorizationState as TdApi.AuthorizationStateWaitOtherDeviceConfirmation).link
                    qrLatch1.countDown()
                }
            }
        )
        assertTrue(qrLatch1.await(2, TimeUnit.SECONDS))
        assertTrue("QR link must start with tg://login?token=", link1?.startsWith("tg://login?token=") == true)

        // Request again (refresh)
        val qrLatch2 = CountDownLatch(1)
        var link2: String? = null
        TdLibFallbackEngine.send(
            TdApi.RequestQrCodeAuthentication(longArrayOf()),
            { /* resultHandler */ },
            null,
            { update ->
                if (update is TdApi.UpdateAuthorizationState &&
                    update.authorizationState is TdApi.AuthorizationStateWaitOtherDeviceConfirmation
                ) {
                    link2 = (update.authorizationState as TdApi.AuthorizationStateWaitOtherDeviceConfirmation).link
                    qrLatch2.countDown()
                }
            }
        )
        assertTrue(qrLatch2.await(2, TimeUnit.SECONDS))
        assertTrue("Second QR link must start with tg://login?token=", link2?.startsWith("tg://login?token=") == true)
        assertNotEquals("Each QR request must generate a fresh unique token", link1, link2)
    }
}
