package com.vaydelux.bousygram.presentation.layout

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.VideoChatItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.ChatSourceTabs
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
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
    onVideoFocused: (MediaItem) -> Unit = {}
) {
    val adaptive = rememberAdaptiveMetrics()
    val compact = adaptive.isCompact
    val cardWidth = if (compact) 205.dp else 260.dp
    val heroHeight = if (compact) 310.dp else 360.dp
    var focusedItem by remember(featuredVideos) {
        mutableStateOf(featuredVideos.firstOrNull())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Crossfade(targetState = focusedItem, label = "heroBackdrop") { video ->
            HeroBackdrop(video = video, safeMode = safeMode)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = if (compact) 16.dp else 28.dp,
                    top = if (compact) 10.dp else 20.dp,
                    end = if (compact) 16.dp else 22.dp,
                    bottom = 28.dp
                )
        ) {
            if (!compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bousygram",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (safeMode) "Private streaming workspace" else "Streaming from $channelLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        if (!lastPlayedTitle.isNullOrBlank()) {
                            BousygramButton("Resume last", onOpenLastPlayed, selected = true)
                        }
                        BousygramButton("Library", onOpenLibrary)
                        BousygramButton("Favorites", onOpenFavorites)
                        BousygramButton("Search", onOpenSearch)
                        BousygramButton("Settings", onOpenSettings)
                    }
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (safeMode) "Private streaming workspace" else "Streaming from $channelLabel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        if (!lastPlayedTitle.isNullOrBlank()) {
                            BousygramButton("Resume last", onOpenLastPlayed, selected = true)
                        }
                        BousygramButton("Library", onOpenLibrary)
                        BousygramButton("Favorites", onOpenFavorites)
                        BousygramButton("Search", onOpenSearch)
                        BousygramButton("Settings", onOpenSettings)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            ChatSourceTabs(
                chats = sourceChats,
                savedSelected = savedMessagesSelected,
                selectedChatId = selectedChatId,
                onSelectSavedMessages = onSelectSavedMessages,
                onSelectChat = onSelectChat,
                onOpenAllChats = onOpenAllChats,
                pinnedChatIds = pinnedChatIds
            )
            Spacer(Modifier.height(14.dp))

            if (focusedItem != null) {
                HeroCopy(
                    video = focusedItem,
                    channelLabel = channelLabel,
                    safeMode = safeMode,
                    compact = compact,
                    heroHeight = heroHeight,
                    favoriteKeys = favoriteKeys,
                    offlineFileIds = offlineFileIds,
                    onVideoSelected = onVideoSelected,
                    onToggleFavorite = onToggleFavorite,
                    onToggleOffline = onToggleOffline
                )
                Spacer(Modifier.height(18.dp))
            }

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
            if (isLoading && featuredVideos.isNotEmpty()) {
                Text(
                    "Refreshing library…",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            when {
                featuredVideos.isEmpty() && !isLoading -> {
                    EmptyStateView(
                        title = "No videos in this source",
                        message = "Switch to another Telegram chat above or search across all your channels and groups.",
                        actionText = "Search",
                        onAction = onOpenSearch,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)
                    )
                }
                isLoading && featuredVideos.isEmpty() -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(if (compact) 2 else 4) {
                            ShimmerCard(Modifier.width(cardWidth).height(if (compact) 150.dp else 190.dp))
                        }
                    }
                }
                else -> {
                    val continueItems = featuredVideos
                        .filter { item ->
                            val key = "${item.chatId}:${item.messageId}"
                            resumeMessageKeys.contains(key) || item.fileId == lastPlayedFileId
                        }
                        .distinctBy { "${it.chatId}:${it.messageId}" }
                    val favoriteItems = featuredVideos.filter { favoriteKeys.contains(mediaKey(it)) }
                    val offlineItems = featuredVideos.filter { offlineFileIds.contains(it.fileId ?: -1) }
                    if (continueItems.isNotEmpty()) {
                        MediaRail("Continue watching", continueItems, channelLabel, "Continue", safeMode, cardWidth, onVideoSelected) {
                            focusedItem = it
                            onVideoFocused(it)
                        }
                    }
                    if (favoriteItems.isNotEmpty()) {
                        MediaRail("Favorites", favoriteItems, channelLabel, "Favorite", safeMode, cardWidth, onVideoSelected) {
                            focusedItem = it
                            onVideoFocused(it)
                        }
                    }
                    if (offlineItems.isNotEmpty()) {
                        MediaRail("Offline pins", offlineItems, channelLabel, "Offline", safeMode, cardWidth, onVideoSelected) {
                            focusedItem = it
                            onVideoFocused(it)
                        }
                    }
                    MediaRail(
                        if (safeMode) "Recently viewed" else "Recent videos in $channelLabel",
                        featuredVideos,
                        channelLabel,
                        null,
                        safeMode,
                        cardWidth,
                        onVideoSelected
                    ) {
                        focusedItem = it
                        onVideoFocused(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(video: MediaItem?, safeMode: Boolean) {
    if (video == null) return
    val miniThumbnail = rememberMiniThumbnail(
        video.chatId,
        video.messageId,
        if (safeMode) null else video.miniThumbnailBytes
    )
    val imageData = if (safeMode) null else video.thumbnailPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val context = LocalContext.current
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context).data(imageData).crossfade(false).size(1280, 720).build()
    }
    Box(Modifier.fillMaxSize()) {
        if (imageData != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(32.dp)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.22f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.62f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
    }
}

@Composable
private fun HeroCopy(
    video: MediaItem?,
    channelLabel: String,
    safeMode: Boolean,
    compact: Boolean,
    heroHeight: Dp,
    favoriteKeys: Set<String>,
    offlineFileIds: Set<Int>,
    onVideoSelected: (MediaItem) -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onToggleOffline: (MediaItem) -> Unit
) {
    val current = video ?: return
    val labels = mediaDisplayLabels(current, channelLabel)
    val miniThumbnail = rememberMiniThumbnail(
        current.chatId,
        current.messageId,
        if (safeMode) null else current.miniThumbnailBytes
    )
    val imageData = if (safeMode) null else current.thumbnailPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val context = LocalContext.current
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context).data(imageData).crossfade(false).size(1280, 720).build()
    }
    val heroShape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(heroShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                )
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, heroShape)
    ) {
        if (imageData != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (compact) {
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.15f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                                    Color.Transparent
                                )
                            )
                        }
                    )
            )
        }

        Column(
            modifier = Modifier
                .align(if (compact) Alignment.BottomStart else Alignment.CenterStart)
                .fillMaxWidth(if (compact) 1f else 0.56f)
                .padding(horizontal = if (compact) 18.dp else 28.dp, vertical = if (compact) 18.dp else 24.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        RoundedCornerShape(999.dp)
                    )
                ) {
                    Text(
                        text = if (safeMode) "PRIVATE MODE" else "FEATURED · ${channelLabel.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!safeMode && labels.durationLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
                    ) {
                        Text(
                            text = labels.durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (safeMode) "Private video" else labels.title,
                style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (safeMode) {
                    "Media details hidden"
                } else {
                    listOfNotNull(labels.subtitle, labels.fileSizeLabel).joinToString(" • ")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                BousygramButton("Play", { onVideoSelected(current) }, selected = true)
                BousygramButton(
                    if (favoriteKeys.contains(mediaKey(current))) "Favorited" else "Favorite",
                    { onToggleFavorite(current) },
                    selected = favoriteKeys.contains(mediaKey(current))
                )
                if (current.fileId != null) {
                    BousygramButton(
                        if (offlineFileIds.contains(current.fileId)) "Offline" else "Pin offline",
                        { onToggleOffline(current) },
                        selected = offlineFileIds.contains(current.fileId)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaRail(
    title: String,
    items: List<MediaItem>,
    channelLabel: String,
    badge: String?,
    safeMode: Boolean,
    cardWidth: Dp,
    onVideoSelected: (MediaItem) -> Unit,
    onFocused: (MediaItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${items.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(end = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(items, key = { _, item -> "${item.chatId}:${item.messageId}:$title" }) { _, video ->
            MediaPosterCard(
                media = video,
                subtitle = channelLabel,
                onClick = { onVideoSelected(video) },
                badge = badge,
                privacyMode = safeMode,
                cardWidth = cardWidth,
                onFocusChanged = { focused ->
                    if (focused) onFocused(video)
                }
            )
        }
    }
    Spacer(Modifier.height(14.dp))
}

private fun mediaKey(item: MediaItem): String = "${item.chatId}:${item.messageId}"
