package com.vaydelux.bousygram.core.media

import androidx.media3.common.PlaybackException
import com.vaydelux.bousygram.core.observability.PlaybackErrorMapper
import com.vaydelux.bousygram.core.observability.PlaybackRecoveryAction
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.prefetch.FocusPrefetchManager
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.BufferMode
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

enum class PlaybackStallReason {
    None,
    TelegramPending,
    NetworkTimeout,
    DecoderUnsupported,
    FileUnavailable,
    AppBackgroundStop,
    UserExit,
    TrackSwitch,
    ErrorRecovery,
    Unknown
}

enum class PlaybackCacheStopReason {
    None,
    Back,
    TrackSwitch,
    LifecycleStop,
    Dispose,
    ErrorRecovery,
    ViewModelStop,
    ViewModelCleared
}

enum class PlaybackRetryDecision {
    RetrySameQuality,
    LowerQuality,
    CacheFirstRetry,
    SkipOrReport
}

object PlaybackSessionController {
    private const val STARTUP_CACHE_LEAD_MS = 12_000L
    private const val STABLE_CACHE_LEAD_MS = 30_000L
    private const val MIN_CACHE_AHEAD_BYTES = 8L * 1024L * 1024L
    private const val NORMAL_CACHE_AHEAD_BYTES = 32L * 1024L * 1024L
    private const val FAST_START_CACHE_AHEAD_BYTES = 16L * 1024L * 1024L
    private const val STABLE_CACHE_AHEAD_BYTES = 48L * 1024L * 1024L

    private val nextSessionNumber = AtomicLong(1L)
    private val activeSessionsByFile = ConcurrentHashMap<Int, Int>()
    private val activeSessions = ConcurrentHashMap.newKeySet<PlaybackSession>()
    private val resourceOwners = ConcurrentHashMap<PlaybackSession, () -> Unit>()
    private val isVideoPlayingFlag = AtomicBoolean(false)

    fun setVideoPlaying(playing: Boolean) {
        isVideoPlayingFlag.set(playing)
        if (playing) {
            FocusPrefetchManager.cancelAll()
        }
    }

    /** Returns true if video playback is currently active. */
    fun isVideoPlaying(): Boolean = isVideoPlayingFlag.get() || !activeSessionsByFile.isEmpty()

    /** The screen owns its engine; account invalidation can synchronously close that owner. */
    fun registerResourceOwner(session: PlaybackSession, close: () -> Unit): () -> Unit {
        check(!session.isStopped()) { "Cannot attach resources to a stopped playback session" }
        resourceOwners[session] = close
        return { resourceOwners.remove(session, close) }
    }

    fun stopAll(reason: PlaybackCacheStopReason = PlaybackCacheStopReason.Dispose) {
        var failure: Throwable? = null
        activeSessions.toList().forEach { session ->
            runCatching { stop(session, reason) }.onFailure { failure = failure ?: it }
        }
        activeSessionsByFile.clear()
        isVideoPlayingFlag.set(false)
        failure?.let { throw it }
    }

    fun bindActiveFile(fileId: Int) {
        if (fileId > 0) {
            activeSessionsByFile.compute(fileId) { _, count -> (count ?: 0) + 1 }
        }
    }

    fun unbindActiveFile(fileId: Int) {
        if (fileId > 0) {
            activeSessionsByFile.computeIfPresent(fileId) { _, count ->
                if (count <= 1) null else count - 1
            }
        }
    }

    /**
     * Switch the file capability when a TDLib message resolves to a different native fileId.
     * The caller must update the same session queue item before starting the new data source.
     * The normal stop() path then unbinds only the refreshed ID.
     */
    fun rebindActiveFile(session: PlaybackSession, previousId: Int, resolvedId: Int) {
        if (resolvedId <= 0 || resolvedId == previousId || session.isStopped() ||
            !activeSessions.contains(session)
        ) return
        unbindActiveFile(previousId)
        bindActiveFile(resolvedId)
    }

