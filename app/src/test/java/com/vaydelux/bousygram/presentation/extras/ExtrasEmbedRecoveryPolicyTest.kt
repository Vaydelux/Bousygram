package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.webbridge.MediaForgeWebIssue
import com.vaydelux.mediaforge.webbridge.MediaForgeWebIssuePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasEmbedRecoveryPolicyTest {
    @Test fun webIssueClassifierRecognizesFrameAutoplayAndCodecFailures() {
        assertEquals(MediaForgeWebIssue.IFRAME_POLICY,
            MediaForgeWebIssuePolicy.classifyConsoleError(
                "Refused to frame 'https://hidden.example/?key=secret' because of frame-ancestors"
            ))
        assertEquals(MediaForgeWebIssue.AUTOPLAY_DENIED,
            MediaForgeWebIssuePolicy.classifyConsoleError(
                "NotAllowedError: play() failed because the user didn't interact with the document"
            ))
        assertEquals(MediaForgeWebIssue.VIDEO_UNSUPPORTED,
            MediaForgeWebIssuePolicy.classifyConsoleError(
                "NotSupportedError: no supported source was found"
            ))
        assertNull(MediaForgeWebIssuePolicy.classifyConsoleError(
            "Advertisement request failed at https://provider.example/private?token=hidden"
        ))
    }

    @Test fun playerFailuresTakePriorityOverNoisyAdAndNetworkErrors() {
        var current: MediaForgeWebIssue? = null
        current = preferredAnimeWebIssue(current, MediaForgeWebIssue.SUBRESOURCE_NETWORK)
        current = preferredAnimeWebIssue(current, MediaForgeWebIssue.IFRAME_POLICY)
        current = preferredAnimeWebIssue(current, MediaForgeWebIssue.SUBRESOURCE_HTTP)
        assertEquals(MediaForgeWebIssue.IFRAME_POLICY, current)
    }

    @Test fun recoveryAdviceNeverExposesUntrustedConsoleUrlsOrSecrets() {
        val hidden = "secret"
        for (issue in MediaForgeWebIssue.entries) {
            val advice = animeWebIssueRecoveryMessage(issue)
            assertFalse(advice.contains(hidden))
            assertFalse(advice.contains("https://"))
            assertTrue(advice.isNotBlank())
        }
        assertTrue(animeWebIssueRecoveryMessage(null).contains("compatibility mode"))
        assertTrue(animeWebIssueRecoveryMessage(MediaForgeWebIssue.IFRAME_POLICY).contains("browser"))
    }

    @Test fun animeBlankScreenGetsVisibleNonBlockingSourceRecovery() {
        assertTrue(shouldOfferAnimeBlankPageHelp(
            isAnime = true, loadFailed = false, immersive = false,
            detectedVideo = false, longLoad = true
        ))
    }

    @Test fun allExtrasVideoKindsOfferNonBlockingWebRecovery() {
        assertTrue(shouldOfferExtrasBlankPageHelp(
            loadFailed = false, immersive = false, detectedVideo = false, longLoad = true
        ))
        assertFalse(shouldOfferExtrasBlankPageHelp(
            loadFailed = false, immersive = false, detectedVideo = true, longLoad = true
        ))
        assertFalse(shouldOfferExtrasBlankPageHelp(
            loadFailed = true, immersive = false, detectedVideo = false, longLoad = true
        ))
        assertFalse(shouldOfferExtrasBlankPageHelp(
            loadFailed = false, immersive = true, detectedVideo = false, longLoad = true
        ))
        assertFalse("Do not interrupt a loading player before the recovery delay",
            shouldOfferExtrasBlankPageHelp(
                loadFailed = false, immersive = false, detectedVideo = false, longLoad = false
            ))
    }

    @Test fun blockedMainFrameNavigationExplainsWebPlayerWithoutLeakingUrls() {
        val issue = MediaForgeWebIssue.NAVIGATION_BLOCKED
        val advice = animeWebIssueRecoveryMessage(issue)
        assertTrue(advice.contains("blocked"))
        assertTrue(advice.contains("retry the selected page"))
        assertTrue(advice.contains("choose another server"))
        assertTrue(advice.contains("Playback tools"))
        assertFalse("Do not present legacy routing as the default recovery", advice.contains("try the optional legacy mode"))
        assertFalse(advice.contains("https://"))
        assertEquals(issue, preferredAnimeWebIssue(MediaForgeWebIssue.SUBRESOURCE_NETWORK, issue))
        assertEquals(MediaForgeWebIssue.IFRAME_POLICY,
            preferredAnimeWebIssue(MediaForgeWebIssue.IFRAME_POLICY, issue))
    }

    @Test fun neverInterruptWorkingVideoOrMoviesAndTv() {
        for (isAnime in listOf(false, true)) {
            assertFalse(shouldOfferAnimeBlankPageHelp(
                isAnime, loadFailed = false, immersive = false,
                detectedVideo = true, longLoad = true
            ))
        }
        assertFalse(shouldOfferAnimeBlankPageHelp(
            isAnime = false, loadFailed = false, immersive = false,
            detectedVideo = false, longLoad = true
        ))
        assertFalse(shouldOfferAnimeBlankPageHelp(
            isAnime = true, loadFailed = true, immersive = false,
            detectedVideo = false, longLoad = true
        ))
        assertFalse(shouldOfferAnimeBlankPageHelp(
            isAnime = true, loadFailed = false, immersive = true,
            detectedVideo = false, longLoad = true
        ))
        assertFalse(shouldOfferAnimeBlankPageHelp(
            isAnime = true, loadFailed = false, immersive = false,
            detectedVideo = false, longLoad = false
        ))
    }
}
