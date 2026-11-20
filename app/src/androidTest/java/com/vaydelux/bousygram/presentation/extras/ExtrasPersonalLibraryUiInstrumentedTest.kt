package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Pure fixture-driven interaction tests: no TMDB key or network needed. */
@RunWith(AndroidJUnit4::class)
class ExtrasPersonalLibraryUiInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    private fun entry(type: String = "MOVIES", id: Int = 7, title: String = "Saved fixture") =
        ExtrasLibraryEntry(type, id, title, "2026", "Offline description")

    @Test fun savedCardOpensAndCanBeRemovedWithoutAProviderCall() {
        val selected = mutableStateOf("")
        rule.setContent {
            BousygramTheme {
                var library by remember {
                    mutableStateOf(ExtrasLibrarySnapshot(saved = listOf(entry())))
                }
                Box(Modifier.fillMaxSize()) {
                    ExtrasPersonalLibraryPanel(
                        snapshot = library, ready = true, busy = false,
                        error = null, isAndroidTv = false,
                        onBrowse = {},
                        onOpen = { selected.value = it.title },
                        onRemove = {
                            library = ExtrasPersonalLibraryPolicy.removeSaved(
                                library, it.type.name, it.id
                            )
                        },
                        onClearRecent = {}, onRetry = {}
                    )
                }
            }
        }
        rule.onNodeWithTag("extras-library-item-MOVIES:7").assertExists()
        rule.onNodeWithTag("extras-poster-movies-7").performClick()
        rule.runOnIdle { assertEquals("Saved fixture", selected.value) }
        rule.onNodeWithTag("extras-library-remove-MOVIES:7").performClick()
        rule.onNodeWithTag("extras-library-empty").assertExists()
    }

    @Test fun recentHistoryHasConfirmationAndDoesNotEraseSavedTitles() {
        rule.setContent {
            BousygramTheme {
                var library by remember {
                    mutableStateOf(ExtrasLibrarySnapshot(
                        saved = listOf(entry()),
                        recent = listOf(entry("TV", 8, "Recently viewed metadata"))
                    ))
                }
                ExtrasPersonalLibraryPanel(
                    snapshot = library, ready = true, busy = false,
                    error = null, isAndroidTv = false,
                    onBrowse = {}, onOpen = {}, onRemove = {},
                    onClearRecent = {
                        library = ExtrasPersonalLibraryPolicy.clearRecent(library)
                    },
                    onRetry = {}
                )
            }
        }
        rule.onNodeWithTag("extras-library-recent-tab").performClick()
        rule.onNodeWithTag("extras-library-item-TV:8").assertExists()
        rule.onNodeWithTag("extras-library-clear-recent").performClick()
        rule.onNodeWithText("Clear recently explored?").assertExists()
        rule.onNodeWithText("Clear recent", substring = false).performClick()
        rule.onNodeWithTag("extras-library-empty").assertExists()
        rule.onNodeWithTag("extras-library-saved-tab").performClick()
        rule.onNodeWithTag("extras-library-item-MOVIES:7").assertExists()
    }

    @Test fun providerErrorOffersRetryAndIndependentAnimeEscape() {
        val retries = mutableIntStateOf(0)
        val browse = mutableIntStateOf(0)
        rule.setContent {
            BousygramTheme {
                ExtrasFailureScreen(
                    message = "The catalog provider is temporarily unavailable.",
                    isTv = true,
                    onRetry = { retries.intValue += 1 },
                    onBrowseAnime = { browse.intValue += 1 }
                )
            }
        }
        rule.onNodeWithTag("extras-failure-screen").assertExists()
        rule.onNodeWithTag("extras-failure-retry").performClick()
        rule.onNodeWithTag("extras-failure-browse-anime").performClick()
        rule.runOnIdle {
            assertEquals(1, retries.intValue)
            assertEquals(1, browse.intValue)
        }
    }

    @Test fun detailSaveButtonReflectsItsStateAndCanToggle() {
        val spec = ExtrasDetailLayoutPolicy.resolve(360f, 740f, false)
        val fixture = ExtrasDetail(
            item = ExtrasItem(11, ExtrasType.MOVIES, "Local fixture"),
            description = "No network used",
            genres = emptyList(), facts = emptyList(),
            cast = emptyList(), reviews = emptyList(), seasons = emptyList()
        )
        rule.setContent {
            BousygramTheme {
                var saved by remember { mutableStateOf(false) }
                ExtrasDetailSummary(
                    fixture, spec,
                    isSaved = saved, libraryReady = true,
                    onSaveToggle = { saved = !saved }
                )
            }
        }
        rule.onNodeWithTag("extras-save-title").performClick()
        rule.onNodeWithText("Saved to My Extras").assertExists()
        rule.onNodeWithTag("extras-save-title").performClick()
        rule.onNodeWithText("Save to My Extras").assertExists()
    }

    @Test fun loadingAndFailureStatesDoNotPretendToHaveTitles() {
        rule.setContent {
            BousygramTheme {
                ExtrasPersonalLibraryPanel(
                    snapshot = ExtrasLibrarySnapshot(), ready = false, busy = false,
                    error = null, isAndroidTv = false,
                    onBrowse = {}, onOpen = {}, onRemove = {},
                    onClearRecent = {}, onRetry = {}
                )
            }
        }
        rule.onNodeWithTag("extras-library-loading").assertExists()
        rule.onNodeWithTag("extras-library-grid").assertDoesNotExist()
    }
}
