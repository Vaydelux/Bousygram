package com.vaydelux.bousygram.core.tdlib.server

import com.vaydelux.bousygram.core.cache.TvStorageBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundTelegramWindowPolicyTest {
    private val mib = TvStorageBudget.MIB

    @Test fun eachRequestIsBoundedAndScalesDownUnderStoragePressure() {
        val size = 500L * mib
        assertEquals(16L * mib, ForegroundTelegramWindowPolicy.plan(0L, size, 800L * mib)?.limit?.toLong())
        assertEquals(8L * mib, ForegroundTelegramWindowPolicy.plan(0L, size, 400L * mib)?.limit?.toLong())
        assertEquals(4L * mib, ForegroundTelegramWindowPolicy.plan(0L, size, 200L * mib)?.limit?.toLong())
        assertEquals(4L * mib, ForegroundTelegramWindowPolicy.plan(0L, size, null)?.limit?.toLong())
    }

    @Test fun tailRangeIsClampedToKnownEofWithoutLosingRandomAccess() {
        val size = 300L * mib
        val tail = ForegroundTelegramWindowPolicy.plan(size - 4096L, size, 800L * mib)
        assertEquals(size - 4096L, tail?.offset)
        assertEquals(4096, tail?.limit)
        assertEquals(size, tail?.endExclusive)
        assertNull(ForegroundTelegramWindowPolicy.plan(size, size, 800L * mib))
        assertNull(ForegroundTelegramWindowPolicy.plan(-1L, size, 800L * mib))
    }

    @Test fun shortUnknownSizeStillUsesFiniteWindows() {
        assertEquals(
            (16L * mib).toInt(),
            ForegroundTelegramWindowPolicy.plan(0L, 0L, 900L * mib)?.limit
        )
        assertEquals(
            (4L * mib).toInt(),
            ForegroundTelegramWindowPolicy.plan(1_000L, 0L, null)?.limit
        )
    }

    @Test fun successiveDataSourceReopensDoNotRepositionTheSameWindow() {
        assertFalse(ForegroundTelegramWindowPolicy.needsWindow(
            64L * 1024L, 4096, 0L, 16L * mib, 300L * mib
        ))
        assertFalse(ForegroundTelegramWindowPolicy.needsWindow(
            8L * mib, 4096, 0L, 16L * mib, 300L * mib
        ))
        assertTrue(ForegroundTelegramWindowPolicy.needsWindow(
            15L * mib, 4096, 0L, 16L * mib, 300L * mib
        ))
    }

    @Test fun seeksRepositionOnlyAsNeededAndEofDoesNotThrash() {
        assertTrue(ForegroundTelegramWindowPolicy.needsWindow(
            1_000L, 4096, 2L * mib, 18L * mib, 300L * mib
        ))
        assertTrue(ForegroundTelegramWindowPolicy.needsWindow(
            270L * mib, 4096, 0L, 16L * mib, 300L * mib
        ))
        assertFalse(ForegroundTelegramWindowPolicy.needsWindow(
            300L * mib - 4096L, 4096, 284L * mib, 300L * mib, 300L * mib
        ))
        assertFalse(ForegroundTelegramWindowPolicy.needsWindow(
            300L * mib, 4096, 284L * mib, 300L * mib, 300L * mib
        ))
    }

    @Test fun uncachedInteriorCueSeekRepositionsButNearbyReadDoesNotThrash() {
        val start = 15L * mib + 512L * 1024L
        val eof = 20L * mib
        // Planned window reaches EOF, but native TDLib reports only 128 KiB
        // contiguous from its start. Cues near EOF must not wait for 4 MiB.
        assertTrue(ForegroundTelegramWindowPolicy.needsWindow(
            requestedOffset = eof - 4096L,
            requestedBytes = 4096,
            currentOffset = start,
            currentEndExclusive = eof,
            knownSize = eof,
            downloadedPrefixBytes = 128L * 1024L
        ))
        assertFalse(ForegroundTelegramWindowPolicy.needsWindow(
            requestedOffset = start + 32L * 1024L,
            requestedBytes = 4096,
            currentOffset = start,
            currentEndExclusive = eof,
            knownSize = eof,
            downloadedPrefixBytes = 128L * 1024L
        ))
    }

    @Test fun unknownSizeAndNearBoundaryAdvanceWithoutOpenEndedLimit() {
        assertTrue(ForegroundTelegramWindowPolicy.needsWindow(
            15L * mib, 4096, 0L, 16L * mib, 0L
        ))
        val next = ForegroundTelegramWindowPolicy.plan(15L * mib, 0L, 900L * mib)
        assertTrue(next != null && next.limit > 0)
        assertEquals(31L * mib, next?.endExclusive)
    }
}
