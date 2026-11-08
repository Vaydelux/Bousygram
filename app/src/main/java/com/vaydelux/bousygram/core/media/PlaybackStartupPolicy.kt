package com.vaydelux.bousygram.core.media

/**
 * A timeout is fatal only for the exact playback attempt that scheduled it.
 * An ExoPlayer -> libmpv fallback replaces the engine and its start timestamp;
 * a stale watchdog must not stop an already-playing replacement engine.
 */
internal fun shouldFailPlaybackStartup(
    sameEngine: Boolean,
    sameStartTimestamp: Boolean,
    hasInitialOutput: Boolean,
    hasPlaybackError: Boolean,
    startedAtMs: Long
): Boolean = sameEngine && sameStartTimestamp && startedAtMs > 0L &&
    !hasInitialOutput && !hasPlaybackError


/** A ready engine may still be stopped after async demux/cache handoff.
 * Only reassert Play when the user's intent is still armed; explicit Pause wins.
 */
internal fun shouldReassertPlaybackOnReady(
    ready: Boolean,
    playWhenReady: Boolean,
    isPlaying: Boolean
): Boolean = ready && playWhenReady && !isPlaying

/**
 * Elapsed time eligible for automatic startup fallback/timeout.
 *
 * Wall-clock delays while manually paused, backgrounded, casting, or
 * audio-focus suppressed do NOT make a source appear broken. Count an
 * interval only if playback was eligible at both sampling boundaries;
 * this also avoids adding hours of paused time to the first resumed tick.
 *
 * The caller must create one budget per engine/attempt and call tick()
 * against a monotonic clock such as SystemClock.elapsedRealtime().
 */
internal class PlaybackStartupActiveBudget(private val timeoutMs: Long) {
    init { require(timeoutMs > 0L) }
    private var lastSampleMs: Long? = null
    private var previouslyEligible = false
    private var countedMs = 0L

    fun tick(nowElapsedMs: Long, eligible: Boolean): Boolean {
        val last = lastSampleMs
        if (last != null && previouslyEligible && eligible && nowElapsedMs >= last) {
            val elapsed = nowElapsedMs - last
            countedMs = if (timeoutMs - countedMs <= elapsed) timeoutMs
                else countedMs + elapsed
        }
        lastSampleMs = nowElapsedMs
        previouslyEligible = eligible
        return countedMs >= timeoutMs
    }
}
