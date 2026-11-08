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
    @Test fun transientAudioFocusLossResumesExactlyOnce() {
        val intent = ExtrasAudioFocusRecoveryIntent()
        assertFalse(intent.hasPendingResume)
        intent.onFocusLost(transient = true, wasPlayRequested = true)
        assertTrue(intent.hasPendingResume)
        assertTrue(intent.onFocusGained(inForeground = true))
        assertFalse(intent.onFocusGained(inForeground = true))
    }

    @Test fun explicitPausePermanentLossAndBackgroundDoNotAutoPlay() {
        val intent = ExtrasAudioFocusRecoveryIntent()
        intent.onFocusLost(transient = true, wasPlayRequested = true)
        intent.cancel() // user pressed Pause
        assertFalse(intent.onFocusGained(inForeground = true))
        intent.onFocusLost(transient = true, wasPlayRequested = true)
        intent.onFocusLost(transient = false, wasPlayRequested = true)
        assertFalse(intent.onFocusGained(inForeground = true))
        intent.onFocusLost(transient = true, wasPlayRequested = true)
        assertFalse(intent.onFocusGained(inForeground = false))
        assertFalse(intent.onFocusGained(inForeground = true))
    }

    @Test fun transientFocusPendingSurvivesRepeatedTransientCallbacks() {
        val intent = ExtrasAudioFocusRecoveryIntent()
        intent.onFocusLost(transient = true, wasPlayRequested = true)
        intent.onFocusLost(transient = true, wasPlayRequested = false)
        assertTrue(intent.hasPendingResume)
        assertTrue(intent.onFocusGained(inForeground = true))
        intent.onFocusLost(transient = true, wasPlayRequested = false)
        assertFalse(intent.onFocusGained(inForeground = true))
    }
}
