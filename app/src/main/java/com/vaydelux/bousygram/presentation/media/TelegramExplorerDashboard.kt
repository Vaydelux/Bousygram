package com.vaydelux.bousygram.presentation.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BrowseGridShimmerSkeleton
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.media.vm.BrowseRestoration
import com.vaydelux.bousygram.presentation.media.vm.ExplorerLevel
import com.vaydelux.bousygram.presentation.media.vm.ExplorerPosition
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

/** Real Telegram directories. No synthetic topics, no horizontal rails. */
@Composable
fun TelegramExplorerDashboard(
    position: ExplorerPosition,
    sources: List<VideoChatItem>,
    topics: List<ForumTopicChannel>,
    selectedChatTitle: String,
    selectedChatId: Long?,
    selectedTopicId: Int?,
    videos: List<MediaItem>,
    isLoading: Boolean,
    isLoadingSources: Boolean,
    isLoadingTopics: Boolean,
    errorMessage: String?,
    sourceErrorMessage: String?,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    privacyMode: Boolean,
    gridState: LazyGridState,
    sourceFolderRestoration: BrowseRestoration,
    onSourceFocused: (VideoChatItem) -> Unit,
    onSourceViewport: (Int, Int) -> Unit,
    onSelectTab: (ExplorerSection) -> Unit,
    onSelectSource: (VideoChatItem) -> Unit,
    onSelectTopic: (Int) -> Unit,
    focusedTopicId: Int?,
    onTopicFocused: (Int) -> Unit,
    onGoUp: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onFocusMedia: (MediaItem) -> Unit,
    restoreFocusedMediaKey: String?,
    restoreFocusRequester: FocusRequester,
    onLoadMore: () -> Unit,
    onRefreshSources: () -> Unit,
    onRefreshTopics: () -> Unit,
    onRefreshMedia: () -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    hasResume: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adaptive = rememberAdaptiveMetrics()
    val gap = if (adaptive.isTv) 14.dp else 10.dp
    val cardMin = when {
        adaptive.isTv -> 190.dp
        adaptive.isTabletLayout -> 166.dp
        else -> 140.dp
    }
    val directory = remember(sources, position.section) {
        explorerSources(sources, position.section)
    }
    val channelCount = remember(sources) { explorerSources(sources, ExplorerSection.CHANNELS).size }
    val groupCount = remember(sources) { explorerSources(sources, ExplorerSection.GROUPS).size }
    val showSources = position.level == ExplorerLevel.SOURCES && position.section != ExplorerSection.SAVED
    val showTopics = position.level == ExplorerLevel.TOPICS && position.section == ExplorerSection.GROUPS
    val showMedia = !showSources && !showTopics
    val folderGrid = remember(position.section) {
        LazyGridState(
            firstVisibleItemIndex = sourceFolderRestoration.firstVisibleItemIndex
                .coerceAtMost((directory.size - 1).coerceAtLeast(0)),
            firstVisibleItemScrollOffset = sourceFolderRestoration.firstVisibleItemScrollOffset
        )
    }
    val sourceFocusRequester = remember(position.section) { FocusRequester() }
    val topicGrid = remember(selectedChatId) { LazyGridState() }
    val topicFocusRequester = remember(selectedChatId) { FocusRequester() }

    LaunchedEffect(showTopics, selectedChatId, focusedTopicId, topics.size) {
        if (!showTopics || focusedTopicId == null) return@LaunchedEffect
        val targetIndex = topics.indexOfFirst { it.id == focusedTopicId }
        if (targetIndex >= 0) {
            topicGrid.scrollToItem(targetIndex)
            delay(60L)
            runCatching { topicFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(showSources, position.section, folderGrid) {
        onDispose {
            if (showSources) {
                onSourceViewport(folderGrid.firstVisibleItemIndex, folderGrid.firstVisibleItemScrollOffset)
            }
        }
    }

    // An async GetChat batch changes directory.size repeatedly. Restore focus once
    // per directory visit, not on every batch (which steals D-pad focus mid-navigation).
    var restoredDirectoryFocus by remember(position.section) { mutableStateOf(false) }
    LaunchedEffect(showSources) {
        if (!showSources) restoredDirectoryFocus = false
    }
    LaunchedEffect(showSources, directory.size, sourceFolderRestoration.focusedMediaKey) {
        if (!showSources || restoredDirectoryFocus) return@LaunchedEffect
        val target = sourceFolderRestoration.focusedMediaKey ?: return@LaunchedEffect
        val targetIndex = directory.indexOfFirst { it.chatId.toString() == target }
        if (targetIndex >= 0) {
            folderGrid.scrollToItem(targetIndex)
            delay(60L)
            if (runCatching { sourceFocusRequester.requestFocus() }.isSuccess) {
                restoredDirectoryFocus = true
            }
        }
    }

    BackHandler(enabled = position.section != ExplorerSection.SAVED && position.level != ExplorerLevel.SOURCES) {
        onGoUp()
    }
    LaunchedEffect(showMedia, videos.size, hasMore, isLoading, isLoadingMore) {
        if (!showMedia || videos.isEmpty()) return@LaunchedEffect
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { index ->
                if (index >= videos.size - 6 && hasMore && !isLoading && !isLoadingMore) onLoadMore()
            }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = if (adaptive.isTv) 28.dp else 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        // A single oversized action row clips on compact phones. Keep TV/tablet
        // actions on one line; use two comfortable touch rows on phones.
        if (adaptive.isTv || adaptive.isTabletLayout) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BOUSYGRAM  /  TELEGRAM MEDIA",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall)
                    Text("Your library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (hasResume) OutlinedButton(onClick = onResume, modifier = Modifier.heightIn(min = 48.dp)) { Text("Continue") }
                OutlinedButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) { Text("Search") }
                OutlinedButton(onClick = onFavorites, modifier = Modifier.heightIn(min = 48.dp)) { Text("Favorites") }
                OutlinedButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Settings") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("BOUSYGRAM", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall)
                    Text("Your library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onSearch, modifier = Modifier.heightIn(min = 48.dp)) { Text("Search") }
                OutlinedButton(onClick = onSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Settings") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasResume) OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Continue") }
                OutlinedButton(onClick = onFavorites, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Favorites") }
            }
        }
        // Fixed, equal-width tabs; the explorer scrolls vertically instead.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExplorerSection.entries.forEach { section ->
                val label = when (section) {
                    ExplorerSection.SAVED -> "Saved Messages"
                    ExplorerSection.CHANNELS -> "Channels"
                    ExplorerSection.GROUPS -> "Groups"
                }
                val active = section == position.section
                val detailCount = when (section) {
                    ExplorerSection.SAVED -> 0
                    ExplorerSection.CHANNELS -> channelCount
                    ExplorerSection.GROUPS -> groupCount
                }
                val tabTitle = if (section != ExplorerSection.SAVED && !adaptive.isPhoneLayout) {
                    "$label · $detailCount"
                } else label
                var tabFocused by remember(section) { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { if (!active) onSelectTab(section) },
                    modifier = Modifier.weight(1f)
                        .border(
                            if (tabFocused) 3.dp else 1.dp,
                            if (tabFocused || active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(18.dp)
                        )
                        .semantics { this.selected = active }
                        .onFocusChanged { tabFocused = it.isFocused },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = if (adaptive.isTv) 11.dp else 13.dp)
                ) {
                    Text(
                        tabTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (adaptive.isTv) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        // Contextual collection header: concise on phones, comfortably readable on TV.
        val folderTitle = when {
            position.section == ExplorerSection.SAVED -> "Saved Messages"
            showSources && position.section == ExplorerSection.CHANNELS -> "Your channels"
            showSources -> "Your groups"
            showTopics -> "Forum topics"
            else -> selectedChatTitle
        }
        val folderDescription = when {
            position.section == ExplorerSection.SAVED -> "Media saved to your own Telegram chat"
            showSources && position.section == ExplorerSection.CHANNELS -> "Explore media from channels you follow"
            showSources -> "Explore your groups and forum discussions"
            showTopics -> "Choose a topic to browse its shared media"
            else -> "Videos, audio and documents in this Telegram collection"
        }
        val collectionStatus = when {
            showSources -> explorerDirectoryStatus(position.section, directory.size, isLoadingSources)
            showTopics -> if (isLoadingTopics) "${topics.size} topics found · Syncing" else "${topics.size} topics available"
            isLoading -> "${videos.size} media shown · Loading"
            else -> "${videos.size} media shown"
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (adaptive.isTv) 20.dp else 14.dp,
                    vertical = if (adaptive.isTv) 18.dp else 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!showSources && position.section != ExplorerSection.SAVED) {
                        OutlinedButton(
                            onClick = onGoUp,
                            modifier = Modifier.heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text("‹ Back") }
                    }
                    Text(
                        when (position.section) {
                            ExplorerSection.SAVED -> "PERSONAL  /  SAVED"
                            ExplorerSection.CHANNELS -> "TELEGRAM  /  CHANNELS"
                            ExplorerSection.GROUPS -> "TELEGRAM  /  GROUPS"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            folderTitle,
                            style = if (adaptive.isTv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            folderDescription,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (adaptive.isPhoneLayout) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (showSources && !isLoadingSources) {
                        OutlinedButton(
                            onClick = onRefreshSources,
                            modifier = Modifier.heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("Refresh") }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if ((showSources && isLoadingSources) || (showTopics && isLoadingTopics) || (showMedia && isLoading)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    Text(collectionStatus, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (privacyMode) {
                        Text("· Privacy mode", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                showSources -> {
                    if (directory.isEmpty()) {
                        if (isLoadingSources) ExplorerLoadingGrid("Loading source directory…", cardMin)
                        else ExplorerMessage(
                            message = sourceErrorMessage ?: "No joined sources in this section",
                            onRetry = onRefreshSources
                        )
                    } else LazyVerticalGrid(
                        state = folderGrid, columns = GridCells.Adaptive(cardMin),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 36.dp)
                    ) {
                        items(directory, key = { "chat:" + it.chatId }) { source ->
                            ExplorerFolderCard(
                                title = source.title,
                                subtitle = explorerSourceLabel(source),
                                detail = explorerSourceDetail(source),
                                badge = if (source.isArchived) "ARCHIVED" else if (source.isForum) "TOPICS" else "MEDIA",
                                symbol = if (source.isForum) "▦" else if (source.kind == com.vaydelux.bousygram.domain.model.ChatSourceKind.CHANNEL) "▣" else "▤",
                                isTv = adaptive.isTv,
                                modifier = if (sourceFolderRestoration.focusedMediaKey == source.chatId.toString()) {
                                    Modifier.focusRequester(sourceFocusRequester)
                                } else Modifier,
                                onFocused = { onSourceFocused(source) },
                                onClick = {
                                    onSourceFocused(source)
                                    onSelectSource(source)
                                }
                            )
                        }
                    }
                }
                showTopics -> {
                    if (topics.isEmpty()) {
                        if (isLoadingTopics) ExplorerLoadingGrid("Loading forum topics…", cardMin)
                        else ExplorerMessage(
                            message = errorMessage ?: "No accessible topics in this group",
                            onRetry = onRefreshTopics
                        )
                    } else LazyVerticalGrid(
                        state = topicGrid,
                        columns = GridCells.Adaptive(cardMin),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 36.dp)
                    ) {
                        items(topics, key = { "topic:" + selectedChatId + ":" + it.id }) { topic ->
                            ExplorerFolderCard(
                                title = topic.name,
                                subtitle = if (topic.isGeneral) "General topic" else "Forum topic",
                                detail = "Open messages and shared media",
                                badge = if (topic.isGeneral) "GENERAL" else "TOPIC",
                                symbol = "▦",
                                isTv = adaptive.isTv,
                                modifier = if (focusedTopicId == topic.id) {
                                    Modifier.focusRequester(topicFocusRequester)
                                } else Modifier,
                                onFocused = { onTopicFocused(topic.id) },
                                onClick = {
                                    onTopicFocused(topic.id)
                                    onSelectTopic(topic.id)
                                }
                            )
                        }
                    }
                }
                videos.isEmpty() -> {
                    if (isLoading) ExplorerLoadingGrid("Loading media…", cardMin)
                    else ExplorerMessage(
                        message = errorMessage ?: "No playable media found in this folder",
                        onRetry = onRefreshMedia
                    )
                }
                else -> LazyVerticalGrid(
                    state = gridState, columns = GridCells.Adaptive(cardMin),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap),
                    contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 40.dp)
                ) {
                    items(videos, key = { it.chatId.toString() + ":" + it.messageId }) { item ->
                        MediaPosterCard(
                            media = item,
                            subtitle = selectedChatTitle,
                            cardWidth = null,
                            privacyMode = privacyMode,
                            modifier = Modifier.fillMaxWidth()
                                .then(
                                    if (restoreFocusedMediaKey == item.chatId.toString() + ":" + item.messageId) {
                                        Modifier.focusRequester(restoreFocusRequester)
                                    } else Modifier
                                ),
                            onClick = { onPlay(item) },
                            onFocusChanged = { focused -> if (focused) onFocusMedia(item) }
                        )
                    }
                    if (isLoadingMore) item(key = "loading-more") { Text("Loading more…") }
                }
            }
        }
    }
}

/** Skeletons are shown only when the selected folder has no cached content. */
@Composable
private fun ExplorerLoadingGrid(message: String, cardMin: androidx.compose.ui.unit.Dp) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        BrowseGridShimmerSkeleton(
            columns = GridCells.Adaptive(cardMin),
            itemCount = 8,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExplorerMessage(message: String, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * One shared card for channels, groups and real forum topics.
 * Metadata is derived from TDLib; no placeholder ratings, thumbnails or item counts.
 * The whole card remains accessible via touch, keyboard and TV D-pad.
 */
@Composable
private fun ExplorerFolderCard(
    title: String,
    subtitle: String,
    detail: String,
    badge: String,
    symbol: String,
    isTv: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (isTv) 164.dp else 148.dp)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) accent else MaterialTheme.colorScheme.outlineVariant,
                shape
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title, $subtitle. $detail" },
        shape = shape,
        color = if (focused) MaterialTheme.colorScheme.surfaceContainerHighest
            else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = if (focused) 6.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        symbol,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    badge,
                    color = if (focused) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "›",
                    color = accent,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
