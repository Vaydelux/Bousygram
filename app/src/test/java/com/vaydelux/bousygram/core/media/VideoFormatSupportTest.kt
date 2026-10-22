package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatSupportTest {
    @Test
    fun recognizesCommonProgressiveVideoContainers() {
        listOf("clip.mp4", "clip.fmp4", "clip.m4v", "clip.webm", "clip.mkv", "clip.ts", "clip.mpg", "clip.mpeg", "clip.ogg")
            .forEach { fileName ->
                assertTrue("Expected video extension to be recognized: $fileName", VideoFormatSupport.isPlayableVideo("application/octet-stream", fileName))
            }
    }

    @Test
    fun doesNotClassifyPhotosAsVideos() {
        assertFalse(VideoFormatSupport.isPlayableVideo("image/jpeg", "photo.jpg"))
        assertFalse(VideoFormatSupport.isPlayableVideo("image/png", "image.png"))
    }

    @Test
    fun fragmentedMp4UsesMp4MimeType() {
        assertEquals("video/mp4", VideoFormatSupport.exoMimeType(null, "clip.fmp4", "Clip"))
        assertEquals("video/mp4", VideoFormatSupport.httpContentType(null, "clip.fmp4", "Clip"))
    }

    @Test
    fun hlsPlaylistUsesM3u8MimeType() {
        assertTrue(VideoFormatSupport.isPlayableVideo("application/vnd.apple.mpegurl", "live.m3u8"))
        assertTrue(VideoFormatSupport.isPlayableVideo("application/octet-stream", "live.m3u8"))
        assertEquals("application/vnd.apple.mpegurl", VideoFormatSupport.httpContentType(null, "live.m3u8", "Live"))
        assertEquals("application/x-mpegURL", VideoFormatSupport.exoMimeType(null, "live.m3u8", "Live"))
    }
}
