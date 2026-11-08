package com.vaydelux.bousygram.presentation.extras

/**
 * Remember Play only across a transient activity stop.
 * Buffering is not a pause: callers pass playWhenReady / native Play intent,
 * not isPlaying. This is separate from Telegram's playback lifecycle.
 *
 * A stopped player must never resume on an unrelated foreground event.
 */
internal class ExtrasForegroundPlaybackIntent {
    private var resumePending = false

    fun onBackground(playRequested: Boolean) {
        resumePending = playRequested
    }

    /** Return the one-shot intent and immediately consume it. */
    fun onForeground(): Boolean {
        val resume = resumePending
        resumePending = false
        return resume
    }

    fun cancel() {
        resumePending = false
    }
}
