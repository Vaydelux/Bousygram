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

    @Test fun bufferedTimelineSaturatesInsteadOfWrappingNegative() {
        assertEquals(
            Long.MAX_VALUE,
            nativeMpvBufferedPositionMs(Long.MAX_VALUE - 100, 250L)
        )
        assertEquals(7_000L, nativeMpvBufferedPositionMs(5_000L, 2_000L))
    }
}
