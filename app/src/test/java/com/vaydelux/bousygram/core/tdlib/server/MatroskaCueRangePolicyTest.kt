package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatroskaCueRangePolicyTest {
    @Test fun cueAtFileTailRequestsNewWindowWhenHeadIsCurrent() {
        val fileSize = 800L * 1024L * 1024L
        val headEnd = 2L * 1024L * 1024L
        val cuesOffset = fileSize - 128L * 1024L
        assertTrue(requiresPlaybackWindowReposition(cuesOffset, 0L, headEnd))
    }

    @Test fun contiguousWindowDoesNotRestartExistingDownload() {
        assertFalse(requiresPlaybackWindowReposition(1024L, 0L, 2L * 1024L * 1024L))
        assertFalse(requiresPlaybackWindowReposition(2048L, 1024L, 8192L))
    }

    @Test fun emptyWindowAndBackwardSeekRequireRange() {
        assertTrue(requiresPlaybackWindowReposition(0L, null, 0L))
        assertTrue(requiresPlaybackWindowReposition(100L, 200L, 1000L))
        assertTrue(requiresPlaybackWindowReposition(1000L, 200L, 1000L))
    }
}
