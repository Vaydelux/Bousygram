package com.vaydelux.bousygram.presentation.media.vm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailRetryPolicyTest {
    @Test fun firstRequestAndExpiredFailuresMayRetry() {
        assertTrue(allowThumbnailRetry(null, 1000L, 60_000L))
        assertFalse(allowThumbnailRetry(1000L, 1001L, 60_000L))
        assertFalse(allowThumbnailRetry(1000L, 60_999L, 60_000L))
        assertTrue(allowThumbnailRetry(1000L, 61_000L, 60_000L))
    }

    @Test fun clockResetDoesNotLeaveThumbnailsPermanentlyBlocked() {
        assertTrue(allowThumbnailRetry(900_000L, 2_000L, 60_000L))
    }
}
