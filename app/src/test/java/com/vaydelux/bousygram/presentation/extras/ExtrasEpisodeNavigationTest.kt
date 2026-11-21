package com.vaydelux.bousygram.presentation.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExtrasEpisodeNavigationTest {
    @Test fun nextAndPreviousCrossVerifiedTvSeasonsButSkipUnknownAndSpecials() {
        val guide = ExtrasEpisodeGuide(
            catalogType = ExtrasType.TV,
            titleId = 481,
            seasons = listOf(
                ExtrasSeason(0, "Specials", 5),
                ExtrasSeason(1, "Season 1", 2),
                ExtrasSeason(2, "Unknown", 0),
                ExtrasSeason(3, "Season 3", 3)
            )
        )
        assertEquals(1 to 2, guide.adjacent(1, 1, 1))
        assertEquals(3 to 1, guide.adjacent(1, 2, 1))
        assertEquals(1 to 2, guide.adjacent(3, 1, -1))
        assertNull(guide.adjacent(1, 1, -1))
        assertNull(guide.adjacent(3, 3, 1))
        assertFalse(guide.contains(2, 1))
        assertEquals(1..2, guide.page(1, 1))
    }

    @Test fun animePlaybackNeverCrossesAnilistMediaIdentity() {
        val guide = ExtrasEpisodeGuide(
            catalogType = ExtrasType.ANIME, titleId = 197,
            seasons = emptyList(), animeEpisodeCount = 24
        )
        assertEquals(1 to 2, guide.adjacent(1, 1, 1))
        assertEquals(1 to 23, guide.adjacent(1, 24, -1))
        assertNull(guide.adjacent(1, 24, 1))
        assertNull(guide.adjacent(1, 1, -1))
        assertFalse(guide.contains(2, 1))
        val request = ExtrasEmbedRequest.anime(197, 1, "Anime")
        val next = request.forEpisode(1, 2)
        assertEquals(197, next.anilistId)
        assertEquals(0, next.tmdbId)
        assertEquals(2, next.episode)
        assertEquals(request.animeServer, next.animeServer)
        assertTrue(next.url.contains("/197/2"))
        try {
            request.forEpisode(2, 1)
            fail("Next AniList season must use a new, explicitly resolved AniList ID")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test fun tvEpisodeChangesKeepChosenServerAndOriginalTmdbId() {
        val request = ExtrasEmbedRequest.episode(481, 1, 2, "Show")
            .forServer(ExtrasEmbedServer.XXIKU)
        val next = request.forEpisode(2, 1)
        assertEquals(481, next.tmdbId)
        assertEquals(0, next.anilistId)
        assertEquals(ExtrasEmbedServer.XXIKU, next.server)
        assertEquals(2, next.season)
        assertEquals(1, next.episode)
        assertTrue(next.url.contains("/481/2/1"))
    }

    @Test fun metadataOnlyMappingAcceptsSplitCourOffsetWithoutChangingPlaybackIds() {
        val match = ExtrasAnimeTmdbMatch(
            anilistId = 197,
            tmdbTvId = 987,
            tmdbSeason = 2,
            firstTmdbEpisode = 13,
            tmdbTitle = "Confirmed title"
        )
        assertEquals(13, match.tmdbEpisode(1))
        assertEquals(24, match.tmdbEpisode(12))
        assertEquals(1, match.anilistEpisode(13))
        assertEquals(12, match.anilistEpisode(24))
        assertNull(match.anilistEpisode(12))
        assertEquals(197, ExtrasEpisodeGuide(
            catalogType = ExtrasType.ANIME,
            titleId = 197, seasons = emptyList(),
            animeEpisodeCount = 12, animeTmdbMatch = match
        ).titleId)
    }

    @Test fun listWindowsAreBoundedEvenForThousandEpisodeSeries() {
        val long = ExtrasEpisodeGuide(
            catalogType = ExtrasType.ANIME, titleId = 101,
            seasons = emptyList(), animeEpisodeCount = 1400
        )
        assertEquals(1..48, long.page(1, 1))
        assertEquals(1344..1391, long.page(1, 1344))
        assertEquals(1400..1400, long.page(1, 9999))
        assertNull(long.page(2, 1))
        assertNull(ExtrasEpisodeGuide(
            catalogType = ExtrasType.ANIME, titleId = 101,
            seasons = emptyList(), animeEpisodeCount = null
        ).page(1, 1))
    }
}
