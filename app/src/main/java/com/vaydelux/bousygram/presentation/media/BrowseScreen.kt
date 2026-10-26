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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.components.SeriesPosterCard
import com.vaydelux.bousygram.presentation.layout.TvHybridHomeDashboard
import com.vaydelux.bousygram.presentation.media.vm.MediaViewModel

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.media.SeriesDetailScreen

@Composable
fun BrowseScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onOpenSearch: (chatId: Long?, chatTitle: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onStreamError: (String) -> Unit = {},
    libraryMode: Boolean = false,
    resumeSignal: Int = 0,
    onResumeConsumed: () -> Unit = {},
    onBackToNavBar: () -> Unit = {},
    viewModel: MediaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val adaptive = rememberAdaptiveMetrics()
    val context = LocalContext.current
    var favoriteKeys by remember { mutableStateOf(EnterprisePreferences.favoriteKeys()) }
    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var hideWatched by remember { mutableStateOf(EnterprisePreferences.hideWatched()) }
    var pinnedChats by remember { mutableStateOf(EnterprisePreferences.pinnedChatIds()) }
    var showAllChatsPicker by remember { mutableStateOf(false) }
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
    val standaloneVideos = remember(videos, state.seriesGroups) {
        if (state.seriesGroups.isEmpty()) {
            videos
        } else {
            videos.filter { v ->
                state.seriesGroups.none { g ->
                    g.episodes.any { ep -> ep.chatId == v.chatId && ep.messageId == v.messageId }
                }
            }
        }
    }
    val libraryGridState = rememberLazyGridState()

    // The library fetches media metadata a page at a time from TDLib. Near the end of the
    // currently loaded grid, prefetch the next metadata page automatically, without downloading
    // video bytes; playback remains the responsibility of the existing ExoPlayer stream path.
    LaunchedEffect(libraryMode, videos.size, state.hasMore, state.isLoading, state.isLoadingMore) {
        if (!libraryMode) return@LaunchedEffect
        snapshotFlow { libraryGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
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

    BackHandler(enabled = state.selectedSeriesTitle != null) {
        viewModel.selectSeries(null)
    }

    BackHandler(enabled = state.selectedSeriesTitle == null && state.selectedTopicId != null) {
        viewModel.selectTopic(null)
    }

    fun openVideo(item: MediaItem) {
        val fileId = item.fileId ?: return
        fun onUiThread(block: () -> Unit) {
            val activity = context as? Activity
            if (activity != null) activity.runOnUiThread(block) else block()
        }
        EnterprisePreferences.setLastPlayed(item.chatId, item.messageId, fileId, item.title)
        OfflineCacheManager.markFileAccessed(fileId)
        viewModel.preparePlayback(item) { url, error ->
            onUiThread {
                if (url.isNullOrBlank()) {
                    onStreamError(error ?: "Could not prepare stream")
                    return@onUiThread
                }
                val queueItems = if (videos.any { it.chatId == item.chatId && it.messageId == item.messageId }) {
                    videos
                } else {
                    listOf(item) + videos
                }
                onPlay(buildPlaybackRequest(item, url, queueItems, null))
            }
        }
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

    val pagePadding = if (adaptive.isCompact) 12.dp else 28.dp
    val posterMin = if (adaptive.isCompact) 160.dp else 220.dp
    Box(modifier = modifier.fillMaxSize()) {
        val activeSeries = state.seriesGroups.firstOrNull { it.title == state.selectedSeriesTitle }
        if (activeSeries != null) {
            SeriesDetailScreen(
                series = activeSeries,
                onPlayEpisode = ::openVideo,
                onBack = { viewModel.selectSeries(null) },
                isPhone = adaptive.isPhoneLayout,
                privacyMode = safeMode,
                modifier = Modifier.fillMaxSize()
            )
        } else if (libraryMode) {
            if (adaptive.isPhoneLayout) {
                // Phone Library: group list, then topic chips, then a 3-column poster grid.
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
                        items(state.videoChats, key = { it.chatId }) { chat ->
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

                    // 3-column poster grid
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (state.isLoading && videos.isEmpty()) {
                            CircularProgressIndicator(Modifier.align(Alignment.Center))
                        } else if (videos.isEmpty()) {
                            EmptyStateView(
                                title = "No videos in this source",
                                message = "Switch to another channel above or search your media.",
                                actionText = "Refresh",
                                onAction = { viewModel.refresh(); viewModel.refreshVideoChats() },
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyVerticalGrid(
                                state = libraryGridState,
                                columns = GridCells.Fixed(3),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                            ) {
                                if (state.seriesGroups.isNotEmpty()) {
                                    items(state.seriesGroups, key = { "series_${it.title}" }) { series ->
                                        SeriesPosterCard(
                                            series = series,
                                            onClick = { viewModel.selectSeries(series.title) },
                                            cardWidth = null,
                                            privacyMode = safeMode,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                items(standaloneVideos, key = { "${it.chatId}:${it.messageId}" }) { item ->
                                    MediaPosterCard(
                                        media = item,
                                        subtitle = state.selectedChatTitle,
                                        onClick = { openVideo(item) },
                                        cardWidth = null,
                                        privacyMode = safeMode,
                                        modifier = Modifier.fillMaxWidth(),
                                        onFocusChanged = { focused -> if (focused) viewModel.onItemFocused(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // TV and Tablet split layout:
                // Left list: Groups & Channels | Right: Topics (chips) + grid of posters
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
                        .onKeyEvent { event ->
                            if (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                                onBackToNavBar()
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
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
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
                            items(state.videoChats, key = { it.chatId }) { chat ->
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
                        }
                    }

                    // Right pane: Topics (chips) on top + grid of posters
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

                        // Grid of posters: touch-friendly on tablets/phones, TV-friendly on TV
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (state.isLoading && videos.isEmpty()) {
                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                            } else if (videos.isEmpty()) {
                                EmptyStateView(
                                    title = "No videos in this source",
                                    message = "Choose another channel from the left or search your media.",
                                    actionText = "Refresh",
                                    onAction = { viewModel.refresh(); viewModel.refreshVideoChats() },
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                val gridColumns = when {
                                    adaptive.isTv -> GridCells.Adaptive(200.dp)
                                    adaptive.isTabletLayout -> GridCells.Adaptive(170.dp)
                                    else -> GridCells.Adaptive(140.dp)
                                }
                                LazyVerticalGrid(
                                    state = libraryGridState,
                                    columns = gridColumns,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                                ) {
                                    if (state.seriesGroups.isNotEmpty()) {
                                        items(state.seriesGroups, key = { "series_${it.title}" }) { series ->
                                            SeriesPosterCard(
                                                series = series,
                                                onClick = { viewModel.selectSeries(series.title) },
                                                cardWidth = null,
                                                privacyMode = safeMode,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    items(standaloneVideos, key = { "${it.chatId}:${it.messageId}" }) { item ->
                                        MediaPosterCard(
                                            media = item,
                                            subtitle = state.selectedChatTitle,
                                            onClick = { openVideo(item) },
                                            cardWidth = null,
                                            privacyMode = safeMode,
                                            modifier = Modifier.fillMaxWidth(),
                                            onFocusChanged = { focused -> if (focused) viewModel.onItemFocused(item) }
                                        )
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
                onVideoSelected = ::openVideo,
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
                selectedSeriesTitle = state.selectedSeriesTitle,
                seriesGroups = state.seriesGroups,
                topicGroups = state.topicGroups,
                availableTopics = state.availableTopics,
                selectedTopicId = state.selectedTopicId,
                onSelectCategoryTab = viewModel::setCategoryTab,
                onSelectSeries = viewModel::selectSeries,
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
        if (state.preparingFileId != null) {
            LoadingOverlay(
                text = "Preparing video...",
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f))
            )
        }
    }
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
