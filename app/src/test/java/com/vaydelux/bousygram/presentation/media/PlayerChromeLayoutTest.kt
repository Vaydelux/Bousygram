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
    @Test fun hudAutoHideRequiresUninterruptedPlayback() {
        assertEquals(
            true,
            shouldAutoHidePlayerHud(
                visible = true, playing = true, buffering = false,
                hasError = false, menuOpen = false, scrubbing = false, autoNextVisible = false
            )
        )
        assertEquals(false, shouldAutoHidePlayerHud(false, true, false, false, false, false, false))
        assertEquals(false, shouldAutoHidePlayerHud(true, false, false, false, false, false, false))
        assertEquals(false, shouldAutoHidePlayerHud(true, true, true, false, false, false, false))
        assertEquals(false, shouldAutoHidePlayerHud(true, true, false, true, false, false, false))
        assertEquals(false, shouldAutoHidePlayerHud(true, true, false, false, true, false, false))
        assertEquals(false, shouldAutoHidePlayerHud(true, true, false, false, false, true, false))
        assertEquals(false, shouldAutoHidePlayerHud(true, true, false, false, false, false, true))
    }

    @Test fun scrubClampsToRealDuration() {
        assertEquals(0L, playerScrubTarget(-0.4f, 100_000L))
        assertEquals(25_000L, playerScrubTarget(0.25f, 100_000L))
        assertEquals(100_000L, playerScrubTarget(1.5f, 100_000L))
    }

    @Test fun scrubRejectsUnknownDurationAndNonFiniteFractions() {
        assertEquals(null, playerScrubTarget(0.5f, 0L))
        assertEquals(null, playerScrubTarget(0.5f, -1L))
        assertEquals(null, playerScrubTarget(Float.NaN, 100_000L))
        assertEquals(null, playerScrubTarget(Float.POSITIVE_INFINITY, 100_000L))
    }

}
