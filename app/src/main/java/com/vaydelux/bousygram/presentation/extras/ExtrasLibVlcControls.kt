package com.vaydelux.bousygram.presentation.extras

/** Read-only LibVLC state published on the main thread for Compose controls. */
internal data class ExtrasLibVlcControlState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seekable: Boolean = false,
    val playing: Boolean = false,
    val playRequested: Boolean = false,
    val failed: Boolean = false,
    val ended: Boolean = false
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
