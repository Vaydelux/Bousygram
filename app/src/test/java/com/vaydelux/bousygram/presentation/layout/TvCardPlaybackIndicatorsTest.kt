package com.vaydelux.bousygram.presentation.layout

import com.vaydelux.bousygram.data.repository.ContinueWatchingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvCardPlaybackIndicatorsTest {
    private fun entry(positionMs: Long, durationMs: Long = 120_000L, completed: Boolean = false) =
        ContinueWatchingEntry(
            chatId = -100L,
            messageId = 500L,
            fileId = 17,
            title = "Fixture",
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed
        )

    @Test fun partialVideoGetsResumeBarWithoutWatchedBadge() {
        val indicators = tvCardPlaybackIndicators(entry(30_000L), 0L, 120_000L)
        assertFalse(indicators.watched)
        assertEquals(0.25f, indicators.resumeFraction, 0.0001f)
    }

    @Test fun completionClearsResumeBarEvenWhenOldPreferencesStillContainProgress() {
        val indicators = tvCardPlaybackIndicators(
            entry(100_000L, completed = true),
            fallbackResumeMs = 70_000L,
            knownDurationMs = 120_000L
        )
        assertTrue(indicators.watched)
        assertEquals(0f, indicators.resumeFraction, 0f)
    }

    @Test fun completionThresholdMatchesAccountProgressLedger() {
        assertTrue(tvCardPlaybackIndicators(entry(108_000L), 0L, 120_000L).watched)
        assertFalse(tvCardPlaybackIndicators(entry(107_999L), 0L, 120_000L).watched)
    }

    @Test fun fallbackResumeIsUsedOnlyWhenAccountLedgerEntryIsAbsent() {
        val fallback = tvCardPlaybackIndicators(null, 48_000L, 120_000L)
        assertFalse(fallback.watched)
        assertEquals(0.4f, fallback.resumeFraction, 0.0001f)
        val ledgerWins = tvCardPlaybackIndicators(entry(12_000L), 48_000L, 120_000L)
        assertEquals(0.1f, ledgerWins.resumeFraction, 0.0001f)
    }

    @Test fun unknownDurationDoesNotInventProgress() {
        val indicators = tvCardPlaybackIndicators(entry(42_000L, durationMs = 0L), 42_000L, 0L)
        assertFalse(indicators.watched)
        assertEquals(0f, indicators.resumeFraction, 0f)
    }

    @Test fun malformedNegativeFallbackCannotShowNegativeProgress() {
        val indicators = tvCardPlaybackIndicators(null, -10_000L, 120_000L)
        assertFalse(indicators.watched)
        assertEquals(0f, indicators.resumeFraction, 0f)
    }
}
