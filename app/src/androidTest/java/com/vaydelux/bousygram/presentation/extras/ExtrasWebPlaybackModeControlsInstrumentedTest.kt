package com.vaydelux.bousygram.presentation.extras

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** No live third-party anime server is required to verify recovery mode UX. */
@RunWith(AndroidJUnit4::class)
class ExtrasWebPlaybackModeControlsInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun strictCompatibilityLegacyAndRestoreAreReachableByExplicitClicks() {
        rule.setContent {
            BousygramTheme {
                var compatible by remember { mutableStateOf(false) }
                var legacy by remember { mutableStateOf(false) }
                ExtrasWebPlaybackModeControls(
                    compatibility = compatible,
                    legacyWindows = legacy,
                    onStrict = { compatible = false; legacy = false },
                    onCompatibility = { compatible = true; legacy = false },
                    onLegacy = { compatible = true; legacy = true }
                )
            }
        }

        val mainToggle = rule.onNodeWithTag("extras-webview-compatibility")
        val legacyToggle = rule.onNodeWithTag("extras-webview-legacy-navigation")
        mainToggle.assertExists()
        legacyToggle.assertDoesNotExist()

        mainToggle.performClick()
        rule.onNodeWithText("Use strict WebView mode").assertExists()
        legacyToggle.assertExists()
        rule.onNodeWithText("Still black? Try legacy WebView navigation").assertExists()

        legacyToggle.performClick()
        rule.onNodeWithText("Use popup-isolated compatibility").assertExists()
        legacyToggle.performClick()
        rule.onNodeWithText("Still black? Try legacy WebView navigation").assertExists()

        mainToggle.performClick()
        legacyToggle.assertDoesNotExist()
        rule.onNodeWithText("Black screen? Try compatibility (cookies + autoplay)")
            .assertExists()
    }
}
