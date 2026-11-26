package com.vaydelux.bousygram.core.cast

import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaTrack
import com.vaydelux.mediaforge.core.MediaForgeCastPlan

/**
 * SDK request construction only. Does not discover receivers, connect,
 * load media, stop another session or bypass the private Telegram gate.
 * An isolated Extras sender will own actual commands and acknowledgement.
 */
internal object ExtrasCastRequestAdapter {
    fun build(plan: MediaForgeCastPlan): MediaLoadRequestData {
        val tracks = plan.captions.map { caption ->
            MediaTrack.Builder(caption.id, MediaTrack.TYPE_TEXT)
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(caption.url)
                .setContentType(caption.mimeType)
                .setName(caption.label)
                .setLanguage(caption.language ?: "und")
                .build()
        }
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, plan.title)
        }
        val info = MediaInfo.Builder(plan.url)
            .setContentType(plan.mimeType)
            .setStreamType(if (plan.live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .setMediaTracks(tracks)
            .build()
        return MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(plan.playRequested)
            .apply {
                // A live stream uses the receiver's default live position.
                plan.positionMs?.let { setCurrentTime(it) }
                if (tracks.isNotEmpty()) {
                    setActiveTrackIds(plan.selectedTrackId?.let { longArrayOf(it) } ?: longArrayOf())
                }
            }
            .build()
    }
}
