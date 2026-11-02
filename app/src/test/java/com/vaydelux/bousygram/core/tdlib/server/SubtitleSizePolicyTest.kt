package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSizePolicyTest {
    @Test
    fun subtitleResponsesAreBoundedBeforeAllocatingOrWriting() {
        assertTrue(allowedSubtitleBytes(0L))
        assertTrue(allowedSubtitleBytes(1024L))
        assertTrue(allowedSubtitleBytes(16L * 1024L * 1024L))
        assertFalse(allowedSubtitleBytes(-1L))
        assertFalse(allowedSubtitleBytes(16L * 1024L * 1024L + 1L))
        assertFalse(allowedSubtitleBytes(Long.MAX_VALUE))
    }
}
