package com.vaydelux.bousygram.core.cast

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaTrack
import com.vaydelux.mediaforge.core.CaptionCandidate
import com.vaydelux.mediaforge.core.CaptionFormat
import com.vaydelux.mediaforge.core.InspectedMedia
import com.vaydelux.mediaforge.core.MediaForgeCastPolicy
import com.vaydelux.mediaforge.core.StreamCandidate
import com.vaydelux.mediaforge.core.StreamFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtrasCastRequestInstrumentedTest {
    private val policy = MediaForgeCastPolicy(setOf("media.example.org"))
    private val caption = CaptionCandidate(
        "https://media.example.org/en.vtt", CaptionFormat.VTT, "media.example.org", "en", "English"
    )
    private val media = InspectedMedia(
        StreamCandidate("https://media.example.org/master.m3u8", StreamFormat.HLS, "media.example.org"),
        listOf(caption), 200
    )

    @Test fun mapsHlsAndSelectedCaptionWithoutLosingPauseIntent() {
        val plan = policy.plan(media, "Test", 4200, false, selectedCaption = caption)!!
        val request = ExtrasCastRequestAdapter.build(plan)
        val info = request.mediaInfo!!
        assertEquals(MediaInfo.STREAM_TYPE_BUFFERED, info.streamType)
        assertEquals("application/vnd.apple.mpegurl", info.contentType)
        assertEquals(media.stream.url, info.contentId)
        assertEquals(4200L, request.currentTime)
        assertEquals(false, request.autoplay)
        val track = info.mediaTracks!!.single()
        assertEquals(MediaTrack.TYPE_TEXT, track.type)
        assertEquals(MediaTrack.SUBTYPE_SUBTITLES, track.subtype)
        assertEquals(caption.url, track.contentId)
        assertEquals("text/vtt", track.contentType)
        assertArrayEquals(longArrayOf(track.id), request.activeTrackIds)
    }

    @Test fun liveHlsAndExplicitSubtitleOffAreRepresented() {
        val request = ExtrasCastRequestAdapter.build(policy.plan(media, "Live", live = true)!!)
        assertEquals(MediaInfo.STREAM_TYPE_LIVE, request.mediaInfo!!.streamType)
        assertEquals(true, request.autoplay)
        assertArrayEquals(longArrayOf(), request.activeTrackIds)
    }
}
