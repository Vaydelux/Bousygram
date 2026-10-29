package com.vaydelux.bousygram.presentation.media

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerChromeLayoutTest {
    @Test fun tvRemainsFocusFirstEvenAtCompactWidths() {
        assertEquals(PlayerChromeLayout.TV, playerChromeLayout(isTv = true, compact = true))
        assertEquals(PlayerChromeLayout.TV, playerChromeLayout(isTv = true, compact = false))
    }

    @Test fun phonesUseCompactTouchControls() {
        assertEquals(PlayerChromeLayout.TOUCH_COMPACT, playerChromeLayout(isTv = false, compact = true))
    }

    @Test fun tabletsUseWideTouchControls() {
        assertEquals(PlayerChromeLayout.TOUCH_WIDE, playerChromeLayout(isTv = false, compact = false))
    }

    @Test fun shortLandscapeWindowsLimitExpandedHud() {
        assertEquals(296.dp, playerHudHeightLimit(windowHeightDp = 320, compact = true))
        assertEquals(656.dp, playerHudHeightLimit(windowHeightDp = 720, compact = false))
    }

    @Test fun tinyWindowsNeverProduceNegativeHeightConstraints() {
        assertEquals(1.dp, playerHudHeightLimit(windowHeightDp = 0, compact = true))
        assertEquals(1.dp, playerHudHeightLimit(windowHeightDp = 20, compact = false))
    }
}
