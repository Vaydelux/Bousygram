package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackEngineOwnerTest {
    @Test
    fun replacingRegisteredResourceClosesOldBridgeExactlyOnce() {
        val session = PlaybackSessionController.start(fileId = 778)
        val engine = FakeTestEngine()
        val owner = PlaybackEngineOwner(session, engine, onOwnerThread = { it() })
        var older = 0
        var newer = 0
        try {
            val closeOlder = owner.registerEngineResource(engine) { older++ }
            owner.registerEngineResource(engine) { newer++ }
            assertEquals(1, older)
            assertEquals(0, newer)
            closeOlder()
            assertEquals(1, older)
        } finally {
            PlaybackSessionController.stop(session, PlaybackCacheStopReason.Dispose)
        }
        assertEquals(1, newer)
        assertEquals(1, older)
    }

    @Test
    fun replacementAndRepeatedStopReleaseEachEngineOnlyOnce() {
        val session = PlaybackSessionController.start(fileId = 777)
        val firstCalls = mutableListOf<String>()
        val secondCalls = mutableListOf<String>()
        val first = object : FakeTestEngine() {
            override fun detachSurface() { firstCalls += "detach" }
            override fun stop() { firstCalls += "stop" }
            override fun release() { firstCalls += "release" }
        }
        val second = object : FakeTestEngine() {
            override fun detachSurface() { secondCalls += "detach" }
            override fun stop() { secondCalls += "stop" }
            override fun release() { secondCalls += "release" }
        }
        val owner = PlaybackEngineOwner(session, first, onOwnerThread = { it() })
        var bridgeReleases = 0
        owner.registerEngineResource(first) { bridgeReleases++ }
        owner.value = second

        assertEquals(listOf("detach", "stop", "release"), firstCalls)
        assertEquals(1, bridgeReleases)
        owner.registerEngineResource(second) { bridgeReleases++ }
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Back)
        owner.close()
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Dispose)
        assertEquals(listOf("detach", "stop", "release"), firstCalls)
        assertEquals(listOf("detach", "stop", "release"), secondCalls)
        assertEquals(2, bridgeReleases)
    }
}
