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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasClickAction
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // Assert the actual navigation actions, not mutable brand copy.
        composeRule.onNodeWithTag("mode-telegram").assertHasClickAction()
        composeRule.onNodeWithTag("mode-extras").assertHasClickAction()
        writeUiEvidence(composeRule, "mode-selection")
        composeRule.onNodeWithTag("mode-extras").performClick()
        composeRule.onNodeWithText("More to Explore").assertExists()
        composeRule.onNodeWithText("Back to Mode Selection").performClick()
        composeRule.onNodeWithText("Telegram Media").assertExists()
    }

    @Test fun startupAppearancePickerUsesTheSameThemeStateAsEveryRoute() {
        composeRule.setContent {
            var theme by remember { mutableStateOf(BousygramThemePreset.DARK) }
            BousygramTheme(preset = theme) {
                BousygramModeSelection(
                    onTelegram = {}, onExtras = {},
                    themePreset = theme,
                    onThemePresetChange = { theme = it }
                )
            }
        }
        composeRule.onNodeWithTag("startup-change-appearance").performClick()
        composeRule.onNodeWithTag("startup-appearance-studio").assertExists()
        composeRule.onNodeWithTag("theme-preset-dark").assertIsSelected()
        composeRule.onNodeWithTag("theme-preset-aurora").performScrollTo().performClick()
        composeRule.onNodeWithTag("theme-preset-aurora").assertIsSelected()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag("startup-appearance-studio").assertDoesNotExist()
        composeRule.onNodeWithTag("mode-extras").assertExists()
    }

    @Test fun startupModeCardsHaveMatchingHeightsAndAppearanceActionIsAccessible() {
        composeRule.setContent {
            BousygramTheme {
                BousygramModeSelection(onTelegram = {}, onExtras = {})
            }
        }
        val telegram = composeRule.onNodeWithTag("mode-telegram").fetchSemanticsNode().boundsInRoot
        val extras = composeRule.onNodeWithTag("mode-extras").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Mode cards should have equal visual heights when one title wraps",
            kotlin.math.abs(telegram.height - extras.height) < 2f
        )
        assertTrue("Cards must have a real gap", extras.top > telegram.bottom)
        composeRule.onNodeWithTag("startup-change-appearance").assertExists()
        composeRule.onNodeWithTag("startup-change-appearance").performClick()
        composeRule.onNodeWithTag("startup-appearance-studio").assertExists()
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
