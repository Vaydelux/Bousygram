package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPlaybackWindowPolicyTest {
    private val m = ForegroundPlaybackWindowPolicy.MIB

    @Test fun requestsDurationAwareRangesWithoutDownloadingTheWholeMovie() {
        // 600 MiB over 60 min => 20 MiB for a two-minute requested runway.
        val bytes = ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = 600L * m, offsetBytes = 200L * m,
            durationMs = 3_600_000L, freeBytes = 1_000L * m
        )
        assertEquals(20L * m, bytes.toLong())
        assertTrue(bytes < 600L * m)
    }

    @Test fun highBitrateAndLowStorageRemainBounded() {
        assertEquals(32L * m, ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = 4_096L * m, offsetBytes = 0L,
            durationMs = 5_400_000L, freeBytes = 1_024L * m).toLong())
        assertEquals(8L * m, ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = 4_096L * m, offsetBytes = 0L,
            durationMs = 5_400_000L, freeBytes = 120L * m).toLong())
        assertEquals(8L * m, ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = 4_096L * m, offsetBytes = 0L,
            durationMs = 5_400_000L, freeBytes = null).toLong())
    }

    @Test fun unknownDurationAndNearEndCannotBecomeOpenEnded() {
        assertEquals(16L * m, ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = 200L * m, offsetBytes = 0L,
            durationMs = 0L, freeBytes = 2_000L * m).toLong())
        assertEquals(32_768, ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = 200L * m, offsetBytes = 200L * m - 32_768L,
            durationMs = 5_000L, freeBytes = 2_000L * m))
        assertEquals(0, ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = 200L * m, offsetBytes = 200L * m,
            durationMs = 0L, freeBytes = 2_000L * m))
    }

    @Test fun noOverflowOnHugeFileOrDuration() {
        val value = ForegroundPlaybackWindowPolicy.requestBytes(
            totalSizeBytes = Long.MAX_VALUE - 1L,
            offsetBytes = Long.MAX_VALUE - 100L,
            durationMs = Long.MAX_VALUE,
            freeBytes = Long.MAX_VALUE
        )
        assertEquals(99, value)
    }

    @Test fun windowRefreshUsesRealByteRequestsNotTimeToOffsetEstimation() {
        val start = 24L * m
        val end = start + 32L * m
        assertFalse(ForegroundPlaybackWindowPolicy.needsNewWindow(start + 10L * m, 1024, start, end))
        assertTrue(ForegroundPlaybackWindowPolicy.needsNewWindow(end - 3L * m, 4096, start, end))
        assertTrue(ForegroundPlaybackWindowPolicy.needsNewWindow(end + 1L, 4096, start, end))
        assertTrue(ForegroundPlaybackWindowPolicy.needsNewWindow(0L, 4096, start, end))
        assertTrue(ForegroundPlaybackWindowPolicy.needsNewWindow(0L, 4096, 0L, 0L))
        assertFalse(ForegroundPlaybackWindowPolicy.needsNewWindow(0L, 0, 0L, 0L))
    }
}
