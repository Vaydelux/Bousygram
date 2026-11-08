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
        assertTrue(!seek.isPending)
        assertEquals(null, seek.nextTarget(playing = true, seekable = true, durationMs = 90_000L))
    }
    @Test fun resumePositionIsClampedAndRetryCountIsBounded() {
        val seek = ExtrasVlcInitialSeek(Long.MAX_VALUE, maxPlayingChecks = 2)
        assertEquals(500L, seek.nextTarget(playing = true, seekable = true, durationMs = 500L))
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

    @Test fun timeLabelsStayReadableForTv() {
        assertEquals("0:00", extrasVlcTimeLabel(-1L))
        assertEquals("0:09", extrasVlcTimeLabel(9_000L))
        assertEquals("9:59", extrasVlcTimeLabel(599_000L))
        assertEquals("1:00:00", extrasVlcTimeLabel(3_600_000L))
        assertEquals("12:34:56", extrasVlcTimeLabel(45_296_000L))
    }
}
