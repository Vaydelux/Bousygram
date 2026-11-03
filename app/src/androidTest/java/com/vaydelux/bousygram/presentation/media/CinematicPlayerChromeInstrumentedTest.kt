package com.vaydelux.bousygram.presentation.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CinematicPlayerChromeInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()
    private var playClicks = 0
    private var changeChromeVisibility: ((Boolean) -> Unit)? = null
    @Test fun cinematicOverlayHasEdgeActionsAndExpandableNavigation() {
        playClicks = 0
        composeRule.setContent {
            BousygramTheme {
                var options by remember { mutableStateOf(false) }
                var chromeVisible by remember { mutableStateOf(true) }
                changeChromeVisibility = { chromeVisible = it }
                var section by remember { mutableStateOf(CinematicMenuSection.ROOT) }
                val playFocus = remember { FocusRequester() }
                val optionsFocus = remember { FocusRequester() }
                val firstFocus = remember { FocusRequester() }
                CinematicPlayerChrome(
                    state = PlayerHudState(
                        title = "Sample video", fileName = "sample.mp4",
                        positionMs = 15_000L, durationMs = 120_000L,
                        durationTrusted = true, speed = 1f,
                        isPlaying = false, isBuffering = false,
                        error = null, trackLabel = "1 / 2"
                    ),
                    isTv = true,
                    optionsVisible = options,
                    controlsVisible = chromeVisible,
                    menuSection = section,
                    onMenuSectionChange = { section = it },
                    onScrub = {},
                    onOptions = { options = !options },
                    onCloseOptions = { options = false },
                    onExit = {}, onStartOver = {}, onRetry = {},
                    onPlayPause = { playClicks++ },
                    onSeekBy = {}, onSeekTo = {},
                    onPrevious = {}, onNext = {},
                    onTracks = {}, onAspect = {}, onSpeed = {},
                    onEpisodes = {}, episodesFocus = remember { FocusRequester() },
                    onDecoder = {}, playFocus = playFocus,
                    optionsFocus = optionsFocus, firstOptionFocus = firstFocus,
                    seekFocus = remember { FocusRequester() }, retryFocus = null
                )
            }
        }
        composeRule.onNodeWithTag("cinematic_player_chrome").assertExists()
        composeRule.onNodeWithTag("cinematic_title").assertExists()
        composeRule.onNodeWithTag("cinematic_scrubber").assertExists()
        composeRule.onNodeWithTag("cinematic_play").performClick()
        composeRule.runOnIdle { assertEquals(1, playClicks) }
        composeRule.onNodeWithTag("cinematic_more").performClick()
        composeRule.onNodeWithTag("cinematic_navigation_menu").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_group_playback").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_group_library").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_group_advanced").assertExists()

        composeRule.onNodeWithTag("cinematic_menu_group_playback").performClick()
        composeRule.onNodeWithTag("cinematic_menu_tracks").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_speed").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_back").performClick()
        composeRule.onNodeWithTag("cinematic_menu_group_library").performClick()
        composeRule.onNodeWithTag("cinematic_menu_previous").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_next").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_back").performClick()
        composeRule.onNodeWithTag("cinematic_menu_group_library").assertIsFocused()
        composeRule.onNodeWithTag("cinematic_menu_group_advanced").performClick()
        composeRule.onNodeWithTag("cinematic_menu_decoder").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_help").performClick()
        composeRule.onNodeWithTag("cinematic_menu_help_text").assertExists()
        composeRule.onNodeWithTag("cinematic_menu_back").performClick()
        composeRule.onNodeWithTag("cinematic_menu_group_advanced").assertIsFocused()

        // The fullscreen container stays mounted as controls fade out;
        // focusable buttons and the dropdown must leave the semantics tree.
        composeRule.runOnIdle { changeChromeVisibility?.invoke(false) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cinematic_player_chrome").assertExists()
        composeRule.onNodeWithTag("cinematic_play").assertDoesNotExist()
        composeRule.onNodeWithTag("cinematic_navigation_menu").assertDoesNotExist()
        composeRule.runOnIdle { changeChromeVisibility?.invoke(true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cinematic_play").assertExists()
    }
}
