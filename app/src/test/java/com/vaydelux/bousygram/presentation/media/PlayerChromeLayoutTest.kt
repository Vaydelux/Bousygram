package com.vaydelux.bousygram.presentation.media

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
}
