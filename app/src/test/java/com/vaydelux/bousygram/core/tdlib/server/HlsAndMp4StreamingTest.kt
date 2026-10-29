package com.vaydelux.bousygram.core.tdlib.server

import androidx.media3.common.MimeTypes
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.media.TelegramProxyPlayerFactory
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.domain.model.MediaAlternativeVideo
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import com.vaydelux.bousygram.presentation.media.buildPlaybackQueue
import com.vaydelux.bousygram.presentation.media.buildPlaybackRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsAndMp4StreamingTest {

    @Test
    fun progressiveStreamUrlIsAuthorizedForItsFile() {
        // Private loopback URLs must only be minted for a verified Telegram account.
        AccountSession.invalidate()
        checkNotNull(AccountSession.activate(101L, AccountSession.generation()))
        try {
            val url = TelegramProxyManager.baseStreamUrl(101)
            assertTrue(url.contains("/stream?fileId=101&token="))
            val token = url.substringAfter("token=").substringBefore('&')
            assertTrue(StreamAccessRegistry.authorizesFile(token, 101))
            assertTrue(!StreamAccessRegistry.authorizesFile(token, 102))
        } finally {
            AccountSession.invalidate()
            StreamAccessRegistry.revokeAll()
        }
    }

    @Test
    fun testExtendedVideoExtensionsSupport() {
        assertTrue(VideoFormatSupport.hasVideoExtension("video.mp4"))
        assertTrue(VideoFormatSupport.hasVideoExtension("clip.ts"))
        assertTrue(VideoFormatSupport.hasVideoExtension("segment.m4s"))
        assertTrue(VideoFormatSupport.hasVideoExtension("segment.cmaf"))
        assertTrue(VideoFormatSupport.hasVideoExtension("stream.m3u8"))
        assertTrue(VideoFormatSupport.hasVideoExtension("movie.mkv"))

        assertEquals("video/mp4", VideoFormatSupport.httpContentType(null, "segment.m4s", "segment"))
        assertEquals("video/mp4", VideoFormatSupport.httpContentType(null, "segment.cmaf", "segment"))
        assertEquals(MimeTypes.VIDEO_MP4, VideoFormatSupport.exoMimeType(null, "video.mp4", "video"))
        assertEquals(MimeTypes.VIDEO_MP2T, VideoFormatSupport.exoMimeType(null, "clip.ts", "clip"))
        assertEquals(MimeTypes.APPLICATION_M3U8, VideoFormatSupport.exoMimeType(null, "stream.m3u8", "stream"))
    }

    @Test
    fun testCustomExtractorsFactoryInitialization() {
        val factory = TelegramProxyPlayerFactory.customExtractorsFactory()
        assertNotNull(factory)
    }

    @Test
    fun testAlternativeVideosPropagationInPlaybackQueue() {
        val alt = listOf(
            MediaAlternativeVideo(id = 1L, width = 1920, height = 1080, codec = "h264", fileId = 201),
            MediaAlternativeVideo(id = 2L, width = 1280, height = 720, codec = "h264", fileId = 202)
        )
        val item = MediaItem(
            chatId = 100L,
            messageId = 1L,
            date = 1700000000,
            type = MediaType.Video,
            title = "Test Video",
            fileId = 200,
            durationSeconds = 120,
            fileSizeBytes = 50000000L,
            fileName = "movie.mp4",
            mimeType = "video/mp4",
            alternativeVideos = alt
        )

        val queue = buildPlaybackQueue(listOf(item))
        assertEquals(1, queue.size)
        assertEquals(2, queue.first().alternativeVideos.size)
        assertEquals(1080, queue.first().alternativeVideos[0].height)

        val request = buildPlaybackRequest(item, "http://127.0.0.1:8080/stream?fileId=200", listOf(item))
        assertEquals(2, request.alternativeVideos.size)
        assertEquals(201, request.alternativeVideos[0].fileId)
    }
}
