package com.vaydelux.bousygram.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueWatchingEntryTest {

    @Test
    fun `progress calculates correct fraction between 0 and 1`() {
        val entry = ContinueWatchingEntry(
            chatId = 123L,
            messageId = 456L,
            fileId = 789,
            title = "Test Video",
            positionMs = 30_000L,
            durationMs = 60_000L
        )
        assertEquals(0.5f, entry.progress, 0.001f)
    }

    @Test
    fun `progress is clamped to 1 when position exceeds duration`() {
        val entry = ContinueWatchingEntry(
            chatId = 123L,
            messageId = 456L,
            fileId = 789,
            title = "Test Video",
            positionMs = 70_000L,
            durationMs = 60_000L
        )
        assertEquals(1.0f, entry.progress, 0.001f)
    }

    @Test
    fun `progress is 0 when duration is zero or negative`() {
        val entry = ContinueWatchingEntry(
            chatId = 123L,
            messageId = 456L,
            fileId = 789,
            title = "Test Video",
            positionMs = 15_000L,
            durationMs = 0L
        )
        assertEquals(0f, entry.progress, 0.001f)
    }

    @Test
    fun `completed flag is preserved`() {
        val entry = ContinueWatchingEntry(
            chatId = 123L,
            messageId = 456L,
            fileId = 789,
            title = "Test Video",
            positionMs = 55_000L,
            durationMs = 60_000L,
            completed = true
        )
        assertTrue(entry.completed)
    }
}
