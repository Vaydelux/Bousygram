package com.vaydelux.bousygram.presentation.media

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.SortOrder
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.LoadingOverlay
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.layout.TvHybridHomeDashboard
import com.vaydelux.bousygram.presentation.media.vm.MediaViewModel
import java.io.File

@Composable
fun BrowseScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onOpenSearch: (chatId: Long?, chatTitle: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onStreamError: (String) -> Unit = {},
    libraryMode: Boolean = false,
    viewModel: MediaViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val adaptive = rememberAdaptiveMetrics()
    val context = LocalContext.current
    var favoriteKeys by remember { mutableStateOf(EnterprisePreferences.favoriteKeys()) }
    var safeMode by remember { mutableStateOf(EnterprisePreferences.safeMode()) }
    var hideWatched by remember { mutableStateOf(EnterprisePreferences.hideWatched()) }
    var pinnedChats by remember { mutableStateOf(EnterprisePreferences.pinnedChatIds()) }
    val lastPlayed = EnterprisePreferences.lastPlayed()
    val videos = remember(state.filteredAndSortedItems, hideWatched) {
        val playable = playableVideos(state.filteredAndSortedItems)
        if (hideWatched) {
            val continueOnlyItems = playable.filter { item ->
                val fileId = item.fileId ?: return@filter true
                PlaybackProgressTracker.getPosition(fileId) > 0
            }
            // UX fallback: if nothing has progress yet, don't show an empty media area.
            if (continueOnlyItems.isEmpty()) playable else continueOnlyItems
        } else {
            playable
        }
    }
    val chatFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.initialize()
        viewModel.loadVideoChatsIfNeeded()
    }

    LaunchedEffect(libraryMode, state.isSidebarLoading, state.videoChats) {
        if (!state.isSidebarLoading) {
            runCatching { chatFocusRequester.requestFocus() }
        }
    }

    fun openVideo(item: MediaItem) {
        val fileId = item.fileId ?: return
        fun onUiThread(block: () -> Unit) {
            val activity = context as? Activity
            if (activity != null) activity.runOnUiThread(block) else block()
        }
        EnterprisePreferences.setLastPlayed(item.chatId, item.messageId, fileId, item.title)
        OfflineCacheManager.markFileAccessed(fileId)
        viewModel.requestFastLink(item) { url, error ->
            onUiThread {
                if (url.isNullOrBlank()) {
                    onStreamError(error ?: "Could not prepare stream")
                    return@onUiThread
                }
                viewModel.findSidecarSubtitle(item) { subFileId, _ ->
                    val subUrl = if (EnterprisePreferences.subtitlesDefault()) {
                        subFileId?.let { LocalStreamingServer.getSubtitleUrl(it, item.title) }
                    } else {
                        null
                    }
                    onUiThread {
                        val queueItems = if (videos.any { it.chatId == item.chatId && it.messageId == item.messageId }) {
                            videos
                        } else {
                            listOf(item) + videos
                        }
                        onPlay(buildPlaybackRequest(item, url, queueItems, subUrl))
                    }
                }
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

    val isCompactLayout = !adaptive.useTvChrome
    Box(modifier = modifier.fillMaxSize()) {
        if (isCompactLayout) {
            CompactBrowseLayout(
                modifier = Modifier.fillMaxSize(),
                state = state,
                videos = videos,
                hideWatched = hideWatched,
                safeMode = safeMode,
                onToggleHideWatched = {
                    hideWatched = !hideWatched
                    EnterprisePreferences.setHideWatched(hideWatched)
                },
                onToggleSafeMode = {
                    safeMode = !safeMode
                    EnterprisePreferences.setSafeMode(safeMode)
                },
                onSelectSavedMessages = viewModel::load,
                onSelectChat = { viewModel.loadChat(it.chatId, it.title) },
                onSetSortOrder = viewModel::setSortOrder,
                onRefresh = viewModel::refresh,
                onOpenSettings = onOpenSettings,
                onOpenLibrary = onOpenLibrary,
                onOpenHome = onOpenHome,
                onOpenFavorites = onOpenFavorites,
                onOpenSearch = {
                    val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                    onOpenSearch(chatId, state.selectedChatTitle)
                },
                onOpenLastPlayed = ::openLastPlayed,
                onOpenVideo = ::openVideo,
                onFocusedVideo = viewModel::onItemFocused,
                onLoadMore = viewModel::loadMoreIfNeeded
            )
        } else if (libraryMode) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF08090C), Color(0xFF111318))))
                    .padding(24.dp)
            ) {
                ChatRail(
                    chats = state.videoChats,
                    pinnedChats = pinnedChats,
                    savedSelected = state.isSavedMessagesSelected,
                    isLoading = state.isSidebarLoading,
                    sidebarError = state.sidebarError,
                    firstItemFocusRequester = chatFocusRequester,
                    onSaved = viewModel::load,
                    onChat = { viewModel.loadChat(it.chatId, it.title) }
                )
                Spacer(Modifier.width(22.dp))
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Library", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                            Text(
                                state.selectedChatTitle,
                                color = Color.White.copy(alpha = 0.62f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        RefreshChip("Home", onOpenHome)
                        RefreshChip("Favorites", onOpenFavorites)
                        RefreshChip("Search", {
                            val chatId = if (state.isSavedMessagesSelected) null else state.selectedChatId
                            onOpenSearch(chatId, state.selectedChatTitle)
                        })
                        RefreshChip("Settings", onOpenSettings)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        item { SortChip("Newest", state.sortOrder == SortOrder.DATE_DESC) { viewModel.setSortOrder(SortOrder.DATE_DESC) } }
                        item { SortChip("Oldest", state.sortOrder == SortOrder.DATE_ASC) { viewModel.setSortOrder(SortOrder.DATE_ASC) } }
                        item { SortChip("Largest", state.sortOrder == SortOrder.SIZE_DESC) { viewModel.setSortOrder(SortOrder.SIZE_DESC) } }
                        item { SortChip("Smallest", state.sortOrder == SortOrder.SIZE_ASC) { viewModel.setSortOrder(SortOrder.SIZE_ASC) } }
                        item { SortChip("Continue only", hideWatched) {
                            hideWatched = !hideWatched
                            EnterprisePreferences.setHideWatched(hideWatched)
                        } }
                        item { SortChip("Privacy mode", safeMode) {
                            safeMode = !safeMode
                            EnterprisePreferences.setSafeMode(safeMode)
                        } }
                        item { SortChip("Pin source", EnterprisePreferences.isPinnedChat(state.selectedChatId)) {
                            EnterprisePreferences.togglePinnedChat(state.selectedChatId)
                            pinnedChats = EnterprisePreferences.pinnedChatIds()
                        } }
                        item { RefreshChip("Refresh", viewModel::refresh) }
                    }
                    if (!state.error.isNullOrBlank()) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.isLoading && videos.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                            videos.isEmpty() -> Text(
                                text = if (safeMode) "No videos in this source. Choose another source or refresh." else "No videos in ${state.selectedChatTitle}. Choose another source or refresh.",
                                color = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.align(Alignment.Center)
                            )
                            else -> LazyVerticalGrid(
                                columns = GridCells.Adaptive(220.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                            ) {
                                items(videos, key = { "${it.chatId}:${it.messageId}" }) { item ->
                                    MediaPosterCard(
                                        media = item,
                                        subtitle = state.selectedChatTitle,
                                        onClick = { openVideo(item) },
                                        cardWidth = 220.dp,
                                        privacyMode = safeMode,
                                        onFocusChanged = { focused -> if (focused) viewModel.onItemFocused(item) }
                                    )
                                }
                            }
                        }
                    }
                    if (state.hasMore) {
                        Button(onClick = viewModel::loadMoreIfNeeded, modifier = Modifier.focusable()) {
                            Text(if (state.isLoadingMore) "Loading…" else "Load more", color = MaterialTheme.colorScheme.onPrimary)
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
                offlineFileIds = videos.mapNotNull { it.fileId }.filter { OfflineCacheManager.isPinned(it) }.toSet(),
                resumeFileIds = videos.mapNotNull { it.fileId }.filter { PlaybackProgressTracker.getPosition(it) > 0 }.toSet(),
                safeMode = safeMode,
                modifier = Modifier.fillMaxSize()
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
private fun CompactBrowseLayout(
    modifier: Modifier,
    state: com.vaydelux.bousygram.domain.model.MediaUiState,
    videos: List<MediaItem>,
    hideWatched: Boolean,
    safeMode: Boolean,
    onToggleHideWatched: () -> Unit,
    onToggleSafeMode: () -> Unit,
    onSelectSavedMessages: () -> Unit,
    onSelectChat: (VideoChatItem) -> Unit,
    onSetSortOrder: (SortOrder) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLastPlayed: () -> Unit,
    onOpenVideo: (MediaItem) -> Unit,
    onFocusedVideo: (MediaItem) -> Unit,
    onLoadMore: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050814), Color(0xFF0A1424))))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Bousygram", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.weight(1f))
            RefreshChip("Home", onOpenHome)
            RefreshChip("Library", onOpenLibrary)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SortChip("Saved Messages", state.isSavedMessagesSelected, onSelectSavedMessages)
            }
            items(state.videoChats, key = { it.chatId }) { chat ->
                SortChip(chat.title, chat.isSelected, { onSelectChat(chat) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SortChip("Newest", state.sortOrder == SortOrder.DATE_DESC) { onSetSortOrder(SortOrder.DATE_DESC) } }
            item { SortChip("Oldest", state.sortOrder == SortOrder.DATE_ASC) { onSetSortOrder(SortOrder.DATE_ASC) } }
            item { SortChip("Largest", state.sortOrder == SortOrder.SIZE_DESC) { onSetSortOrder(SortOrder.SIZE_DESC) } }
            item { SortChip("Smallest", state.sortOrder == SortOrder.SIZE_ASC) { onSetSortOrder(SortOrder.SIZE_ASC) } }
            item { SortChip("Continue only", hideWatched, onToggleHideWatched) }
            item { SortChip("Privacy mode", safeMode, onToggleSafeMode) }
            item { RefreshChip("Favorites", onOpenFavorites) }
            item { RefreshChip("Search", onOpenSearch) }
            item { RefreshChip("Resume", onOpenLastPlayed) }
            item { RefreshChip("Refresh", onRefresh) }
            item { RefreshChip("Settings", onOpenSettings) }
        }
        if (!state.error.isNullOrBlank()) {
            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val cardMinWidth = if (maxWidth < 650.dp) 150.dp else 190.dp
            if (state.isLoading && videos.isEmpty()) {
                CircularProgressIndicator()
            } else if (videos.isEmpty()) {
                Text(
                    text = "No media yet in ${state.selectedChatTitle}. Tap Refresh.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(cardMinWidth),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(videos, key = { item -> "${item.chatId}:${item.messageId}" }) { item ->
                        CompactMediaCard(
                            item = item,
                            sourceLabel = state.selectedChatTitle,
                            onClick = { onOpenVideo(item) },
                            onFocused = { onFocusedVideo(item) }
                        )
                    }
                }
            }
        }

        if (state.hasMore) {
            Button(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(if (state.isLoadingMore) "Loading..." else "Load more")
            }
        }
    }
}

