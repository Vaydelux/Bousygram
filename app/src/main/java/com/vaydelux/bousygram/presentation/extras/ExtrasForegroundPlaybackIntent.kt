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

/**
 * Audio focus is distinct from a user Pause. Preserve autoplay intent for a
 * temporary focus interruption only, then consume it once on GAIN.
 * A permanent loss, explicit Pause, or a backgrounded UI cannot resume.
 *
 * All calls are serialized on the main thread by ExtrasLibVlcSession.
 */
internal class ExtrasAudioFocusRecoveryIntent {
    private var resumePending = false

    val hasPendingResume: Boolean get() = resumePending

    fun onFocusLost(transient: Boolean, wasPlayRequested: Boolean) {
        resumePending = transient && (wasPlayRequested || resumePending)
    }

    fun onFocusGained(inForeground: Boolean): Boolean {
        val resume = resumePending && inForeground
        resumePending = false
        return resume
    }

    fun cancel() { resumePending = false }
}
