package com.vaydelux.bousygram.core.tdlib.server

import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPreflightRecoveryTest {
    @Test fun localHttpPreflightAllowsTargetedTdlibStallRecovery() {
        // TDLib first waits 3s for a prefix, then 6s after a new
        // prioritized download. Returning 503 at 2s bypassed both stages.
        assertTrue(TelegramChunkScheduler.STREAM_FIRST_CHUNK_WAIT_MS >= 9_500L)
        assertTrue(TelegramChunkScheduler.STREAM_FIRST_CHUNK_WAIT_MS <= 15_000L)
    }
}
