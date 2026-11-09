package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasEmbedHeavenRoutesTest {
    @Test fun allFiveRouteShapesMatchExamples() {
        assertEquals("https://player.embedheaven.st/movie/550", ExtrasEmbedHeavenRoutes.movie(550))
        assertEquals("https://player.embedheaven.st/tv/108978/1/1",
            ExtrasEmbedHeavenRoutes.tv(108978, season = 1, episode = 1))
        assertEquals("https://player.embedheaven.st/anime/21/1",
            ExtrasEmbedHeavenRoutes.anime(21, episode = 1))
        assertEquals("https://player.embedheaven.st/live/bbc-one-gb",
            ExtrasEmbedHeavenRoutes.live("bbc-one-gb"))
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

        val live = ExtrasEmbedRequest.live("bbc-one-gb")
        assertEquals("bbc-one-gb", live.liveChannelSlug)
        assertEquals(0, live.tmdbId)
        assertEquals(0, live.anilistId)
        assertEquals(ExtrasEmbedHeavenRoutes.live("bbc-one-gb"), live.url)
        assertTrue(MediaForgeUrlPolicy.safeHttpsUrl(live.url) != null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun liveSlugRejectsTraversal() {
        ExtrasEmbedHeavenRoutes.live("bbc-one-gb/../../movie/550")
    }

    @Test(expected = IllegalArgumentException::class)
    fun liveSlugRejectsQueryInjection() {
        ExtrasEmbedHeavenRoutes.live("bbc-one-gb?foo=bar")
    }

    @Test(expected = IllegalArgumentException::class)
    fun liveSlugRejectsNonCanonicalCaseOrWhitespace() {
        ExtrasEmbedHeavenRoutes.live("BBC One GB")
    }

    @Test(expected = IllegalArgumentException::class)
    fun liveSlugRejectsOverlongInput() {
        ExtrasEmbedHeavenRoutes.live("a".repeat(81))
    }

    @Test(expected = IllegalArgumentException::class)
    fun liveRequestCannotChooseTmdbProvider() {
        ExtrasEmbedRequest.live("bbc-one-gb").forServer(ExtrasEmbedServer.CINESRC)
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
