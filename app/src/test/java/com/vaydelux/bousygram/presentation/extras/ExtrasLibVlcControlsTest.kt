package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasLibVlcControlsTest {
    @Test fun seekClampsAtStartAndEnd() {
        assertEquals(0L, extrasVlcSeekTarget(500L, -10_000L, 60_000L))
        assertEquals(60_000L, extrasVlcSeekTarget(59_000L, 10_000L, 60_000L))
        assertEquals(20_000L, extrasVlcSeekTarget(10_000L, 10_000L, -1L))
        assertEquals(60_000L, extrasVlcSeekAbsolute(90_000L, 60_000L))
        assertEquals(0L, extrasVlcSeekAbsolute(-5L, 60_000L))
    }
    @Test fun malformedExtremeSeekValuesNeverOverflow() {
        assertEquals(Long.MAX_VALUE, extrasVlcSeekTarget(Long.MAX_VALUE, 10_000L, 0L))
        assertEquals(0L, extrasVlcSeekTarget(Long.MAX_VALUE, Long.MIN_VALUE, 0L))
        assertEquals(0L, extrasVlcSeekTarget(-100L, -10_000L, 0L))
        assertEquals(0L, extrasVlcSeekTarget(0L, Long.MIN_VALUE, -1L))
    }
    @Test fun progressHandlesUnknownDurationAndInvalidNativePositions() {
        assertEquals(0f, extrasVlcProgress(500L, 0L), 0f)
        assertEquals(0f, extrasVlcProgress(-10L, 100L), 0f)
        assertEquals(0.5f, extrasVlcProgress(50L, 100L), 0f)
        assertEquals(1f, extrasVlcProgress(200L, 100L), 0f)
        assertTrue(extrasVlcProgress(Long.MAX_VALUE, Long.MAX_VALUE).isFinite())
    }
    @Test fun subtitleChangeResumesOnlyAfterNativePlaybackIsSeekable() {
        val seek = ExtrasVlcInitialSeek(45_000L)
        assertEquals(null, seek.nextTarget(playing = false, seekable = true, durationMs = 90_000L))
        assertEquals(null, seek.nextTarget(playing = true, seekable = false, durationMs = 90_000L))
        assertTrue(seek.isPending)
        assertEquals(45_000L, seek.nextTarget(playing = true, seekable = true, durationMs = 90_000L))
        assertTrue(seek.isPending)
        seek.onApplied(success = true)
        assertTrue(!seek.isPending)
        assertEquals(null, seek.nextTarget(playing = true, seekable = true, durationMs = 90_000L))
    }
    @Test fun resumePositionIsClampedAndRetryCountIsBounded() {
        val seek = ExtrasVlcInitialSeek(Long.MAX_VALUE, maxPlayingChecks = 2)
        assertEquals(500L, seek.nextTarget(playing = true, seekable = true, durationMs = 500L))
        seek.onApplied(success = true)
        val nonseekable = ExtrasVlcInitialSeek(2_000L, maxPlayingChecks = 2)
        assertEquals(null, nonseekable.nextTarget(true, false, 10_000L))
        assertTrue(nonseekable.isPending)
        assertEquals(null, nonseekable.nextTarget(true, false, 10_000L))
        assertTrue(!nonseekable.isPending)
    }

    @Test fun latencyProbeSeparatesPlayingCallbackFromPositionAcknowledgement() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onPlayRequested(1_000L)
        probe.onNativePlaying(1_750L)
        probe.onNativePlaying(2_000L) // Only the first native Playing event matters.
        assertEquals(750L, probe.snapshot().startupToNativePlayingMs)
        probe.onSeekRequested(45_000L, 5_000L)
        probe.observeNativePosition(12_000L, 5_500L)
        assertEquals(0, probe.snapshot().seekSamples)
        probe.observeNativePosition(44_600L, 5_800L)
        assertEquals(1, probe.snapshot().seekSamples)
        assertEquals(800L, probe.snapshot().lastSeekPositionMs)
    }

    @Test fun latencyProbeExpiresStalledSeeksAndKeepsLastValidResult() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onSeekRequested(20_000L, 1_000L)
        probe.observeNativePosition(19_500L, 1_100L)
        assertEquals(100L, probe.snapshot().lastSeekPositionMs)
        probe.onSeekRequested(80_000L, 2_000L)
        probe.observeNativePosition(1_000L, 17_001L)
        assertEquals(1, probe.snapshot().seekSamples)
        assertEquals(1, probe.snapshot().seekTimeouts)
        assertEquals(100L, probe.snapshot().lastSeekPositionMs)
        probe.observeNativePosition(80_000L, 20_000L)
        assertEquals(1, probe.snapshot().seekSamples)
    }

    @Test fun latencyProbeHandlesUnknownOrOutOfOrderNativeTimestamps() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onNativePlaying(1_000L)
        assertEquals(null, probe.snapshot().startupToNativePlayingMs)
        probe.onPlayRequested(2_000L)
        probe.onNativePlaying(1_000L)
        assertEquals(null, probe.snapshot().startupToNativePlayingMs)
        probe.onNativePlaying(2_100L)
        assertEquals(100L, probe.snapshot().startupToNativePlayingMs)
        probe.onSeekRequested(15_000L, 5_000L)
        probe.observeNativePosition(15_000L, 4_000L)
        assertEquals(0, probe.snapshot().seekSamples)
    }

    @Test fun rejectedAndSupersededSeeksCannotInflateSuccessCount() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onSeekRejected() // native method rejected or stream unseekable
        probe.onSeekRequested(30_000L, 1_000L, previousPositionMs = 2_000L)
        probe.onSeekRequested(60_000L, 1_100L, previousPositionMs = 2_000L)
        probe.observeNativePosition(30_000L, 1_500L)
        assertEquals(0, probe.snapshot().seekSamples)
        assertEquals(1, probe.snapshot().seekRejectedCount)
        assertEquals(1, probe.snapshot().seekSupersededCount)
        probe.observeNativePosition(59_700L, 1_900L)
        assertEquals(1, probe.snapshot().seekSamples)
        assertEquals(800L, probe.snapshot().lastSeekPositionMs)
    }

    @Test fun stalePositionInsideToleranceDoesNotCountAsSeekAck() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onSeekRequested(41_000L, 1_000L, previousPositionMs = 40_000L)
        // 40s is within the normal 1.5s tolerance but has not moved.
        probe.observeNativePosition(40_000L, 1_100L)
        assertEquals(0, probe.snapshot().seekSamples)
        probe.observeNativePosition(40_600L, 1_200L)
        assertEquals(1, probe.snapshot().seekSamples)
        assertEquals(200L, probe.snapshot().lastSeekPositionMs)
    }

    @Test fun seekAckAfterDeadlineIsTimeoutNotSuccess() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onSeekRequested(50_000L, 1_000L, previousPositionMs = 5_000L)
        probe.observeNativePosition(49_900L, 16_001L)
        assertEquals(0, probe.snapshot().seekSamples)
        assertEquals(1, probe.snapshot().seekTimeouts)
        assertEquals(null, probe.snapshot().lastSeekPositionMs)
    }

    @Test fun requestingCurrentPositionIsNotMiscountedAsSeek() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onSeekRequested(12_000L, 1_000L, previousPositionMs = 12_000L)
        probe.observeNativePosition(12_000L, 1_010L)
        assertEquals(1, probe.snapshot().seekNoChangeCount)
        assertEquals(0, probe.snapshot().seekSamples)
        assertEquals(0, probe.snapshot().seekTimeouts)
    }

    @Test fun seekAverageAndLongestUseOnlyAcknowledgedSamples() {
        val probe = ExtrasVlcLatencyProbe()
        assertEquals(null, probe.snapshot().averageSeekPositionMs)
        probe.onSeekRequested(10_000L, 1_000L, previousPositionMs = 0L)
        probe.observeNativePosition(10_000L, 1_100L)
        probe.onSeekRequested(20_000L, 1_200L, previousPositionMs = 10_000L)
        probe.observeNativePosition(20_000L, 1_500L)
        probe.onSeekRejected()
        probe.onSeekRequested(30_000L, 2_000L, previousPositionMs = 20_000L)
        probe.observeNativePosition(0L, 17_001L) // This one times out.
        val stats = probe.snapshot()
        assertEquals(2, stats.seekSamples)
        assertEquals(200L, stats.averageSeekPositionMs)
        assertEquals(300L, stats.longestSeekPositionMs)
        assertEquals(1, stats.seekTimeouts)
        assertEquals(1, stats.seekRejectedCount)
    }

    @Test fun pausedSeekPollingIsBoundedByAcknowledgementOrTimeout() {
        val probe = ExtrasVlcLatencyProbe()
        assertTrue(!probe.hasPendingSeek)
        probe.onSeekRequested(90_000L, 1_000L, previousPositionMs = 2_000L)
        assertTrue(probe.hasPendingSeek)
        probe.observeNativePosition(40_000L, 1_500L)
        assertTrue(probe.hasPendingSeek)
        probe.observeNativePosition(89_900L, 2_000L)
        assertTrue(!probe.hasPendingSeek)
        assertEquals(1, probe.snapshot().seekSamples)

        probe.onSeekRequested(120_000L, 3_000L, previousPositionMs = 90_000L)
        assertTrue(probe.hasPendingSeek)
        probe.observeNativePosition(90_000L, 18_001L)
        assertTrue(!probe.hasPendingSeek)
        assertEquals(1, probe.snapshot().seekTimeouts)
    }

    @Test fun currentPositionSeekDoesNotArmPausedPolling() {
        val probe = ExtrasVlcLatencyProbe()
        probe.onSeekRequested(12_000L, 500L, previousPositionMs = 12_000L)
        assertTrue(!probe.hasPendingSeek)
        assertEquals(1, probe.snapshot().seekNoChangeCount)
    }

    @Test fun bufferingAndPausedChromeNeverAutoHide() {
        val playing = ExtrasLibVlcControlState(playRequested = true, playing = true)
        val paused = playing.copy(playRequested = false, playing = false)
        assertTrue(extrasVlcChromeMustRemainVisible(playing, "Buffering…", false))
        assertTrue(extrasVlcChromeMustRemainVisible(paused, "Paused", false))
        assertTrue(extrasVlcChromeMustRemainVisible(
            playing.copy(failed = true), "Playback failed", false
        ))
        assertTrue(extrasVlcChromeMustRemainVisible(
            playing.copy(ended = true), "Playback finished", false
        ))
        assertTrue(extrasVlcChromeMustRemainVisible(playing, "Playing", true))
    }

    @Test fun healthyPlayingChromeHidesOnlyWithNoFocusedControls() {
        val playing = ExtrasLibVlcControlState(playRequested = true, playing = true)
        assertTrue(extrasVlcChromeMayAutoHide(
            playing, "Playing", false, false, false
        ))
        assertTrue(!extrasVlcChromeMayAutoHide(
            playing, "Playing", false, true, false
        ))
        assertTrue(!extrasVlcChromeMayAutoHide(
            playing, "Playing", false, false, true
        ))
        assertTrue(!extrasVlcChromeMayAutoHide(
            playing, "Buffering", false, false, false
        ))
        assertTrue(!extrasVlcChromeMayAutoHide(
            playing, "Playing", true, false, false
        ))
    }

    @Test fun subtitleResumeRetransmitsOneFailedNativeSeek() {
        val seek = ExtrasVlcInitialSeek(38_000L)
        assertEquals(38_000L, seek.nextTarget(true, true, 70_000L))
        seek.onApplied(success = false)
        assertTrue(seek.isPending)
        assertEquals(38_000L, seek.nextTarget(true, true, 70_000L))
        seek.onApplied(success = true)
        assertTrue(!seek.isPending)
    }

    @Test fun subtitleResumeStopsAfterTwoFailedNativeSeeks() {
        val seek = ExtrasVlcInitialSeek(38_000L)
        repeat(2) {
            assertEquals(38_000L, seek.nextTarget(true, true, 70_000L))
            seek.onApplied(success = false)
        }
        assertTrue(!seek.isPending)
        assertEquals(null, seek.nextTarget(true, true, 70_000L))
    }

    @Test fun engineHandoffClampsPositionAndRejectsUnseekableStreams() {
        assertEquals(0L, extrasVlcHandoffPosition(53_000L, false))
        assertEquals(0L, extrasVlcHandoffPosition(-100L, true))
        assertEquals(53_000L, extrasVlcHandoffPosition(53_000L, true))
    }

    @Test fun timeLabelsStayReadableForTv() {
        assertEquals("0:00", extrasVlcTimeLabel(-1L))
        assertEquals("0:09", extrasVlcTimeLabel(9_000L))
        assertEquals("9:59", extrasVlcTimeLabel(599_000L))
        assertEquals("1:00:00", extrasVlcTimeLabel(3_600_000L))
        assertEquals("12:34:56", extrasVlcTimeLabel(45_296_000L))
    }
}
