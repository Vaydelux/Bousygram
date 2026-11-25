package com.vaydelux.bousygram.core.tdlib.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewBucketPolicyTest {
    @Test fun ordinarySeekRetainsTenSecondBuckets() {
        assertEquals(20_000L, boundedPreviewBucketMs(29_999L, 100_000L))
        assertEquals(0L, boundedPreviewBucketMs(-1L, 100_000L))
    }

    @Test fun durationAndMicrosecondConversionAreBounded() {
        assertEquals(30_000L, boundedPreviewBucketMs(90_000L, 35_000L))
        val extreme = boundedPreviewBucketMs(Long.MAX_VALUE, 0L)
        assertTrue(extreme in 0L..Long.MAX_VALUE / 1_000L)
        assertTrue(extreme * 1_000L >= 0L)
    }
}
