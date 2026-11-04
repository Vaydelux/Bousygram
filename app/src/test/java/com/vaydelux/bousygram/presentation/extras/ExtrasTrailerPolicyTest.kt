package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasTrailerPolicyTest {
    private val videoId = "dQw4w9WgXcQ"

    @Test fun androidTvOnlyUsesNativeYouTubeAndTouchDevicesUseEmbeddedHtml() {
        assertEquals(TrailerMode.NATIVE_YOUTUBE_TV, ExtrasTrailerPolicy.playbackMode(isAndroidTv = true))
        assertEquals(TrailerMode.INLINE_WEB, ExtrasTrailerPolicy.playbackMode(isAndroidTv = false))
        assertEquals("com.google.android.youtube.tv", ExtrasTrailerPolicy.YOUTUBE_TV_PACKAGE)
    }

    @Test fun nativeUriAndExternalFallbackAlwaysPreserveExactVideoId() {
        assertEquals("vnd.youtube:$videoId", ExtrasTrailerPolicy.tvVideoUri(videoId))
        assertEquals("https://www.youtube.com/watch?v=$videoId", ExtrasTrailerPolicy.watchUrl(videoId))
        assertNull(ExtrasTrailerPolicy.tvVideoUri("https://youtube.com/watch?v=$videoId"))
        assertNull(ExtrasTrailerPolicy.watchUrl("javascript:alert(1)"))
        assertNull(ExtrasTrailerPolicy.watchUrl("short"))
    }

    @Test fun embeddedIframeUsesOfficialYoutubeAndAppIdentityReferer() {
        assertEquals("https://com.vaydelux.bousygram/",
            ExtrasTrailerPolicy.embedBaseUrl("com.vaydelux.bousygram"))
        val html = ExtrasTrailerPolicy.embedHtml(videoId)
        assertTrue(html.contains("https://www.youtube.com/embed/$videoId?playsinline=1"))
        assertTrue(html.contains("referrerpolicy=\"strict-origin-when-cross-origin\""))
        assertTrue(html.contains("allowfullscreen"))
        assertFalse(html.contains("autoplay=1"))
        assertFalse(html.contains("http://"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun embedRejectsUntrustedHtmlOrVideoUrls() {
        ExtrasTrailerPolicy.embedHtml("javascript:alert(1)")
    }
}
