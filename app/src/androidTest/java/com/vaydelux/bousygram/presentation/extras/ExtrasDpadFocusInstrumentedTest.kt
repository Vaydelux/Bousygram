package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Offline D-pad contract. No TMDB key, Telegram login or AniList service
 * required: tests the real Compose tabs/poster input surfaces independently.
 */
@RunWith(AndroidJUnit4::class)
class ExtrasDpadFocusInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun centerKeyActivatesFocusedTvTab() {
        rule.setContent {
            BousygramTheme {
                var selected by remember { mutableIntStateOf(0) }
                Column {
                    ExtrasCatalogTabs(selected) { selected = it }
                    Text("Selected: ${ExtrasType.entries[selected].label}")
                }
            }
        }

        val tvTab = rule.onNodeWithTag("extras-tab-tv")
        tvTab.assertExists()
        tvTab.performSemanticsAction(SemanticsActions.RequestFocus)
        tvTab.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Selected: TV Shows").assertExists()
        rule.onNodeWithTag("extras-tab-anime").assertExists()
    }

    @Test fun centerKeyOpensFocusedPosterWithoutProviderNetwork() {
        rule.setContent {
            BousygramTheme {
                var opened by remember { mutableStateOf(false) }
                Column {
                    ExtrasPosterCard(
                        item = ExtrasItem(501, ExtrasType.ANIME, "Focus fixture", poster = null)
                    ) { opened = true }
                    if (opened) Text("Poster opened")
                }
            }
        }
        val poster = rule.onNodeWithTag("extras-poster-anime-501")
        poster.assertExists()
        poster.performSemanticsAction(SemanticsActions.RequestFocus)
        poster.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.onNodeWithText("Poster opened").assertExists()
    }
}
