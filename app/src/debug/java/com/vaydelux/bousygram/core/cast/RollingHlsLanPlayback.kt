package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import java.io.IOException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Off-main qualification owner, main-thread player lifecycle. Playback
 * completion requires decoded first frame, sustained playing and actual ENDED.
 * User may pause/resume; prolonged pauses fail closed at the session deadline. */
@UnstableApi
internal object RollingHlsLanPlayback {
    private fun <T> main(action: () -> T): T {
        val task = FutureTask(action)
        check(Handler(Looper.getMainLooper()).post(task))
        try { return task.get(5, TimeUnit.SECONDS) } finally { task.cancel(false) }
    }
    fun play(context: Context, client: RollingHlsLiveLanClient, permitted: () -> Boolean,
             attach: (ExoPlayer?) -> Unit, status: (String) -> Unit = {},
             localClip: Boolean = false): Boolean {
        val first = AtomicBoolean()
        val failure = AtomicReference<PlaybackException?>(null)
        val holder = AtomicReference<ExoPlayer?>(null)
        try {
            val player = main {
                check(permitted())
                ExoPlayer.Builder(context).build().also { engine ->
                    holder.set(engine)
                    engine.setAudioAttributes(AudioAttributes.DEFAULT, false)
                    engine.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { first.set(true) }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error) }
                    })
                    attach(engine)
                    val item = MediaItem.Builder().setUri(client.uri()).setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(18_000)
                            .setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()).build()
                    engine.setMediaSource(HlsMediaSource.Factory(client.factory()).createMediaSource(item))
                    engine.prepare()
                    engine.play()
                }
            }
            val deadline = SystemClock.elapsedRealtime() + 120_000
            var playingMs = 0L
            var tick = SystemClock.elapsedRealtime()
            var previous = ""
            while (permitted() && SystemClock.elapsedRealtime() < deadline) {
                failure.get()?.let { throw IOException("Live player failed", it) }
                val (playing, state) = main { player.isPlaying to player.playbackState }
                val now = SystemClock.elapsedRealtime()
                if (playing && first.get()) playingMs += (now - tick).coerceIn(0, 250)
                tick = now
                if (state == Player.STATE_ENDED) return first.get() &&
                    (if (localClip) playingMs > 0 else playingMs >= 45_000) && permitted()
                val text = if (playing) "Playing encrypted test stream" else if (state == Player.STATE_BUFFERING) "Buffering encrypted stream" else "Playback paused"
                if (text != previous) { previous = text; status(text) }
                Thread.sleep(100)
            }
            throw IOException("Live playback expired or revoked")
        } finally {
            main {
                try { attach(null) } finally { holder.getAndSet(null)?.release() }
            }
        }
    }
}
