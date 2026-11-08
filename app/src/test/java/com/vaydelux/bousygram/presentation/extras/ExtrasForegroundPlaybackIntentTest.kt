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

    @Test fun pausedMedia3ToVlcToMedia3RoundTripPreservesUnappliedSeek() {
        val enteredVlc = extrasMedia3Handoff(
            positionMs = 42_000L, seekable = true, playWhenReady = false,
            failed = false, ended = false
        )
        val leftVlc = extrasLibVlcHandoff(ExtrasLibVlcControlState(
            positionMs = 0L,
            pendingHandoffPositionMs = enteredVlc.positionMs,
            seekable = false, // Native decoder has not prepared yet.
            playing = false,
            playRequested = enteredVlc.playRequested
        ))
        assertEquals(42_000L, leftVlc.positionMs)
        assertFalse(leftVlc.playRequested)
    }

    @Test fun pendingResumeSurvivesEarlyVlcFailureForManualMedia3Fallback() {
        val fallback = extrasLibVlcHandoff(ExtrasLibVlcControlState(
            positionMs = 0L,
            pendingHandoffPositionMs = 80_000L,
            seekable = false, failed = true, playRequested = false
        ))
        assertEquals(80_000L, fallback.positionMs)
        assertFalse(fallback.playRequested)
    }

    @Test fun completedManualSeekTakesPriorityOverEarlierHandoff() {
        val seek = ExtrasVlcInitialSeek(50_000L)
        seek.onExplicitSeekAccepted()
        val outbound = extrasLibVlcHandoff(ExtrasLibVlcControlState(
            positionMs = 86_000L,
            pendingHandoffPositionMs = seek.pendingPositionMs,
            seekable = true,
            playRequested = true
        ))
        assertEquals(null, seek.pendingPositionMs)
        assertEquals(86_000L, outbound.positionMs)
        assertTrue(outbound.playRequested)
    }

    @Test fun naturalEndDoesNotResurrectPendingSeekOrPlayback() {
        val result = extrasLibVlcHandoff(ExtrasLibVlcControlState(
            positionMs = 100_000L,
            pendingHandoffPositionMs = 42_000L,
            seekable = true, ended = true, playRequested = false
        ))
        assertEquals(100_000L, result.positionMs)
        assertFalse(result.playRequested)
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

    @Test fun firstForegroundResumesInitiallyRequestedPlaybackExactlyOnce() {
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onInitialAttach(playRequested = true, inForeground = false)
        assertTrue(intent.onForeground())
        assertFalse(intent.onForeground()) // A second START must not replay it.
    }

    @Test fun initiallyPausedPlaybackStaysPausedWhenActivityStarts() {
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onInitialAttach(playRequested = false, inForeground = false)
        assertFalse(intent.onForeground())
    }

    @Test fun alreadyForegroundPlayerCannotQueueAnExtraAutoplay() {
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onInitialAttach(playRequested = true, inForeground = true)
        assertFalse(intent.onForeground())
    }

    @Test fun lifecycleStopOrDisposeOverridesInitialAutoResume() {
        val intent = ExtrasForegroundPlaybackIntent()
        intent.onInitialAttach(playRequested = true, inForeground = false)
        intent.onBackground(playRequested = false) // Explicitly paused while stopped.
        assertFalse(intent.onForeground())
        intent.onInitialAttach(playRequested = true, inForeground = false)
        intent.cancel() // Player was disposed before START.
        assertFalse(intent.onForeground())
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
