package com.vaydelux.bousygram.presentation.extras

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps WebView visible on small rotated phones, multi-window and 720p TVs. */
class ExtrasEmbedPanelLimitsTest {
    @Test fun shortWindowsReserveVideoSpaceRatherThanGivingItAllToToolbars() {
        for (height in listOf(200, 240, 320, 480, 540)) {
            val pane = extrasEmbedPanelLimits(height.dp, 1f)
            assertTrue("Expected short viewport for ${height}dp", pane.compactHeight)
            val remaining = height.dp - pane.headerMax - pane.footerMax
            assertTrue("Video has no measured space at ${height}dp",
                remaining.value >= height * 0.40f)
            assertTrue(pane.headerMax > 0.dp)
            assertTrue(pane.footerMax > 0.dp)
        }
    }

    @Test fun largeTextTurnsLandscapeIntoCompactScrollablePanels() {
        assertFalse(extrasEmbedPanelLimits(700.dp, 1f).compactHeight)
        assertTrue(extrasEmbedPanelLimits(700.dp, 2f).compactHeight)
    }

    @Test fun tinyWindowDoesNotInheritUnpayableMinimumPanelHeights() {
        val pane = extrasEmbedPanelLimits(120.dp, 1f)
        assertTrue(pane.compactHeight)
        assertTrue(pane.headerMax + pane.footerMax < 120.dp)
    }
}
