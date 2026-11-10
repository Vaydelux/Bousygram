package com.vaydelux.mediaforge.players

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import com.vaydelux.mediaforge.core.MediaForgePlaybackTransport
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.CaptionFormat
import com.vaydelux.mediaforge.core.InspectedMedia
import com.vaydelux.mediaforge.core.StreamFormat

/**
 * Isolated adapter: never imports TDLib, Bousygram PlaybackCore, or Telegram sessions.
 * Hosting app owns the ExoPlayer lifecycle and audio focus.
 */
object MediaForgeMedia3 {
    @OptIn(UnstableApi::class)
    fun newPlayer(context: Context): ExoPlayer {
        // This guarded source factory is used by every nested manifest/segment
        // and external subtitle fetch. No Telegram custom data sources are used.
        val sourceFactory = DefaultMediaSourceFactory(
            OkHttpDataSource.Factory(MediaForgePlaybackTransport.client)
        )
        return ExoPlayer.Builder(context.applicationContext)
        .setMediaSourceFactory(sourceFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
    }

    fun mediaItem(media: InspectedMedia, selectedCaption: CaptionCandidate?): MediaItem {
        val mime = when (media.stream.format) {
            StreamFormat.HLS -> MimeTypes.APPLICATION_M3U8
            StreamFormat.DASH -> MimeTypes.APPLICATION_MPD
            StreamFormat.MP4 -> MimeTypes.VIDEO_MP4
            StreamFormat.WEBM -> MimeTypes.VIDEO_WEBM
            StreamFormat.MPEG_TS -> MimeTypes.VIDEO_MP2T
        }
        val builder = MediaItem.Builder().setUri(media.stream.url).setMimeType(mime)
        val captions = selectedCaption?.let {
            val format = when (it.format) {
                CaptionFormat.SRT -> MimeTypes.APPLICATION_SUBRIP
                CaptionFormat.VTT -> MimeTypes.TEXT_VTT
                CaptionFormat.TTML -> MimeTypes.APPLICATION_TTML
            }
            listOf(MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                .setMimeType(format).setLanguage(it.language ?: "und")
                .setLabel(it.label ?: it.host).build())
        }.orEmpty()
        builder.setSubtitleConfigurations(captions)
        return builder.build()
    }
}

/** Host apps may register a LibVLC/libmpv adapter without pulling their native binaries into the SDK. */
interface MediaForgeNativeAdapter {
    val engineName: String
    fun play(media: InspectedMedia, selectedCaption: CaptionCandidate?)
    fun pause()
    fun release()
}

/**
 * Real Media3 implementation of the shared control contract.
 * The host retains the surface/listener lifecycle; this wrapper ensures
 * released players cannot receive late Play/Pause/Seek callbacks.
 */
class MediaForgeMedia3Controls(
    private val player: ExoPlayer
) : MediaForgePlaybackControls {
    private val releaseLease = MediaForgeReleaseLease()

    override fun play() {
        if (releaseLease.isOpen()) player.play()
    }

    override fun pause() {
        if (releaseLease.isOpen()) player.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (releaseLease.isOpen()) player.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun release() {
        if (!releaseLease.tryRelease()) return
        try {
            player.stop()
        } finally {
            player.release()
        }
    }
}
