package com.vaydelux.bousygram.core.media

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The native compatibility engine must never receive remote Telegram token URLs.
 * These checks use Android's real URI parser, not JVM unit-test stubs.
 */
@RunWith(AndroidJUnit4::class)
class ExperimentalStreamUrlPolicyInstrumentedTest {
    @Test fun onlyLiteralLocalhostCapabilityStreamsAreAccepted() {
        assertTrue(isTrustedExperimentalLocalStream(
            Uri.parse("http://127.0.0.1:45851/media/123?token=example")
        ))
        val rejected = listOf(
            "https://127.0.0.1:45851/media/123?token=example",
            "http://localhost:45851/media/123?token=example",
            "http://192.168.1.7:45851/media/123?token=example",
            "http://127.0.0.1.evil.test:45851/media/123?token=example",
            "http://user:secret@127.0.0.1:45851/media/123",
            "http://127.0.0.1/media/123",
            "file:///sdcard/Movies/private.ts",
            "tdlib://telegram/123"
        )
        rejected.forEach { candidate ->
            assertFalse(candidate, isTrustedExperimentalLocalStream(Uri.parse(candidate)))
        }
    }
}
