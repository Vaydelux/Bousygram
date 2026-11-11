package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Offline source picker checks: no Telegram login or provider stream required. */
@RunWith(AndroidJUnit4::class)
class ExtrasEmbedServerDropdownInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tvRemoteCanOpenServerMenuAndChangeProvider() {
        var selected: ExtrasEmbedServer = ExtrasEmbedServer.CINESRC
        compose.setContent {
            BousygramTheme {
                var current by remember { mutableStateOf(ExtrasEmbedServer.CINESRC) }
                ExtrasEmbedServerDropdown(
                    server = current,
                    isTv = true,
                    onSelected = { current = it; selected = it }
                )
            }
        }
        val button = compose.onNodeWithTag("extras-server-dropdown")
        button.assertExists()
        button.performSemanticsAction(SemanticsActions.RequestFocus)
        button.performClick()
        compose.onNodeWithTag("extras-server-xxiku").assertExists().performClick()
        compose.runOnIdle { assertEquals(ExtrasEmbedServer.XXIKU, selected) }
        compose.onNodeWithTag("extras-server-dropdown").performClick()
        compose.onNodeWithTag("extras-server-xxiku").assertIsSelected()
    }

    @Test fun animeSourceIsSelectableBeforeAnyWebViewIsCreated() {
        var picked = ExtrasAnimeEmbedServer.FILMU
        var launched = false
        compose.setContent {
            BousygramTheme {
                var current by remember { mutableStateOf(ExtrasAnimeEmbedServer.FILMU) }
                ExtrasAnimeSourceChooser(
                    request = ExtrasEmbedRequest.anime(21, episode = 2, title = "Anime fixture"),
                    selected = current,
                    isTv = true,
                    onSelect = { current = it; picked = it },
                    onOpen = { launched = true },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithTag("extras-anime-server-chooser").assertExists()
        compose.onNodeWithTag("extras-server-dropdown").assertExists().performClick()
        // This upstream URL only identifies the show, not the chosen episode.
        compose.onNodeWithTag("extras-anime-server-cinezo").assertDoesNotExist()
        compose.onNodeWithTag("extras-anime-server-xxiku").assertExists().performClick()
        compose.runOnIdle {
            assertEquals(ExtrasAnimeEmbedServer.XXIKU, picked)
            assertEquals(false, launched)
        }
        compose.onNodeWithTag("extras-anime-open-selected-server").performClick()
        compose.runOnIdle { assertEquals(true, launched) }
    }

    @Test fun animeServerDialogKeepsScrollableChoicesWithinPanelAndCanClose() {
        compose.setContent {
            BousygramTheme {
                ExtrasAnimeEmbedServerDropdown(
                    server = ExtrasAnimeEmbedServer.FILMU,
                    isTv = true,
                    onSelected = {}
                )
            }
        }
        compose.onNodeWithTag("extras-server-dropdown").performClick()
        compose.onNodeWithTag("extras-server-dialog").assertIsDisplayed()
        compose.onNodeWithTag("extras-server-options").assertIsDisplayed()
        val panel = compose.onNodeWithTag("extras-server-dialog")
            .fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("extras-server-options")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("The server list should stay inside the dialog panel", list.bottom <= panel.bottom + 1f)
        assertTrue("The server list should start inside the dialog panel", list.top >= panel.top - 1f)
        compose.onNodeWithTag("extras-server-close").performClick()
        compose.onNodeWithTag("extras-server-dropdown").assertIsDisplayed()
    }

    @Test fun fullscreenAlwaysHasNativeAnimeServerRecoveryControls() {
        var exited = false
        var returnedToPicker = false
        compose.setContent {
            BousygramTheme {
                ExtrasFullscreenRecoveryControls(
                    isAnime = true,
                    onExitFullscreen = { exited = true },
                    onChooseAnimeServer = { returnedToPicker = true }
                )
            }
        }
        compose.onNodeWithTag("extras-embed-exit-html-fullscreen").assertExists().performClick()
        compose.onNodeWithTag("extras-anime-fullscreen-servers").assertExists().performClick()
        compose.runOnIdle {
            assertEquals(true, exited)
            assertEquals(true, returnedToPicker)
        }
    }

    @Test fun narrowPhoneStillShowsSourceSelector() {
        compose.setContent {
            BousygramTheme {
                Box(Modifier.width(280.dp)) {
                    ExtrasEmbedServerDropdown(
                        server = ExtrasEmbedServer.CINESRC,
                        isTv = false,
                        onSelected = {}
                    )
                }
            }
        }
        compose.onNodeWithTag("extras-server-dropdown").assertExists().performClick()
        compose.onNodeWithTag("extras-server-embed_heaven").assertExists()
    }
}
