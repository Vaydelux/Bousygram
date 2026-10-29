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
}
