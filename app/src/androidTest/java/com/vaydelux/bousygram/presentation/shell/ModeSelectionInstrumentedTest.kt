package com.vaydelux.bousygram.presentation.shell

import com.vaydelux.bousygram.presentation.components.writeUiEvidence
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests visible, touch-accessible mode routes without creating an Extras client. */
@RunWith(AndroidJUnit4::class)
class ModeSelectionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun extrasMaintenanceIsolatedAndReturnsToSelection() {
        composeRule.setContent {
            BousygramTheme {
                var extras by remember { mutableStateOf(false) }
                if (extras) ExtrasMaintenanceScreen(onBack = { extras = false })
                else BousygramModeSelection(onTelegram = {}, onExtras = { extras = true })
            }
        }
        // The redundant second app-name line was removed from the spacious hero.
        // Its stable tagline remains present on both compact and wide layouts.
        composeRule.onNodeWithText("Your screen. Your library.").assertExists()
        writeUiEvidence(composeRule, "mode-selection")
        composeRule.onNodeWithTag("mode-extras").performClick()
        composeRule.onNodeWithText("More to Explore").assertExists()
        composeRule.onNodeWithText("Back to Mode Selection").performClick()
        composeRule.onNodeWithText("Telegram Media").assertExists()
    }

    @Test fun telegramChoiceIsSingleExplicitAction() {
        var telegramLaunches = 0
        composeRule.setContent {
            BousygramTheme {
                BousygramModeSelection(onTelegram = { telegramLaunches++ }, onExtras = {})
            }
        }
        composeRule.onNodeWithTag("mode-telegram").performClick()
        composeRule.runOnIdle { assertEquals(1, telegramLaunches) }
    }
}
