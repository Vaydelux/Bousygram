package com.vaydelux.bousygram.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackBudgetTest {
    @Test fun malformedNativeTrackCountsCannotCreateUnboundedUiRows() {
        assertEquals(0, PlaybackTrackBudget.boundedCount(-1))
        assertEquals(0, PlaybackTrackBudget.boundedCount(Int.MIN_VALUE))
        assertEquals(4, PlaybackTrackBudget.boundedCount(4))
        assertEquals(PlaybackTrackBudget.MAX_UI_TRACKS,
            PlaybackTrackBudget.boundedCount(Int.MAX_VALUE))
    }

    @Test fun hugeTrackNamesAndLanguageTagsAreTruncatedBeforeStoringInCompose() {
        val hugeLabel = "x".repeat(100_000)
        val hugeLanguage = "e".repeat(10_000)
        val display = PlaybackTrackBudget.label(hugeLabel, "Audio 1")
        val lang = PlaybackTrackBudget.language(hugeLanguage)
        assertEquals(PlaybackTrackBudget.MAX_LABEL_CHARS, display.length)
        assertEquals(PlaybackTrackBudget.MAX_LANGUAGE_CHARS, lang?.length)
        assertTrue(display.all { it == 'x' })
    }

    @Test fun emptyOrWhitespaceOnlyNativeLabelsUseBoundedFallbacks() {
        assertEquals("Audio 17", PlaybackTrackBudget.label(null, "Audio 17"))
        assertEquals("Audio 17", PlaybackTrackBudget.label("", "Audio 17"))
        assertEquals("Subtitles 5", PlaybackTrackBudget.label("  ", "Subtitles 5"))
        assertEquals("English", PlaybackTrackBudget.label("  English  ", "Track"))
        assertNull(PlaybackTrackBudget.language("  "))
        assertNull(PlaybackTrackBudget.language(null))
        assertEquals(PlaybackTrackBudget.MAX_LABEL_CHARS,
            PlaybackTrackBudget.label(null, "Fallback".repeat(500)).length)
    }
}
