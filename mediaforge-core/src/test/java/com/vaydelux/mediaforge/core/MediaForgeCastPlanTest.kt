package com.vaydelux.mediaforge.core

import org.junit.Assert.*
import org.junit.Test

class MediaForgeCastPlanTest {
    private val policy = MediaForgeCastPolicy(setOf("media.example.org", "captions.example.org"))
    private fun media(url: String = "https://media.example.org/main.m3u8",
                      format: StreamFormat = StreamFormat.HLS,
                      captions: List<CaptionCandidate> = emptyList()) =
        InspectedMedia(StreamCandidate(url, format, "media.example.org"), captions, 200)

    @Test fun mapsReceiverFormatsAndRetainsPlayIntent() {
        val hls = policy.plan(media(), "Video", positionMs = 4000, playRequested = false)!!
        assertEquals("application/vnd.apple.mpegurl", hls.mimeType)
        assertEquals(4000L, hls.positionMs)
        assertFalse(hls.playRequested)
        val live = policy.plan(media(), "Live", live = true)!!
        assertTrue(live.live)
        assertNull(live.positionMs)
        assertNull(policy.plan(media(), "Live", positionMs = 1, live = true))
        assertEquals("video/mp4", policy.plan(media("https://media.example.org/a.mp4", StreamFormat.MP4), "MP4")!!.mimeType)
        assertEquals("application/dash+xml", policy.plan(media("https://media.example.org/a.mpd", StreamFormat.DASH), "DASH")!!.mimeType)
        assertNull(policy.plan(media(format = StreamFormat.MPEG_TS), "TS"))
    }

    @Test fun rejectsUnqualifiedOrCredentialBearingUrls() {
        for (url in listOf("http://media.example.org/a.m3u8",
            "https://media.example.org.evil.org/a.m3u8",
            "https://media.example.org/a.m3u8?token=secret",
            "https://media.example.org/a.m3u8#fragment",
            "https://user@media.example.org/a.m3u8",
            "https://192.168.0.2/a.m3u8")) {
            assertNull(policy.plan(media(url), "Video"))
        }
        assertNull(MediaForgeCastPolicy(emptySet()).plan(media(), "Video"))
    }

    @Test fun captionSelectionCannotInventTracksOrMislabelSrt() {
        val vtt = CaptionCandidate("https://captions.example.org/en.vtt", CaptionFormat.VTT, "captions.example.org", "en", "English")
        val srt = vtt.copy(url = "https://captions.example.org/en.srt", format = CaptionFormat.SRT)
        val foreign = vtt.copy(url = "https://unqualified.example.org/en.vtt")
        val approved = media(captions = listOf(vtt, vtt, srt, foreign))
        val plan = policy.plan(approved, "Video", selectedCaption = vtt)!!
        assertEquals(1, plan.captions.size)
        assertEquals(1L, plan.selectedTrackId)
        assertEquals("text/vtt", plan.captions.single().mimeType)
        assertNull(policy.plan(approved, "Video", selectedCaption = srt))
        assertNull(policy.plan(approved, "Video", selectedCaption = foreign))
        assertNull(policy.plan(approved, "Video", selectedCaption = vtt.copy(label = "Invented")))
        assertNull(policy.plan(approved, "Video")!!.selectedTrackId)
    }

    @Test fun boundedSrtConversionAndInvalidInput() {
        val input = "\uFEFF1\r\n00:00:05,000 --> 00:00:08,123\r\nHello & <world>\r\n\r\n"
        assertEquals("WEBVTT\n\n00:00:05.000 --> 00:00:08.123\nHello &amp; &lt;world&gt;\n\n",
            SrtWebVtt.convert(input.toByteArray())!!.toString(Charsets.UTF_8))
        for (invalid in listOf("", "1\n00:00:08,000 --> 00:00:05,000\nWrong",
            "1\n00:99:00,000 --> 01:00:00,000\nWrong", "1\n00:00:00,000 --> 00:00:01,000\n",
            "1\n00:00:00,000 --> 00:00:01,000\n-->")) {
            assertNull(SrtWebVtt.convert(invalid.toByteArray()))
        }
        assertNull(SrtWebVtt.convert(byteArrayOf(0xc3.toByte(), 0x28)))
        assertNull(SrtWebVtt.convert(ByteArray(SrtWebVtt.MAX_INPUT_BYTES + 1)))
    }
}
