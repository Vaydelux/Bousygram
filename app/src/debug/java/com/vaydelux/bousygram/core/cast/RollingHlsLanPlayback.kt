package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MediaSource
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
import androidx.media3.exoplayer.DefaultLoadControl
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
    private data class Snapshot(val playing: Boolean, val state: Int, val position: Long, val requested: Boolean)

    private fun <T> main(action: () -> T): T {
        val task = FutureTask(action)
        check(Handler(Looper.getMainLooper()).post(task))
        try { return task.get(5, TimeUnit.SECONDS) } finally { task.cancel(false) }
    }
    fun play(context: Context, client: RollingHlsLiveLanClient, permitted: () -> Boolean,
             attach: (ExoPlayer?) -> Unit, status: (String) -> Unit = {},
             localClip: Boolean = false): Boolean = playSource(context, permitted, attach, status, localClip) {
        val item = MediaItem.Builder().setUri(client.uri()).setMimeType(MimeTypes.APPLICATION_M3U8)
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(18_000)
                .setMinPlaybackSpeed(1f).setMaxPlaybackSpeed(1f).build()).build()
        HlsMediaSource.Factory(client.factory()).createMediaSource(item)
    }

    fun playDirect(context: Context, uri: Uri, factory: DataSource.Factory, permitted: () -> Boolean,
                   attach: (ExoPlayer?) -> Unit, status: (String) -> Unit): Boolean =
        playSource(context, permitted, attach, status, true, direct = true) {
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(uri))
        }

    private fun playSource(context: Context, permitted: () -> Boolean,
                           attach: (ExoPlayer?) -> Unit, status: (String) -> Unit,
                           localClip: Boolean, direct: Boolean = false, media: () -> MediaSource): Boolean {
        val first = AtomicBoolean()
        val failure = AtomicReference<PlaybackException?>(null)
        val holder = AtomicReference<ExoPlayer?>(null)
        try {
            val player = main {
                check(permitted())
                val builder = ExoPlayer.Builder(context)
                if (direct) builder.setLoadControl(DefaultLoadControl.Builder()
                    .setTargetBufferBytes(16 * 1024 * 1024)
                    .setPrioritizeTimeOverSizeThresholds(false).build())
                builder.build().also { engine ->
                    holder.set(engine)
                    engine.setAudioAttributes(AudioAttributes.DEFAULT, false)
                    engine.addListener(object : Player.Listener {
                        override fun onRenderedFirstFrame() { first.set(true) }
                        override fun onPlayerError(error: PlaybackException) { failure.set(error) }
                    })
                    attach(engine)
                    engine.setMediaSource(media())
                    engine.prepare()
                    engine.play()
                }
            }
            val deadline = Math.addExact(SystemClock.elapsedRealtime(),
                if (direct) RollingLanSessionLifetime.DIRECT_VIDEO.playbackMs else RollingLanSessionLifetime.QUALIFICATION.playbackMs)
            var playingMs = 0L
            var tick = SystemClock.elapsedRealtime()
            var previous = ""
            val liveness = if (direct) RollingLanPlaybackProgress(tick) else null
            while (permitted() && SystemClock.elapsedRealtime() < deadline) {
                failure.get()?.let { throw IOException("Live player failed", it) }
                val snapshot = main { Snapshot(player.isPlaying, player.playbackState,
                    player.currentPosition, player.playWhenReady) }
                val playing = snapshot.playing
                val state = snapshot.state
                val now = SystemClock.elapsedRealtime()
                liveness?.observe(now, snapshot.position, first.get(), snapshot.requested,
                    state == Player.STATE_ENDED)?.let { reason ->
                    throw IOException(when (reason) {
                        RollingLanPlaybackProgress.Failure.STARTUP -> "Selected video did not render within startup deadline"
                        RollingLanPlaybackProgress.Failure.STALL -> "Selected video stopped progressing"
                        RollingLanPlaybackProgress.Failure.CLOCK -> "Playback clock invalid"
                    })
                }
                if (playing && first.get()) playingMs += (now - tick).coerceIn(0, 250)
                tick = now
                if (state == Player.STATE_ENDED) return first.get() &&
                    (if (localClip) playingMs > 0 else playingMs >= 45_000) && permitted()
                val text = if (playing) (if (direct) "Playing selected video" else "Playing encrypted test stream") else if (state == Player.STATE_BUFFERING) "Buffering encrypted stream" else "Playback paused"
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
