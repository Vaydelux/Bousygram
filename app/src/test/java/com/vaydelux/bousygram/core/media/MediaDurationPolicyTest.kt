package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDurationPolicyTest {
    @Test fun unknownLargeFileDurationIsNotAcceptedFromPartialProgressiveExtractor() {
        assertFalse(trustPlayerDuration(120_000L, 800L * 1024 * 1024, 0, false))
    }

    @Test fun seekableNativeContainerDurationWorksForLargeMkvAndTs() {
        assertTrue(trustPlayerDuration(1_440_000L, 800L * 1024 * 1024, 0, true))
        assertTrue(trustPlayerDuration(1_440_000L, 2L * 1024 * 1024 * 1024, 0, true))
    }

    @Test fun invalidAndImplausibleDurationsAreNotTrusted() {
        assertFalse(trustPlayerDuration(0L, 2_000L, 0, true))
        assertFalse(trustPlayerDuration(Long.MAX_VALUE, 2_000L, 0, true))
        assertFalse(trustPlayerDuration(50_000L, 1_000L, 1_200, true))
    }
}
