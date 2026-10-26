package com.vaydelux.bousygram.core.tdlib.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInputPolicyTest {
    @Test
    fun phoneNormalizationStripsFormattingAndInternationalPrefix() {
        assertEquals("+15551234567", AuthInputPolicy.normalizePhone(" +1 (555) 123-4567 "))
        assertEquals("+15551234567", AuthInputPolicy.normalizePhone("0015551234567"))
        assertNull(AuthInputPolicy.phoneError("+15551234567"))
        assertEquals(
            "Use an international number, for example +15551234567",
            AuthInputPolicy.phoneError("5551234567")
        )
    }

    @Test
    fun loginCodeMustBeNumeric() {
        assertNull(AuthInputPolicy.codeError("12345"))
        assertEquals(
            "Enter the numeric login code from Telegram",
            AuthInputPolicy.codeError("12ab")
        )
    }

    @Test
    fun apiCredentialsRequirePositiveIdAndHexHash() {
        assertNull(AuthInputPolicy.apiCredentialsError("123456", "abcdefabcdefabcdefabcdefabcdefab"))
        assertEquals(
            "Enter a 32-character API hash",
            AuthInputPolicy.apiCredentialsError("123456", "not-a-hash")
        )
    }

    @Test
    fun floodWaitIncludesTheServerDelay() {
        assertEquals(
            "Too many attempts. Wait 42 seconds, then try again.",
            AuthInputPolicy.friendlyMessage("FLOOD_WAIT_42")
        )
    }

    @Test
    fun defaultApiCredentialsAreValid() {
        assertNull(
            AuthInputPolicy.apiCredentialsError(
                TelegramAuthManager.DEFAULT_API_ID.toString(),
                TelegramAuthManager.DEFAULT_API_HASH
            )
        )
    }
}
