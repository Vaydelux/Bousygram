package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.players.MediaForgeRangeMetricsSnapshot

/** Read-only LibVLC state published on the main thread for Compose controls. */
internal data class ExtrasLibVlcControlState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seekable: Boolean = false,
    val playing: Boolean = false,
    val playRequested: Boolean = false,
    val failed: Boolean = false,
    val ended: Boolean = false,
    val timing: ExtrasVlcLatencySnapshot = ExtrasVlcLatencySnapshot(),
    val rangeMetrics: MediaForgeRangeMetricsSnapshot? = null
)

/** Normalize native seek requests, including remote-duration unknown cases. */
internal fun extrasVlcSeekAbsolute(positionMs: Long, durationMs: Long): Long {
    val position = positionMs.coerceAtLeast(0L)
    return if (durationMs > 0L) position.coerceAtMost(durationMs) else position
}

/** Saturating seek arithmetic: malformed native durations must never overflow. */
internal fun extrasVlcSeekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val position = positionMs.coerceAtLeast(0L)
    val requested = if (deltaMs >= 0L) {
        if (position > Long.MAX_VALUE - deltaMs) Long.MAX_VALUE else position + deltaMs
    } else if (deltaMs == Long.MIN_VALUE) {
        0L
    } else {
        (position - (-deltaMs)).coerceAtLeast(0L)
    }
    return extrasVlcSeekAbsolute(requested, durationMs)
}

internal fun extrasVlcProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

internal fun extrasVlcTimeLabel(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    val second = (seconds % 60).toString().padStart(2, '0')
    val minute = ((seconds / 60) % 60).toString().padStart(2, '0')
    val hours = seconds / 3600
    return if (hours > 0L) "$hours:$minute:$second" else "${seconds / 60}:$second"
}

/**
 * One-time position handoff after external subtitle changes re-create the FD
 * and native decoder. Never seek before native playback has actually started.
 * Give nonseekable streams a bounded chance; do not force infinite retries.
 */
internal class ExtrasVlcInitialSeek(
    positionMs: Long,
    private val maxPlayingChecks: Int = 32
) {
    private val initialPositionMs = positionMs.coerceAtLeast(0L)
    private var pending = initialPositionMs > 0L
    private var checks = 0

    fun nextTarget(playing: Boolean, seekable: Boolean, durationMs: Long): Long? {
        if (!pending || !playing) return null
        if (seekable) {
            pending = false
            return extrasVlcSeekAbsolute(initialPositionMs, durationMs)
        }
        checks++
        if (checks >= maxPlayingChecks) pending = false
        return null
    }

    val isPending: Boolean get() = pending
}

/**
 * Debug-only local latency estimator. These signals are not time-to-first-
 * rendered-frame or decoder seek recovery metrics: they measure LibVLC's
 * native Playing callback and reported position acknowledgement only.
 *
 * All methods are called on the player's main Handler thread.
 */
internal data class ExtrasVlcLatencySnapshot(
    val startupToNativePlayingMs: Long? = null,
    val lastSeekPositionMs: Long? = null,
    val seekSamples: Int = 0,
    val seekTimeouts: Int = 0,
    val seekRejectedCount: Int = 0,
    val seekSupersededCount: Int = 0,
    val seekNoChangeCount: Int = 0
)

internal class ExtrasVlcLatencyProbe {
    private var firstPlayStartedMs: Long? = null
    private var firstPlayingMs: Long? = null
    private var pendingSeekTarget: Long? = null
    private var pendingSeekStartedMs: Long? = null
    private var pendingSeekPreviousPositionMs: Long? = null
    private var lastSeekAckMs: Long? = null
    private var seekSamples = 0
    private var seekTimeouts = 0
    private var seekRejectedCount = 0
    private var seekSupersededCount = 0
    private var seekNoChangeCount = 0

    fun onPlayRequested(nowMs: Long) {
        if (firstPlayingMs == null) firstPlayStartedMs = nowMs.coerceAtLeast(0L)
    }

    fun onNativePlaying(nowMs: Long) {
        if (firstPlayingMs != null) return
        val started = firstPlayStartedMs ?: return
        if (nowMs >= started) {
            firstPlayingMs = nowMs - started
            firstPlayStartedMs = null
        }
    }

    /**
     * Only call this after setTime returned without throwing. A successful
     * native method call still does not prove a frame was decoded or rendered.
     * Previous position rejects stale near-target samples as seek evidence.
     */
    fun onSeekRequested(targetMs: Long, nowMs: Long, previousPositionMs: Long? = null) {
        val target = targetMs.coerceAtLeast(0L)
        val previous = previousPositionMs?.coerceAtLeast(0L)
        if (previous != null && previous == target) {
            seekNoChangeCount++
            return
        }
        if (pendingSeekTarget != null) seekSupersededCount++
        pendingSeekTarget = target
        pendingSeekStartedMs = nowMs.coerceAtLeast(0L)
        pendingSeekPreviousPositionMs = previous
    }

    /** Unseekable streams or a native setTime exception aren't successes. */
    fun onSeekRejected() {
        seekRejectedCount++
    }

    private fun clearPending() {
        pendingSeekTarget = null
        pendingSeekStartedMs = null
        pendingSeekPreviousPositionMs = null
    }

    fun observeNativePosition(positionMs: Long, nowMs: Long) {
        val target = pendingSeekTarget ?: return
        val started = pendingSeekStartedMs ?: return
        if (nowMs < started) return
        val duration = nowMs - started
        val actual = positionMs.coerceAtLeast(0L)
        val distance = if (actual >= target) actual - target else target - actual
        val prior = pendingSeekPreviousPositionMs
        val moved = if (prior == null) true else {
            val requestedChange = if (target >= prior) target - prior else prior - target
            val observedChange = if (actual >= prior) actual - prior else prior - actual
            observedChange >= minOf(500L, requestedChange)
        }
        // Expire BEFORE acknowledging to avoid crediting a late position sample
        // as a successful fast seek after the defined 15-second window.
        if (duration >= 15_000L) {
            seekTimeouts++
            clearPending()
        } else if (distance <= 1_500L && moved) {
            lastSeekAckMs = duration
            seekSamples++
            clearPending()
        }
    }

    fun snapshot() = ExtrasVlcLatencySnapshot(
        startupToNativePlayingMs = firstPlayingMs,
        lastSeekPositionMs = lastSeekAckMs,
        seekSamples = seekSamples,
        seekTimeouts = seekTimeouts,
        seekRejectedCount = seekRejectedCount,
        seekSupersededCount = seekSupersededCount,
        seekNoChangeCount = seekNoChangeCount
    )
}
