package com.vaydelux.bousygram.presentation.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShimmerMotionPolicyTest {
    @Test fun disabledAndroidAnimationsProduceStaticSkeletons() {
        assertFalse(shouldAnimateShimmer(0f))
        assertFalse(shouldAnimateShimmer(-1f))
    }

    @Test fun normalAnimationScaleAllowsShimmer() {
        assertTrue(shouldAnimateShimmer(0.5f))
        assertTrue(shouldAnimateShimmer(1f))
    }

    @Test fun malformedScaleCannotStartInfiniteAnimation() {
        assertFalse(shouldAnimateShimmer(Float.NaN))
        assertFalse(shouldAnimateShimmer(Float.POSITIVE_INFINITY))
    }
}
