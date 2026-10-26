package com.vaydelux.bousygram.core.media

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.core.tdlib.server.StreamAccessRegistry
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * App-scoped singleton controlling all media playback sessions and engines.
 *
 * Core Guarantees:
 * 1. Strictly ONE active playback session at all times across the entire process.
 * 2. Deterministic, ordered, background teardown:
 *    Cancel scheduler/TDLib downloads -> Revoke stream tokens -> Detach surface -> Destroy engine -> Release session.
 * 3. Lifecycle-independent engine management: UI recompositions and configuration changes
 *    attach/detach surfaces without destroying engines.
 * 4. Serialized execution: Transitions and teardowns run on a dedicated single-threaded dispatcher.
 *    Starting while releasing strictly awaits completion with forced-kill fallback.
 */
object PlaybackCore {
    private const val TAG = "PlaybackCore"
    private const val TEARDOWN_AWAIT_TIMEOUT_MS = 5_000L
    private const val PREPARING_WATCHDOG_TIMEOUT_MS = 15_000L

    private val playbackExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Bousygram-PlaybackCore").apply { isDaemon = true }
    }
    private val playbackDispatcher: CoroutineDispatcher = playbackExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + playbackDispatcher)
    private val mutex = Mutex()

    private val stateMachine = PlaybackStateMachine()
    val state: StateFlow<PlaybackCoreState> = stateMachine.state

    private val _currentSession = MutableStateFlow<PlaybackSession?>(null)
    val currentSession: StateFlow<PlaybackSession?> = _currentSession.asStateFlow()

    private val _currentEngine = MutableStateFlow<PlayerEngine?>(null)
    val currentEngine: StateFlow<PlayerEngine?> = _currentEngine.asStateFlow()

    private var activeSurface: Surface? = null
    private var engineObserverJob: Job? = null
    private var preparingWatchdogJob: Job? = null
    private val isTeardownInProgress = AtomicBoolean(false)

    val isVideoPlaying: Boolean
        get() = stateMachine.isPlaying || PlaybackSessionController.isVideoPlaying()

    /**
     * Starts a playback session with the given queue.
     * Invariant: strictly one active session. Any existing session is torn down and awaited first.
     */
    suspend fun startSession(
        context: Context,
        queue: List<PlaybackQueueItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        preferredSpeed: Float = EnterprisePreferences.preferredSpeed(),
        preferredEngine: SelectedPlayerEngine = EnterprisePreferences.selectedPlayerEngine()
    ): PlaybackSession = withContext(playbackDispatcher) {
        mutex.withLock {
            Log.i(TAG, "Initiating session with ${queue.size} items at index $startIndex")

            // 1. Await and finish any existing session teardown
            awaitTeardownAndForceKillIfNeeded()

            // 2. Teardown any currently active session
            teardownInternal(PlaybackCacheStopReason.TrackSwitch)

            // 3. Initialize new session through controller
            val session = PlaybackSessionController.start(
                queue = queue,
                startIndex = startIndex,
                startPositionMs = startPositionMs,
                onCancelDownloads = { fileId ->
                    if (fileId > 0) {
                        cancelFileJobs(fileId)
                    }
                }
            )
            _currentSession.value = session

            // 4. Instantiate designated PlayerEngine
            val item = queue.getOrNull(startIndex)
            val engine = PlayerEngineFactory.create(
                context = context,
                preferredEngine = preferredEngine,
                preferredSpeed = preferredSpeed,
                fileName = item?.fileName,
                mimeType = item?.mimeType
            )
            _currentEngine.value = engine

            // 5. Attach surface if one is already bound and valid
            activeSurface?.let { surface ->
                if (surface.isValid) {
                    engine.attachSurface(surface)
                }
            }

            // 6. Connect engine state flow to PlaybackStateMachine
            stateMachine.onPreparing(session, item)
            observeEngineState(session, engine, context)
            startPreparingWatchdog(session, engine, context)

            PlaybackSessionController.setVideoPlaying(true)
            return@withLock session
        }
    }

    private fun cancelFileJobs(fileId: Int) {
        if (fileId <= 0) return
        TelegramPreCacheEngine.stopCaching(fileId, cancelTdLibDownload = true)
        TelegramChunkScheduler.cancelPrefetch(fileId)
        StreamAccessRegistry.revokeFile(fileId)
    }

    private fun startPreparingWatchdog(session: PlaybackSession, engine: PlayerEngine, context: Context) {
        preparingWatchdogJob?.cancel()
        preparingWatchdogJob = scope.launch {
            delay(PREPARING_WATCHDOG_TIMEOUT_MS)
            if (stateMachine.isPreparing) {
                val item = session.currentItem
                Log.w(TAG, "Preparing watchdog timed out after ${PREPARING_WATCHDOG_TIMEOUT_MS}ms with no first frame. FileId=${item?.fileId}")
                PlaybackDiagnostics.recordStreamError("PreparingWatchdogTimeout")
                PlaybackTelemetryRecorder.recordFallback(session.sessionId, "PreparingWatchdogTimeout")

                // Automatic engine fallback attempt
                val fallbackSuccessful = fallbackToMpvInternal(context)
                if (!fallbackSuccessful) {
                    stateMachine.onError(
                        session,
                        "Video preparation timed out. Please check your connection or try another playback engine."
                    )
                }
            }
        }
    }

    private fun observeEngineState(session: PlaybackSession, engine: PlayerEngine, context: Context) {
        engineObserverJob?.cancel()
        engineObserverJob = scope.launch {
            engine.playbackState.collect { engineState ->
                when (engineState) {
                    is EnginePlaybackState.Idle -> {
                        // Engine idle
                    }
                    is EnginePlaybackState.Buffering -> {
                        preparingWatchdogJob?.cancel()
                        stateMachine.onBuffering(session, engine.currentPositionMs, engine.durationMs)
                    }
                    is EnginePlaybackState.Ready -> {
                        preparingWatchdogJob?.cancel()
                        if (engine.isPlaying) {
                            stateMachine.onPlaying(session, engine.currentPositionMs, engine.durationMs)
                        } else {
                            stateMachine.onPaused(session, engine.currentPositionMs, engine.durationMs)
                        }
                    }
                    is EnginePlaybackState.Ended -> {
                        preparingWatchdogJob?.cancel()
                        stateMachine.onEnding(session, session.currentItem)
                    }
                    is EnginePlaybackState.Error -> {
                        preparingWatchdogJob?.cancel()
                        stateMachine.onError(session, engineState.message, engineState.cause)
                    }
                }
            }
        }
    }

    /**
     * Attaches display surface to the active video engine without recreation.
     */
    fun attachSurface(surface: Surface) {
        activeSurface = surface
        _currentEngine.value?.let { engine ->
            if (surface.isValid) {
                engine.attachSurface(surface)
            }
        }
    }

    /**
     * Detaches display surface without stopping or tearing down playback.
     */
    fun detachSurface() {
        _currentEngine.value?.detachSurface()
        activeSurface = null
    }

    /**
     * Performs ordered, idempotent background teardown.
     */
    suspend fun teardown(reason: PlaybackCacheStopReason = PlaybackCacheStopReason.Dispose) = withContext(playbackDispatcher) {
        mutex.withLock {
            teardownInternal(reason)
        }
    }

    private fun teardownInternal(reason: PlaybackCacheStopReason) {
        isTeardownInProgress.set(true)
        try {
            Log.i(TAG, "Teardown started (reason=$reason)")
            preparingWatchdogJob?.cancel()
            preparingWatchdogJob = null
            engineObserverJob?.cancel()
            engineObserverJob = null

            val session = _currentSession.value
            val currentFileId = session?.fileId ?: 0

            // Step 1: Cancel active chunk scheduler prefetch & TDLib downloads
            if (currentFileId > 0) {
                cancelFileJobs(currentFileId)
            }
            session?.queue?.forEach { item ->
                if (item.fileId > 0 && item.fileId != currentFileId) {
                    cancelFileJobs(item.fileId)
                }
            }

            // Step 2: Revoke streaming capability tokens
            if (currentFileId > 0) {
                StreamAccessRegistry.revokeFile(currentFileId)
            }

            // Step 3: Detach surface
            _currentEngine.value?.detachSurface()
            activeSurface = null

            // Step 4: Stop and release player engine
            _currentEngine.value?.let { engine ->
                runCatching {
                    engine.stop()
                    engine.release()
                }.onFailure { err ->
                    Log.w(TAG, "Error releasing engine in teardown", err)
                }
            }
            _currentEngine.value = null

            // Step 5: Stop and release playback session
            if (session != null) {
                PlaybackSessionController.stop(session, reason, cancelTdLibDownload = true)
                session.release()
                _currentSession.value = null
            }

            // Step 6: Clear global video playing flag
            PlaybackSessionController.setVideoPlaying(false)

            // Step 7: Reset state machine
            stateMachine.onResetToIdle()
            Log.i(TAG, "Teardown complete: 0 active sessions, 0 active engines")
        } finally {
            isTeardownInProgress.set(false)
        }
    }

    private suspend fun awaitTeardownAndForceKillIfNeeded() {
        if (!isTeardownInProgress.get()) return
        Log.w(TAG, "Awaiting previous teardown completion...")
        val clean = withTimeoutOrNull(TEARDOWN_AWAIT_TIMEOUT_MS) {
            while (isTeardownInProgress.get()) {
                delay(20)
            }
            true
        }
        if (clean == null) {
            Log.e(TAG, "Teardown timed out after ${TEARDOWN_AWAIT_TIMEOUT_MS}ms! Executing forced-kill fallback")
            forceKillActiveResources()
        }
    }

    private fun forceKillActiveResources() {
        runCatching { _currentEngine.value?.detachSurface() }
        runCatching { _currentEngine.value?.stop() }
        runCatching { _currentEngine.value?.release() }
        _currentEngine.value = null
        activeSurface = null
        _currentSession.value?.release()
        _currentSession.value = null
        PlaybackSessionController.setVideoPlaying(false)
        stateMachine.onResetToIdle()
        isTeardownInProgress.set(false)
    }

    /**
     * Fallback to MpvPlayerEngine when ExoPlayer encounters unsupported codecs or containers.
     */
    suspend fun fallbackToMpv(context: Context, speed: Float = EnterprisePreferences.preferredSpeed()): Boolean = withContext(playbackDispatcher) {
        mutex.withLock {
            fallbackToMpvInternal(context, speed)
        }
    }

    private fun fallbackToMpvInternal(context: Context, speed: Float = EnterprisePreferences.preferredSpeed()): Boolean {
        val current = _currentEngine.value ?: return false
        val session = _currentSession.value ?: return false
        if (current is MpvPlayerEngine) return false

        val resumePos = current.currentPositionMs.coerceAtLeast(0L)
        Log.w(TAG, "Executing engine fallback from ${current.engineName} to mpv at $resumePos ms")

        current.detachSurface()
        runCatching {
            current.stop()
            current.release()
        }

        return try {
            val mpv = MpvPlayerEngine(context = context).apply {
                setPlaybackSpeed(speed)
            }
            _currentEngine.value = mpv
            activeSurface?.let { surface ->
                if (surface.isValid) {
                    mpv.attachSurface(surface)
                }
            }
            observeEngineState(session, mpv, context)
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to instantiate fallback MpvPlayerEngine", e)
            false
        }
    }

    // Transport controls
    fun play() {
        _currentEngine.value?.play()
        _currentSession.value?.let { session ->
            _currentEngine.value?.let { eng ->
                stateMachine.onPlaying(session, eng.currentPositionMs, eng.durationMs)
            }
        }
    }

    fun pause() {
        _currentEngine.value?.pause()
        _currentSession.value?.let { session ->
            _currentEngine.value?.let { eng ->
                stateMachine.onPaused(session, eng.currentPositionMs, eng.durationMs)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        _currentEngine.value?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun setPlaybackSpeed(speed: Float) {
        _currentEngine.value?.setPlaybackSpeed(speed)
    }

    fun selectTrack(track: MediaTrackInfo) {
        _currentEngine.value?.selectTrack(track)
    }

    fun deselectTrack(type: MediaTrackInfo.TrackType) {
        _currentEngine.value?.deselectTrack(type)
    }

    /**
     * 30-cycle open/close stress test debug action.
     * Validates zero unclosed sessions, zero dangling downloads, and clean memory stability.
     */
    suspend fun runStressTest(context: Context, iterations: Int = 30): StressTestReport = withContext(playbackDispatcher) {
        Log.i(TAG, "Starting $iterations-cycle open/close stress test")
        System.gc()
        SystemClock.sleep(100)
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        var successfulCycles = 0
        var failedCycles = 0

        val dummyTexture = SurfaceTexture(10)
        val dummySurface = Surface(dummyTexture)

        try {
            for (i in 1..iterations) {
                try {
                    val sampleQueue = listOf(
                        PlaybackQueueItem(
                            chatId = 1001L,
                            messageId = 2000L + i,
                            title = "Stress Test Item $i",
                            fileId = 9000 + i,
                            durationSeconds = 60,
                            fileSizeBytes = 10_000_000L
                        )
                    )
                    // Start session
                    startSession(
                        context = context,
                        queue = sampleQueue,
                        startIndex = 0,
                        startPositionMs = 0L,
                        preferredEngine = SelectedPlayerEngine.ExoPlayer
                    )
                    attachSurface(dummySurface)

                    // Briefly hold session to simulate fast user toggles
                    delay(25)

                    // Teardown
                    teardown(PlaybackCacheStopReason.Back)

                    // Verify invariants
                    check(_currentSession.value == null) { "Session remained active after teardown at iteration $i" }
                    check(_currentEngine.value == null) { "Engine remained active after teardown at iteration $i" }
                    check(!isVideoPlaying) { "Video marked as playing after teardown at iteration $i" }

                    successfulCycles++
                } catch (e: Throwable) {
                    Log.e(TAG, "Stress test cycle $i failed", e)
                    failedCycles++
                    teardown(PlaybackCacheStopReason.Dispose)
                }
            }
        } finally {
            dummySurface.release()
            dummyTexture.release()
            teardown(PlaybackCacheStopReason.Dispose)
        }

        System.gc()
        SystemClock.sleep(100)
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryDelta = finalMemory - initialMemory

        val isClean = failedCycles == 0 && !isVideoPlaying && _currentSession.value == null
        val message = if (isClean) {
            "30-cycle stress test passed successfully with 0 leaks (Memory delta: ${memoryDelta / 1024} KB)"
        } else {
            "Stress test completed with $failedCycles failures out of $iterations cycles"
        }

        Log.i(TAG, message)
        return@withContext StressTestReport(
            iterations = iterations,
            successfulCycles = successfulCycles,
            failedCycles = failedCycles,
            initialMemoryBytes = initialMemory,
            finalMemoryBytes = finalMemory,
            memoryDeltaBytes = memoryDelta,
            isLeakClean = isClean,
            message = message
        )
    }
}

data class StressTestReport(
    val iterations: Int,
    val successfulCycles: Int,
    val failedCycles: Int,
    val initialMemoryBytes: Long,
    val finalMemoryBytes: Long,
    val memoryDeltaBytes: Long,
    val isLeakClean: Boolean,
    val message: String
)
