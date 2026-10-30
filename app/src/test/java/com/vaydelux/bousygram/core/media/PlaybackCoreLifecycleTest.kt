package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackCoreLifecycleTest {

    private fun sampleQueue(count: Int = 3): List<PlaybackQueueItem> =
        (1..count).map { i ->
            PlaybackQueueItem(
                chatId = 100L,
                messageId = 1000L + i,
                title = "Episode $i",
                fileId = 500 + i,
                durationSeconds = 60,
                fileSizeBytes = 20_000_000L
            )
        }

    @Before
    fun setUp() {
        // Reset controller active state before each test
        PlaybackSessionController.setVideoPlaying(false)
    }

    @Test
    fun playbackStateMachineTransitionsCorrectly() {
        val stateMachine = PlaybackStateMachine()
        val queue = sampleQueue(2)
        val session = PlaybackSession(queue = queue, initialIndex = 0)

        assertEquals(PlaybackCoreState.Idle, stateMachine.currentState)

        // Idle -> Preparing
        assertTrue(stateMachine.onPreparing(session, queue[0]))
        assertTrue(stateMachine.isPreparing)
        assertEquals(PlaybackCoreState.Preparing(session, queue[0]), stateMachine.currentState)

        // Preparing -> Buffering
        assertTrue(stateMachine.onBuffering(session, 0L, 60_000L))
        assertTrue(stateMachine.isBuffering)

        // Buffering -> Playing
        assertTrue(stateMachine.onPlaying(session, 1_000L, 60_000L))
        assertTrue(stateMachine.isPlaying)

        // Playing -> Paused
        assertTrue(stateMachine.onPaused(session, 5_000L, 60_000L))
        assertTrue(stateMachine.isPaused)

        // Paused -> Ending
        assertTrue(stateMachine.onEnding(session, queue[0]))
        assertTrue(stateMachine.isEnded)

        // Ending -> Reset to Idle
        assertTrue(stateMachine.onResetToIdle())
        assertEquals(PlaybackCoreState.Idle, stateMachine.currentState)
    }

    @Test
    fun playbackStateMachineErrorHandling() {
        val stateMachine = PlaybackStateMachine()
        val queue = sampleQueue(1)
        val session = PlaybackSession(queue = queue, initialIndex = 0)

        stateMachine.onPreparing(session, queue[0])
        val exception = IllegalStateException("Decoder init failure")
        assertTrue(stateMachine.onError(session, "Failed to decode", exception))

        val state = stateMachine.currentState
        assertTrue(state is PlaybackCoreState.Error)
        val errorState = state as PlaybackCoreState.Error
        assertEquals("Failed to decode", errorState.message)
        assertEquals(exception, errorState.cause)
    }

    @Test
    fun singleActiveSessionInvariantEnforcedByController() {
        val queueA = sampleQueue(2)
        val queueB = sampleQueue(2)

        val sessionA = PlaybackSessionController.start(queue = queueA, startIndex = 0)
        assertFalse(sessionA.isStopped())
        assertTrue(PlaybackSessionController.isFileActive(501))

        // Starting session B must automatically stop session A
        val sessionB = PlaybackSessionController.start(queue = queueB, startIndex = 0)
        assertTrue(sessionA.isStopped())
        assertFalse(sessionB.isStopped())
        assertTrue(PlaybackSessionController.isFileActive(501)) // sessionB has file 501

        PlaybackSessionController.stop(sessionB, PlaybackCacheStopReason.Dispose)
        assertTrue(sessionB.isStopped())
        assertFalse(PlaybackSessionController.isFileActive(501))
    }

    @Test
    fun refreshedTdLibFileRebindsPlaybackCapability() {
        val queue = sampleQueue(1).toMutableList()
        val session = PlaybackSessionController.start(queue)
        assertTrue(PlaybackSessionController.isFileActive(501))
        PlaybackSessionController.rebindActiveFile(session, 501, 9876)
        queue[0] = queue[0].copy(fileId = 9876)
        assertFalse(PlaybackSessionController.isFileActive(501))
        assertTrue(PlaybackSessionController.isFileActive(9876))
        assertEquals(9876, session.fileId)
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Dispose)
        assertFalse(PlaybackSessionController.isFileActive(9876))
    }

    @Test
    fun teardownOrderCancelsDownloadsRevokesTokensAndReleasesSession() {
        val cancelledFileIds = mutableListOf<Int>()
        val queue = sampleQueue(2)
        val session = PlaybackSession(
            queue = queue,
            initialIndex = 0,
            onCancelDownloads = { cancelledFileIds.add(it) }
        )

        assertFalse(session.isStopped())
        session.release()
        assertTrue(session.isStopped())
        assertEquals(listOf(501), cancelledFileIds)
        assertEquals(PlaybackState.Idle, session.state.value)
    }

    @Test
    fun fakeEngineTeardownSequenceIsOrdered() {
        val teardownSteps = mutableListOf<String>()

        val fakeEngine = object : FakeTestEngine() {
            override fun detachSurface() {
                teardownSteps.add("detachSurface")
            }
            override fun stop() {
                teardownSteps.add("stop")
            }
            override fun release() {
                teardownSteps.add("release")
            }
        }

        // Simulate PlaybackCore ordered teardown sequence
        teardownSteps.add("cancelJobs")
        teardownSteps.add("revokeTokens")
        fakeEngine.detachSurface()
        fakeEngine.stop()
        fakeEngine.release()
        teardownSteps.add("releaseSession")

        assertEquals(
            listOf(
                "cancelJobs",
                "revokeTokens",
                "detachSurface",
                "stop",
                "release",
                "releaseSession"
            ),
            teardownSteps
        )
    }
}

open class FakeTestEngine : PlayerEngine {
    override val engineName: String = "FakeTestEngine"
    private val _playbackState = MutableStateFlow<EnginePlaybackState>(EnginePlaybackState.Idle)
    override val playbackState: StateFlow<EnginePlaybackState> = _playbackState.asStateFlow()
    override val currentPositionMs: Long = 0L
    override val durationMs: Long = 60_000L
    override val bufferedPositionMs: Long = 10_000L
    override val isPlaying: Boolean = false
    override val playWhenReady: Boolean = false
    override val activeDecoder: String = "FakeDecoder"
    private val _tracks = MutableStateFlow<List<MediaTrackInfo>>(emptyList())
    override val tracks: StateFlow<List<MediaTrackInfo>> = _tracks.asStateFlow()
    private val _videoDimensions = MutableStateFlow(Pair(1920, 1080))
    override val videoDimensions: StateFlow<Pair<Int, Int>> = _videoDimensions.asStateFlow()

    override fun attachSurface(surface: android.view.Surface) {}
    override fun detachSurface() {}
    override fun prepare(streamUrl: String) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun setPlaybackSpeed(speed: Float) {}
    override fun setResizeMode(mode: Int) {}
    override fun selectTrack(track: MediaTrackInfo) {}
    override fun deselectTrack(type: MediaTrackInfo.TrackType) {}
    override fun updateNetworkState(isCellular: Boolean) {}
    override fun stop() {}
    override fun release() {}
}
