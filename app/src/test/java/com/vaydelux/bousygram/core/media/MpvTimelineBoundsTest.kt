package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvTimelineBoundsTest {
    @Test fun rejectsNonFiniteNativeTimelineValues() {
        assertNull(nativeMpvTimelineMs(Double.NaN))
        assertNull(nativeMpvTimelineMs(Double.POSITIVE_INFINITY))
        assertNull(nativeMpvTimelineMs(-1.0))
        assertNull(nativeMpvTimelineMs(Double.MAX_VALUE))
    }

    @Test fun convertsValidFractionalSecondsWithoutInventingTime() {
        assertEquals(1_250L, nativeMpvTimelineMs(1.25))
        assertEquals(0L, nativeMpvTimelineMs(0.0))
    }

    @Test fun seeksClampToKnownDurationWithoutAssumingUnknownDuration() {
        assertEquals(60_000L, boundedMpvSeekMs(Long.MAX_VALUE, 60_000L))
        assertEquals(0L, boundedMpvSeekMs(-500L, 60_000L))
        assertEquals(75_000L, boundedMpvSeekMs(75_000L, 0L))
    }

    @Test fun bufferedTimelineSaturatesInsteadOfWrappingNegative() {
        assertEquals(
            Long.MAX_VALUE,
            nativeMpvBufferedPositionMs(Long.MAX_VALUE - 100, 250L)
        )
        assertEquals(7_000L, nativeMpvBufferedPositionMs(5_000L, 2_000L))
    }
}