    fun start(
        queue: List<com.vaydelux.bousygram.presentation.media.PlaybackQueueItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L,
        onCancelDownloads: ((fileId: Int) -> Unit)? = null
    ): PlaybackSession {
        FocusPrefetchManager.cancelAll()
        // Enforce strictly one active playback session across the entire app
        val lingering = activeSessions.toList()
        lingering.forEach { existing ->
            if (!existing.isStopped()) {
                stop(existing, PlaybackCacheStopReason.TrackSwitch, cancelTdLibDownload = true)
            }
        }
        activeSessions.clear()
        val session = PlaybackSession(
            queue = queue,
            initialIndex = startIndex,
            sessionId = "pb-${System.currentTimeMillis()}-${nextSessionNumber.getAndIncrement()}",
            startedAtMs = System.currentTimeMillis(),
            startPositionMs = startPositionMs.coerceAtLeast(0L),
            onCancelDownloads = onCancelDownloads
        )
        queue.forEach { item ->
            if (item.fileId > 0) {
                bindActiveFile(item.fileId)
            }
        }
        activeSessions.add(session)
        record(session)
        return session
    }

    fun start(fileId: Int, startPositionMs: Long = 0L): PlaybackSession {
        FocusPrefetchManager.cancelAll()
        // Enforce strictly one active playback session across the entire app
        val lingering = activeSessions.toList()
        lingering.forEach { existing ->
            if (!existing.isStopped()) {
                stop(existing, PlaybackCacheStopReason.TrackSwitch, cancelTdLibDownload = true)
            }
        }
        activeSessions.clear()
        val session = PlaybackSession(
            sessionId = "pb-${System.currentTimeMillis()}-${nextSessionNumber.getAndIncrement()}",
            fileId = fileId,
            startedAtMs = System.currentTimeMillis(),
            startPositionMs = startPositionMs.coerceAtLeast(0L)
        )
        if (fileId > 0) {
            bindActiveFile(fileId)
        }
        activeSessions.add(session)
        record(session)
        return session
    }

    /** True while at least one playback session is using this TDLib file. */
    fun isFileActive(fileId: Int): Boolean {
        if (fileId <= 0) return false
        if ((activeSessionsByFile[fileId] ?: 0) > 0) return true
        return activeSessions.any { session ->
            !session.isStopped() && (session.fileId == fileId || session.queue.any { it.fileId == fileId })
        }
    }

    fun recordFirstFrame(session: PlaybackSession, firstFrameMs: Long) {
        if (firstFrameMs in 1L..4_000L) {
            session.consecutiveRangeStalls = 0
        }
        record(session)
    }

    fun recordProgress(
        session: PlaybackSession,
        positionMs: Long,
        totalSize: Long,
        durationMs: Long
    ) {
        session.currentPositionMs = positionMs.coerceAtLeast(0L)
        session.currentOffsetBytes = positionToOffset(totalSize, session.currentPositionMs, durationMs)
        record(session)
    }

    fun cacheAhead(
        session: PlaybackSession,
        totalSize: Long,
        positionMs: Long,
        durationMs: Long,
        startup: Boolean = false
    ) {
        if (session.fileId <= 0 || totalSize <= 0L) return
        val mode = EnterprisePreferences.bufferMode()
        val leadMs = when (mode) {
            BufferMode.FastStart -> if (startup) 8_000L else STARTUP_CACHE_LEAD_MS
            BufferMode.Balanced -> if (startup) 15_000L else STABLE_CACHE_LEAD_MS
            BufferMode.StableNetwork -> if (startup) 30_000L else 45_000L
        }
        val targetPosition = positionMs.coerceAtLeast(0L) + leadMs
        val maxBytes = when (mode) {
            BufferMode.FastStart -> if (session.consecutiveRangeStalls >= 2) MIN_CACHE_AHEAD_BYTES else FAST_START_CACHE_AHEAD_BYTES
            BufferMode.Balanced -> adaptiveCacheAheadBytes(session)
            BufferMode.StableNetwork -> STABLE_CACHE_AHEAD_BYTES
        }
        TelegramPreCacheEngine.cacheAhead(
            fileId = session.fileId,
            totalSize = totalSize,
            positionMs = targetPosition,
            durationMs = durationMs,
            maxBytes = maxBytes
        )
        recordProgress(session, positionMs, totalSize, durationMs)
    }

    fun classify(error: PlaybackException): PlaybackStallReason {
        val detail = error.message.orEmpty().lowercase()
        return when {
            "still caching" in detail || "range pending" in detail || "source error" in detail ->
                PlaybackStallReason.TelegramPending
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                PlaybackStallReason.NetworkTimeout
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ->
                PlaybackStallReason.DecoderUnsupported
            error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                PlaybackStallReason.FileUnavailable
            else -> PlaybackStallReason.Unknown
        }
    }

