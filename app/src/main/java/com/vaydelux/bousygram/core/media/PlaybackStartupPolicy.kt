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
