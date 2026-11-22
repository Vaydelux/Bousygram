package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.lazy.grid.LazyGridState
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
import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import com.vaydelux.bousygram.presentation.media.vm.BrowseRestoration
import com.vaydelux.bousygram.presentation.media.vm.ExplorerLevel
import com.vaydelux.bousygram.presentation.media.vm.ExplorerPosition
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end Compose page append without a Telegram account or Internet. */
@RunWith(AndroidJUnit4::class)
class TelegramForumPagingInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun existingTopicsStayVisibleWhenLoadingNextPageAndThenAppend() {
        var nextPageClicks = 0
        compose.setContent {
            BousygramTheme {
                var topics by remember {
                    mutableStateOf(listOf(ForumTopicChannel(1, 99L, "First real topic", order = 20)))
                }
                var hasMore by remember { mutableStateOf(true) }
                val grid = remember { LazyGridState() }
                val focus = remember { FocusRequester() }
                TelegramExplorerDashboard(
                    position = ExplorerPosition(ExplorerSection.GROUPS, ExplorerLevel.TOPICS),
                    sources = emptyList(),
                    topics = topics,
                    selectedChatTitle = "Forum",
                    selectedChatId = 99L,
                    selectedTopicId = null,
                    videos = emptyList(),
                    isLoading = false,
                    isLoadingSources = false,
                    hasMoreSources = false,
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
                    onSelectTab = {},
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
                    onLoadMoreSources = {},
                    onRefreshSources = {},
                    onRefreshTopics = {},
                    onRefreshMedia = {},
                    onSearch = {},
                    onFavorites = {},
                    onSettings = {},
                    onNavigate = {},
                    hasResume = false,
                    onResume = {},
                    libraryMode = true,
                    hasMoreTopics = hasMore,
                    isLoadingMoreTopics = false,
                    onLoadMoreTopics = {
                        nextPageClicks++
                        topics = topics + ForumTopicChannel(2, 99L, "Second real topic", order = 10)
                        hasMore = false
                    }
                )
            }
        }
        compose.onNodeWithText("First real topic").assertExists()
        compose.onNodeWithTag("telegram-topics-load-more").performClick()
        compose.runOnIdle { assertEquals(1, nextPageClicks) }
        compose.onNodeWithText("First real topic").assertExists()
        compose.onNodeWithText("Second real topic").assertExists()
        compose.onNodeWithTag("telegram-topics-load-more").assertDoesNotExist()
    }
}
