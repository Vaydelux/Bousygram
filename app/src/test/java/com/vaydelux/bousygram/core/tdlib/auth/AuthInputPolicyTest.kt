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
    fun loginCodeMustBeFiveOrSixDigits() {
        assertNull(AuthInputPolicy.codeError("12345"))
        assertNull(AuthInputPolicy.codeError("123456"))
        assertEquals(
            "Enter the 5-digit verification code from Telegram",
            AuthInputPolicy.codeError("12ab")
        )
        assertEquals(
            "Enter the 5-digit verification code from Telegram",
            AuthInputPolicy.codeError("123")
        )
        assertEquals(
            "Enter the 5-digit verification code from Telegram",
            AuthInputPolicy.codeError("12345678")
        )
    }

    @Test
    fun dummyAndFakePhoneNumbersAreRejected() {
        assertEquals(
            "That phone number is not valid. Include the country code, for example +15551234567",
            AuthInputPolicy.phoneError("+1234567890")
        )
        assertEquals(
            "That phone number is not valid. Include the country code, for example +15551234567",
            AuthInputPolicy.phoneError("+0000000000")
        )
        assertEquals(
            "That phone number is not valid. Include the country code, for example +15551234567",
            AuthInputPolicy.phoneError("+1111111111")
        )
        // Telegram official test DC numbers are allowed
        assertNull(AuthInputPolicy.phoneError("+9996621111"))
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
