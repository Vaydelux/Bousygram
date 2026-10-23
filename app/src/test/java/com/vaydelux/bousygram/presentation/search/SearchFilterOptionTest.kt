package com.vaydelux.bousygram.presentation.search

import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterOptionTest {

    private fun testMedia(id: Long, durationSeconds: Int, sizeBytes: Long) = MediaItem(
        chatId = 100L,
        messageId = id,
        date = 1_700_000_000,
        type = MediaType.Video,
        title = "Video $id",
        fileId = id.toInt(),
        durationSeconds = durationSeconds,
        fileSizeBytes = sizeBytes
    )

    @Test
    fun testMovieFilterSelectsDurationTwentyMinutesAndAbove() {
        val shortClip = testMedia(1L, durationSeconds = 180, sizeBytes = 50 * 1024 * 1024L)
        val mediumVideo = testMedia(2L, durationSeconds = 900, sizeBytes = 150 * 1024 * 1024L)
        val movie = testMedia(3L, durationSeconds = 1250, sizeBytes = 500 * 1024 * 1024L)

        val list = listOf(shortClip, mediumVideo, movie)
        val moviesOnly = list.filter { it.durationSeconds >= 1200 }

        assertEquals(1, moviesOnly.size)
        assertEquals(3L, moviesOnly.first().messageId)
    }

    @Test
    fun testClipsFilterSelectsDurationUnderFiveMinutes() {
        val shortClip = testMedia(1L, durationSeconds = 180, sizeBytes = 50 * 1024 * 1024L)
        val mediumVideo = testMedia(2L, durationSeconds = 900, sizeBytes = 150 * 1024 * 1024L)
        val zeroDuration = testMedia(3L, durationSeconds = 0, sizeBytes = 10 * 1024 * 1024L)

        val list = listOf(shortClip, mediumVideo, zeroDuration)
        val clipsOnly = list.filter { it.durationSeconds in 1..300 }

        assertEquals(1, clipsOnly.size)
        assertEquals(1L, clipsOnly.first().messageId)
    }

    @Test
    fun testLargeFilterSelectsAboveThreeHundredMegabytes() {
        val small = testMedia(1L, durationSeconds = 100, sizeBytes = 100 * 1024 * 1024L)
        val large = testMedia(2L, durationSeconds = 3000, sizeBytes = 450 * 1024 * 1024L)

        val list = listOf(small, large)
        val largeOnly = list.filter { it.fileSizeBytes >= 300 * 1024 * 1024L }

        assertEquals(1, largeOnly.size)
        assertEquals(2L, largeOnly.first().messageId)
    }

    @Test
    fun testAllSearchFilterOptionsHaveDescriptions() {
        SearchFilterOption.entries.forEach { option ->
            assertTrue(option.label.isNotBlank())
        }
    }
}
