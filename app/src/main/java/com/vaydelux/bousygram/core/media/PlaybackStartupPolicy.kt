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
