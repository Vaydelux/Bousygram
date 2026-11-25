package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.os.Looper
import android.view.Surface
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import java.io.IOException

/**
 * One real Media3 player for a short completed verified HLS window.
 * No live playback route, receiver Activity or LAN transport. All player
 * calls occur on the main looper; no store retirement occurs there.
 * Retirement is deliberately deferred until whole-window completion since
 * a generic HLS player may retry previously loaded media.
 */
@UnstableApi
internal class RollingHlsLocalReceiver(
    context: Context,
    private val session: RollingHlsDeliverySession,
    surface: Surface
) : AutoCloseable {
    private val segments = checkNotNull(session.completedSnapshot())
    private val receipt = RollingHlsPlaybackReceipt(session::active)
    private val player: ExoPlayer
    @Volatile private var closed = false
    @Volatile private var completed = false
    private var error: PlaybackException? = null

    init {
        main()
        require(segments.first().sequence == 1L && segments.first().startUs == 0L)
        val end = segments.last().let { it.startUs + it.durationUs }
        require(end in 1_000L..RollingHlsBudget.MAX_SEGMENT_DURATION_US)
        check(receipt.begin(1L, end))
        player = ExoPlayer.Builder(context).build()
        try {
            player.setAudioAttributes(AudioAttributes.DEFAULT, false)
            player.setVideoSurface(surface)
            player.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { receipt.renderedFirstFrame() }
                override fun onPlayerError(problem: PlaybackException) {
                    error = problem
                    receipt.revoke()
                }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo, reason: Int) {
                    if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                        reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) receipt.revoke()
                }
            })
            val item = MediaItem.Builder().setUri(RollingHlsLocalDataSource.playlistUri(session))
                .setMimeType(MimeTypes.APPLICATION_M3U8).build()
            player.setMediaSource(HlsMediaSource.Factory(RollingHlsLocalDataSource.factory(session))
                .createMediaSource(item))
            player.prepare()
        } catch (problem: Exception) {
            try { player.release() } finally { receipt.revoke() }
            throw problem
        }
    }

    data class State(val positionMs: Long, val playing: Boolean, val completed: Boolean)
    private fun main() { check(Looper.myLooper() == Looper.getMainLooper()) }

    fun play() {
        main()
        check(!closed && !completed && session.active())
        player.play()
    }
    fun pause() {
        main()
        check(!closed)
        player.pause()
    }

    fun poll(): State {
        main()
        check(!closed)
        if (!session.active()) {
            receipt.revoke()
            player.stop()
            throw IOException("Receiver session revoked")
        }
        error?.let { throw IOException("Receiver decoder failed", it) }
        val position = player.currentPosition
        val ended = player.playbackState == Player.STATE_ENDED
        if (!completed && receipt.observe(position, player.isPlaying, ended) != null) completed = true
        if (ended && !completed) throw IOException("Receiver ended without verified playback progression")
        return State(position, player.isPlaying, completed)
    }

    /** Must be called after release on main, then on the IO owner. No
     * acknowledgement for incomplete playback or account/route loss. */
    fun retireAfterRelease(): Boolean {
        check(Looper.myLooper() != Looper.getMainLooper())
        if (!closed || !completed || !session.active()) return false
        for (segment in segments.dropLast(1)) {
            if (!session.acknowledge(session.resourceId, session.controlToken, segment.sequence.toString()))
                return false
        }
        return session.active()
    }

    override fun close() {
        main()
        if (closed) return
        closed = true
        receipt.revoke()
        try { player.clearVideoSurface() } finally { player.release() }
    }
}
