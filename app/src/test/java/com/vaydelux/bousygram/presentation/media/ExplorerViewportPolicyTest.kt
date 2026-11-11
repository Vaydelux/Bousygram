package com.vaydelux.bousygram.presentation.media

import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.MediaCardDensity
import com.vaydelux.bousygram.presentation.theme.mediaCardDensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 540dp televisions and landscape phones must expose media, not headers.
 * The density policy never checks native ABI or Android architecture.
 */
class ExplorerViewportPolicyTest {
    @Test fun allTelevisionsUseCompactBrowsingHeader() {
        assertEquals(ExplorerHeaderDensity.COMPACT, explorerHeaderDensity(true, 960.dp, 540.dp))
        assertEquals(ExplorerHeaderDensity.COMPACT, explorerHeaderDensity(true, 1280.dp, 720.dp))
        assertEquals(ExplorerHeaderDensity.COMPACT, explorerHeaderDensity(true, 720.dp, 400.dp))
    }

    @Test fun portraitAndLandscapeAdaptToAvailableHeight() {
        assertEquals(ExplorerHeaderDensity.NORMAL, explorerHeaderDensity(false, 393.dp, 852.dp))
        assertEquals(ExplorerHeaderDensity.COMPACT, explorerHeaderDensity(false, 844.dp, 390.dp))
        assertEquals(ExplorerHeaderDensity.COMPACT, explorerHeaderDensity(false, 1024.dp, 600.dp))
        assertFalse(explorerHeaderShowInlineActions(720.dp))
        assertTrue(explorerHeaderShowInlineActions(960.dp))
    }

    @Test fun mediaCardsDropRepeatedMetadataOnlyWhenViewportIsShort() {
        assertEquals(MediaCardDensity.SHORT_TV, mediaCardDensity(true, 960.dp, 540.dp))
        assertEquals(MediaCardDensity.SHORT_TV, mediaCardDensity(true, 720.dp, 400.dp))
        assertEquals(MediaCardDensity.SHORT_LANDSCAPE, mediaCardDensity(false, 844.dp, 390.dp))
        assertEquals(MediaCardDensity.STANDARD, mediaCardDensity(false, 393.dp, 852.dp))
        assertEquals(MediaCardDensity.STANDARD, mediaCardDensity(true, 1280.dp, 720.dp))
    }
}
