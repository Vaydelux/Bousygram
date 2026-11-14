package com.vaydelux.bousygram.presentation.media

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramExplorerCompactHeaderPolicyTest {
    @Test fun tvKeepsCompactToolbar() {
        assertEquals(
            ExplorerHeaderDensity.COMPACT,
            explorerHeaderDensity(isTv = true, widthDp = 960.dp, heightDp = 540.dp)
        )
    }

    @Test fun shortLandscapeKeepsGridVisible() {
        assertEquals(
            ExplorerHeaderDensity.COMPACT,
            explorerHeaderDensity(isTv = false, widthDp = 873.dp, heightDp = 393.dp)
        )
        assertFalse(explorerHeaderShowInlineActions(873.dp))
    }

    @Test fun tallPhoneKeepsReadableHeader() {
        assertEquals(
            ExplorerHeaderDensity.NORMAL,
            explorerHeaderDensity(isTv = false, widthDp = 393.dp, heightDp = 873.dp)
        )
        assertFalse(explorerHeaderShowInlineActions(393.dp))
    }

    @Test fun wideScreenCanExposeActionsInline() {
        assertFalse(explorerHeaderShowInlineActions(929.dp))
        assertTrue(explorerHeaderShowInlineActions(930.dp))
    }
}
