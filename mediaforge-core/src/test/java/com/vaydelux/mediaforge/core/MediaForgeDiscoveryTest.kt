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

    @Test fun hlsCaptionPlaylist() {
        val playlist = "#EXTM3U\n#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"subs\",URI=\"sub/en.vtt\""
        val results = MediaForgeHlsCaptions.parse(playlist, "https://media.example.org/master.m3u8")
        assertEquals("https://media.example.org/sub/en.vtt", results.single().url)
    }
}
