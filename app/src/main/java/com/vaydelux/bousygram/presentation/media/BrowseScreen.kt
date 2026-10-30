package com.vaydelux.bousygram.presentation.media

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.core.media.ExternalPlayerLauncher
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.SortOrder
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.LoadingOverlay
import com.vaydelux.bousygram.presentation.components.ChatSourceTabs
import com.vaydelux.bousygram.presentation.components.AllChatsPickerDialog
import com.vaydelux.bousygram.presentation.components.BrowseGridShimmerSkeleton
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.components.MediaPosterSkeletonCard
import com.vaydelux.bousygram.presentation.components.NoSearchResultsEmptyState
import com.vaydelux.bousygram.presentation.components.RecentSearchesSection
import com.vaydelux.bousygram.presentation.components.SidebarListShimmerSkeleton
import com.vaydelux.bousygram.presentation.layout.TvHybridHomeDashboard
import com.vaydelux.bousygram.presentation.media.vm.MediaViewModel
import com.vaydelux.bousygram.data.database.RecentSearchQuery

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.vaydelux.bousygram.presentation.components.BousygramTextField
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.media.launch.LaunchOpeningIndicator
import com.vaydelux.bousygram.presentation.media.launch.rememberMediaLauncher
import com.vaydelux.bousygram.presentation.search.SearchBarVoiceButton
import com.vaydelux.bousygram.presentation.search.SearchMode
import com.vaydelux.bousygram.presentation.search.buildVoiceSearchPrompt
import com.vaydelux.bousygram.presentation.search.rememberVoiceSearchController

