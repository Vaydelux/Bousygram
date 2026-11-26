package com.vaydelux.bousygram.core.cast

/** Monotonic direct-playback liveness guard. An explicit pause is not a
 * stalled decoder. First-frame and rebuffer limits are independent of the
 * overall approved session duration. */
internal class RollingLanPlaybackProgress(private val startedMs: Long) {
    enum class Failure { CLOCK, STARTUP, STALL }
    private var lastTick = startedMs
    private var lastProgress = startedMs
    private var previousPosition = -1L
    private var rendered = false

    fun observe(nowMs: Long, positionMs: Long, firstFrame: Boolean,
                playRequested: Boolean, ended: Boolean): Failure? {
        if (nowMs < lastTick || nowMs < startedMs) return Failure.CLOCK
        lastTick = nowMs
        if (!rendered && firstFrame) {
            rendered = true
            lastProgress = nowMs
        }
        if (ended) return null
        if (!rendered) {
            if (!playRequested) {
                lastProgress = nowMs
                return null
            }
            return if (nowMs - lastProgress >= STARTUP_MS) Failure.STARTUP else null
        }
        if (!playRequested || (positionMs >= 0 && positionMs != previousPosition)) {
            lastProgress = nowMs
        }
        previousPosition = positionMs
        return if (playRequested && nowMs - lastProgress >= STALL_MS) Failure.STALL else null
    }

    companion object {
        const val STARTUP_MS = 30_000L
        const val STALL_MS = 45_000L
    }
}