    fun recordError(
        session: PlaybackSession,
        error: PlaybackException,
        retryCount: Int
    ): PlaybackRetryDecision {
        val reason = classify(error)
        session.lastStallReason = reason
        session.retryCount = retryCount
        if (reason == PlaybackStallReason.TelegramPending) {
            session.consecutiveRangeStalls++
        }
        record(session)

        val resolution = PlaybackErrorMapper.fromPlaybackException(error, retryCount)
        return when {
            reason == PlaybackStallReason.TelegramPending && session.consecutiveRangeStalls >= 2 ->
                PlaybackRetryDecision.CacheFirstRetry
            resolution.primaryAction == PlaybackRecoveryAction.LowerQuality ->
                PlaybackRetryDecision.LowerQuality
            resolution.primaryAction == PlaybackRecoveryAction.CacheFirst ->
                PlaybackRetryDecision.CacheFirstRetry
            resolution.primaryAction == PlaybackRecoveryAction.Reconnect && retryCount <= 1 ->
                PlaybackRetryDecision.RetrySameQuality
            resolution.secondaryAction == PlaybackRecoveryAction.LowerQuality ->
                PlaybackRetryDecision.LowerQuality
            else -> PlaybackRetryDecision.SkipOrReport
        }
    }

    fun stop(session: PlaybackSession?, reason: PlaybackCacheStopReason, cancelTdLibDownload: Boolean = true) {
        session ?: return
        val wasRegistered = activeSessions.remove(session)
        val firstStop = session.markStoppedOnce()
        if (!wasRegistered && !firstStop) return
        // A caller may already have released the session. Registration still has to be
        // removed exactly once, otherwise direct reads and cache protection remain live.
        if (wasRegistered) session.queue.forEach { item -> unbindActiveFile(item.fileId) }
        val ownerFailure = runCatching { resourceOwners.remove(session)?.invoke() }.exceptionOrNull()
        session.lastStopReason = reason
        session.lastStallReason = when (reason) {
            PlaybackCacheStopReason.Back -> PlaybackStallReason.UserExit
            PlaybackCacheStopReason.TrackSwitch -> PlaybackStallReason.TrackSwitch
            PlaybackCacheStopReason.LifecycleStop -> PlaybackStallReason.AppBackgroundStop
            PlaybackCacheStopReason.ErrorRecovery -> PlaybackStallReason.ErrorRecovery
            else -> session.lastStallReason
        }
        TelegramPreCacheEngine.stopCaching(session.fileId, cancelTdLibDownload)
        if (cancelTdLibDownload && session.fileId > 0) {
            com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler.cancelPrefetch(session.fileId)
        }
        session.release()
        if (activeSessions.isEmpty()) isVideoPlayingFlag.set(false)
        record(session)
        ownerFailure?.let { throw it }
    }

    fun positionToOffset(totalSize: Long, positionMs: Long, durationMs: Long): Long {
        if (totalSize <= 0L) return 0L
        return if (durationMs > 0L) {
            ((positionMs.coerceAtLeast(0L) * totalSize) / durationMs).coerceIn(0L, totalSize - 1L)
        } else {
            min((positionMs.coerceAtLeast(0L) / 1000L) * 512_000L, totalSize - 1L)
        }
    }

    private fun adaptiveCacheAheadBytes(session: PlaybackSession): Long =
        when {
            session.consecutiveRangeStalls >= 2 -> MIN_CACHE_AHEAD_BYTES
            session.lastStallReason == PlaybackStallReason.None &&
                System.currentTimeMillis() - session.startedAtMs > 30_000L -> STABLE_CACHE_AHEAD_BYTES
            else -> NORMAL_CACHE_AHEAD_BYTES
        }

    private fun record(session: PlaybackSession) {
        runCatching {
            PlaybackDiagnostics.recordSession(
                sessionId = session.sessionId,
                activeFileId = session.fileId,
                currentOffset = session.currentOffsetBytes,
                retryCount = session.retryCount,
                stallReason = session.lastStallReason.name,
                cacheStopReason = session.lastStopReason.name
            )
        }
    }
}
