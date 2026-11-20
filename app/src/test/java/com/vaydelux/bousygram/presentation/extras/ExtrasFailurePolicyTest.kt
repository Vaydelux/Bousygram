package com.vaydelux.bousygram.presentation.extras

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasFailurePolicyTest {
    @Test fun timeoutsAreActionable() {
        assertTrue(ExtrasFailurePolicy.message(SocketTimeoutException("socket timed out"))
            .contains("took too long"))
    }

    @Test fun offlineHostsAreExplained() {
        assertTrue(ExtrasFailurePolicy.message(UnknownHostException("offline"))
            .contains("Check your connection"))
    }

    @Test fun providerRateLimitsHaveNoAutomaticRetryPromise() {
        assertTrue(ExtrasFailurePolicy.message(IOException("HTTP 429 /token/secret"))
            .contains("limiting requests"))
    }

    @Test fun authErrorsDoNotLeakRawApiResponse() {
        val raw = "HTTP 401 /v3/movie?api_key=top-secret"
        val msg = ExtrasFailurePolicy.message(IOException(raw))
        assertTrue(msg.contains("not configured"))
        assertFalse(msg.contains("top-secret"))
        assertFalse(msg.contains("api_key"))
    }

    @Test fun sslErrorsProvideDeviceTimeHint() {
        assertTrue(ExtrasFailurePolicy.message(SSLHandshakeException("bad certificate"))
            .contains("device clock"))
    }

    @Test fun unknownErrorsNeverEchoSensitiveText() {
        val msg = ExtrasFailurePolicy.message(
            IllegalArgumentException("Authorization: Bearer MY-PRIVATE-TOKEN")
        )
        assertFalse(msg.contains("MY-PRIVATE"))
        assertTrue(msg.contains("Try again"))
    }
}
