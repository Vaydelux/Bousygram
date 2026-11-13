package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No provider credentials, routes or playback are needed to test status components. */
@RunWith(AndroidJUnit4::class)
class BousygramStatusComponentsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun inlineErrorKeepsItsExistingRecoveryActionReachableOnLightTheme() {
        var retries = 0
        compose.setContent {
            BousygramTheme(preset = BousygramThemePreset.IVORY) {
                BousygramInlineStatus(
                    message = "Catalog temporarily unavailable",
                    tone = BousygramStatusTone.ERROR,
                    trailingAction = {
                        TextButton(onClick = { retries++ }) { Text("Retry") }
                    }
                )
            }
        }
        compose.onNodeWithTag("bousygram-inline-status").assertExists()
        compose.onNodeWithTag("bousygram-inline-status-message").assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun compactErrorAndEmptyStateDoNotReplaceExistingActions() {
        compose.setContent {
            BousygramTheme(preset = BousygramThemePreset.HIGH_CONTRAST) {
                var actions by remember { mutableIntStateOf(0) }
                Column {
                    BousygramCenteredStatus(
                        title = "Details unavailable",
                        message = "Unable to load details",
                        tone = BousygramStatusTone.ERROR
                    )
                    EmptyStateView(
                        title = "No favorites yet",
                        message = "Browse your collection.",
                        actionText = "Browse videos",
                        onAction = { actions++ }
                    )
                    Text("Actions: $actions")
                }
            }
        }
        compose.onNodeWithTag("bousygram-centered-status-title").assertExists()
        compose.onNodeWithTag("bousygram-centered-status-message").assertExists()
        compose.onNodeWithTag("bousygram-empty-state-title").assertExists()
        compose.onNodeWithTag("bousygram-empty-state-message").assertExists()
        compose.onNodeWithTag("bousygram-empty-state-action").performClick()
        compose.onNodeWithText("Actions: 1").assertExists()
    }

    @Test fun warningBannerDisplaysMessageWithoutAddingAnyFocusableAction() {
        compose.setContent {
            BousygramTheme {
                BousygramInlineStatus(
                    message = "Some saved items are unavailable",
                    tone = BousygramStatusTone.WARNING
                )
            }
        }
        compose.onNodeWithTag("bousygram-inline-status-message").assertExists()
        compose.onNodeWithText("Some saved items are unavailable").assertExists()
        compose.onNodeWithText("Retry").assertDoesNotExist()
    }
}
