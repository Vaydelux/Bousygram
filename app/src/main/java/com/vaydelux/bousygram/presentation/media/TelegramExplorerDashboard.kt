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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.domain.model.ChatSourceKind
import com.vaydelux.bousygram.domain.model.ForumTopicChannel
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.media.vm.ExplorerLevel
import com.vaydelux.bousygram.presentation.media.vm.ExplorerPosition
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import kotlinx.coroutines.flow.distinctUntilChanged

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
    isLoadingMore: Boolean,
    hasMore: Boolean,
    privacyMode: Boolean,
    gridState: LazyGridState,
    onSelectTab: (ExplorerSection) -> Unit,
    onSelectSource: (VideoChatItem) -> Unit,
    onSelectTopic: (Int) -> Unit,
    onGoUp: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onFocusMedia: (MediaItem) -> Unit,
    restoreFocusedMediaKey: String?,
    restoreFocusRequester: FocusRequester,
    onLoadMore: () -> Unit,
    onSearch: () -> Unit,
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
        sources.filter {
            !it.isSavedMessages && when (position.section) {
                ExplorerSection.CHANNELS -> it.kind == ChatSourceKind.CHANNEL
                ExplorerSection.GROUPS -> it.kind == ChatSourceKind.GROUP || it.isForum
                ExplorerSection.SAVED -> false
            }
        }.distinctBy { it.chatId }
    }
    val showSources = position.level == ExplorerLevel.SOURCES && position.section != ExplorerSection.SAVED
    val showTopics = position.level == ExplorerLevel.TOPICS && position.section == ExplorerSection.GROUPS
    val showMedia = !showSources && !showTopics
    val folderGrid = rememberLazyGridState()

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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bousygram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (hasResume) OutlinedButton(onClick = onResume) { Text("Continue") }
            OutlinedButton(onClick = onSearch) { Text("Search") }
            OutlinedButton(onClick = onSettings) { Text("Settings") }
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
                OutlinedButton(
                    onClick = { if (!active) onSelectTab(section) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    contentPadding = PaddingValues(horizontal = 3.dp, vertical = 9.dp)
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!showSources && position.section != ExplorerSection.SAVED) {
                OutlinedButton(onClick = onGoUp, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)) {
                    Text("‹ Back")
                }
            }
            val path = when {
                position.section == ExplorerSection.SAVED -> "Saved Messages / Media"
                showSources -> if (position.section == ExplorerSection.GROUPS) "Groups" else "Channels"
                showTopics -> "Groups / " + selectedChatTitle + " / Topics"
                selectedTopicId != null -> "Groups / " + selectedChatTitle + " / Topic / Media"
                else -> selectedChatTitle + " / Media"
            }
            Text(path, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                showSources -> {
                    if (directory.isEmpty()) {
                        ExplorerMessage(if (isLoading) "Loading source directory…" else "No joined sources in this section")
                    } else LazyVerticalGrid(
                        state = folderGrid, columns = GridCells.Adaptive(cardMin),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 36.dp)
                    ) {
                        items(directory, key = { "chat:" + it.chatId }) { source ->
                            ExplorerFolderCard(source.title, if (source.isForum) "Topics" else "Media files") { onSelectSource(source) }
                        }
                    }
                }
                showTopics -> {
                    if (topics.isEmpty()) {
                        ExplorerMessage(if (isLoading) "Loading forum topics…" else "No accessible topics in this group")
                    } else LazyVerticalGrid(
                        columns = GridCells.Adaptive(cardMin),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 36.dp)
                    ) {
                        items(topics, key = { "topic:" + selectedChatId + ":" + it.id }) { topic ->
                            ExplorerFolderCard(topic.name, if (topic.isGeneral) "General topic" else "Forum topic") { onSelectTopic(topic.id) }
                        }
                    }
                }
                videos.isEmpty() -> ExplorerMessage(if (isLoading) "Loading media…" else "No playable media found in this folder")
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

@Composable
private fun ExplorerMessage(message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExplorerFolderCard(title: String, subtitle: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth()
            .border(if (focused) 3.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("▣", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
