package com.vaydelux.bousygram.presentation.media

/**
 * A requested seek is not a successful seek. Confirm only after the decoder
 * reports the target and playback is no longer buffering. A timer that expires
 * during buffering is a failure, even if a native clock already jumped ahead.
 */
internal enum class SeekCompletion {
    Waiting,
    Confirmed,
    TimedOut
}

internal fun seekCompletion(
    isBuffering: Boolean,
    observedNearTarget: Boolean,
    elapsedMs: Long,
    timeoutMs: Long = 30_000L
): SeekCompletion = when {
    !isBuffering && observedNearTarget -> SeekCompletion.Confirmed
    elapsedMs >= timeoutMs -> SeekCompletion.TimedOut
    else -> SeekCompletion.Waiting
}


/**
 * A delayed decoder/transport retry must not resurrect a stopped player or
 * override a manual Pause, a new queue item, Cast, or audio-focus loss.
 * This applies to MKV/TS fallback as well as other media engines.
 */
internal fun shouldRunDelayedPlaybackRetry(
    sameQueueItem: Boolean,
    sameReload: Boolean,
    sessionActive: Boolean,
    inForeground: Boolean,
    explicitlyPaused: Boolean,
    castConnected: Boolean,
    audioFocusSuppressed: Boolean
): Boolean =
    sameQueueItem && sameReload && sessionActive && inForeground &&
        !explicitlyPaused && !castConnected && !audioFocusSuppressed
