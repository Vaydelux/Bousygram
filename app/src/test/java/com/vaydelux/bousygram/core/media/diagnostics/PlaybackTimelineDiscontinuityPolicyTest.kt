package com.vaydelux.bousygram.core.media.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTimelineDiscontinuityPolicyTest {
    @Test fun plausibleRealtimeAudioProgressIsNotSeek() {
        assertFalse(playbackTimelineDiscontinuity(500L, 4_500L, 4_000L))
        assertFalse(playbackTimelineDiscontinuity(500L, 2_000L, 500L))
        assertFalse(playbackTimelineDiscontinuity(8_000L, 7_700L, 100L))
    }

    @Test fun seekBackwardsOrForwardsRearmsWatchdog() {
        assertTrue(playbackTimelineDiscontinuity(2_000L, 90_000L, 100L))
        assertTrue(playbackTimelineDiscontinuity(180_000L, 5_000L, 100L))
        assertFalse(playbackTimelineDiscontinuity(2_000L, 7_000L, 0L))
        assertTrue(playbackTimelineDiscontinuity(2_000L, 7_001L, 0L))
    }

    @Test fun clockRegressionAndExtremeDurationDoNotOverflow() {
        assertTrue(playbackTimelineDiscontinuity(2_000L, 2_100L, -1L))
        assertFalse(playbackTimelineDiscontinuity(0L, Long.MAX_VALUE, Long.MAX_VALUE))
        assertTrue(playbackTimelineDiscontinuity(-1L, 2_000L, 5L))
    }
}
