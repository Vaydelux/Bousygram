package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasEmbedRecoveryPolicyTest {
    @Test fun animeBlankScreenGetsVisibleNonBlockingSourceRecovery() {
        assertTrue(shouldOfferAnimeBlankPageHelp(
            isAnime = true, loadFailed = false, immersive = false,
            detectedVideo = false, longLoad = true
        ))
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
