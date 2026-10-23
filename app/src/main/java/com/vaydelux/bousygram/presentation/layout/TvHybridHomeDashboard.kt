package com.vaydelux.bousygram.presentation.layout

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.data.repository.ContinueWatchingEntry
import com.vaydelux.bousygram.data.repository.ContinueWatchingRepository
import com.vaydelux.bousygram.presentation.components.CenteredPlayBadge
import com.vaydelux.bousygram.presentation.components.ChatSourceTabs
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.GlassPill
import com.vaydelux.bousygram.presentation.components.ImagePlaceholderIcon
import com.vaydelux.bousygram.presentation.components.ShimmerCard
import com.vaydelux.bousygram.presentation.components.rememberMiniThumbnail
import com.vaydelux.bousygram.presentation.media.mediaDisplayLabels
import java.io.File

@Composable
fun TvHybridHomeDashboard(
    featuredVideos: List<MediaItem>,
    channelLabel: String,
    isLoading: Boolean,
    errorMessage: String?,
    onVideoSelected: (MediaItem) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLastPlayed: () -> Unit,
    lastPlayedFileId: Int?,
    lastPlayedTitle: String?,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleOffline: (MediaItem) -> Unit,
    favoriteKeys: Set<String>,
    sourceChats: List<VideoChatItem>,
    savedMessagesSelected: Boolean,
    selectedChatId: Long?,
    pinnedChatIds: Set<Long>,
    onSelectSavedMessages: () -> Unit,
    onSelectChat: (VideoChatItem) -> Unit,
    onOpenAllChats: () -> Unit,
    offlineFileIds: Set<Int>,
    resumeMessageKeys: Set<String>,
    safeMode: Boolean,
    modifier: Modifier = Modifier,
    onVideoFocused: (MediaItem) -> Unit = {},
    selectedCategoryTab: com.vaydelux.bousygram.domain.model.MediaCategoryTab = com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL,
    selectedSeriesTitle: String? = null,
    seriesGroups: List<com.vaydelux.bousygram.domain.model.SeriesGroup> = emptyList(),
    topicGroups: Map<String, List<MediaItem>> = emptyMap(),
    onSelectCategoryTab: (com.vaydelux.bousygram.domain.model.MediaCategoryTab) -> Unit = {},
    onSelectSeries: (String?) -> Unit = {}
) {
    val adaptive = rememberAdaptiveMetrics()
    val compact = adaptive.isCompact
    val cardWidth: Dp = if (compact) 180.dp else 224.dp

    val continueWatchingRail by ContinueWatchingRepository.railItems.collectAsStateWithLifecycle()

    val continueItem = remember(featuredVideos, lastPlayedFileId, continueWatchingRail) {
        val topRail = continueWatchingRail.firstOrNull()
        if (topRail != null) {
            featuredVideos.firstOrNull { it.chatId == topRail.chatId && it.messageId == topRail.messageId }
                ?: MediaItem(
                    chatId = topRail.chatId,
                    messageId = topRail.messageId,
                    date = (topRail.updatedAt / 1000).toInt(),
                    type = com.vaydelux.bousygram.domain.model.MediaType.Video,
                    title = topRail.title,
                    fileId = topRail.fileId,
                    durationSeconds = (topRail.durationMs / 1000).toInt(),
                    thumbnailPath = topRail.thumbnailPath
                )
        } else {
            featuredVideos.firstOrNull { it.fileId == lastPlayedFileId }
                ?: featuredVideos.firstOrNull { resumeMessageKeys.contains("${it.chatId}:${it.messageId}") }
                ?: featuredVideos.firstOrNull()
        }
    }

    val favoriteVideos = remember(featuredVideos, favoriteKeys) {
        val favs = featuredVideos.filter { favoriteKeys.contains("${it.chatId}:${it.messageId}") }
        if (favs.isNotEmpty()) favs else featuredVideos.take(6)
    }

    var showDetailsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (compact) 14.dp else 24.dp,
                    top = if (compact) 8.dp else 12.dp,
                    end = if (compact) 14.dp else 24.dp,
                    bottom = 32.dp
                ),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Source selector tabs if multiple Telegram sources are available
            if (sourceChats.isNotEmpty()) {
                ChatSourceTabs(
                    chats = sourceChats,
                    savedSelected = savedMessagesSelected,
                    selectedChatId = selectedChatId,
                    onSelectSavedMessages = onSelectSavedMessages,
                    onSelectChat = onSelectChat,
                    onOpenAllChats = onOpenAllChats,
                    pinnedChatIds = pinnedChatIds
                )
            }

            // Category filter chips: "All Videos" | "Series & Shows" | "Topics"
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                com.vaydelux.bousygram.domain.model.MediaCategoryTab.entries.forEach { tab ->
                    val isSelected = tab == selectedCategoryTab
                    val shape = RoundedCornerShape(20.dp)
                    Surface(
                        shape = shape,
                        color = if (isSelected) Color(0xFF5EE5BE) else Color(0xFF131D2E),
                        modifier = Modifier
                            .clip(shape)
                            .clickable {
                                onSelectCategoryTab(tab)
                            }
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF5EE5BE) else Color(0xFF223249),
                                shape
                            )
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) Color(0xFF091418) else Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Error banner if TDLib reports issue
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Shimmer loading cards
            if (isLoading && featuredVideos.isEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Loading media…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(if (compact) 2 else 4) {
                            ShimmerCard(Modifier.width(cardWidth).height(if (compact) 110.dp else 134.dp))
                        }
                    }
                }
            } else if (featuredVideos.isEmpty() && !isLoading) {
                EmptyStateView(
                    title = "No videos in this source",
                    message = "Switch to another Telegram chat above or search across your channels.",
                    actionText = "Search",
                    onAction = onOpenSearch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                )
            } else when (selectedCategoryTab) {
                com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL -> {
                    // Top Hero Card: "PICK UP WHERE YOU LEFT OFF"
                    ModernHeroBanner(
                        video = continueItem,
                        lastPlayedTitle = lastPlayedTitle,
                        onResume = {
                            if (continueItem != null) onVideoSelected(continueItem) else onOpenLastPlayed()
                        },
                        onDetails = { showDetailsDialog = true }
                    )

                    // Continue Watching Rail (Top 12 unfinished videos)
                    if (continueWatchingRail.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Continue Watching",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E2E48)
                                ) {
                                    Text(
                                        text = "${continueWatchingRail.size}",
                                        color = Color(0xFF5EE5BE),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    continueWatchingRail,
                                    key = { index, item -> "cw_${item.chatId}_${item.messageId}_$index" }
                                ) { index, item ->
                                    val matchingMedia = featuredVideos.firstOrNull {
                                        it.chatId == item.chatId && it.messageId == item.messageId
                                    } ?: MediaItem(
                                        chatId = item.chatId,
                                        messageId = item.messageId,
                                        date = (item.updatedAt / 1000).toInt(),
                                        type = com.vaydelux.bousygram.domain.model.MediaType.Video,
                                        title = item.title,
                                        fileId = item.fileId,
                                        durationSeconds = (item.durationMs / 1000).toInt(),
                                        thumbnailPath = item.thumbnailPath
                                    )
                                    ContinueWatchingCard(
                                        entry = item,
                                        media = matchingMedia,
                                        onClick = { onVideoSelected(matchingMedia) },
                                        cardWidth = cardWidth,
                                        onFocusChanged = { if (it) onVideoFocused(matchingMedia) }
                                    )
                                }
                            }
                        }
                    }

                    // Section 1: "Recently added"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Recently added",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(end = 24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(
                                featuredVideos,
                                key = { index, item -> "recent_${item.chatId}_${item.messageId}_$index" }
                            ) { index, item ->
                                ModernVideoCard(
                                    media = item,
                                    index = index,
                                    isFavoriteSection = false,
                                    onClick = { onVideoSelected(item) },
                                    cardWidth = cardWidth,
                                    onFocusChanged = { if (it) onVideoFocused(item) }
                                )
                            }
                        }
                    }

                    // Section 2: "Your favorites"
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Your favorites",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(end = 24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(
                                favoriteVideos,
                                key = { index, item -> "fav_${item.chatId}_${item.messageId}_$index" }
                            ) { index, item ->
                                ModernVideoCard(
                                    media = item,
                                    index = index,
                                    isFavoriteSection = true,
                                    onClick = { onVideoSelected(item) },
                                    cardWidth = cardWidth,
                                    onFocusChanged = { if (it) onVideoFocused(item) }
                                )
                            }
                        }
                    }
                }

                com.vaydelux.bousygram.domain.model.MediaCategoryTab.SERIES -> {
                    val activeSeries = seriesGroups.firstOrNull { it.title == selectedSeriesTitle }
                    if (activeSeries != null) {
                        // Series Episode View
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E2E48),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectSeries(null) }
                                ) {
                                    Text(
                                        text = "← All Shows",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Text(
                                    text = activeSeries.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF5EE5BE).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${activeSeries.totalSeasons} Season(s) • ${activeSeries.episodes.size} Episodes",
                                        color = Color(0xFF5EE5BE),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    activeSeries.episodes,
                                    key = { idx, ep -> "ep_${ep.chatId}_${ep.messageId}_$idx" }
                                ) { index, episode ->
                                    ModernVideoCard(
                                        media = episode,
                                        index = index,
                                        isFavoriteSection = false,
                                        onClick = { onVideoSelected(episode) },
                                        cardWidth = cardWidth,
                                        onFocusChanged = { if (it) onVideoFocused(episode) }
                                    )
                                }
                            }
                        }
                    } else if (seriesGroups.isEmpty()) {
                        EmptyStateView(
                            title = "No TV Series Detected",
                            message = "Videos with episode numbering (e.g. S01E01 or 1x01) will automatically appear grouped here.",
                            actionText = "Show All",
                            onAction = { onSelectCategoryTab(com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else {
                        // Series Cards Grid / Row
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                text = "TV Shows & Series (${seriesGroups.size})",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                                fontWeight = FontWeight.Bold
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(
                                    seriesGroups,
                                    key = { idx, s -> "series_${s.title}_$idx" }
                                ) { _, series ->
                                    ModernSeriesCard(
                                        series = series,
                                        onClick = { onSelectSeries(series.title) },
                                        cardWidth = cardWidth
                                    )
                                }
                            }
                        }
                    }
                }

                com.vaydelux.bousygram.domain.model.MediaCategoryTab.TOPICS -> {
                    if (topicGroups.isEmpty()) {
                        EmptyStateView(
                            title = "No Topics Found",
                            message = "This chat does not contain forum topics or message threads.",
                            actionText = "Show All",
                            onAction = { onSelectCategoryTab(com.vaydelux.bousygram.domain.model.MediaCategoryTab.ALL) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            topicGroups.forEach { (topicTitle, topicVideos) ->
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = topicTitle,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF1E2E48)
                                        ) {
                                            Text(
                                                text = "${topicVideos.size}",
                                                color = Color(0xFFCBD5E1),
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(end = 24.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(
                                            topicVideos,
                                            key = { idx, itm -> "topic_${topicTitle}_${itm.chatId}_${itm.messageId}_$idx" }
                                        ) { index, item ->
                                            ModernVideoCard(
                                                media = item,
                                                index = index,
                                                isFavoriteSection = false,
                                                onClick = { onVideoSelected(item) },
                                                cardWidth = cardWidth,
                                                onFocusChanged = { if (it) onVideoFocused(item) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Details Dialog popup
        if (showDetailsDialog) {
            MediaDetailsDialog(
                video = continueItem,
                channelLabel = channelLabel,
                onDismiss = { showDetailsDialog = false },
                onPlay = {
                    showDetailsDialog = false
                    if (continueItem != null) onVideoSelected(continueItem) else onOpenLastPlayed()
                }
            )
        }
    }
}

@Composable
fun ModernHeroBanner(
    video: MediaItem?,
    lastPlayedTitle: String?,
    onResume: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val title = lastPlayedTitle?.takeIf { it.isNotBlank() }
        ?: video?.title?.takeIf { it.isNotBlank() }
        ?: "Continue watching"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF16253C),
                        Color(0xFF131F33),
                        Color(0xFF0F1829)
                    )
                )
            )
            .border(1.dp, Color(0xFF1F304B), shape)
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "PICK UP WHERE YOU LEFT OFF",
                color = Color(0xFF5EE5BE),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp
                ),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (title == "Continue watching") "Continue watching" else title,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    letterSpacing = (-0.3).sp
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Resume at your saved position",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModernResumeButton(
                    text = "Resume",
                    onClick = onResume
                )
                ModernDetailsButton(
                    text = "Details",
                    onClick = onDetails
                )
            }
        }
    }
}

