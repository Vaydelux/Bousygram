package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    fun concurrentBridgeRegistrationAndEngineReplacementNeverLeaksResources() {
        val session = PlaybackSessionController.start(fileId = 779)
        val ownerLock = Any()
        val releaseCounts = mutableListOf<AtomicInteger>()
        fun newEngine(): FakeTestEngine {
            val releases = AtomicInteger()
            releaseCounts += releases
            return object : FakeTestEngine() {
                override fun release() { releases.incrementAndGet() }
            }
        }
        val owner = PlaybackEngineOwner(session, newEngine(), onOwnerThread = { action ->
            synchronized(ownerLock) { action() }
        })
        try {
            repeat(80) {
                val current = owner.value
                val replacement = newEngine()
                val bridgeCloses = AtomicInteger()
                val started = CountDownLatch(1)
                val completed = CountDownLatch(2)
                val error = AtomicReference<Throwable?>()
                val register = Thread {
                    try {
                        started.await()
                        owner.registerEngineResource(current) { bridgeCloses.incrementAndGet() }
                    } catch (failure: Throwable) { error.compareAndSet(null, failure) }
                    finally { completed.countDown() }
                }
                val replace = Thread {
                    try {
                        started.await()
                        owner.value = replacement
                    } catch (failure: Throwable) { error.compareAndSet(null, failure) }
                    finally { completed.countDown() }
                }
                register.start()
                replace.start()
                started.countDown()
                assertTrue("Concurrent engine lifecycle deadlocked", completed.await(5, TimeUnit.SECONDS))
                error.get()?.let { throw AssertionError("Engine lifecycle race failed", it) }
                assertEquals("Old engine bridge was leaked or closed twice", 1, bridgeCloses.get())
            }
        } finally {
            owner.close()
            PlaybackSessionController.stop(
                session, PlaybackCacheStopReason.Dispose, cancelTdLibDownload = false
            )
        }
        assertEquals(81, releaseCounts.size)
        releaseCounts.forEach { assertEquals("Engine was released more or less than once", 1, it.get()) }
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
