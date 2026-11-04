package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasProviderPolicyTest {
    @Test fun eachTabHasAnIsolatedCatalogRoute() {
        assertEquals("/search/movie", ExtrasProviderPolicy.searchPath(ExtrasType.MOVIES, true))
        assertEquals("/discover/movie", ExtrasProviderPolicy.searchPath(ExtrasType.MOVIES, false))
        assertEquals("/search/tv", ExtrasProviderPolicy.searchPath(ExtrasType.TV, true))
        assertEquals("/discover/tv", ExtrasProviderPolicy.searchPath(ExtrasType.TV, false))
        assertEquals("/graphql", ExtrasProviderPolicy.searchPath(ExtrasType.ANIME, true))
        assertEquals("/graphql", ExtrasProviderPolicy.searchPath(ExtrasType.ANIME, false))
        assertEquals("AniList", ExtrasType.ANIME.provider)
        assertEquals("TMDB", ExtrasType.TV.provider)
    }

    @Test fun tmdbTrendingUsesTrendingInsteadOfPopularityRoute() {
        assertEquals("/trending/movie/week",
            ExtrasProviderPolicy.tmdbBrowsePath(ExtrasType.MOVIES, false, ExtrasSort.TRENDING))
        assertEquals("/trending/tv/week",
            ExtrasProviderPolicy.tmdbBrowsePath(ExtrasType.TV, false, ExtrasSort.TRENDING))
        assertEquals("/search/movie",
            ExtrasProviderPolicy.tmdbBrowsePath(ExtrasType.MOVIES, true, ExtrasSort.TRENDING))
        assertEquals("/discover/tv",
            ExtrasProviderPolicy.tmdbBrowsePath(ExtrasType.TV, false, ExtrasSort.TOP_RATED))
        try {
            ExtrasProviderPolicy.tmdbBrowsePath(ExtrasType.ANIME, false, ExtrasSort.TRENDING)
            org.junit.Assert.fail("AniList must not route to TMDB")
        } catch (_: IllegalArgumentException) {
            // Provider isolation is intentional.
        }
    }

    @Test fun trailerKeyRejectsUrlPayloadAndInvalidCharacters() {
        assertEquals("dQw4w9WgXcQ", ExtrasProviderPolicy.youtubeId("dQw4w9WgXcQ"))
        assertNull(ExtrasProviderPolicy.youtubeId("https://youtube.com/watch?v=123"))
        assertNull(ExtrasProviderPolicy.youtubeId("javascript:alert(1)"))
        assertNull(ExtrasProviderPolicy.youtubeId(""))
        assertNull(ExtrasProviderPolicy.youtubeId("a"))
    }

    @Test fun externalEpisodeLinksMustBePublicHttpsUrls() {
        assertEquals("https://www.crunchyroll.com/watch/test",
            ExtrasProviderPolicy.safeExternalHttps("https://www.crunchyroll.com/watch/test"))
        assertNull(ExtrasProviderPolicy.safeExternalHttps("javascript:alert(1)"))
        assertNull(ExtrasProviderPolicy.safeExternalHttps("http://example.org/video"))
        assertNull(ExtrasProviderPolicy.safeExternalHttps("https://localhost/private"))
        assertNull(ExtrasProviderPolicy.safeExternalHttps("https://127.0.0.1/secrets"))
        assertNull(ExtrasProviderPolicy.safeExternalHttps("https://user:pass@example.org/episode"))
        assertNull(ExtrasProviderPolicy.safeExternalHttps("https://example.org:4443/video"))
        assertNull(ExtrasProviderPolicy.safeExternalHttps("https://internal.local/video"))
    }

    @Test fun anilistNativeFiltersRemainProviderSpecific() {
        assertTrue(ExtrasProviderPolicy.animeFormats().contains("TV" to "TV"))
        assertTrue(ExtrasProviderPolicy.animeFormats().contains("Movie" to "MOVIE"))
        assertTrue(ExtrasProviderPolicy.animeFormats().contains("Any format" to null))
        assertTrue(ExtrasProviderPolicy.animeStatuses().contains("Airing" to "RELEASING"))
        assertTrue(ExtrasProviderPolicy.animeStatuses().contains("Upcoming" to "NOT_YET_RELEASED"))
        assertTrue(ExtrasProviderPolicy.animeStatuses().contains("Any status" to null))
        assertNull(ExtrasFilters().animeFormat)
        assertNull(ExtrasFilters().animeStatus)
    }

    @Test fun graphQlErrorResponseMustNotBeCached() {
        assertFalse(ExtrasProviderPolicy.mayCacheResponse(true, true))
        assertTrue(ExtrasProviderPolicy.mayCacheResponse(true, false))
        assertTrue(ExtrasProviderPolicy.mayCacheResponse(false, false))
    }

    @Test fun filtersAreProviderSpecificAndDefaultSafe() {
        assertTrue(ExtrasProviderPolicy.genres(ExtrasType.MOVIES).any { it.first == "Horror" && it.second == "27" })
        assertTrue(ExtrasProviderPolicy.genres(ExtrasType.TV).any { it.first == "Mystery" })
        assertTrue(ExtrasProviderPolicy.genres(ExtrasType.ANIME).any { it.first == "Slice of Life" })
        assertFalse(ExtrasProviderPolicy.genres(ExtrasType.ANIME).any { it.second == "27" })
        assertEquals("All", ExtrasFilters().genre)
        assertEquals(0, ExtrasFilters().minimumRating)
        assertNotNull(ExtrasSort.entries.firstOrNull { it.label == "Top rated" })
    }
}
