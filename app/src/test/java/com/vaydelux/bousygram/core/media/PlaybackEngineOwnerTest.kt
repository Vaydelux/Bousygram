package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackEngineOwnerTest {
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
