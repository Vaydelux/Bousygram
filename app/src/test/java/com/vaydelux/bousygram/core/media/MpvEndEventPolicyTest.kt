package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvEndEventPolicyTest {
    @Test fun stopAndFileReplacementAreNotNaturalVideoCompletion() {
        assertFalse(shouldReportNativeMpvEnded(false))
    }

    @Test fun nativeEndRequiresObservedEof() {
        assertTrue(shouldReportNativeMpvEnded(true))
    }
}
