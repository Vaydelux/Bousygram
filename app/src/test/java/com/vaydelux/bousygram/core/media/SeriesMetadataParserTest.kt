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
        org.junit.Assert.assertTrue(result.isSeries)
    }

    @Test
    fun parsesMovieWithYearAndJunkSuffix() {
        val result = SeriesMetadataParser.parse("Inception.2010.1080p.BluRay.x264.mkv", null)
        assertEquals("Inception", result.seriesTitle)
        assertEquals(2010, result.year)
        org.junit.Assert.assertNull(result.seasonNumber)
        org.junit.Assert.assertNull(result.episodeNumber)
        org.junit.Assert.assertFalse(result.isSeries)
    }

    @Test
    fun parsesMultilineTelegramCaptionWithYearAndTitle() {
        val caption = "Interstellar\nYear: 2014\nGenre: Sci-Fi\n1080p BluRay"
        val result = SeriesMetadataParser.parse(title = "Interstellar.mkv", fileName = "Interstellar.mkv", caption = caption)
        assertEquals("Interstellar", result.seriesTitle)
        assertEquals(2014, result.year)
        org.junit.Assert.assertFalse(result.isSeries)
    }

    @Test
    fun parsesAnimeBracketFormat() {
        val result = SeriesMetadataParser.parse("[SubsPlease] Frieren - 08 [1080p].mkv", null)
        assertEquals("Frieren", result.seriesTitle)
        assertEquals(1, result.seasonNumber)
        assertEquals(8, result.episodeNumber)
        org.junit.Assert.assertTrue(result.isSeries)
    }

    @Test
    fun mapsParserOutputToTmdbQueryParameters() {
        // 1. Movie mapping test
        val movieParsed = SeriesMetadataParser.parse("The.Matrix.1999.1080p.mkv", null)
        assertEquals("The Matrix", movieParsed.cleanTitle)
        assertEquals(1999, movieParsed.year)
        org.junit.Assert.assertFalse(movieParsed.isSeries)

        val movieKey = com.vaydelux.bousygram.data.repository.TmdbRepository.generateCacheKey(
            title = movieParsed.cleanTitle!!,
            year = movieParsed.year,
            isSeries = movieParsed.isSeries
        )
        org.junit.Assert.assertTrue(movieKey.isNotBlank())

        // 2. TV Series with Season and Episode mapping test
        val tvParsed = SeriesMetadataParser.parse("Breaking.Bad.S02E08.Better.Call.Saul.1080p.mkv", null)
        assertEquals("Breaking Bad", tvParsed.cleanTitle)
        assertEquals(2, tvParsed.seasonNumber)
        assertEquals(8, tvParsed.episodeNumber)
        org.junit.Assert.assertTrue(tvParsed.isSeries)

        val tvKey = com.vaydelux.bousygram.data.repository.TmdbRepository.generateCacheKey(
            title = tvParsed.cleanTitle!!,
            year = tvParsed.year,
            isSeries = tvParsed.isSeries,
            seasonNumber = tvParsed.seasonNumber,
            episodeNumber = tvParsed.episodeNumber
        )
        org.junit.Assert.assertTrue(tvKey.isNotBlank())
        org.junit.Assert.assertNotEquals(movieKey, tvKey)
    }
}
