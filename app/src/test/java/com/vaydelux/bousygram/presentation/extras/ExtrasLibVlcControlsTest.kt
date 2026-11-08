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
    @Test fun timeLabelsStayReadableForTv() {
        assertEquals("0:00", extrasVlcTimeLabel(-1L))
        assertEquals("0:09", extrasVlcTimeLabel(9_000L))
        assertEquals("9:59", extrasVlcTimeLabel(599_000L))
        assertEquals("1:00:00", extrasVlcTimeLabel(3_600_000L))
        assertEquals("12:34:56", extrasVlcTimeLabel(45_296_000L))
    }
}
