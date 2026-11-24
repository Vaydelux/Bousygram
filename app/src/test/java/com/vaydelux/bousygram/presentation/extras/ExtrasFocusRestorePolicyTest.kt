package com.vaydelux.bousygram.presentation.extras

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasFocusRestorePolicyTest {
    @Test fun retriesUntilLazyNodeAttaches() = runBlocking {
        var frames = 0
        assertTrue(restoreExtrasFocus({ frames++ }, { frames == 2 }, { error("fallback") }))
        assertEquals(2, frames)
    }

    @Test fun fallsBackOnceAfterThreeFailedFrames() = runBlocking {
        var frames = 0
        var fallbackCalls = 0
        assertTrue(restoreExtrasFocus({ frames++ }, { false }, { fallbackCalls++; true }))
        assertEquals(4, frames)
        assertEquals(1, fallbackCalls)
    }

    @Test fun failedFallbackNeverReportsSuccess() = runBlocking {
        assertFalse(restoreExtrasFocus({}, { false }, { false }))
    }
}
