package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasTvCursorPolicyTest {
    @Test fun basicDpadMovesMatchLegacySmallScreenBehavior() {
        assertEquals(18, ExtrasTvCursorPolicy.step(360, 240, 0))
        assertEquals(18, ExtrasTvCursorPolicy.step(360, 240, 5))
        assertEquals(36, ExtrasTvCursorPolicy.step(360, 240, 6))
    }

    @Test fun largeTvScreensAndHeldDpadAccelerateWithoutUnboundedSteps() {
        val normal = ExtrasTvCursorPolicy.step(3840, 2160, 0)
        val repeat = ExtrasTvCursorPolicy.step(3840, 2160, 6)
        val longHold = ExtrasTvCursorPolicy.step(3840, 2160, 16)
        assertTrue(normal > 18)
        assertEquals(normal * 2, repeat)
        assertEquals(normal * 4, longHold)
        assertTrue(longHold <= 256)
        assertEquals(256, ExtrasTvCursorPolicy.step(7680, 4320, 16))
    }

    @Test fun pointerBoundsAlwaysStayWithinTargetAndResistOverflow() {
        assertEquals(359, ExtrasTvCursorPolicy.clamp(350, 200, 360))
        assertEquals(0, ExtrasTvCursorPolicy.clamp(4, -300, 360))
        assertEquals(0, ExtrasTvCursorPolicy.clamp(900, 200, 0))
        assertEquals(0, ExtrasTvCursorPolicy.clamp(Int.MAX_VALUE, Int.MAX_VALUE, 1))
        assertEquals(0, ExtrasTvCursorPolicy.clamp(Int.MIN_VALUE, Int.MIN_VALUE, 1))
    }
}
