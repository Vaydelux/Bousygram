package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import org.junit.Assert.*
import org.junit.Test

class PlaybackEngineSwitchCoordinatorTest {
    @Test fun swappingReleasesOldEngineExactlyOnceButKeepsSession() {
        val session = PlaybackSessionController.start(fileId = 701)
        var oldReleases = 0
        var newReleases = 0
        val old = object : FakeTestEngine() { override fun release() { oldReleases++ } }
        val next = object : FakeTestEngine() { override fun release() { newReleases++ } }
        val owner = PlaybackEngineOwner(session, old, onOwnerThread = { it() })
        val switcher = PlaybackEngineSwitchCoordinator(owner) { _, _ -> next }
        assertTrue(switcher.switchTo(SelectedPlayerEngine.MPV, 1f).isSuccess)
        assertSame(next, owner.value)
        assertEquals(1, oldReleases)
        assertFalse(session.isStopped())
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        assertEquals(1, oldReleases)
        assertEquals(1, newReleases)
    }

    @Test fun nativePlayerCreationFailurePreservesCurrentEngine() {
        val session = PlaybackSessionController.start(fileId = 702)
        var releases = 0
        val old = object : FakeTestEngine() { override fun release() { releases++ } }
        val owner = PlaybackEngineOwner(session, old, onOwnerThread = { it() })
        val switcher = PlaybackEngineSwitchCoordinator(owner) { _, _ -> error("Native player unavailable") }
        assertTrue(switcher.switchTo(SelectedPlayerEngine.MPV, 1f).isFailure)
        assertSame(old, owner.value)
        assertEquals(0, releases)
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        assertEquals(1, releases)
    }
    @Test fun disposedSessionRejectsReplacementAndCleansUnadoptedEngine() {
        val session = PlaybackSessionController.start(fileId = 703)
        var oldReleases = 0
        val old = object : FakeTestEngine() { override fun release() { oldReleases++ } }
        val owner = PlaybackEngineOwner(session, old, onOwnerThread = { it() })
        owner.close()

        val calls = mutableListOf<String>()
        val unadopted = object : FakeTestEngine() {
            override fun detachSurface() { calls += "detach" }
            override fun stop() { calls += "stop" }
            override fun release() { calls += "release" }
        }
        val result = PlaybackEngineSwitchCoordinator(owner) { _, _ -> unadopted }
            .switchTo(SelectedPlayerEngine.MPV, 1f)

        assertTrue(result.isFailure)
        assertSame(old, owner.value)
        assertEquals(1, oldReleases)
        assertEquals(listOf("detach", "stop", "release"), calls)
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        assertEquals(1, oldReleases)
    }

    @Test fun fallbackConstructorFailureRetainsCurrentEngine() {
        val session = PlaybackSessionController.start(fileId = 704)
        var releases = 0
        val original = object : FakeTestEngine() { override fun release() { releases++ } }
        val owner = PlaybackEngineOwner(session, original, onOwnerThread = { it() })
        val switcher = PlaybackEngineSwitchCoordinator(owner) { _, _ -> error("not used") }

        assertTrue(switcher.fallback { error("No compatible native decoder") }.isFailure)
        assertSame(original, owner.value)
        assertEquals(0, releases)
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        assertEquals(1, releases)
    }

    @Test fun fallbackSuccessReleasesOldOnceAndPreservesPosition() {
        val session = PlaybackSessionController.start(fileId = 705)
        var releases = 0
        val old = object : FakeTestEngine() { override fun release() { releases++ } }
        val next = object : FakeTestEngine()
        val owner = PlaybackEngineOwner(session, old, onOwnerThread = { it() })

        val result = PlaybackEngineSwitchCoordinator(owner) { _, _ -> next }
            .fallback { next to 7_500L }

        assertTrue(result.isSuccess)
        assertSame(next, result.getOrThrow().first)
        assertEquals(7_500L, result.getOrThrow().second)
        assertSame(next, owner.value)
        assertEquals(1, releases)
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        assertEquals(1, releases)
    }

}
