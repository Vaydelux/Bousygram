package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
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

/**
 * Native Compose rendering regression tests. Uses a test host, not a fake Telegram
 * stream. UI visibility and click callbacks are verified without authentication.
 */
@RunWith(AndroidJUnit4::class)
class PlayerChromeInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    private var playClicks = 0

    private fun showChrome(layout: PlayerChromeLayout) {
        playClicks = 0
        composeRule.setContent {
            BousygramTheme {
                var options by remember { mutableStateOf(false) }
                val playFocus = remember { FocusRequester() }
                val optionsFocus = remember { FocusRequester() }
                val firstOptionFocus = remember { FocusRequester() }
                Column {
                    PlayerTransportControls(
                        layout = layout,
                        isPlayingOrPending = false,
                        optionsExpanded = options,
                        onPrevious = {},
                        onSeekBack = {},
                        onPlayPause = { playClicks++ },
                        onSeekForward = {},
                        onNext = {},
                        onOptions = { options = !options },
                        playPauseFocusRequester = playFocus,
                        optionsFocusRequester = optionsFocus
                    )
                    if (options) {
                        PlayerOptionsPanel(
                            layout = layout,
                            onTracks = {},
                            onSpeed = {},
                            speedLabel = "1.0x",
                            onAspect = {},
                            aspectLabel = "Fit",
                            onEpisodes = null,
                            onSwitchEngine = null,
                            onPrevious = {},
                            onNext = {},
                            onSeekBack30 = {},
                            onSeekForward30 = {},
                            onClose = { options = false },
                            firstOptionFocusRequester = firstOptionFocus
                        )
                    }
                }
            }
        }
    }

    @Test fun phoneHasFourPrimaryTouchTargetsAndDismissibleOptions() {
        showChrome(PlayerChromeLayout.TOUCH_COMPACT)
        composeRule.onNodeWithTag("player_transport_controls").assertExists()
        composeRule.onNodeWithTag("player_previous").assertDoesNotExist()
        composeRule.onNodeWithTag("player_next").assertDoesNotExist()
        composeRule.onNodeWithTag("player_toggle_playback").performClick()
        composeRule.runOnIdle { assertEquals(1, playClicks) }

        composeRule.onNodeWithTag("player_options_toggle").performClick()
        composeRule.onNodeWithTag("player_options_panel").assertExists()
        composeRule.onNodeWithText("Audio / CC").assertExists()
        composeRule.onNodeWithTag("player_options_toggle").performClick()
        composeRule.onNodeWithTag("player_options_panel").assertDoesNotExist()
    }

    @Test fun tabletShowsWideTransportTargets() {
        showChrome(PlayerChromeLayout.TOUCH_WIDE)
        composeRule.onNodeWithTag("player_previous").assertExists()
        composeRule.onNodeWithTag("player_next").assertExists()
        composeRule.onNodeWithTag("player_seek_back").assertExists()
        composeRule.onNodeWithTag("player_seek_forward").assertExists()
    }

    @Test fun tvShowsDedicatedFocusFirstTransportAndOptions() {
        showChrome(PlayerChromeLayout.TV)
        // TV uses D-pad Left/Right for seek and keeps the primary transport compact.
        // Previous/Next are deliberately absent from the TV transport strip.
        composeRule.onNodeWithTag("player_previous").assertDoesNotExist()
        composeRule.onNodeWithTag("player_next").assertDoesNotExist()
        composeRule.onNodeWithTag("player_seek_back").assertDoesNotExist()
        composeRule.onNodeWithTag("player_seek_forward").assertDoesNotExist()
        composeRule.onNodeWithTag("player_toggle_playback").assertExists()
        composeRule.onNodeWithTag("player_options_toggle").performClick()
        composeRule.onNodeWithTag("player_options_panel").assertExists()
        composeRule.onNodeWithText("Audio / CC").assertExists()
    }
}
