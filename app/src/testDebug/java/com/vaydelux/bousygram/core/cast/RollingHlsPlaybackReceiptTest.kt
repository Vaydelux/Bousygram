package com.vaydelux.bousygram.core.cast

import org.junit.Assert.*
import org.junit.Test

class RollingHlsPlaybackReceiptTest {
    @Test fun requiresRenderedFrameAndActualEnd() {
        val receipt = RollingHlsPlaybackReceipt { true }
        assertTrue(receipt.begin(4, 6_000_000))
        assertNull(receipt.observe(6000, false, false))
        receipt.renderedFirstFrame()
        assertNull(receipt.observe(3000, true, false))
        assertEquals(4L, receipt.observe(6000, false, true))
        assertNull(receipt.observe(6000, false, true))
    }
    @Test fun earlyEndSeekAndRevocationCannotAcknowledge() {
        for (reason in 0..3) {
            var active = true
            val receipt = RollingHlsPlaybackReceipt { active }
            assertTrue(receipt.begin(1, 6_000_000))
            if (reason != 0) receipt.renderedFirstFrame()
            if (reason == 2) receipt.observe(5000, true, false, discontinuity = true)
            if (reason == 3) active = false
            assertNull(receipt.observe(if (reason == 1) 1000 else 6000, false, true))
            assertNull(receipt.observe(6000, false, true))
        }
    }
}
