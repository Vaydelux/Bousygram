package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasForegroundPlaybackIntentTest {
    @Test fun activePlaybackRestoresOnlyOnceAfterActivityStops() {
        val intent = ExtrasForegroundPlaybackIntent()
        assertFalse(intent.onForeground())
        intent.onBackground(playRequested = true)
        assertTrue(intent.onForeground())
        assertFalse(intent.onForeground())
    }

    @Test fun userPauseRemainsPausedAfterActivityStops() {
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onBackground(playRequested = false)
        assertFalse(intent.onForeground())
    }

    @Test fun bufferingWithPlayRequestedIsNotMistakenForUserPause() {
        val intent = ExtrasForegroundPlaybackIntent()
        // A decoder can be buffering (isPlaying=false) while Play is armed.
        intent.onBackground(playRequested = true)
        assertTrue(intent.onForeground())
    }

    @Test fun cancellationPreventsResurrectionOfDisposedPlayer() {
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onBackground(playRequested = true)
        intent.cancel()
        assertFalse(intent.onForeground())
    }

    @Test fun newerStopReplacesOlderIntent() {
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onBackground(playRequested = true)
        intent.onBackground(playRequested = false)
        assertFalse(intent.onForeground())
    }
}
