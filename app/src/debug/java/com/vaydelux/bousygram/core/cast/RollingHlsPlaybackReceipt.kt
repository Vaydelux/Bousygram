package com.vaydelux.bousygram.core.cast

/**
 * One forward-only segment playback proof. All observations must come from
 * the same trusted local player on its application looper. Network receipt,
 * buffering, READY, position alone or an arbitrary seek never suffice.
 * This does not attest hardware output or provide a remote security proof.
 */
internal class RollingHlsPlaybackReceipt(private val permitted: () -> Boolean) {
    private var sequence = 0L
    private var durationMs = 0L
    private var started = false
    private var firstFrame = false
    private var maxPosition = 0L
    private var failed = false
    private var completed = false

    fun begin(sequence: Long, durationUs: Long): Boolean {
        if (failed || started || completed || !permitted() || sequence <= 0 ||
            durationUs !in 1_000L..RollingHlsBudget.MAX_SEGMENT_DURATION_US) return false
        this.sequence = sequence
        durationMs = durationUs / 1000
        started = true
        return true
    }

    fun renderedFirstFrame() { if (started && !failed && permitted()) firstFrame = true }

    fun observe(positionMs: Long, playing: Boolean, ended: Boolean, discontinuity: Boolean = false): Long? {
        if (!started || completed || failed) return null
        if (!permitted() || discontinuity || positionMs < 0 || positionMs < maxPosition - 100) {
            failed = true
            return null
        }
        if (playing || ended) maxPosition = maxOf(maxPosition, positionMs)
        // Container audio may extend slightly beyond video. The verified
        // duration is derived from video decode time, with bounded tolerance.
        if (ended) {
            if (!firstFrame || maxPosition < durationMs - 100 || maxPosition > durationMs + 250) {
                failed = true
                return null
            }
            completed = true
            return sequence
        }
        return null
    }

    fun revoke() { failed = true }
}
