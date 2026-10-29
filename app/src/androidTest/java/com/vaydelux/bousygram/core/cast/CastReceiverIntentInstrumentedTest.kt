package com.vaydelux.bousygram.core.cast

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise Android Intent extras, which the local JVM Android stubs do not implement. */
@RunWith(AndroidJUnit4::class)
class CastReceiverIntentInstrumentedTest {
    private fun validIntent() = Intent(CastReceiverManager.ACTION_CAST_LOAD).apply {
        putExtra(CastReceiverManager.EXTRA_CHAT_ID, -100123L)
        putExtra(CastReceiverManager.EXTRA_MESSAGE_ID, 42L)
        putExtra(CastReceiverManager.EXTRA_FILE_ID, 13)
        putExtra(CastReceiverManager.EXTRA_TITLE, "Episode 1")
        putExtra(CastReceiverManager.EXTRA_POSITION_MS, 1_000L)
        putExtra(CastReceiverManager.EXTRA_DURATION_MS, 5_000L)
    }

    @Test
    fun validExplicitExtrasUseTheValidatedPayload() {
        val parsed = CastReceiverManager.parseIntent(validIntent())
        assertNotNull(parsed)
        assertEquals(13, parsed?.fileId)
        assertEquals(1_000L, parsed?.positionMs)
    }

    @Test
    fun invalidDirectExtrasCannotBypassValidation() {
        val negativePosition = validIntent().apply {
            putExtra(CastReceiverManager.EXTRA_POSITION_MS, -1L)
        }
        assertNull(CastReceiverManager.parseIntent(negativePosition))

        val oversizedTitle = validIntent().apply {
            putExtra(CastReceiverManager.EXTRA_TITLE, "x".repeat(257))
        }
        assertNull(CastReceiverManager.parseIntent(oversizedTitle))

        val outOfBoundsPosition = validIntent().apply {
            putExtra(CastReceiverManager.EXTRA_POSITION_MS, 5_001L)
        }
        assertNull(CastReceiverManager.parseIntent(outOfBoundsPosition))
    }

    @Test
    fun arbitraryLauncherExtrasDoNotTriggerMediaHandoff() {
        val launcher = validIntent().apply { action = Intent.ACTION_MAIN }
        assertNull(CastReceiverManager.parseIntent(launcher))
    }

    @Test
    fun invalidJsonDoesNotFallBackToValidDirectExtras() {
        val intent = validIntent().apply {
            putExtra(CastReceiverManager.EXTRA_PAYLOAD_JSON, "{invalid")
        }
        assertNull(CastReceiverManager.parseIntent(intent))
    }
}