@Composable
private fun CompactMediaCard(
    item: MediaItem,
    sourceLabel: String,
    onClick: () -> Unit,
    onFocused: () -> Unit
) {
    val imageData = item.thumbnailPath?.let { path -> if (path.startsWith("http")) path else File(path) }
    val labels = mediaDisplayLabels(item, sourceLabel)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(imageData).crossfade(true).build(),
                    contentDescription = labels.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(labels.durationLabel, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = labels.title,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(10.dp)
            )
            Text(
                text = labels.fileSizeLabel ?: labels.subtitle,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp)
            )
        }
    }
}

@Composable
private fun ChatRail(
    chats: List<VideoChatItem>,
    pinnedChats: Set<Long>,
    savedSelected: Boolean,
    isLoading: Boolean,
    sidebarError: String?,
    firstItemFocusRequester: FocusRequester,
    onSaved: () -> Unit,
    onChat: (VideoChatItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.width(250.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Chats", style = MaterialTheme.typography.titleLarge, color = Color.White)
        }
        if (!sidebarError.isNullOrBlank()) {
            item {
                Text(sidebarError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            ChatItem(
                title = "Saved Messages",
                selected = savedSelected,
                onClick = onSaved,
                modifier = Modifier.focusRequester(firstItemFocusRequester)
            )
        }
        items(chats.sortedByDescending { pinnedChats.contains(it.chatId) }, key = { it.chatId }) { chat ->
            ChatItem(
                title = if (pinnedChats.contains(chat.chatId)) "Pinned  ${chat.title}" else chat.title,
                selected = chat.isSelected,
                onClick = { onChat(chat) }
            )
        }
        if (isLoading) {
            item { CircularProgressIndicator(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun ChatItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "chatScale")
    val borderColor by animateColorAsState(
        if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "chatBorder"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = title,
            color = if (selected || focused) Color.White else Color(0xFFE4E6F0),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        color = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        val textColor = when {
            selected -> Color.White
            focused -> Color.White
            else -> Color(0xFFE4E6F0)
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
            .clip(RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            color = if (focused) Color.White else Color(0xFFE4E6F0),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}