@Composable
private fun ModernResumeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "resumeScale")
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        shape = shape,
        color = if (focused) Color(0xFF75F0CC) else Color(0xFF5EE5BE)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = Color(0xFF091418),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModernDetailsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) Color(0xFF1E2E48) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (focused) Color.White else Color(0xFFCBD5E1),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ModernVideoCard(
    media: MediaItem,
    index: Int,
    isFavoriteSection: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 220.dp,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    val context = LocalContext.current
    val miniThumbnail = rememberMiniThumbnail(media.chatId, media.messageId, media.miniThumbnailBytes)
    val imageData = media.thumbnailPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }

    val defaultGradient = remember(index, isFavoriteSection) {
        if (!isFavoriteSection) {
            when (index % 3) {
                0 -> listOf(Color(0xFF3B4B5E), Color(0xFF2C3949))
                1 -> listOf(Color(0xFF294144), Color(0xFF1E3134))
                else -> listOf(Color(0xFF45384E), Color(0xFF33293A))
            }
        } else {
            when (index % 3) {
                0 -> listOf(Color(0xFF3F3546), Color(0xFF2F2734))
                1 -> listOf(Color(0xFF2C3843), Color(0xFF1E2831))
                else -> listOf(Color(0xFF434534), Color(0xFF323425))
            }
        }
    }

    val durationMs = remember(media.trustedDurationMs, media.durationSeconds) {
        media.trustedDurationMs.takeIf { it > 0L }
            ?: media.durationSeconds.takeIf { it > 0 }?.times(1000L)
            ?: 0L
    }
    val isWatched = remember(media.chatId, media.messageId, durationMs) {
        PlaybackProgressTracker.isWatched(media.chatId, media.messageId, durationMs)
    }
    val positionMs = remember(media.chatId, media.messageId, durationMs) {
        PlaybackProgressTracker.getMessagePosition(media.chatId, media.messageId, durationMs)
    }
    val progress = if (durationMs > 0L && positionMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (isWatched) alpha = 0.58f
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.primary else Color(0xFF1F2E45),
                    shape = shape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = colors.primary),
                    onClick = onClick
                )
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .focusable(),
            shape = shape,
            color = Color(0xFF16243A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(defaultGradient)),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f))
                    )
                }

                if (!isFavoriteSection) {
                    CenteredPlayBadge()
                } else {
                    ImagePlaceholderIcon(
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isWatched) {
                    GlassPill(
                        text = "✓ Watched",
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        accent = true
                    )
                } else if (progress in 0.05f..0.95f) {
                    GlassPill(
                        text = "Resume ${(progress * 100).toInt()}%",
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    )
                }

                if (!isWatched && progress in 0.05f..0.95f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.5.dp),
                        color = Color(0xFF5EE5BE),
                        trackColor = Color.Black.copy(alpha = 0.45f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = media.title.ifBlank { if (!isFavoriteSection) "Video ${index + 1}" else "Favorite ${index + 1}" },
            color = if (focused) colors.primary else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: ContinueWatchingEntry,
    media: MediaItem,
    onClick: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cwScale"
    )
    val context = LocalContext.current
    val miniThumbnail = rememberMiniThumbnail(entry.chatId, entry.messageId, media.miniThumbnailBytes)
    val imageData = entry.thumbnailPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }

    val progress = entry.progress

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color(0xFF5EE5BE) else Color(0xFF1F2E45),
                    shape = shape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = Color(0xFF5EE5BE)),
                    onClick = onClick
                )
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged(it.isFocused)
                }
                .focusable(),
            shape = shape,
            color = Color(0xFF16243A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF243B55), Color(0xFF141E30)))),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f))
                    )
                }

                CenteredPlayBadge()

                GlassPill(
                    text = "Resume ${(progress * 100).toInt()}%",
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color(0xFF5EE5BE),
                    trackColor = Color.Black.copy(alpha = 0.50f)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = entry.title,
            color = if (focused) Color(0xFF5EE5BE) else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediaDetailsDialog(
    video: MediaItem?,
    channelLabel: String,
    onDismiss: () -> Unit,
    onPlay: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(20.dp)
        Surface(
            shape = shape,
            color = Color(0xFF111927),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.dp, Color(0xFF223249), shape)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Media Details",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                if (video != null) {
                    val labels = mediaDisplayLabels(video, channelLabel)
                    DetailRow(label = "Title", value = labels.title)
                    DetailRow(label = "Source", value = channelLabel)
                    if (labels.durationLabel.isNotBlank()) {
                        DetailRow(label = "Duration", value = labels.durationLabel)
                    }
                    if (!labels.fileSizeLabel.isNullOrBlank()) {
                        DetailRow(label = "File Size", value = labels.fileSizeLabel)
                    }
                    DetailRow(label = "Engine", value = "libmpv (Hardware Accelerated)")
                } else {
                    Text(
                        text = "Ready to stream directly from Telegram high-speed servers.",
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ModernDetailsButton(text = "Close", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    ModernResumeButton(text = "Play Now", onClick = onPlay)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
fun ModernSeriesCard(
    series: com.vaydelux.bousygram.domain.model.SeriesGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 220.dp
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "seriesCardScale"
    )
    val context = LocalContext.current
    val firstEpisode = series.episodes.firstOrNull()
    val miniThumbnail = firstEpisode?.let { rememberMiniThumbnail(it.chatId, it.messageId, it.miniThumbnailBytes) }
    val imageData = firstEpisode?.thumbnailPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) colors.primary else Color(0xFF1F2E45),
                    shape = shape
                )
                .clickable(
                    interactionSource = interaction,
                    indication = ripple(color = colors.primary),
                    onClick = onClick
                )
                .onFocusChanged { focused = it.isFocused }
                .focusable(),
            shape = shape,
            color = Color(0xFF16243A)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFF2B3A4F), Color(0xFF182333)))),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = series.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = "${series.episodes.size} Episodes",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = series.title,
            color = if (focused) colors.primary else Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${series.totalSeasons} Season(s)",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

