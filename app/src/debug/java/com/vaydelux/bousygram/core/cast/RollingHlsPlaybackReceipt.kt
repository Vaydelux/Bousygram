package com.vaydelux.bousygram.core.cast

/**
 * One forward-only segment playback proof. All observations must come from
 * the same trusted local player on its application looper. Network receipt,
 * buffering, READY, position alone or an arbitrary seek never suffice.
 * This does not attest hardware output or provide a remote security proof.
 */
internal class RollingHlsPlaybackReceipt(private val permitted: () -> Boolean) {
    private var sequence = 0L
    private var startMs = 0L
    private var endMs = 0L
    private var firstPlayingMs: Long? = null
    private var lastPlayingMs: Long? = null
    private var started = false
    private var firstFrame = false
    private var maxPosition = 0L
    private var failed = false
    private var completed = false

    fun begin(sequence: Long, durationUs: Long, startUs: Long = 0L): Boolean {
        if (failed || started || completed || !permitted() || sequence <= 0 ||
            durationUs !in 1_000L..RollingHlsBudget.MAX_SEGMENT_DURATION_US ||
            startUs < 0L || startUs > Long.MAX_VALUE - durationUs) return false
        this.sequence = sequence
        startMs = startUs / 1000
        endMs = (startUs + durationUs) / 1000
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
        if (playing && firstFrame && positionMs >= startMs - 100 && positionMs <= endMs + 250) {
            if (firstPlayingMs == null) {
                if (positionMs > startMs + 500) {
                    failed = true
                    return null
                }
                firstPlayingMs = positionMs
            }
            lastPlayingMs = positionMs
        }
        // Require observed playback across the verified interval, not a jump
        // to STATE_ENDED. Keep the source timeline for standalone fragments.
        if (ended) {
            val began = firstPlayingMs
            val progressed = lastPlayingMs
            if (!firstFrame || began == null || progressed == null || progressed <= began ||
                progressed < endMs - 500 || maxPosition < endMs - 100 || maxPosition > endMs + 250) {
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
