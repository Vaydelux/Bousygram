package com.vaydelux.bousygram.ui.adaptive

import org.junit.Assert.*
import org.junit.Test

class AdaptiveUiTest {
    @Test fun compactPortraitUsesBottomBar() {
        val ui = AdaptiveUi.resolve(393, 873, false)
        assertEquals(WindowClass.COMPACT, ui.windowClass)
        assertEquals(NavigationMode.BOTTOM_BAR, ui.navigationMode)
        assertEquals(16, ui.contentGutterDp)
    }

    @Test fun shortLandscapeUsesCompactHeader() {
        val ui = AdaptiveUi.resolve(873, 393, false)
        assertTrue(ui.compactHeader)
        assertEquals(NavigationMode.BOTTOM_BAR, ui.navigationMode)
    }

    @Test fun televisionAlwaysUsesTvRail() {
        assertEquals(NavigationMode.TV_RAIL, AdaptiveUi.resolve(960, 540, true).navigationMode)
    }

    @Test fun highContrastDisablesPatterns() {
        assertEquals(BackgroundPattern.NONE, AdaptiveUi.effectivePattern(BackgroundPattern.FINE_GRID, true, false))
    }

    @Test fun loadingIsNotEmpty() {
        assertFalse(MediaContentState.Loading.isConfirmedEmpty())
        assertTrue(MediaContentState.Loaded(0).isConfirmedEmpty())
        assertFalse(MediaContentState.Loaded(17).isConfirmedEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidWindowSize() { AdaptiveUi.resolve(0, 400, false) }
}
