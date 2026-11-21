package com.vaydelux.bousygram.presentation.media

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.components.writeUiEvidence
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No Telegram account or provider network access needed. */
@RunWith(AndroidJUnit4::class)
class TelegramHomeOverviewInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeHasDistinctContentAndOpensRealLibraryDirectory() {
        val selected = mutableStateOf<ExplorerSection?>(null)
        rule.setContent {
            BousygramTheme {
                TelegramHomeOverview(
                    recentSaved = emptyList(),
                    isLoadingSaved = false,
                    connection = null,
                    isTv = false,
                    privacyMode = false,
                    onPlay = {},
                    onFocusedMedia = {},
                    onOpenLibrary = {},
                    onOpenSection = { selected.value = it },
                    onOpenSearch = {},
                    onOpenFavorites = {},
                    onOpenSettings = {}
                )
            }
        }
        rule.onNodeWithTag("telegram-home-overview").assertExists()
        rule.onNodeWithTag("telegram-home-preview-status").assertExists()
        writeUiEvidence(rule, "telegram-home-overview")
        rule.onNodeWithTag("telegram-home-section-groups").performClick()
        rule.runOnIdle { assertEquals(ExplorerSection.GROUPS, selected.value) }
    }
}
