package com.vaydelux.bousygram.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure motion budget contracts for accessibility and small-RAM televisions. */
class BousygramMotionPolicyTest {
    @Test fun userDisabledAnimationsNeverAllocateDecorativeTransitions() {
        val policy = androidMotionPolicy(animationsEnabled = false, lowRam = false)
        assertEquals(0, policy.duration(220))
        assertFalse(policy.animateFocusScale)
        assertFalse(policy.animateShimmer)
        assertFalse(policy.animateIndeterminateProgress)
    }

    @Test fun lowRamDevicesUseStaticShimmerAndShortFocusTransitions() {
        val policy = androidMotionPolicy(animationsEnabled = true, lowRam = true)
        assertEquals(90, policy.duration(220))
        assertEquals(80, policy.duration(80))
        assertFalse(policy.animateFocusScale)
        assertFalse(policy.animateShimmer)
        assertFalse(policy.animateIndeterminateProgress)
    }

    @Test fun regularDevicesKeepShortDeliberateMotion() {
        val policy = androidMotionPolicy(animationsEnabled = true, lowRam = false)
        assertEquals(220, policy.duration(220))
        assertTrue(policy.animateFocusScale)
        assertTrue(policy.animateShimmer)
        assertTrue(policy.animateIndeterminateProgress)
    }

    @Test fun negativeDurationNeverEscapesMotionBudget() {
        assertEquals(0, androidMotionPolicy(true, false).duration(-10))
        assertEquals(0, androidMotionPolicy(true, true).duration(-10))
    }
}
