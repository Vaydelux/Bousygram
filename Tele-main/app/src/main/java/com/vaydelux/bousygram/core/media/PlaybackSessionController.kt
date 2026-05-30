package com.vaydelux.bousygram.core.media

import androidx.media3.common.PlaybackException
import com.vaydelux.bousygram.core.observability.PlaybackErrorMapper
import com.vaydelux.bousygram.core.observability.PlaybackRecoveryAction
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import java.util.concurrent.atomic.AtomicLong
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

class PlaybackSession internal constructor(
    val sessionId: String,
    val fileId: Int,
    val startedAtMs: Long,
    startPositionMs: Long
) {
    var currentPositionMs: Long = startPositionMs
        internal set
    var currentOffsetBytes: Long = 0L
        internal set
    var retryCount: Int = 0
        internal set
    var consecutiveRangeStalls: Int = 0
        internal set
    var lastStallReason: PlaybackStallReason = PlaybackStallReason.None
        internal set
    var lastStopReason: PlaybackCacheStopReason = PlaybackCacheStopReason.None
        internal set
}

object PlaybackSessionController {
    private const val STARTUP_CACHE_LEAD_MS = 12_000L
    private const val STABLE_CACHE_LEAD_MS = 30_000L
    private const val MIN_CACHE_AHEAD_BYTES = 8L * 1024L * 1024L
    private const val NORMAL_CACHE_AHEAD_BYTES = 32L * 1024L * 1024L
    private const val STABLE_CACHE_AHEAD_BYTES = 48L * 1024L * 1024L

    private val nextSessionNumber = AtomicLong(1L)

    fun start(fileId: Int, startPositionMs: Long = 0L): PlaybackSession {
        val session = PlaybackSession(
            sessionId = "pb-${System.currentTimeMillis()}-${nextSessionNumber.getAndIncrement()}",
            fileId = fileId,
            startedAtMs = System.currentTimeMillis(),
            startPositionMs = startPositionMs.coerceAtLeast(0L)
        )
        record(session)
        return session
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
        val leadMs = if (startup) STARTUP_CACHE_LEAD_MS else STABLE_CACHE_LEAD_MS
        val targetPosition = positionMs.coerceAtLeast(0L) + leadMs
        TelegramPreCacheEngine.cacheAhead(
            fileId = session.fileId,
            totalSize = totalSize,
            positionMs = targetPosition,
            durationMs = durationMs,
            maxBytes = adaptiveCacheAheadBytes(session)
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
        session.lastStopReason = reason
        session.lastStallReason = when (reason) {
            PlaybackCacheStopReason.Back -> PlaybackStallReason.UserExit
            PlaybackCacheStopReason.TrackSwitch -> PlaybackStallReason.TrackSwitch
            PlaybackCacheStopReason.LifecycleStop -> PlaybackStallReason.AppBackgroundStop
            PlaybackCacheStopReason.ErrorRecovery -> PlaybackStallReason.ErrorRecovery
            else -> session.lastStallReason
        }
        TelegramPreCacheEngine.stopCaching(session.fileId, cancelTdLibDownload)
        record(session)
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
