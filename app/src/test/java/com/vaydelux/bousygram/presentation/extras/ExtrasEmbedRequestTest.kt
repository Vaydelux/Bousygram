package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtrasEmbedRequestTest {
    @Test fun movieUsesTmdbId() {
        assertEquals(
            "https://cinesrc.st/embed/movie/1084242",
            ExtrasEmbedRequest.movie(1084242, "Movie").url
        )
    }

    @Test fun tvUsesSeasonAndEpisode() {
        assertEquals(
            "https://cinesrc.st/embed/tv/12345?s=0&e=3",
            ExtrasEmbedRequest.episode(12345, 0, 3, "Show").url
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIdRejected() {
        ExtrasEmbedRequest.movie(0, "Invalid")
    }
}
