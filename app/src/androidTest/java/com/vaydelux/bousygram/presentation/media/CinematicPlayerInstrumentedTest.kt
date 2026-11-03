package com.vaydelux.bousygram.presentation.media

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

/** Tests pure Compose chrome without Telegram login or playing a protected stream. */
@RunWith(AndroidJUnit4::class)
class CinematicPlayerInstrumentedTest {
    @get:Rule val rule = createComposeRule()
    private var playToggles = 0
    private var speedChanges = 0

    private fun showChrome(visible: Boolean) {
        playToggles = 0
        speedChanges = 0
        rule.setContent {
            BousygramTheme {
                var menuExpanded by remember { mutableStateOf(false) }
                val playFocus = remember { FocusRequester() }
                val optionsFocus = remember { FocusRequester() }
                val menuFocus = remember { FocusRequester() }
                val seekFocus = remember { FocusRequester() }
                val episodesFocus = remember { FocusRequester() }
                val retryFocus = remember { FocusRequester() }
                CinematicPlayerOverlay(
                    state = PlayerHudState(
                        title = "Example video", fileName = "example.mkv",
                        positionMs = 30_000L, durationMs = 90_000L,
                        durationTrusted = true, speed = 1.0f,
                        isPlaying = true, playWhenReady = true,
                        isBuffering = false, error = null,
                        trackLabel = "1 / 2", mediaKey = "test:video"
                    ),
                    controlsVisible = visible, compact = false, isTv = false,
                    optionsVisible = menuExpanded,
                    onScrubActiveChanged = {},
                    onOptionsToggle = { menuExpanded = !menuExpanded },
                    onOptionsClose = { menuExpanded = false },
                    onExit = {}, onStartOver = {}, onRetry = {},
                    onPlayPause = { playToggles++ }, onSeekBy = {},
                    onSeekTo = {}, onSkipPrevious = {}, onSkipNext = {},
                    onTracks = {}, onToggleResizeMode = {},
                    onSpeed = { speedChanges++ }, onEpisodes = {},
                    episodesFocusRequester = episodesFocus, onSwitchEngine = {},
                    playPauseFocusRequester = playFocus,
                    optionsFocusRequester = optionsFocus,
                    firstOptionFocusRequester = menuFocus,
                    seekBarFocusRequester = seekFocus,
                    retryFocusRequester = retryFocus
                )
            }
        }
    }

    @Test fun activeChromeUsesEdgeControlsAndExpandableNavigation() {
        showChrome(visible = true)
        rule.onNodeWithTag("cinematic_player_overlay").assertExists()
        rule.onNodeWithTag("cinematic_back").assertExists()
        rule.onNodeWithText("Example video").assertExists()
        rule.onNodeWithTag("cinematic_progress_active").assertExists()
        rule.onNodeWithTag("cinematic_play_pause").performClick()
        rule.runOnIdle { assertEquals(1, playToggles) }
        rule.onNodeWithTag("cinematic_more").performClick()
        rule.onNodeWithTag("cinematic_options_dropdown").assertExists()
        rule.onNodeWithText("Audio & subtitles").assertExists()
        rule.onNodeWithText("Speed · 1.0×").performClick()
        rule.runOnIdle { assertEquals(1, speedChanges) }
        rule.onNodeWithTag("cinematic_more").performClick()
        rule.onNodeWithTag("cinematic_options_dropdown").assertDoesNotExist()
    }

    @Test fun hiddenChromeKeepsPassiveHairlineAndNoFloatingButtons() {
        showChrome(visible = false)
        rule.onNodeWithTag("cinematic_player_overlay").assertExists()
        rule.onNodeWithTag("cinematic_progress_passive").assertExists()
        rule.onNodeWithTag("cinematic_play_pause").assertDoesNotExist()
        rule.onNodeWithTag("cinematic_more").assertDoesNotExist()
        rule.onNodeWithTag("cinematic_options_dropdown").assertDoesNotExist()
    }
}