@Composable
fun BrowseScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onOpenSearch: (chatId: Long?, chatTitle: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSearchWithQuery: (chatId: Long?, chatTitle: String, query: String) -> Unit = { chatId, chatTitle, _ ->
        onOpenSearch(chatId, chatTitle)
    },
    onStreamError: (String) -> Unit = {},
    libraryMode: Boolean = false,
    resumeSignal: Int = 0,
    onResumeConsumed: () -> Unit = {},
    onBackToNavBar: () -> Unit = {},
    viewModel: MediaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    // Saved Messages has its own permanent entry. Never list the same chat again.
    val libraryChatSources = remember(state.videoChats) {
        libraryOtherChatSources(state.videoChats)
    }
    val adaptive = rememberAdaptiveMetrics()
    val context = LocalContext.current
    var favoriteKeys by remember { mutableStateOf(EnterprisePreferences.favoriteKeys()) }
    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var hideWatched by remember { mutableStateOf(EnterprisePreferences.hideWatched()) }
    var pinnedChats by remember { mutableStateOf(EnterprisePreferences.pinnedChatIds()) }
    var showAllChatsPicker by remember { mutableStateOf(false) }
    // Keep the sidebar viewport across incremental Telegram chat catalog updates.
    val sourceSidebarListState = rememberLazyListState()
    val lastPlayed = EnterprisePreferences.lastPlayed()
    val videos = remember(state.filteredAndSortedItems, hideWatched) {
        val playable = playableVideos(state.filteredAndSortedItems)
        if (hideWatched) {
            val continueOnlyItems = playable.filter { item ->
                PlaybackProgressTracker.hasResume(item.chatId, item.messageId, item.fileId)
            }
            // UX fallback: if nothing has progress yet, don't show an empty media area.
            if (continueOnlyItems.isEmpty()) playable else continueOnlyItems
        } else {
            playable
        }
    }
    val browseStateKey = viewModel.currentBrowseStateKey()
    val browseRestoration = remember(browseStateKey) { viewModel.browseRestoration(browseStateKey) }
    val libraryGridState = remember(browseStateKey) {
        LazyGridState(
            firstVisibleItemIndex = browseRestoration.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = browseRestoration.firstVisibleItemScrollOffset
        )
    }
    val restoreFocusRequester = remember(browseStateKey) { FocusRequester() }
    var restorationApplied by remember(browseStateKey) { mutableStateOf(false) }

    DisposableEffect(browseStateKey, libraryGridState) {
        onDispose {
            viewModel.saveBrowseViewport(
                browseStateKey,
                libraryGridState.firstVisibleItemIndex,
                libraryGridState.firstVisibleItemScrollOffset
            )
        }
    }

    // The library fetches media metadata a page at a time from TDLib. Near the end of the
    // currently loaded grid, prefetch the next metadata page automatically, without downloading
    // video bytes; playback remains the responsibility of the existing ExoPlayer stream path.
    LaunchedEffect(libraryMode, videos.size, state.hasMore, state.isLoading, state.isLoadingMore) {
        if (!libraryMode) return@LaunchedEffect
        snapshotFlow { libraryGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                viewModel.saveBrowseViewport(
                    browseStateKey,
                    libraryGridState.firstVisibleItemIndex,
                    libraryGridState.firstVisibleItemScrollOffset
                )
                val threshold = (videos.size - LIBRARY_PREFETCH_ITEMS_REMAINING).coerceAtLeast(0)
                if (!state.isLoading && !state.isLoadingMore && state.hasMore &&
                    videos.isNotEmpty() && lastVisibleIndex >= threshold
                ) {
                    viewModel.loadMoreIfNeeded()
                }
            }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    LaunchedEffect(libraryMode, browseStateKey, videos.size, browseRestoration.focusedMediaKey) {
        if (!libraryMode || restorationApplied || videos.isEmpty()) return@LaunchedEffect
        val targetKey = browseRestoration.focusedMediaKey
        if (targetKey == null) {
            restorationApplied = true
            return@LaunchedEffect
        }
        val targetIndex = videos.indexOfFirst { "${it.chatId}:${it.messageId}" == targetKey }
        if (targetIndex >= 0) {
            libraryGridState.scrollToItem(targetIndex)
            delay(80L)
            runCatching { restoreFocusRequester.requestFocus() }
        }
        restorationApplied = true
    }

    BackHandler(enabled = state.quickSearchQuery.isNotBlank()) {
        viewModel.clearQuickSearchQuery()
    }

    BackHandler(enabled = state.selectedTopicId != null && state.quickSearchQuery.isBlank()) {
        viewModel.selectTopic(null)
    }

    val launcher = rememberMediaLauncher(
        screenKey = if (libraryMode) LAUNCH_SCREEN_LIBRARY else LAUNCH_SCREEN_HOME,
        onPlay = onPlay,
        onError = onStreamError
    )

    fun openVideo(item: MediaItem, sectionKey: String? = null) {
        if (libraryMode) viewModel.recordBrowseFocus(item)
        launcher.launch(item, videos, sectionKey)
    }

    fun openLastPlayed() {
        val last = EnterprisePreferences.lastPlayed()
        val loaded = last?.let { saved ->
            state.filteredAndSortedItems.firstOrNull {
                it.chatId == saved.chatId && it.messageId == saved.messageId
            } ?: videos.firstOrNull { it.fileId == saved.fileId }
        }
        if (loaded != null) {
            openVideo(loaded)
            return
        }
        viewModel.loadLastPlayed { item, error ->
            val activity = context as? Activity
            val action = {
                if (item != null) openVideo(item) else onStreamError(error ?: "Could not load the last-played item")
            }
            if (activity != null) activity.runOnUiThread(action) else action()
        }
    }

    fun toggleOffline(item: MediaItem) {
        val fileId = item.fileId ?: return
        if (OfflineCacheManager.isPinned(fileId)) OfflineCacheManager.unpinFile(fileId)
        else OfflineCacheManager.pinFile(fileId)
    }

    LaunchedEffect(resumeSignal) {
        if (resumeSignal > 0 && !libraryMode) {
            openLastPlayed()
            onResumeConsumed()
        }
    }

    val voiceSearchController = rememberVoiceSearchController(
        promptProvider = {
            buildVoiceSearchPrompt(SearchMode.InChat, state.selectedChatTitle)
        },
        onPartialText = { partial ->
            viewModel.setQuickSearchQuery(partial)
        },
        onQueryRecognized = { recognizedQuery ->
            viewModel.executeRecentSearch(recognizedQuery)
        }
    )

    val pagePadding = if (adaptive.isCompact) 12.dp else 28.dp
    val posterMin = if (adaptive.isCompact) 160.dp else 220.dp
    Box(modifier = modifier.fillMaxSize()) {
        if (libraryMode) {
            if (adaptive.isPhoneLayout) {
                // Phone Library: group list, then topic chips, recent searches bar, then a 3-column poster grid.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Library",
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Group list
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            val isSelected = state.isSavedMessagesSelected
                            TvFocusSurface(
                                onClick = viewModel::load,
                                selected = isSelected
                            ) { focused ->
                                Text(
                                    text = "Saved Messages",
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                        items(libraryChatSources, key = { it.chatId }) { chat ->
                            val isSelected = chat.chatId == state.selectedChatId && !state.isSavedMessagesSelected
                            TvFocusSurface(
                                onClick = { viewModel.loadChat(chat.chatId, chat.title) },
                                selected = isSelected
                            ) { focused ->
                                Text(
                                    text = chat.title,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Topic chips
                    if (state.availableTopics.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isSelected = state.selectedTopicId == null
                                TvFocusSurface(
                                    onClick = { viewModel.selectTopic(null) },
                                    selected = isSelected
                                ) { focused ->
                                    Text(
                                        text = "# all-channels",
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            items(state.availableTopics, key = { it.id }) { topic ->
                                val isSelected = state.selectedTopicId == topic.id
                                TvFocusSurface(
                                    onClick = { viewModel.selectTopic(topic.id) },
                                    selected = isSelected
                                ) { focused ->
                                    Text(
                                        text = "# ${topic.name}",
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Search bar with voice search microphone button
                    BrowseSearchBar(
                        query = state.quickSearchQuery,
                        onQueryChange = viewModel::setQuickSearchQuery,
                        onSearch = {
                            if (state.quickSearchQuery.isNotBlank()) {
                                viewModel.executeRecentSearch(state.quickSearchQuery)
                            }
                        },
                        onVoiceSearch = voiceSearchController::toggleVoiceSearch,
                        isVoiceListening = voiceSearchController.isListening,
                        placeholder = "Search in ${state.selectedChatTitle}"
                    )

                    // Cached recent search queries from Room database
                    if (!safeMode && (recentSearches.isNotEmpty() || state.quickSearchQuery.isNotBlank())) {
                        RecentSearches(
                            recentSearches = recentSearches,
                            activeQuery = state.quickSearchQuery,
                            onSelectQuery = viewModel::toggleQuickSearchQuery,
                            onRemoveQuery = viewModel::removeRecentSearch,
                            onClearActiveQuery = viewModel::clearQuickSearchQuery,
                            onClearHistory = {
                                viewModel.clearRecentSearches()
                                viewModel.clearQuickSearchQuery()
                            },
                            onOpenFullSearch = { query ->
                                val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                                onOpenSearchWithQuery(chatId, state.selectedChatTitle, query)
                            }
                        )
                    }

                    // 3-column poster grid with shimmer loading skeletons
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (state.isLoading && videos.isEmpty()) {
                            BrowseGridShimmerSkeleton(
                                columns = GridCells.Adaptive(minSize = posterMin),
                                itemCount = 9,
                                spacing = 8.dp
                            )
                        } else if (videos.isEmpty()) {
                            if (state.quickSearchQuery.isNotBlank()) {
                                NoSearchResultsEmptyState(
                                    query = state.quickSearchQuery,
                                    scopeLabel = state.selectedChatTitle,
                                    onClearQuery = viewModel::clearQuickSearchQuery,
                                    secondaryActionText = "Search All Telegram",
                                    onSecondaryAction = {
                                        val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                                        onOpenSearchWithQuery(chatId, state.selectedChatTitle, state.quickSearchQuery)
                                    },
                                    suggestedSearches = recentSearches
                                        .map { it.query }
                                        .filterNot { it.equals(state.quickSearchQuery, ignoreCase = true) },
                                    onSelectSuggestion = viewModel::executeRecentSearch,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                EmptyStateView(
                                    title = "No videos in this source",
                                    message = "Switch to another channel above or search your media.",
                                    actionText = "Refresh",
                                    onAction = {
                                        viewModel.refresh()
                                        viewModel.refreshVideoChats()
                                    },
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                state = libraryGridState,
                                columns = GridCells.Adaptive(minSize = posterMin),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                            ) {
                                items(videos, key = { "${it.chatId}:${it.messageId}" }) { item ->
                                    val mediaKey = "${item.chatId}:${item.messageId}"
                                    MediaPosterCard(
                                        media = item,
                                        subtitle = state.selectedChatTitle,
                                        onClick = { openVideo(item) },
                                        cardWidth = null,
                                        privacyMode = safeMode,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (mediaKey == browseRestoration.focusedMediaKey) {
                                                    Modifier.focusRequester(restoreFocusRequester)
                                                } else Modifier
                                            ),
                                        onFocusChanged = { focused ->
                                            if (focused) {
                                                viewModel.recordBrowseFocus(item)
                                                viewModel.onItemFocused(item)
                                            }
                                        }
                                    )
                                }
                                if (state.isLoadingMore) {
                                    items(3, key = { "__phone_loading_more_$it" }) {
                                        MediaPosterSkeletonCard(
                                            cardWidth = null,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // TV and Tablet split layout:
                // Left list: Groups & Channels | Right: Topics (chips) + Recent Searches + grid of posters
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                // A pending launch owns Back: cancel it and keep focus on the card.
                                if (!launcher.cancel()) onBackToNavBar()
                                true
                            } else {
                                false
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Left list: Groups & Channels
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Groups & Channels",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        LazyColumn(
                            state = sourceSidebarListState,
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item(key = "saved-messages-sidebar") {
                                val isSelected = state.isSavedMessagesSelected
                                TvFocusSurface(
                                    onClick = viewModel::load,
                                    selected = isSelected
                                ) { focused ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isSelected) "> Saved Messages" else "  Saved Messages",
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            items(libraryChatSources, key = { it.chatId }) { chat ->
                                val isSelected = chat.chatId == state.selectedChatId && !state.isSavedMessagesSelected
                                TvFocusSurface(
                                    onClick = { viewModel.loadChat(chat.chatId, chat.title) },
                                    selected = isSelected
                                ) { focused ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isSelected) "> ${chat.title}" else "  ${chat.title}",
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (chat.isForum) {
                                            Text(
                                                text = "${chat.topicsCount}T",
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.isSidebarLoading && state.videoChats.isEmpty()) {
                                item(key = "__sidebar_shimmer__") {
                                    SidebarListShimmerSkeleton(itemCount = 5)
                                }
                            }
                        }
                    }

                    // Right pane: Topics (chips) on top + Recent Searches + grid of posters
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Topics chips: [All] [Topic A] [Topic B]
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isSelected = state.selectedTopicId == null
                                TvFocusSurface(
                                    onClick = { viewModel.selectTopic(null) },
                                    selected = isSelected
                                ) { focused ->
                                    Text(
                                        text = "# all-channels",
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            items(state.availableTopics, key = { it.id }) { topic ->
                                val isSelected = state.selectedTopicId == topic.id
                                TvFocusSurface(
                                    onClick = { viewModel.selectTopic(topic.id) },
                                    selected = isSelected
                                ) { focused ->
                                    Text(
                                        text = topic.name,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        // Search bar with voice search microphone button
                        BrowseSearchBar(
                            query = state.quickSearchQuery,
                            onQueryChange = viewModel::setQuickSearchQuery,
                            onSearch = {
                                if (state.quickSearchQuery.isNotBlank()) {
                                    viewModel.executeRecentSearch(state.quickSearchQuery)
                                }
                            },
                            onVoiceSearch = voiceSearchController::toggleVoiceSearch,
                            isVoiceListening = voiceSearchController.isListening,
                            placeholder = "Search in ${state.selectedChatTitle}"
                        )

                        // Cached recent search queries from Room database
                        if (!safeMode && (recentSearches.isNotEmpty() || state.quickSearchQuery.isNotBlank())) {
                            RecentSearches(
                                recentSearches = recentSearches,
                                activeQuery = state.quickSearchQuery,
                                onSelectQuery = viewModel::toggleQuickSearchQuery,
                                onRemoveQuery = viewModel::removeRecentSearch,
                                onClearActiveQuery = viewModel::clearQuickSearchQuery,
                                onClearHistory = {
                                    viewModel.clearRecentSearches()
                                    viewModel.clearQuickSearchQuery()
                                },
                                onOpenFullSearch = { query ->
                                    val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                                    onOpenSearchWithQuery(chatId, state.selectedChatTitle, query)
                                }
                            )
                        }

                        // Grid of posters: touch-friendly on tablets/phones, TV-friendly on TV
                        val gridColumns = when {
                            adaptive.isTv -> GridCells.Adaptive(200.dp)
                            adaptive.isTabletLayout -> GridCells.Adaptive(170.dp)
                            else -> GridCells.Adaptive(140.dp)
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (state.isLoading && videos.isEmpty()) {
                                BrowseGridShimmerSkeleton(
                                    columns = gridColumns,
                                    itemCount = 12,
                                    spacing = 14.dp
                                )
                            } else if (videos.isEmpty()) {
                                if (state.quickSearchQuery.isNotBlank()) {
                                    NoSearchResultsEmptyState(
                                        query = state.quickSearchQuery,
                                        scopeLabel = state.selectedChatTitle,
                                        onClearQuery = viewModel::clearQuickSearchQuery,
                                        secondaryActionText = "Search All Telegram",
                                        onSecondaryAction = {
                                            val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                                            onOpenSearchWithQuery(chatId, state.selectedChatTitle, state.quickSearchQuery)
                                        },
                                        suggestedSearches = recentSearches
                                            .map { it.query }
                                            .filterNot { it.equals(state.quickSearchQuery, ignoreCase = true) },
                                        onSelectSuggestion = viewModel::executeRecentSearch,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                } else {
                                    EmptyStateView(
                                        title = "No videos in this source",
                                        message = "Choose another channel from the left or search your media.",
                                        actionText = "Refresh",
                                        onAction = {
                                            viewModel.refresh()
                                            viewModel.refreshVideoChats()
                                        },
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    state = libraryGridState,
                                    columns = gridColumns,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                                ) {
                                    items(videos, key = { "${it.chatId}:${it.messageId}" }) { item ->
                                        val mediaKey = "${item.chatId}:${item.messageId}"
                                        MediaPosterCard(
                                            media = item,
                                            subtitle = state.selectedChatTitle,
                                            onClick = { openVideo(item) },
                                            cardWidth = null,
                                            privacyMode = safeMode,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (mediaKey == browseRestoration.focusedMediaKey) {
                                                        Modifier.focusRequester(restoreFocusRequester)
                                                    } else Modifier
                                                ),
                                            onFocusChanged = { focused ->
                                                if (focused) {
                                                    viewModel.recordBrowseFocus(item)
                                                    viewModel.onItemFocused(item)
                                                }
                                            }
                                        )
                                    }
                                    if (state.isLoadingMore) {
                                        items(4, key = { "__tv_loading_more_$it" }) {
                                            MediaPosterSkeletonCard(
                                                cardWidth = null,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            TvHybridHomeDashboard(
                featuredVideos = videos,
                channelLabel = state.selectedChatTitle,
                isLoading = state.isLoading,
                errorMessage = state.error,
                onVideoSelected = { openVideo(it) },
                onOpenSearch = {
                    val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                    onOpenSearch(chatId, state.selectedChatTitle)
                },
                onOpenLibrary = onOpenLibrary,
                onOpenFavorites = onOpenFavorites,
                onOpenSettings = onOpenSettings,
                onOpenLastPlayed = ::openLastPlayed,
                lastPlayedFileId = lastPlayed?.fileId,
                lastPlayedTitle = lastPlayed?.title,
                onToggleFavorite = {
                    EnterprisePreferences.toggleFavorite(it.chatId, it.messageId)
                    favoriteKeys = EnterprisePreferences.favoriteKeys()
                },
                onToggleOffline = ::toggleOffline,
                onVideoFocused = viewModel::onItemFocused,
                favoriteKeys = favoriteKeys,
                sourceChats = state.videoChats,
                savedMessagesSelected = state.isSavedMessagesSelected,
                selectedChatId = state.selectedChatId,
                pinnedChatIds = pinnedChats,
                onSelectSavedMessages = viewModel::load,
                onSelectChat = { viewModel.loadChat(it.chatId, it.title) },
                onOpenAllChats = { showAllChatsPicker = true },
                offlineFileIds = videos.mapNotNull { it.fileId }.filter { OfflineCacheManager.isPinned(it) }.toSet(),
                resumeMessageKeys = videos
                    .filter { PlaybackProgressTracker.hasResume(it.chatId, it.messageId, it.fileId) }
                    .map { "${it.chatId}:${it.messageId}" }
                    .toSet(),
                safeMode = safeMode,
                selectedCategoryTab = state.selectedCategoryTab,
                selectedSeriesTitle = null,
                seriesGroups = emptyList(),
                topicGroups = state.topicGroups,
                availableTopics = state.availableTopics,
                selectedTopicId = state.selectedTopicId,
                onSelectCategoryTab = viewModel::setCategoryTab,
                onSelectSeries = {},
                onSelectTopic = viewModel::selectTopic,
                onOpenExternalPlayer = { item ->
                    ExternalPlayerLauncher.launch(context, item)
                },
                onDeleteMedia = { item ->
                    viewModel.deleteMediaItem(item) {
                        viewModel.refresh()
                    }
                },
                onBackToNavBar = onBackToNavBar,
                recentSearches = recentSearches,
                activeQuickSearchQuery = state.quickSearchQuery,
                onSelectRecentSearch = viewModel::toggleQuickSearchQuery,
                onRemoveRecentSearch = viewModel::removeRecentSearch,
                onClearQuickSearch = viewModel::clearQuickSearchQuery,
                onClearRecentSearches = viewModel::clearRecentSearches,
                onOpenSearchWithQuery = { query ->
                    val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                    onOpenSearchWithQuery(chatId, state.selectedChatTitle, query)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (showAllChatsPicker) {
            AllChatsPickerDialog(
                chats = state.videoChats,
                selectedChatId = state.selectedChatId,
                savedSelected = state.isSavedMessagesSelected,
                pinnedChatIds = pinnedChats,
                isLoading = state.isSidebarLoading,
                onDismiss = { showAllChatsPicker = false },
                onSelectSavedMessages = {
                    showAllChatsPicker = false
                    viewModel.load()
                },
                onSelectChat = { chat ->
                    showAllChatsPicker = false
                    viewModel.loadChat(chat.chatId, chat.title)
                }
            )
        }
        LaunchOpeningIndicator(
            launcher = launcher,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

/**
 * Search bar with integrated microphone voice search button for BrowseScreen.
 */
@Composable
fun BrowseSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    isVoiceListening: Boolean = false,
    placeholder: String = "Search videos",
    modifier: Modifier = Modifier
) {
    BousygramTextField(
        value = query,
        onValueChange = onQueryChange,
        label = if (isVoiceListening) "Listening... speak now" else placeholder,
        modifier = modifier
            .fillMaxWidth()
            .testTag("browse_search_bar"),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            SearchBarVoiceButton(
                onClick = onVoiceSearch,
                isListening = isVoiceListening
            )
        }
    )
}

/**
 * Recent Searches component in BrowseScreen that displays cached search queries from
 * the Room database, allowing users to tap on any chip to re-execute the search.
 */
@Composable
fun RecentSearches(
    recentSearches: List<RecentSearchQuery>,
    activeQuery: String,
    onSelectQuery: (String) -> Unit,
    onRemoveQuery: (String) -> Unit,
    onClearActiveQuery: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenFullSearch: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    RecentSearchesSection(
        recentSearches = recentSearches,
        activeQuery = activeQuery,
        onSelectQuery = onSelectQuery,
        onRemoveQuery = onRemoveQuery,
        onClearActiveQuery = onClearActiveQuery,
        onClearHistory = onClearHistory,
        onOpenFullSearch = onOpenFullSearch,
        modifier = modifier
    )
}


@Composable
private fun SortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "chipScale")
    val color = when {
        selected -> MaterialTheme.colorScheme.primary
        focused -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    }
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        color = color,
        shape = RoundedCornerShape(14.dp)
    ) {
        val textColor = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            focused -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface
        }
        Text(
            text = text,
            color = textColor,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun RefreshChip(text: String = "Refresh", onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "refreshScale")
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

private const val LIBRARY_PREFETCH_ITEMS_REMAINING = 6
const val LAUNCH_SCREEN_HOME = "home"
const val LAUNCH_SCREEN_LIBRARY = "library"
