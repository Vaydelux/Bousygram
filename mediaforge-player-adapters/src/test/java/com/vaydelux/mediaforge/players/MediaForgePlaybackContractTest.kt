package com.vaydelux.mediaforge.players

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaForgePlaybackContractTest {
    @Test fun pauseSurvivesMedia3LibVlcAndBack() {
        val initial = MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(42_000L, seekable = true, playRequested = false)
        )
        assertEquals(42_000L, initial.positionMs)
        assertFalse(initial.playRequested)
        val returned = MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(
                positionMs = 0L, seekable = false,
                pendingHandoffPositionMs = initial.positionMs,
                playRequested = initial.playRequested
            )
        )
        assertEquals(initial, returned)
    }

    @Test fun bufferingKeepsPlayIntentWithoutUsingIsPlaying() {
        val result = MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(20_000L, seekable = true, playRequested = true)
        )
        assertTrue(result.playRequested)
        assertEquals(20_000L, result.positionMs)
    }

    @Test fun failedAndEndedDecodersCannotAutoplayOnHandoff() {
        for (failed in listOf(false, true)) for (ended in listOf(false, true)) {
            val result = MediaForgePlaybackTransition.handoff(
                MediaForgePlaybackSnapshot(
                    90_000L, seekable = true, playRequested = true,
                    failed = failed, ended = ended
                )
            )
            assertEquals(!failed && !ended, result.playRequested)
        }
        val ended = MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(
                15_000L, false, true, pendingHandoffPositionMs = 80_000L,
                ended = true
            )
        )
        assertEquals(0L, ended.positionMs)
        assertFalse(ended.playRequested)
    }

    @Test fun unseekableHandoffDoesNotInventPositionUnlessPending() {
        assertEquals(0L, MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(100_000L, false, true)
        ).positionMs)
        assertEquals(17_000L, MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(0L, false, false, 17_000L)
        ).positionMs)
        assertEquals(0L, MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(-200L, true, false, -100L)
        ).positionMs)
    }

    @Test fun pendingSeekWinsOverUninitializedNativeClock() {
        val outgoing = MediaForgePlaybackTransition.handoff(
            MediaForgePlaybackSnapshot(
                positionMs = 0L, seekable = false, playRequested = true,
                pendingHandoffPositionMs = 45_000L
            )
        )
        assertEquals(45_000L, outgoing.positionMs)
        assertTrue(outgoing.playRequested)
    }

    @Test fun manualPauseAndFailureSuppressForegroundAutoPlay() {
        val gate = MediaForgeForegroundResumeGate()
        gate.onInitialAttach(playRequested = false, inForeground = false)
        assertFalse(gate.onForeground())
        gate.onBackground(MediaForgePlaybackTransition.mayResume(true, false, false))
        assertTrue(gate.onForeground())
        assertFalse(gate.onForeground())
        gate.onBackground(MediaForgePlaybackTransition.mayResume(true, true, false))
        assertFalse(gate.onForeground())
        gate.onBackground(true)
        gate.cancel()
        assertFalse(gate.onForeground())
    }

    @Test fun releaseLeaseAllowsOnlyOneNativeRelease() {
        val lease = MediaForgeReleaseLease()
        assertTrue(lease.isOpen())
        assertTrue(lease.tryRelease())
        assertFalse(lease.isOpen())
        assertFalse(lease.tryRelease())
    }
}
