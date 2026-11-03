package com.vaydelux.bousygram.presentation.media

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CinematicChromeLayoutTest {
    @Test fun menuHeightCapsAt320DpOnLargeAndroidTvDisplays() {
        assertEquals(320.dp, cinematicMenuHeightLimit(1080.dp))
        assertEquals(320.dp, cinematicMenuHeightLimit(800.dp))
    }

    @Test fun menuFitsCompactLandscapePhoneWithoutTakingFullScreen() {
        val landscape = cinematicMenuHeightLimit(240.dp)
        assertTrue(landscape >= 112.dp)
        assertTrue(landscape < 200.dp)
        assertTrue(landscape < cinematicMenuHeightLimit(600.dp))
    }

    @Test fun extremelySmallOrUnknownScreenDoesNotCollapseMenuRows() {
        assertEquals(112.dp, cinematicMenuHeightLimit(0.dp))
        assertEquals(112.dp, cinematicMenuHeightLimit((-1).dp))
    }
}
