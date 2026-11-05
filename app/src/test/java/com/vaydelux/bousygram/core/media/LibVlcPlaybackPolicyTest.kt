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
        assertFalse(shouldReportLibVlcEnded(activeMedia = true, playbackStarted = false))
        assertFalse(shouldReportLibVlcUnexpectedStop(true, false, false))
        assertTrue(shouldReportLibVlcEnded(activeMedia = true, playbackStarted = true))
        assertTrue(shouldReportLibVlcUnexpectedStop(true, true, false))
        assertFalse(shouldReportLibVlcUnexpectedStop(true, true, true))
        assertFalse(shouldReportLibVlcEnded(activeMedia = false, playbackStarted = true))
    }

    @Test fun seeksAreClampedToContainerBoundsWithoutOverflow() {
        assertEquals(0L, boundedLibVlcSeekMs(-250L, 9000L))
        assertEquals(9000L, boundedLibVlcSeekMs(Long.MAX_VALUE, 9000L))
        assertEquals(4500L, boundedLibVlcSeekMs(4500L, 9000L))
        assertEquals(15000L, boundedLibVlcSeekMs(15000L, 0L))
    }
}
