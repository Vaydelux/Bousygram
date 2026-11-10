package com.vaydelux.mediaforge.players

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Engine-independent controls owned by the Android host, never by Telegram.
 * All calls, including release, must be serialized on Android's main thread
 * by native-player implementations. release() is terminal and idempotent.
 */
interface MediaForgePlaybackControls : AutoCloseable {
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
    override fun close() = release()
}

/**
 * Immutable snapshot at an engine transition or subtitle-driven rebuild.
 *
 * playRequested is the user's intent, not isPlaying (false while buffering).
 * pendingHandoffPositionMs preserves a trusted incoming position until the
 * native player acknowledges it. A manual accepted seek supersedes that value
 * in the engine-specific implementation.
 */
data class MediaForgePlaybackSnapshot(
    val positionMs: Long,
    val seekable: Boolean,
    val playRequested: Boolean,
    val pendingHandoffPositionMs: Long? = null,
    val failed: Boolean = false,
    val ended: Boolean = false
)

data class MediaForgePlaybackHandoff(
    val positionMs: Long,
    val playRequested: Boolean
)

/**
 * One policy for Media3 -> LibVLC, LibVLC -> Media3 and subtitle restarts.
 * Never resurrect a failed/ended decoder or turn manual Pause into autoplay.
 */
object MediaForgePlaybackTransition {
    fun mayResume(playRequested: Boolean, failed: Boolean, ended: Boolean): Boolean =
        playRequested && !failed && !ended

    fun handoff(snapshot: MediaForgePlaybackSnapshot): MediaForgePlaybackHandoff {
        val trustedPending = if (!snapshot.ended) snapshot.pendingHandoffPositionMs else null
        val position = trustedPending?.coerceAtLeast(0L) ?:
            if (snapshot.seekable) snapshot.positionMs.coerceAtLeast(0L) else 0L
        return MediaForgePlaybackHandoff(
            positionMs = position,
            playRequested = mayResume(
                snapshot.playRequested, snapshot.failed, snapshot.ended
            )
        )
    }
}

/** One-shot foreground resume. A background stop alone cannot force Play. */
class MediaForgeForegroundResumeGate {
    private var pending = false

    fun onInitialAttach(playRequested: Boolean, inForeground: Boolean) {
        if (!inForeground) onBackground(playRequested)
    }

    fun onBackground(playRequested: Boolean) {
        pending = playRequested
    }

    fun onForeground(): Boolean {
        val restore = pending
        pending = false
        return restore
    }

    fun cancel() { pending = false }
}

/**
 * Thread-safe release lease for Media3 and other session-owned player handles.
 * This only prevents duplicate release; it does not create or own a player.
 */
internal class MediaForgeReleaseLease {
    private val released = AtomicBoolean(false)
    fun isOpen() = !released.get()
    fun tryRelease() = released.compareAndSet(false, true)
}
