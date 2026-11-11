package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.lazy.grid.LazyGridState
import com.vaydelux.bousygram.presentation.components.writeUiEvidence
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vaydelux.bousygram.presentation.media.vm.BrowseRestoration
import com.vaydelux.bousygram.presentation.media.vm.ExplorerLevel
import com.vaydelux.bousygram.presentation.media.vm.ExplorerPosition
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-only regression: no Telegram login, networking, streaming or database. */
@RunWith(AndroidJUnit4::class)
class TelegramLibraryDesignInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun librarySectionsAreReadableAndSelectableWithoutTelegramAccount() {
        compose.setContent {
            BousygramTheme {
                var selectedSection by remember { mutableStateOf(ExplorerSection.SAVED) }
                val grid = remember { LazyGridState() }
                val focus = remember { FocusRequester() }
                TelegramExplorerDashboard(
                    position = ExplorerPosition(
                        selectedSection,
                        if (selectedSection == ExplorerSection.SAVED)
                            ExplorerLevel.MEDIA else ExplorerLevel.SOURCES
                    ),
                    sources = emptyList(),
                    topics = emptyList(),
                    selectedChatTitle = "",
                    selectedChatId = null,
                    selectedTopicId = null,
                    videos = emptyList(),
                    isLoading = false,
                    isLoadingSources = false,
                    isLoadingTopics = false,
                    errorMessage = null,
                    sourceErrorMessage = null,
                    isLoadingMore = false,
                    hasMore = false,
                    discardedMediaCount = 0,
                    privacyMode = false,
                    gridState = grid,
                    sourceFolderRestoration = BrowseRestoration(),
                    onSourceFocused = {},
                    onSourceViewport = { _, _ -> },
                    onSelectTab = { selectedSection = it },
                    onSelectSource = {},
                    onSelectTopic = {},
                    onBrowseAllMedia = {},
                    focusedTopicId = null,
                    onTopicFocused = {},
                    onGoUp = {},
                    onPlay = {},
                    onFocusMedia = {},
                    restoreFocusedMediaKey = null,
                    restoreFocusRequester = focus,
                    onLoadMore = {},
                    onRefreshSources = {},
                    onRefreshTopics = {},
                    onRefreshMedia = {},
                    onSearch = {},
                    onFavorites = {},
                    onSettings = {},
                    hasResume = false,
                    onResume = {}
                )
            }
        }
        compose.onNodeWithText("Your library").assertExists()
        // The Library used to paint an opaque flat layer over the premium shell.
        // It must now use exactly the same cached backdrop as Extras/Settings.
        compose.onNodeWithTag("bousygram-premium-backdrop").assertExists()
        compose.onNodeWithTag("telegram-library-collection").assertExists()
        // A nonempty Library should show media above the fold, not just page
        // headings and oversized collection metadata. The empty-state viewport
        // uses the same grid slot and catches regressions in its height budget.
        val fullHeight = compose.onNodeWithTag("bousygram-premium-backdrop")
            .fetchSemanticsNode().boundsInRoot.height
        val gridHeight = compose.onNodeWithTag("telegram-explorer-grid")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("Library grid occupies only ${gridHeight / fullHeight} of the viewport",
            fullHeight > 0f && gridHeight / fullHeight >= 0.58f)
        compose.onNodeWithTag("telegram-library-tab-saved").assertIsSelected()
        // An empty folder is not an error. Retry is reserved for actual failures.
        compose.onNodeWithText("Refresh").assertExists()
        compose.onNodeWithText("Retry").assertDoesNotExist()
        writeUiEvidence(compose, "telegram-library")
        compose.onNodeWithTag("telegram-library-tab-channels").performClick()
        compose.onNodeWithTag("telegram-library-tab-channels").assertIsSelected()
        compose.onNodeWithTag("telegram-library-tab-groups").performClick()
        compose.onNodeWithTag("telegram-library-tab-groups").assertIsSelected()
    }
}
