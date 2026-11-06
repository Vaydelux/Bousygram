package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeEngineReplacementTest {
    @Test fun failedNativeConstructionLeavesCurrentDecoderAndSurfaceUntouched() {
        val calls = mutableListOf<String>()
        val current = object : FakeTestEngine() {
            override fun detachSurface() { calls += "old.detach" }
            override fun stop() { calls += "old.stop" }
            override fun release() { calls += "old.release" }
        }
        val failure = IllegalStateException("native allocation failed")
        val result = replacePlayerEngineSafely(
            current = current,
            create = { throw failure },
            bind = { calls += "bind" },
            restore = { calls += "restore" }
        )
        assertSame(failure, result.exceptionOrNull())
        assertEquals(emptyList<String>(), calls)
    }

    @Test fun successfulNativeReplacementPublishesBeforeStoppingPreviousDecoder() {
        val calls = mutableListOf<String>()
        val current = object : FakeTestEngine() {
            override fun detachSurface() { calls += "old.detach" }
            override fun stop() { calls += "old.stop" }
            override fun release() { calls += "old.release" }
        }
        val next = object : FakeTestEngine() {
            override fun release() { calls += "new.release" }
        }
        val result = replacePlayerEngineSafely(
            current = current,
            create = { calls += "new.create"; next },
            bind = { assertSame(next, it); calls += "new.bind" },
            restore = { calls += "old.restore" }
        )
        assertSame(next, result.getOrThrow())
        assertEquals(
            listOf("new.create", "old.detach", "new.bind", "old.stop", "old.release"),
            calls
        )
    }

    @Test fun failedBindingDisposesNewDecoderAndRestoresPreviousOwner() {
        val calls = mutableListOf<String>()
        val current = object : FakeTestEngine() {
            override fun detachSurface() { calls += "old.detach" }
            override fun stop() { calls += "old.stop" }
            override fun release() { calls += "old.release" }
        }
        val next = object : FakeTestEngine() {
            override fun detachSurface() { calls += "new.detach" }
            override fun stop() { calls += "new.stop" }
            override fun release() { calls += "new.release" }
        }
        var installed: PlayerEngine = current
        val error = IllegalStateException("surface unavailable")
        val result = replacePlayerEngineSafely(
            current,
            create = { next },
            bind = { installed = it; calls += "new.bind"; throw error },
            restore = { installed = current; calls += "old.restore" }
        )
        assertSame(error, result.exceptionOrNull())
        assertSame(current, installed)
        assertEquals(
            listOf("old.detach", "new.bind", "new.detach", "new.stop", "new.release", "old.restore"),
            calls
        )
    }

    @Test fun cleanupErrorsAreSuppressedWithoutMaskingBindingFailure() {
        val failure = IllegalStateException("binding failed")
        val cleanup = IllegalArgumentException("release failed")
        val next = object : FakeTestEngine() {
            override fun release() { throw cleanup }
        }
        val result = replacePlayerEngineSafely(
            current = FakeTestEngine(),
            create = { next },
            bind = { throw failure },
            restore = { }
        )
        assertSame(failure, result.exceptionOrNull())
        assertTrue(failure.suppressed.contains(cleanup))
    }

    @Test fun oldEngineStopFailureStillReleasesOldEngineAndKeepsReplacement() {
        val calls = mutableListOf<String>()
        val failure = IllegalStateException("native stop failed")
        val old = object : FakeTestEngine() {
            override fun stop() { calls += "old.stop"; throw failure }
            override fun release() { calls += "old.release" }
        }
        val next = FakeTestEngine()
        val warnings = mutableListOf<Throwable>()
        val result = replacePlayerEngineSafely(
            current = old,
            create = { next },
            bind = { calls += "new.bind" },
            restore = { calls += "old.restore" },
            reportCleanupFailure = warnings::add
        )
        assertSame(next, result.getOrThrow())
        assertEquals(listOf("new.bind", "old.stop", "old.release"), calls)
        assertEquals(listOf(failure), warnings)
    }

    @Test fun factoryReturningCurrentInstanceIsRejectedWithoutDisposal() {
        var released = false
        val old = object : FakeTestEngine() {
            override fun release() { released = true }
        }
        val result = replacePlayerEngineSafely(
            current = old,
            create = { old },
            bind = { error("must not bind the current decoder again") },
            restore = { error("must not restore on same decoder") }
        )
        assertTrue(result.isFailure)
        assertFalse(released)
    }
}
