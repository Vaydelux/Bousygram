package com.vaydelux.bousygram.presentation.media

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicChromeLayoutTest {
    @Test fun menuHeightCapsAt320DpOnLargeAndroidTvDisplays() {
        assertEquals(320.dp, cinematicMenuHeightLimit(1080))
        assertEquals(320.dp, cinematicMenuHeightLimit(800))
    }

    @Test fun menuFitsCompactLandscapePhoneWithoutTakingFullScreen() {
        val landscape = cinematicMenuHeightLimit(240)
        assertTrue(landscape >= 112.dp)
        assertTrue(landscape < 200.dp)
        assertTrue(landscape < cinematicMenuHeightLimit(600))
    }

    @Test fun extremelySmallOrUnknownScreenDoesNotCollapseMenuRows() {
        assertEquals(112.dp, cinematicMenuHeightLimit(0))
        assertEquals(112.dp, cinematicMenuHeightLimit(-1))
    }
}
