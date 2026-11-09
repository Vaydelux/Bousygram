package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
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

    @Test fun movieServersUseTheSameTmdbIdWithDocumentedPublicHttpsRoutes() {
        val original = ExtrasEmbedRequest.movie(1084242, "Fixture Movie")
        val expected = mapOf(
            ExtrasEmbedServer.CINESRC to "https://cinesrc.st/embed/movie/1084242",
            ExtrasEmbedServer.XXIKU to
                "https://player.xxiku.com/embed/movie/1084242",
            ExtrasEmbedServer.EMBED_HEAVEN to
                "https://player.embedheaven.st/movie/1084242?autoplay=0"
        )
        for ((server, url) in expected) {
            val selected = original.forServer(server)
            assertEquals(url, selected.url)
            assertEquals(server, selected.server)
            assertEquals(1084242, selected.tmdbId)
            assertEquals("Fixture Movie", selected.title)
            assertTrue(MediaForgeUrlPolicy.safeHttpsUrl(url) != null)
        }
    }

    @Test fun episodeServerSelectionPreservesSeasonEpisodeAndTitle() {
        val original = ExtrasEmbedRequest.episode(1399, 0, 3, "Fixture Show")
        assertEquals(
            "https://cinesrc.st/embed/tv/1399?s=0&e=3",
            original.url
        )
        assertEquals(
            "https://player.xxiku.com/embed/tv/1399/0/3",
            original.forServer(ExtrasEmbedServer.XXIKU).url
        )
        val alternate = original.forServer(ExtrasEmbedServer.EMBED_HEAVEN)
        assertEquals(
            "https://player.embedheaven.st/tv/1399/0/3?autoplay=0",
            alternate.url
        )
        assertEquals(0, alternate.season)
        assertEquals(3, alternate.episode)
        assertEquals("Fixture Show · S0 E3", alternate.title)
        assertEquals(original, alternate.forServer(ExtrasEmbedServer.CINESRC))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidServerEpisodeRejected() {
        ExtrasEmbedServer.XXIKU.urlFor(1399, season = 1, episode = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteTvIdentityRejected() {
        ExtrasEmbedServer.EMBED_HEAVEN.urlFor(1399, season = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIdRejected() {
        ExtrasEmbedRequest.movie(0, "Invalid")
    }
}
