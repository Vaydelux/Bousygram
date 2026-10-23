package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SeriesMetadataParserTest {

    @Test
    fun parsesStandardSeasonEpisode() {
        val result = SeriesMetadataParser.parse("Breaking.Bad.S02E08.Better.Call.Saul.1080p.mkv", null)
        assertEquals("Breaking Bad", result.seriesTitle)
        assertEquals(2, result.seasonNumber)
        assertEquals(8, result.episodeNumber)
    }

    @Test
    fun parsesXNotation() {
        val result = SeriesMetadataParser.parse("Friends 2x14 The One With The Prom Video", null)
        assertEquals("Friends", result.seriesTitle)
        assertEquals(2, result.seasonNumber)
        assertEquals(14, result.episodeNumber)
    }

    @Test
    fun parsesSeasonAndEpisodeWords() {
        val result = SeriesMetadataParser.parse("Attack on Titan Season 4 Episode 12", null)
        assertEquals("Attack on Titan", result.seriesTitle)
        assertEquals(4, result.seasonNumber)
        assertEquals(12, result.episodeNumber)
    }

    @Test
    fun parsesEpisodeOnly() {
        val result = SeriesMetadataParser.parse("One Piece Ep. 1015", null)
        assertEquals("One Piece", result.seriesTitle)
        assertEquals(1, result.seasonNumber)
        assertEquals(1015, result.episodeNumber)
    }

    @Test
    fun parsesDashDelimitedTitle() {
        val result = SeriesMetadataParser.parse("Game of Thrones - S03E09 - The Rains of Castamere.mp4", null)
        assertEquals("Game of Thrones", result.seriesTitle)
        assertEquals(3, result.seasonNumber)
        assertEquals(9, result.episodeNumber)
    }
}
