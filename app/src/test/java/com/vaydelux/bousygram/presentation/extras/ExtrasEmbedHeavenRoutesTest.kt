package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasEmbedHeavenRoutesTest {
    @Test fun movieTvAnimeAndLegacyRouteShapesMatchExamples() {
        assertEquals("https://player.embedheaven.st/movie/550", ExtrasEmbedHeavenRoutes.movie(550))
        assertEquals("https://player.embedheaven.st/tv/108978/1/1",
            ExtrasEmbedHeavenRoutes.tv(108978, season = 1, episode = 1))
        assertEquals("https://player.embedheaven.st/anime/21/1",
            ExtrasEmbedHeavenRoutes.anime(21, episode = 1))
        assertEquals("https://player.embedheaven.st/embed?id=550&type=movie",
            ExtrasEmbedHeavenRoutes.legacyMovie(550))
    }

    @Test fun pickerAndRequestsUseCanonicalRoutes() {
        assertEquals(ExtrasEmbedHeavenRoutes.movie(550),
            ExtrasEmbedServer.EMBED_HEAVEN.urlFor(550))
        assertEquals(ExtrasEmbedHeavenRoutes.tv(108978, 1, 1),
            ExtrasEmbedServer.EMBED_HEAVEN.urlFor(108978, season = 1, episode = 1))
        assertEquals(ExtrasEmbedHeavenRoutes.anime(21, 1),
            ExtrasAnimeEmbedServer.EMBED_HEAVEN.urlFor(21, episode = 1))

        val movie = ExtrasEmbedRequest.movie(550, "Movie")
        val legacy = movie.forLegacyEmbedHeaven()
        assertTrue(legacy.legacyEmbedHeaven)
        assertEquals(ExtrasEmbedHeavenRoutes.legacyMovie(550), legacy.url)
        val standard = legacy.forServer(ExtrasEmbedServer.EMBED_HEAVEN)
        assertFalse(standard.legacyEmbedHeaven)
        assertEquals(ExtrasEmbedHeavenRoutes.movie(550), standard.url)

    }

    @Test(expected = IllegalArgumentException::class)
    fun legacyFallbackRejectsTvShows() {
        ExtrasEmbedRequest.episode(108978, 1, 1, "TV").forLegacyEmbedHeaven()
    }

    @Test(expected = IllegalArgumentException::class)
    fun legacyFallbackRejectsInvalidTmdbId() {
        ExtrasEmbedHeavenRoutes.legacyMovie(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun animeRejectsInvalidEpisode() {
        ExtrasEmbedHeavenRoutes.anime(21, 0)
    }
}
