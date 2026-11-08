package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasProviderPolicyTest {
    @Test fun anilistPaginationStopsAtProviderMaximum() {
        assertTrue(ExtrasProviderPolicy.animeHasNextPage(1, true))
        assertTrue(ExtrasProviderPolicy.animeHasNextPage(199, true))
        assertFalse(ExtrasProviderPolicy.animeHasNextPage(200, true))
        assertFalse(ExtrasProviderPolicy.animeHasNextPage(201, true))
        assertFalse(ExtrasProviderPolicy.animeHasNextPage(12, false))
        assertFalse(ExtrasProviderPolicy.animeHasNextPage(0, true))
    }

    @Test fun tmdbKeyReadinessDoesNotExposeOrRequireASecret() {
        assertFalse(ExtrasProviderPolicy.tmdbKeyConfigured(""))
        assertFalse(ExtrasProviderPolicy.tmdbKeyConfigured("  "))
        assertFalse(ExtrasProviderPolicy.tmdbKeyConfigured("TMDB_API_KEY_DEFAULT_VALUE"))
        assertFalse(ExtrasProviderPolicy.tmdbKeyConfigured("  TMDB_API_KEY_DEFAULT_VALUE  "))
        assertTrue(ExtrasProviderPolicy.tmdbKeyConfigured("configured-fixture-key"))
        assertTrue(ExtrasProviderPolicy.tmdbKeyConfigured("  configured-fixture-key  "))
    }

    @Test fun tmdbOriginalLanguagesRemainSeparateFromAnime() {
        assertTrue(ExtrasProviderPolicy.tmdbLanguages().contains("English" to "en"))
        assertTrue(ExtrasProviderPolicy.tmdbLanguages().contains("Filipino (Tagalog)" to "tl"))
        assertTrue(ExtrasProviderPolicy.tmdbLanguages().contains("Japanese" to "ja"))
        assertTrue(ExtrasProviderPolicy.tmdbLanguages().contains("Any language" to null))
        assertNull(ExtrasFilters().tmdbOriginalLanguage)
    }

    @Test fun tmdbSearchFiltersUseExactRawScoreGenreLanguageAndReleaseYear() {
        val movie = org.json.JSONObject(
            """{"id":1,"adult":false,"vote_average":6.96,"release_date":"2026-08-19",
                "genre_ids":[28,18],"original_language":"ja"}"""
        )
        // Formatting 6.96 to one decimal can show "7.0", but a 7+ filter must
        // reject the underlying 6.96 rating.
        assertFalse(ExtrasProviderPolicy.tmdbMatchesResult(
            movie, ExtrasType.MOVIES, "28", 2026, 7, "ja"
        ))
        assertTrue(ExtrasProviderPolicy.tmdbMatchesResult(
            movie, ExtrasType.MOVIES, "28", 2026, 6, "ja"
        ))
        assertFalse(ExtrasProviderPolicy.tmdbMatchesResult(
            movie, ExtrasType.MOVIES, "35", 2026, 6, "ja"
        ))
        assertFalse(ExtrasProviderPolicy.tmdbMatchesResult(
            movie, ExtrasType.MOVIES, "", 2025, 6, "ja"
        ))
        assertFalse(ExtrasProviderPolicy.tmdbMatchesResult(
            movie, ExtrasType.MOVIES, "", 2026, 6, "en"
        ))
        assertTrue(ExtrasProviderPolicy.tmdbMatchesResult(
            movie, ExtrasType.MOVIES, "", null, 0, null
        ))
    }

    @Test fun tmdbTvYearUsesFirstAirDateAndAdultTitlesAreNeverListed() {
        val series = org.json.JSONObject(
            """{"id":2,"adult":false,"vote_average":8.1,"release_date":"2021-05-05",
                "first_air_date":"2026-04-12","genre_ids":[10765],"original_language":"ko"}"""
        )
        assertTrue(ExtrasProviderPolicy.tmdbMatchesResult(
            series, ExtrasType.TV, "10765", 2026, 8, "ko"
        ))
        assertFalse(ExtrasProviderPolicy.tmdbMatchesResult(
            series, ExtrasType.TV, "10765", 2021, 8, "ko"
        ))
        series.put("adult", true)
        assertFalse(ExtrasProviderPolicy.tmdbMatchesResult(
            series, ExtrasType.TV, "", null, 0, null
        ))
        try {
            ExtrasProviderPolicy.tmdbMatchesResult(series, ExtrasType.ANIME, "", null, 0, null)
            org.junit.Assert.fail("AniList must not pass through TMDB filters")
        } catch (_: IllegalArgumentException) {
            // Provider isolation is enforced.
        }
    }


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

    @Test fun providerExternalLinksRejectPrivateDnsAliasAndTrailingDotBypass() {
        for (url in listOf(
            "https://tv.localdomain/watch/123",
            "https://nas.lan/watch/123",
            "https://router.home/watch/123",
            "https://router.internal/watch/123",
            "https://tv.home.arpa/watch/123",
            "https://cdn.example.org./watch/123",
            "https://television.local./watch/123",
            "https://example.test/watch/123",
            "https://2130706433/watch/123",
            "https://[::1]/watch/123",
            "https://example.org:8443/watch/123"
        )) {
            assertNull("Private/local provider URL should be rejected: $url",
                ExtrasProviderPolicy.safeExternalHttps(url))
        }
        assertEquals("https://www.crunchyroll.com/watch/123?s=1",
            ExtrasProviderPolicy.safeExternalHttps(
                "https://www.crunchyroll.com/watch/123?s=1"
            ))
        assertEquals("https://example.org:443/watch/123",
            ExtrasProviderPolicy.safeExternalHttps(
                "https://example.org:443/watch/123"
            ))
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
