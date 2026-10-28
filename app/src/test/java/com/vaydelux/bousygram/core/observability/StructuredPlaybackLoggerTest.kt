package com.vaydelux.bousygram.core.observability

import com.vaydelux.bousygram.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredPlaybackLoggerTest {
    @Test
    fun crashReportingIsDisabledByDefault() {
        assertFalse(BuildConfig.CRASH_REPORTING_ENABLED)
    }

    @Test
    fun diagnosticFormattingRedactsUrlsAndOmitsTelegramIdentifiers() {
        val line = StructuredPlaybackLogger.formatForDiagnostics(
            event = "stream_failure",
            message = "failed http://127.0.0.1:4321/stream?fileId=8&token=secret",
            context = PlaybackLogContext(fileId = 8, messageId = 11L, chatId = 12L)
        )

        assertTrue(line.contains("[url]"))
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("fileId"))
        assertFalse(line.contains("messageId"))
        assertFalse(line.contains("chatId"))
    }

    @Test
    fun diagnosticRedactionMasksCredentialParametersWithoutAUrl() {
        val redacted = StructuredPlaybackLogger.redact("authorization=BearerSecret api_key=PrivateKey")

        assertFalse(redacted.contains("BearerSecret"))
        assertFalse(redacted.contains("PrivateKey"))
        assertTrue(redacted.contains("[redacted]"))
    }
}
