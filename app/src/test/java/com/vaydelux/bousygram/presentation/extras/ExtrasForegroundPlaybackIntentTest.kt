package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasForegroundPlaybackIntentTest {
    @Test fun unsupportedVlcChoiceAlwaysResolvesToMedia3() {
        assertEquals(ExtrasNativeEngine.MEDIA3, extrasAvailableNativeEngine(
            ExtrasNativeEngine.LIBVLC,
            guardedVlcEnabled = true,
            progressiveSupported = false // HLS, DASH or TS.
        ))
        assertEquals(ExtrasNativeEngine.MEDIA3, extrasAvailableNativeEngine(
            ExtrasNativeEngine.LIBVLC,
            guardedVlcEnabled = false, // All release and hardened builds.
            progressiveSupported = true
        ))
        assertEquals(ExtrasNativeEngine.LIBVLC, extrasAvailableNativeEngine(
            ExtrasNativeEngine.LIBVLC,
            guardedVlcEnabled = true,
            progressiveSupported = true
        ))
        assertEquals(ExtrasNativeEngine.MEDIA3, extrasAvailableNativeEngine(
            ExtrasNativeEngine.MEDIA3,
            guardedVlcEnabled = false,
            progressiveSupported = false
        ))
    }

    @Test fun engineHandoffKeepsExplicitPauseInsteadOfAutoplaying() {
        val pausedMedia3 = extrasMedia3Handoff(
            positionMs = 42_000L, seekable = true, playWhenReady = false,
            failed = false, ended = false
        )
        assertFalse(pausedMedia3.playRequested)
        assertEquals(42_000L, pausedMedia3.positionMs)

        val pausedVlc = extrasLibVlcHandoff(ExtrasLibVlcControlState(
            positionMs = 35_000L, seekable = true, playRequested = false
        ))
        assertFalse(pausedVlc.playRequested)
        assertEquals(35_000L, pausedVlc.positionMs)
    }

    @Test fun bufferingPlayIntentSurvivesEngineHandoff() {
        // isPlaying is intentionally absent: buffering must not be mistaken for Pause.
        val media3 = extrasMedia3Handoff(
            positionMs = 20_000L, seekable = true, playWhenReady = true,
            failed = false, ended = false
        )
        assertTrue(media3.playRequested)
        val vlc = extrasLibVlcHandoff(ExtrasLibVlcControlState(
            positionMs = 21_000L, seekable = true,
            playing = false, playRequested = true
        ))
        assertTrue(vlc.playRequested)
        assertEquals(21_000L, vlc.positionMs)
    }

    @Test fun failedEndedAndNonseekableHandoffsFailSafe() {
        val failed = extrasMedia3Handoff(
            positionMs = 8_000L, seekable = true, playWhenReady = true,
            failed = true, ended = false
        )
        val ended = extrasMedia3Handoff(
            positionMs = 9_000L, seekable = true, playWhenReady = true,
            failed = false, ended = true
        )
        val nonseekable = extrasLibVlcHandoff(ExtrasLibVlcControlState(
            positionMs = 123_000L, seekable = false, playRequested = true
        ))
        assertFalse(failed.playRequested)
        assertFalse(ended.playRequested)
        assertEquals(0L, nonseekable.positionMs)
        assertTrue(nonseekable.playRequested)
        assertFalse(extrasLibVlcHandoff(ExtrasLibVlcControlState(
            playRequested = true, failed = true
        )).playRequested)
    }

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
    @Test fun media3BackgroundPreservesBufferingPlayButNotUserPause() {
        assertTrue(shouldPreserveExtrasMedia3Resume(
            playWhenReady = true, hasPlaybackError = false, reachedEnd = false
        ))
        assertFalse(shouldPreserveExtrasMedia3Resume(
            playWhenReady = false, hasPlaybackError = false, reachedEnd = false
        ))
    }

    @Test fun media3ErrorAndNaturalEndCannotRestartOnForeground() {
        assertFalse(shouldPreserveExtrasMedia3Resume(
            playWhenReady = true, hasPlaybackError = true, reachedEnd = false
        ))
        assertFalse(shouldPreserveExtrasMedia3Resume(
            playWhenReady = true, hasPlaybackError = false, reachedEnd = true
        ))
        assertFalse(shouldPreserveExtrasMedia3Resume(
            playWhenReady = true, hasPlaybackError = true, reachedEnd = true
        ))
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onBackground(shouldPreserveExtrasMedia3Resume(true, true, false))
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
