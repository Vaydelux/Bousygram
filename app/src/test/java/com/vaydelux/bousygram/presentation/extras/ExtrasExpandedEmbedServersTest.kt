package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasExpandedEmbedServersTest {
    @Test fun fullCatalogsAreRegisteredWithoutReplacingLegacyEntries() {
        // 50 upstream TMDB entries include CineSrc; Bousygram has two additional legacy entries.
        assertEquals(52, ExtrasEmbedServer.entries.size)
        assertEquals(7, ExtrasAnimeEmbedServer.entries.size)
        assertEquals(6, ExtrasAnimeEmbedServer.entries.count { it.selectable })
        assertFalse(ExtrasAnimeEmbedServer.CINEZO.selectable)
        assertFalse(ExtrasEmbedServer.SUPEREMBED_VIP.selectable)
        assertEquals(51, ExtrasEmbedServer.entries.count { it.selectable })
    }

    @Test fun tmdbTemplatesAreSafeAndHaveNoUnresolvedPlaceholders() {
        for (server in ExtrasEmbedServer.entries.filter { it.selectable }) {
            val movie = server.urlFor(16498)
            val episode = server.urlFor(16498, season = 0, episode = 4)
            assertTrue(server.label, MediaForgeUrlPolicy.safeHttpsUrl(movie) != null)
            assertTrue(server.label, MediaForgeUrlPolicy.safeHttpsUrl(episode) != null)
            assertTrue(server.label, movie.contains("16498"))
            assertTrue(server.label, episode.contains("16498"))
            assertFalse(server.label, movie.contains("{"))
            assertFalse(server.label, episode.contains("{"))
        }
    }

    @Test fun selectedTmdbRoutesAndOptionsMatchSourceCatalog() {
        assertEquals("https://0vidads.top/embed/movie/42?branding=bousy&server=orion&subtitle=English&color=FF0000&loading=2&back=true",
            ExtrasEmbedServer.OVIDADS.urlFor(42))
        assertEquals("https://vidfast.pro/tv/42/0/5?autoPlay=true&startAt=78",
            ExtrasEmbedServer.VIDFAST.urlFor(42, 0, 5, startAtSec = 78))
        assertEquals("https://cinesrc.st/embed/tv/42?s=0&e=5",
            ExtrasEmbedServer.CINESRC.urlFor(42, 0, 5))
        assertEquals("https://embed.filmu.in/movie/42", ExtrasEmbedServer.EMBED_SU.urlFor(42))
    }

    @Test fun xxikuRoutesMatchProvidedUrlsWithoutUnverifiedParameters() {
        assertEquals("https://player.xxiku.com/embed/movie/603",
            ExtrasEmbedServer.XXIKU.urlFor(603))
        assertEquals("https://player.xxiku.com/embed/tv/1399/1/1",
            ExtrasEmbedServer.XXIKU.urlFor(1399, season = 1, episode = 1))
        assertEquals("https://player.xxiku.com/embed/anime/16498/1",
            ExtrasAnimeEmbedServer.XXIKU.urlFor(16498, season = 1, episode = 1))
        assertEquals("https://player.xxiku.com/embed/anime/16498/4",
            ExtrasAnimeEmbedServer.XXIKU.urlFor(16498, season = 1, episode = 4))
        assertEquals("https://player.xxiku.com/embed/tv/1399/1/1",
            ExtrasEmbedRequest.episode(1399, 1, 1, "Example").forServer(ExtrasEmbedServer.XXIKU).url)
        val anime = ExtrasEmbedRequest.anime(16498, episode = 4, title = "Example")
            .forServer(ExtrasAnimeEmbedServer.XXIKU)
        assertEquals("https://player.xxiku.com/embed/anime/16498/4", anime.url)
        assertEquals(16498, anime.anilistId)
        assertEquals(0, anime.tmdbId)
    }

    @Test fun embedHeavenAnimeIsSelectableWithoutChangingAniListIdentity() {
        val anime = ExtrasEmbedRequest.anime(21, episode = 1, title = "Anime")
            .forServer(ExtrasAnimeEmbedServer.EMBED_HEAVEN)
        assertEquals("https://player.embedheaven.st/anime/21/1", anime.url)
        assertEquals(21, anime.anilistId)
        assertEquals(0, anime.tmdbId)
        assertEquals(ExtrasAnimeEmbedServer.EMBED_HEAVEN, anime.animeServer)
    }

    @Test fun animeEpisodeRoutesUseAnilistIdentity() {
        assertEquals("https://embed.filmu.in/anime/16498/1/4",
            ExtrasAnimeEmbedServer.FILMU.urlFor(16498, 1, 4))
        assertEquals("https://fmov.my/embed/anime/16498/1/4",
            ExtrasAnimeEmbedServer.FMOV.urlFor(16498, 1, 4))
        assertEquals("https://cinextream.cc/api/embed/anime/sub/16498/4?color=e91e63",
            ExtrasAnimeEmbedServer.CINETARO.urlFor(16498, 1, 4))
        assertEquals("https://vidy.st/anime/16498/4?color=DC2626",
            ExtrasAnimeEmbedServer.VIDY.urlFor(16498, 1, 4))
        for (server in ExtrasAnimeEmbedServer.entries.filter { it.selectable }) {
            val episode4 = server.urlFor(16498, 1, 4)
            assertTrue(MediaForgeUrlPolicy.safeHttpsUrl(episode4) != null)
            assertNotEquals("Provider ${server.label} lost the selected episode",
                episode4, server.urlFor(16498, 1, 5))
        }
    }

    @Test fun animeOpensCanonicalEmbedHeavenPlayerByDefaultLikeMoviesAndTv() {
        val anime = ExtrasEmbedRequest.anime(21, episode = 4, title = "Series")
        assertEquals(ExtrasAnimeEmbedServer.EMBED_HEAVEN, anime.animeServer)
        assertEquals("https://player.embedheaven.st/anime/21/4", anime.url)
        assertEquals(21, anime.anilistId)
        assertEquals(4, anime.episode)
        assertEquals(0, anime.tmdbId)
        assertTrue(MediaForgeUrlPolicy.safeHttpsUrl(anime.url) != null)
    }

    @Test fun switchingAnimeProvidersPreservesEpisodeAndSeparatesIds() {
        val anime = ExtrasEmbedRequest.anime(16498, episode = 4, title = "Test Anime")
        assertEquals("https://player.embedheaven.st/anime/16498/4", anime.url)
        val switched = anime.forServer(ExtrasAnimeEmbedServer.VIDY)
        assertEquals("https://vidy.st/anime/16498/4?color=DC2626", switched.url)
        assertEquals(16498, switched.anilistId)
        assertEquals(4, switched.episode)
        assertEquals(0, switched.tmdbId)
        assertEquals(anime, switched.forServer(ExtrasAnimeEmbedServer.EMBED_HEAVEN))
    }

    @Test fun externalProviderOptionsDoNotAdvertisePipCastOrDownload() {
        val cinezo = ExtrasEmbedServer.CINEZO.urlFor(42)
        assertTrue(cinezo.contains("chromecast=false"))
        assertTrue(cinezo.contains("pip=false"))
        assertTrue(cinezo.contains("download=false"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun animeProviderWithoutEpisodeRouteIsNotSelectable() {
        ExtrasAnimeEmbedServer.CINEZO.urlFor(16498, 1, 4)
    }

    @Test(expected = IllegalArgumentException::class)
    fun animeProviderWithoutSeasonRouteRejectsNonDefaultSeason() {
        ExtrasAnimeEmbedServer.VIDY.urlFor(16498, 2, 4)
    }

    @Test fun animeSeasonAwareRoutePreservesNonDefaultSeason() {
        assertEquals("https://embed.filmu.in/anime/16498/2/4",
            ExtrasAnimeEmbedServer.FILMU.urlFor(16498, 2, 4))
    }

    @Test(expected = IllegalArgumentException::class)
    fun tmdbServerDoesNotAcceptAnimeRequest() {
        ExtrasEmbedRequest.anime(12, 2, "Anime").forServer(ExtrasEmbedServer.CINESRC)
    }

    @Test(expected = IllegalArgumentException::class)
    fun animeServerDoesNotAcceptTmdbRequest() {
        ExtrasEmbedRequest.movie(12, "Movie").forServer(ExtrasAnimeEmbedServer.FILMU)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unusableRelativePhpServerCannotBeOpened() {
        ExtrasEmbedServer.SUPEREMBED_VIP.urlFor(12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidAnimeEpisodeIsRejected() {
        ExtrasAnimeEmbedServer.FILMU.urlFor(12, episode = 0)
    }
}
