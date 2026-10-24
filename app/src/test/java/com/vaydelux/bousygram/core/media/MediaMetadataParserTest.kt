package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMetadataParserTest {

    @Test
    fun parsesMovieWithDotSeparatedYear() {
        val result = MediaMetadataParser.parse(
            title = null,
            caption = null,
            fileName = "Inception.2010.1080p.BluRay.x264.mkv"
        )
        assertEquals("Inception", result.title)
        assertEquals(2010, result.year)
        assertFalse(result.isSeries)
        assertNull(result.seasonNumber)
        assertNull(result.episodeNumber)
    }

    @Test
    fun parsesMovieWithParenthesisYearAndBracketTags() {
        val result = MediaMetadataParser.parse(
            title = "The Matrix (1999) [1080p]",
            caption = null,
            fileName = "The.Matrix.1999.[1080p].mp4"
        )
        assertEquals("The Matrix", result.title)
        assertEquals(1999, result.year)
        assertFalse(result.isSeries)
    }

    @Test
    fun parsesMovieWithBracketYear() {
        val result = MediaMetadataParser.parse(
            title = null,
            caption = null,
            fileName = "Interstellar.[2014].4K.UHD.Remux.mkv"
        )
        assertEquals("Interstellar", result.title)
        assertEquals(2014, result.year)
        assertFalse(result.isSeries)
    }

    @Test
    fun parsesMultilineTelegramCaption() {
        val caption = """
            Oppenheimer (2023)
            IMDb: 8.9/10
            Director: Christopher Nolan
            Genre: Biography, Drama, History
            Quality: 1080p WEBRip x265
        """.trimIndent()

        val result = MediaMetadataParser.parse(
            title = "Oppenheimer",
            caption = caption,
            fileName = "Oppenheimer.2023.1080p.mkv"
        )
        assertEquals("Oppenheimer", result.title)
        assertEquals(2023, result.year)
        assertFalse(result.isSeries)
    }

    @Test
    fun parsesCaptionWithExplicitYearTag() {
        val caption = """
            Dune: Part Two
            Year: 2024
            Audio: English 5.1
            Quality: 2160p UHD
        """.trimIndent()

        val result = MediaMetadataParser.parse(
            title = "Dune: Part Two",
            caption = caption,
            fileName = "Dune.Part.Two.2024.mkv"
        )
        assertEquals("Dune: Part Two", result.title)
        assertEquals(2024, result.year)
    }

    @Test
    fun parsesTvShowWithYearAndStandardSeasonEpisode() {
        val result = MediaMetadataParser.parse(
            title = "Dark.2017.S01E03.1080p.NF.WEB-DL.mkv",
            caption = null,
            fileName = "Dark.2017.S01E03.1080p.NF.WEB-DL.mkv"
        )
        assertEquals("Dark", result.title)
        assertEquals(2017, result.year)
        assertEquals(1, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertTrue(result.isSeries)
    }

    @Test
    fun parsesTvShowXNotation() {
        val result = MediaMetadataParser.parse(
            title = "Stranger Things - 1x03 - Holly, Jolly.mp4",
            caption = null,
            fileName = "Stranger Things - 1x03 - Holly, Jolly.mp4"
        )
        assertEquals("Stranger Things", result.title)
        assertEquals(1, result.seasonNumber)
        assertEquals(3, result.episodeNumber)
        assertTrue(result.isSeries)
    }

    @Test
    fun parsesTvShowSeasonAndEpisodeWords() {
        val result = MediaMetadataParser.parse(
            title = "The Boys Season 2 Episode 4 [1080p].mkv",
            caption = null,
            fileName = "The.Boys.Season.2.Episode.4.mkv"
        )
        assertEquals("The Boys", result.title)
        assertEquals(2, result.seasonNumber)
        assertEquals(4, result.episodeNumber)
        assertTrue(result.isSeries)
    }

    @Test
    fun parsesAnimeEpisode() {
        val result = MediaMetadataParser.parse(
            title = "[SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv",
            caption = null,
            fileName = "[SubsPlease] Frieren - 05 (1080p) [ABCD1234].mkv"
        )
        assertEquals("Frieren", result.title)
        assertEquals(1, result.seasonNumber)
        assertEquals(5, result.episodeNumber)
        assertTrue(result.isSeries)
    }

    @Test
    fun parsesUnderscoreSeparatedNameWithQualityTokens() {
        val result = MediaMetadataParser.parse(
            title = null,
            caption = null,
            fileName = "avatar_the_way_of_water_2022_1080p_hevc.mp4"
        )
        assertEquals("avatar the way of water", result.title)
        assertEquals(2022, result.year)
        assertFalse(result.isSeries)
    }

    @Test
    fun handlesEmptyOrWhitespaceGracefully() {
        val result = MediaMetadataParser.parse(
            title = "",
            caption = "   ",
            fileName = null
        )
        assertEquals("", result.title)
        assertNull(result.year)
        assertNull(result.seasonNumber)
        assertNull(result.episodeNumber)
        assertFalse(result.isSeries)
    }
}
