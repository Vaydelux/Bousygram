package com.vaydelux.bousygram.presentation.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    @Test fun cinematicOverlayHasEdgeActionsAndExpandableNavigation() {
        playClicks = 0
        composeRule.setContent {
            BousygramTheme {
                var options by remember { mutableStateOf(false) }
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
    }
}
