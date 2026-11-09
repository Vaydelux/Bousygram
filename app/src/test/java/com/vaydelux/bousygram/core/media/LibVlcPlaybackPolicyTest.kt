package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibVlcPlaybackPolicyTest {
    @Test fun ongoingPlaybackAndManualPauseStayReadyDuringBufferNotifications() {
        assertEquals(EnginePlaybackState.Ready, libVlcBufferState(true, true))
        assertEquals(EnginePlaybackState.Ready, libVlcBufferState(false, false))
        assertEquals(EnginePlaybackState.Ready, libVlcBufferState(false, true))
        assertEquals(EnginePlaybackState.Buffering, libVlcBufferState(true, false))
    }

    @Test fun stopAndReleaseCallbacksCannotResurrectThePlayer() {
        assertTrue(acceptsLibVlcMediaEvent(false, true, false))
        assertFalse(acceptsLibVlcMediaEvent(true, true, false))
        assertFalse(acceptsLibVlcMediaEvent(false, false, false))
        assertFalse(acceptsLibVlcMediaEvent(false, true, true))
    }

    @Test fun lateTerminalEventsDuringNewPrepareAreNotRealEofOrErrors() {
        assertFalse(shouldReportLibVlcEnded(true, false, true))
        assertFalse(shouldReportLibVlcUnexpectedStop(true, false, false, true))
        assertTrue(shouldReportLibVlcEnded(true, true, true))
        assertTrue(shouldReportLibVlcUnexpectedStop(true, true, false, true))
        assertFalse(shouldReportLibVlcUnexpectedStop(true, true, true, true))
        assertFalse(shouldReportLibVlcEnded(false, true, true))
        // A late EOF/Stopped emitted after a real user Pause must not
        // auto-skip episodes or force a decoder fallback.
        assertFalse(shouldReportLibVlcEnded(true, true, false))
        assertFalse(shouldReportLibVlcUnexpectedStop(true, true, false, false))
    }

    @Test fun unexpectedNativePauseIsBoundedAndCannotOverrideExplicitPause() {
        assertEquals(LibVlcUnexpectedPauseAction.IGNORE,
            libVlcUnexpectedPauseAction(false, true, false, 0))
        assertEquals(LibVlcUnexpectedPauseAction.IGNORE,
            libVlcUnexpectedPauseAction(true, false, false, 0))
        assertEquals(LibVlcUnexpectedPauseAction.IGNORE,
            libVlcUnexpectedPauseAction(true, true, true, 0))
        assertEquals(LibVlcUnexpectedPauseAction.RETRY,
            libVlcUnexpectedPauseAction(true, true, false, 0))
        assertEquals(LibVlcUnexpectedPauseAction.RETRY,
            libVlcUnexpectedPauseAction(true, true, false, 1))
        assertEquals(LibVlcUnexpectedPauseAction.FAIL,
            libVlcUnexpectedPauseAction(true, true, false, 2))
        assertEquals(LibVlcUnexpectedPauseAction.FAIL,
            libVlcUnexpectedPauseAction(true, true, false, 20))
    }

    @Test fun stablePlaybackCanRenewRecoveryBudgetWithoutRapidPauseLoop() {
        val requested = { elapsed: Long, progress: Long, requestedPlay: Boolean,
                          active: Boolean, actuallyPlaying: Boolean, attempts: Int ->
            shouldRestoreLibVlcPauseRetryBudget(
                playRequested = requestedPlay, prepared = active,
                nativePlaying = actuallyPlaying, retriesSoFar = attempts,
                stableElapsedMs = elapsed, timelineProgressMs = progress
            )
        }
        assertFalse(requested(0L, 0L, true, true, true, 1))
        assertFalse(requested(14_999L, 10_000L, true, true, true, 1))
        assertFalse(requested(15_000L, 4_999L, true, true, true, 1))
        assertTrue(requested(15_000L, 5_000L, true, true, true, 1))
        assertTrue(requested(45_000L, 25_000L, true, true, true, 2))
        assertFalse(requested(45_000L, 25_000L, false, true, true, 1))
        assertFalse(requested(45_000L, 25_000L, true, false, true, 1))
        assertFalse(requested(45_000L, 25_000L, true, true, false, 1))
        assertFalse(requested(45_000L, 25_000L, true, true, true, 0))
    }

    @Test fun seeksAreClampedToContainerBoundsWithoutOverflow() {
        assertEquals(0L, boundedLibVlcSeekMs(-250L, 9000L))
        assertEquals(9000L, boundedLibVlcSeekMs(Long.MAX_VALUE, 9000L))
        assertEquals(4500L, boundedLibVlcSeekMs(4500L, 9000L))
        assertEquals(15000L, boundedLibVlcSeekMs(15000L, 0L))
    }
}
