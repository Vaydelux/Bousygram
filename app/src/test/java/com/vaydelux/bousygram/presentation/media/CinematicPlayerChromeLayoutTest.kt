package com.vaydelux.bousygram.presentation.media

import org.junit.Assert.assertEquals
import org.junit.Test

class CinematicPlayerChromeLayoutTest {
    @Test fun passiveProgressClampsWithoutInventingUnknownDuration() {
        assertEquals(0f, cinematicProgressFraction(100_000L, 0L), 0f)
        assertEquals(0f, cinematicProgressFraction(100_000L, -1L), 0f)
        assertEquals(0f, cinematicProgressFraction(-100L, 100_000L), 0f)
        assertEquals(0.5f, cinematicProgressFraction(50_000L, 100_000L), 0.0001f)
        assertEquals(1f, cinematicProgressFraction(200_000L, 100_000L), 0f)
    }
}
