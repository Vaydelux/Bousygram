package com.vaydelux.bousygram.core.media

import android.content.Context
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.util.Log
import androidx.media3.session.MediaSession as Media3Session

/**
 * Universal MediaSession bridge providing OS-level media controls, Android TV remote keys,
 * HDMI-CEC, and Google Assistant voice command compatibility across both ExoPlayer and libmpv engines.
 */
class UniversalMediaSessionBridge(
    context: Context,
    private val playerEngine: PlayerEngine,
    private val onSeekIncrement: (Long) -> Unit,
    private val onSkipNext: () -> Unit,
    private val onSkipPrevious: () -> Unit
) {
    private var media3Session: Media3Session? = null
    private var platformSession: android.media.session.MediaSession? = null

    init {
        val engine = playerEngine
        when (engine) {
            is ExoPlayerEngine -> {
                runCatching {
                    media3Session = Media3Session.Builder(context, engine.player).build()
                }.onFailure { e ->
                    Log.w("MediaSessionBridge", "Failed to initialize Media3 session: ${e.message}")
                }
            }
            is MpvPlayerEngine -> {
                runCatching {
                    platformSession = android.media.session.MediaSession(context, "BousygramMpvMediaSession").apply {
                        setCallback(object : android.media.session.MediaSession.Callback() {
                            override fun onPlay() { playerEngine.play() }
                            override fun onPause() { playerEngine.pause() }
                            override fun onSeekTo(pos: Long) { playerEngine.seekTo(pos) }
                            override fun onFastForward() { onSeekIncrement(10_000L) }
                            override fun onRewind() { onSeekIncrement(-10_000L) }
                            override fun onSkipToNext() { onSkipNext() }
                            override fun onSkipToPrevious() { onSkipPrevious() }
                        })
                        isActive = true
                    }
                }.onFailure { e ->
                    Log.w("MediaSessionBridge", "Failed to initialize platform MediaSession: ${e.message}")
                }
            }
        }
    }

    fun updateState(isPlaying: Boolean, positionMs: Long, speed: Float, title: String, durationMs: Long) {
        val session = platformSession ?: return
        runCatching {
            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val actions = PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_FAST_FORWARD or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS

            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, positionMs, speed)
                    .build()
            )

            val metadata = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .build()
            session.setMetadata(metadata)
        }
    }

    fun release() {
        runCatching { media3Session?.release() }
        media3Session = null
        runCatching {
            platformSession?.isActive = false
            platformSession?.release()
        }
        platformSession = null
    }
}
