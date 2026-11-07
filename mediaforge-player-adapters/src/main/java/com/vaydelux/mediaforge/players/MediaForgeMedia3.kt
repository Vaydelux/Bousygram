package com.vaydelux.mediaforge.players

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.CaptionFormat
import com.vaydelux.mediaforge.core.InspectedMedia
import com.vaydelux.mediaforge.core.StreamFormat

/**
 * Isolated adapter: never imports TDLib, Bousygram PlaybackCore, or Telegram sessions.
 * Hosting app owns the ExoPlayer lifecycle and audio focus.
 */
object MediaForgeMedia3 {
    fun newPlayer(context: Context): ExoPlayer = ExoPlayer.Builder(context).build()

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
                .setMimeType(format).setLanguage("und").setLabel(it.host).build())
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
