package com.vaydelux.mediaforge.core

import org.junit.Assert.*
import org.junit.Test

class MediaForgeDiscoveryTest {
    @Test fun classificationAndBounds() {
        val registry = MediaForgeDiscovery(maxStreams = 2)
        assertTrue(registry.observe("https://video.example.org/a/master.m3u8?token=secret"))
        assertFalse(registry.observe("https://video.example.org/a/master.m3u8?token=secret"))
        assertTrue(registry.observe("https://video.example.org/subtitles/en.srt"))
        assertTrue(registry.observe("https://video.example.org/one.mp4"))
        assertTrue(registry.observe("https://video.example.org/two.mpd"))
        assertEquals(2, registry.snapshot().streams.size)
        assertEquals(1, registry.snapshot().captions.size)
        registry.clear()
        assertTrue(registry.snapshot().streams.isEmpty())
    }

    @Test fun unsafeUrlsRejected() {
        assertNull(MediaForgeClassifier.stream("http://example.org/master.m3u8"))
        assertNull(MediaForgeClassifier.stream("https://127.0.0.1/secret.mp4"))
        assertNull(MediaForgeClassifier.stream("https://localhost/secret.mp4"))
        assertNull(MediaForgeClassifier.stream("https://example.org@localhost/file.mp4"))
    }

    @Test fun mediaQueryStringsAndCaseInsensitiveExtensions() {
        val manifest = MediaForgeClassifier.stream("https://cdn.example.org/MOVIE.M3U8?token=123")
        assertEquals(StreamFormat.HLS, manifest?.format)
        val caption = MediaForgeClassifier.caption("https://cdn.example.org/english.VTT?key=456")
        assertEquals(CaptionFormat.VTT, caption?.format)
        assertNull(MediaForgeClassifier.stream("https://cdn.example.org/fake.m3u8.html"))
    }

    @Test fun maxCaptionsDoNotGrowUnbounded() {
        val collector = MediaForgeDiscovery(maxCaptions = 2)
        for (i in 1..5) assertTrue(collector.observe("https://cdn.example.org/$i.srt"))
        assertEquals(2, collector.snapshot().captions.size)
        assertFalse(collector.observe("https://cdn.example.org/5.srt"))
    }

    @Test fun hlsCaptionsRetainTrackMetadata() {
        val playlist = "#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",NAME=\"English\",LANGUAGE=\"en\",URI=\"sub/en.vtt\""
        val result = MediaForgeHlsCaptions.parse(playlist, "https://media.example.org/master.m3u8").single()
        assertEquals(CaptionFormat.VTT, result.format)
        assertEquals("English", result.label)
        assertEquals("en", result.language)
        assertEquals("https://media.example.org/sub/en.vtt", result.url)
    }

    @Test fun hlsCaptionPlaylist() {
        val playlist = "#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",URI=\"sub/en.vtt\""
        val results = MediaForgeHlsCaptions.parse(playlist, "https://media.example.org/master.m3u8")
        assertEquals("https://media.example.org/sub/en.vtt", results.single().url)
    }
}
