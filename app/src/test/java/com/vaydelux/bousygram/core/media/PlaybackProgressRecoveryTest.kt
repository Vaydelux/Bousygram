package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressRecoveryTest {
    @Test fun playingVideoWithSustainedProgressCanDismissStaleLoading() {
        assertTrue(hasConfirmedVideoProgress(12_000L, 14_400L, playing = true, hasVideoDimensions = true))
        assertTrue(hasInitialMediaOutput(0L, 0L, firstProgressReadyMs = 2_400L))
    }

    @Test fun readyOrBufferingOrSeekJumpCannotCountAsRenderedPlayback() {
        assertFalse(hasConfirmedVideoProgress(0L, 3_000L, playing = false, hasVideoDimensions = true))
        assertFalse(hasConfirmedVideoProgress(0L, 3_000L, playing = true, hasVideoDimensions = false))
        assertFalse(hasConfirmedVideoProgress(4_000L, 5_000L, playing = true, hasVideoDimensions = true))
        assertFalse(hasConfirmedVideoProgress(6_000L, 3_000L, playing = true, hasVideoDimensions = true))
        assertFalse(hasInitialMediaOutput(0L, 0L))
    }

    @Test fun videoContainerDurationOnlyTrustsVerifiedSeekMaps() {
        assertTrue(trustPlayerDuration(
            reportedMs = 24L * 60_000L,
            totalSize = 900L * 1024L * 1024L,
            tdlibHintSeconds = 0,
            seekableContainerDuration = true
        ))
        assertFalse(trustPlayerDuration(
            reportedMs = 14_000L,
            totalSize = 900L * 1024L * 1024L,
            tdlibHintSeconds = 0,
            seekableContainerDuration = false
        ))
    }
}
