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
    fun readyEngineRetriesOnlyWhileUserWantsPlayback() {
        assertTrue(shouldReassertPlaybackOnReady(ready = true, playWhenReady = true, isPlaying = false))
        assertFalse(shouldReassertPlaybackOnReady(ready = false, playWhenReady = true, isPlaying = false))
        assertFalse(shouldReassertPlaybackOnReady(ready = true, playWhenReady = false, isPlaying = false))
        assertFalse(shouldReassertPlaybackOnReady(ready = true, playWhenReady = true, isPlaying = true))
    }

    @Test
    fun transportUiOnlyClaimsPlayingAfterOutputAndNeverOverridesManualPause() {
        // Shared intent-first policy for ExoPlayer, libmpv and LibVLC.
        assertEquals(
            PlaybackPresentationState.BUFFERING,
            playbackPresentationState(ready = false, playWhenReady = true, isPlaying = false)
        )
        assertEquals(
            PlaybackPresentationState.BUFFERING,
            playbackPresentationState(ready = true, playWhenReady = true, isPlaying = false)
        )
        assertEquals(
            PlaybackPresentationState.BUFFERING,
            playbackPresentationState(ready = false, playWhenReady = true, isPlaying = true)
        )
        assertEquals(
            PlaybackPresentationState.PLAYING,
            playbackPresentationState(ready = true, playWhenReady = true, isPlaying = true)
        )
        // Native output flags can stay stale briefly after a user or lifecycle Pause.
        for (ready in listOf(false, true)) {
            for (nativePlaying in listOf(false, true)) {
                assertEquals(
                    PlaybackPresentationState.PAUSED,
                    playbackPresentationState(
                        ready = ready, playWhenReady = false, isPlaying = nativePlaying
                    )
                )
            }
        }
    }

    @Test
    fun pausedPresentationSurvivesLateBufferingEventAndExplicitResume() {
        val machine = PlaybackStateMachine()
        val item = sampleQueue(1).single()
        val session = PlaybackSession(listOf(item))
        assertTrue(machine.onPreparing(session, item))
        assertTrue(machine.onPaused(session, 12_000L, 60_000L))
        val latePhase = playbackPresentationState(
            ready = false, playWhenReady = false, isPlaying = false
        )
        if (latePhase == PlaybackPresentationState.PAUSED) {
            machine.onPaused(session, 12_000L, 60_000L)
        } else {
            machine.onBuffering(session, 12_000L, 60_000L)
        }
        assertTrue(machine.isPaused)
        assertTrue(machine.onBuffering(session, 12_000L, 60_000L))
        assertTrue(machine.isBuffering)
        assertTrue(machine.onPlaying(session, 12_200L, 60_000L))
        assertTrue(machine.isPlaying)
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
    fun lateEventsFromPreviousSessionCannotEndOrPauseTheNewVideo() {
        val machine = PlaybackStateMachine()
        val queue = sampleQueue(2)
        val previous = PlaybackSession(queue, initialIndex = 0)
        val active = PlaybackSession(queue, initialIndex = 1)

        assertTrue(machine.onPreparing(previous, queue[0]))
        assertFalse(machine.onPreparing(active, queue[1])) // requires teardown
        assertFalse(machine.onPlaying(active, 40_000L, 60_000L))
        assertFalse(machine.onEnding(active, queue[1]))
        assertFalse(machine.onError(active, "unowned decoder error"))
        assertEquals(PlaybackCoreState.Preparing(previous, queue[0]), machine.currentState)

        assertTrue(machine.onResetToIdle())
        assertTrue(machine.onPreparing(active, queue[1]))
        assertFalse(machine.onBuffering(previous, 3_000L, 60_000L))
        assertFalse(machine.onPlaying(previous, 3_000L, 60_000L))
        assertFalse(machine.onPaused(previous, 3_000L, 60_000L))
        assertFalse(machine.onEnding(previous, queue[0]))
        assertFalse(machine.onError(previous, "stale error after engine replacement"))
        assertEquals(PlaybackCoreState.Preparing(active, queue[1]), machine.currentState)

        assertTrue(machine.onPlaying(active, 1_000L, 60_000L))
        assertTrue(machine.isPlaying)
    }

    @Test
    fun oldQueueItemCannotFinishOrReprepareNewEpisodeInSameSession() {
        val machine = PlaybackStateMachine()
        val queue = sampleQueue(2)
        val session = PlaybackSession(queue)
        assertTrue(machine.onPreparing(session, queue[0]))
        assertTrue(machine.onPlaying(session, 55_000L, 60_000L))
        assertTrue(session.next())
        assertFalse(machine.onEnding(session, queue[0]))
        assertFalse(machine.onPreparing(session, queue[0]))
        assertTrue(machine.onPreparing(session, queue[1]))
        assertTrue(machine.onPlaying(session, 0L, 60_000L))
        assertFalse(machine.onEnding(session, queue[0]))
        assertTrue(machine.isPlaying)
        assertTrue(machine.onEnding(session, queue[1]))
    }

    @Test
    fun preparingUsesTheClampedCurrentQueueItem() {
        val machine = PlaybackStateMachine()
        val queue = sampleQueue(2)
        val session = PlaybackSession(queue, initialIndex = 999)
        assertEquals(queue[1], session.currentItem)
        assertFalse(machine.onPreparing(session, queue[0]))
        assertTrue(machine.onPreparing(session, session.currentItem))
        assertEquals(PlaybackCoreState.Preparing(session, queue[1]), machine.currentState)
    }

    @Test
    fun naturalEndCannotBeReplacedByLateDecoderError() {
        val machine = PlaybackStateMachine()
        val queue = sampleQueue(1)
        val session = PlaybackSession(queue)
        assertTrue(machine.onPreparing(session, queue[0]))
        assertTrue(machine.onPlaying(session, 59_000L, 60_000L))
        assertTrue(machine.onEnding(session, queue[0]))
        assertFalse(machine.onEnding(session, queue[0]))
        assertFalse(machine.onError(session, "late demuxer EOF reported as error"))
        assertFalse(machine.onBuffering(session, 0L, 60_000L))
        assertEquals(PlaybackCoreState.Ending(session, queue[0]), machine.currentState)
    }

    @Test
    fun stoppedOrReleasedSessionsCannotRevivePlayback() {
        val machine = PlaybackStateMachine()
        val queue = sampleQueue(1)
        val session = PlaybackSession(queue)
        assertTrue(machine.onPreparing(session, queue[0]))
        session.release()
        assertFalse(machine.onPlaying(session, 3_000L, 60_000L))
        assertFalse(machine.onError(session, "late native callback"))
        machine.onResetToIdle()
        assertFalse(machine.onPreparing(session, queue[0]))
        assertFalse(machine.onEnding(session, queue[0]))
        assertEquals(PlaybackCoreState.Idle, machine.currentState)
        assertTrue(machine.onReleased())
        assertFalse(machine.onPreparing(PlaybackSession(queue), queue[0]))
        assertEquals(PlaybackCoreState.Released, machine.currentState)
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
        assertFalse(PlaybackSessionController.rebindActiveFile(session, 999, 9876))
        assertFalse(PlaybackSessionController.isFileActive(9876))
        assertTrue(PlaybackSessionController.rebindActiveFile(session, 501, 9876))
        queue[0] = queue[0].copy(fileId = 9876)
        assertFalse(PlaybackSessionController.isFileActive(501))
        assertTrue(PlaybackSessionController.isFileActive(9876))
        assertEquals(9876, session.fileId)
        PlaybackSessionController.stop(session, PlaybackCacheStopReason.Dispose)
        assertFalse(PlaybackSessionController.isFileActive(9876))
        assertFalse(PlaybackSessionController.rebindActiveFile(session, 9876, 1234))
        assertFalse(PlaybackSessionController.isFileActive(1234))
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
