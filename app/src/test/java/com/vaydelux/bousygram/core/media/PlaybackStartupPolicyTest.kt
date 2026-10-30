package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartupPolicyTest {
    @Test fun oldEngineCannotDeclareFatalAfterFallbackStarts() {
        assertFalse(shouldFailPlaybackStartup(false, true, false, false, 9_000L))
        assertFalse(shouldFailPlaybackStartup(true, false, false, false, 9_000L))
    }
    @Test fun actualActiveStallStillReportsError() {
        assertTrue(shouldFailPlaybackStartup(true, true, false, false, 9_000L))
        assertFalse(shouldFailPlaybackStartup(true, true, true, false, 9_000L))
        assertFalse(shouldFailPlaybackStartup(true, true, false, true, 9_000L))
        assertFalse(shouldFailPlaybackStartup(true, true, false, false, 0L))
    }
}
