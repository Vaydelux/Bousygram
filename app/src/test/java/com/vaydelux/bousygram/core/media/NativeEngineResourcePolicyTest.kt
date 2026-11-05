package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeEngineResourcePolicyTest {
    @Test fun childCreatedSuccessfullyDoesNotCloseNativeParent() {
        val parent = Any()
        val child = Any()
        var calls = 0
        val actual = createNativeChildOrReleaseParent(parent, { child }, { calls++ })
        assertSame(child, actual)
        assertEquals(0, calls)
    }

    @Test fun childConstructorFailureClosesNativeParentExactlyOnce() {
        val parent = Any()
        val failure = IllegalStateException("Native decoder allocation failed")
        var calls = 0
        val caught = runCatching {
            createNativeChildOrReleaseParent(parent, { throw failure }, { calls++ })
        }.exceptionOrNull()
        assertSame(failure, caught)
        assertEquals(1, calls)
    }

    @Test fun cleanupFailureIsSuppressedWithoutMaskingOriginalError() {
        val parent = Any()
        val original = IllegalArgumentException("child init failed")
        val cleanup = IllegalStateException("parent release failed")
        val caught = runCatching {
            createNativeChildOrReleaseParent(parent, { throw original }, { throw cleanup })
        }.exceptionOrNull()
        assertSame(original, caught)
        assertTrue(original.suppressed.contains(cleanup))
    }
}
