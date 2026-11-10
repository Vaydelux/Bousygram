package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.players.MediaForgeForegroundResumeGate
import com.vaydelux.mediaforge.players.MediaForgePlaybackTransition

/**
 * Remember Play only across a transient activity stop.
 * Buffering is not a pause: callers pass playWhenReady / native Play intent,
 * not isPlaying. This is separate from Telegram's playback lifecycle.
 *
 * A stopped player must never resume on an unrelated foreground event.
 */
internal class ExtrasForegroundPlaybackIntent {
    private val gate = MediaForgeForegroundResumeGate()

    fun onBackground(playRequested: Boolean) {
        gate.onBackground(playRequested)
    }

    /**
     * A newly mounted player can be prepared before its Activity reaches STARTED.
     * Queue its initial Play request exactly once for the first foreground event.
     * This is not a request to unpause a player that was explicitly paused.
     */
    fun onInitialAttach(playRequested: Boolean, inForeground: Boolean) {
        gate.onInitialAttach(playRequested, inForeground)
    }

    /** Return the one-shot intent and immediately consume it. */
    fun onForeground(): Boolean {
        return gate.onForeground()
    }

    fun cancel() {
        gate.cancel()
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

/**
 * Only an active, healthy playback request survives activity STOP/START.
 * A decoder error or natural EOF is final until the user explicitly retries
 * (or selects a different title/source); transient buffering is not a pause.
 */
internal fun shouldPreserveExtrasMedia3Resume(
    playWhenReady: Boolean,
    hasPlaybackError: Boolean,
    reachedEnd: Boolean
): Boolean = MediaForgePlaybackTransition.mayResume(
    playWhenReady, hasPlaybackError, reachedEnd
)
