package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.core.tdlib.client.TelegramConnectionState
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramCompactPageTitle
import com.vaydelux.bousygram.presentation.components.BousygramIconButton
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
import com.vaydelux.bousygram.presentation.components.BousygramSectionTab
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import com.vaydelux.bousygram.presentation.theme.LocalBousygramDisplaySpec

/**
 * Home is a read-only overview of the bounded Saved Messages preview.
 * Library owns Telegram folder, topic and history navigation. Switching tabs
 * does not substitute a label while accidentally retaining another folder's
 * media on Home; nor does Home initiate a new 10k-dialog scan.
 */
@Composable
internal fun TelegramHomeOverview(
    recentSaved: List<MediaItem>,
    isLoadingSaved: Boolean,
    connection: TelegramConnectionState?,
    isTv: Boolean,
    privacyMode: Boolean,
    hasResume: Boolean,
    onResume: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onFocusedMedia: (MediaItem) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSection: (ExplorerSection) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val adaptive = rememberAdaptiveMetrics()
    val spec = LocalBousygramDisplaySpec.current
    val shortWindow = adaptive.isShortWindow
    val narrow = adaptive.widthDp < 440.dp
    val fontScale = LocalDensity.current.fontScale
    val sourceIconsOnly = explorerSourceTabsIconOnly(
        adaptive.widthDp / fontScale.coerceAtLeast(1f), isTv, fontScale
    )
    val controlsGap = if (shortWindow) 6.dp else 10.dp
    val status = when {
        connection == TelegramConnectionState.OFFLINE -> "Offline"
        isLoadingSaved -> "Loading Saved Messages"
        else -> "${recentSaved.size} recent videos"
    }
    PremiumGridBackdrop(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize()
                .testTag("telegram-home-overview")
                .padding(
                    horizontal = spec.horizontalPadding,
                    vertical = if (shortWindow) 8.dp else spec.verticalPadding
                ),
            verticalArrangement = Arrangement.spacedBy(controlsGap)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (shortWindow) {
                    BousygramCompactPageTitle(
                        title = "Home",
                        status = status,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    BousygramPageHeading(
                        eyebrow = "Telegram media",
                        title = "Home",
                        subtitle = "Your latest saved videos",
                        compact = true,
                        icon = BousygramIcons.Home,
                        modifier = Modifier.weight(1f)
                    )
                }
                BousygramIconButton(
                    icon = BousygramIcons.Search,
                    contentDescription = "Search",
                    tooltip = "Search Telegram videos",
                    onClick = onOpenSearch
                )
                if (hasResume) {
                    BousygramIconButton(
                        icon = BousygramIcons.Continue,
                        contentDescription = "Continue watching",
                        tooltip = "Resume last playback",
                        selected = true,
                        onClick = onResume
                    )
                }
                BousygramIconButton(
                    icon = BousygramIcons.Favorites,
                    contentDescription = "Favorites",
                    tooltip = "Open Telegram favorites",
                    onClick = onOpenFavorites
                )
                if (!narrow) {
                    BousygramIconButton(
                        icon = BousygramIcons.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BousygramButton(
                    text = "Open library",
                    leadingIcon = BousygramIcons.Library,
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f).testTag("telegram-home-open-library")
                )
                if (narrow) {
                    BousygramIconButton(
                        icon = BousygramIcons.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(controlsGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (section in ExplorerSection.entries) {
                    val label = when (section) {
                        ExplorerSection.SAVED -> "Saved"
                        ExplorerSection.CHANNELS -> "Channels"
                        ExplorerSection.GROUPS -> "Groups"
                    }
                    val icon = when (section) {
                        ExplorerSection.SAVED -> BousygramIcons.Saved
                        ExplorerSection.CHANNELS -> BousygramIcons.Channel
                        ExplorerSection.GROUPS -> BousygramIcons.Group
                    }
                    BousygramSectionTab(
                        label = label,
                        active = false,
                        icon = if (sourceIconsOnly) icon else null,
                        iconOnly = sourceIconsOnly,
                        compact = narrow,
                        tooltip = "Browse $label in Library",
                        isTv = isTv,
                        onClick = { onOpenSection(section) },
                        modifier = Modifier.weight(1f)
                            .testTag("telegram-home-section-${section.name.lowercase()}")
                    )
                }
            }

            Text(
                text = "Recent videos",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (recentSaved.isEmpty()) {
                Text(
                    text = if (isLoadingSaved) "Loading your first Saved Messages page…"
                    else "No Saved Messages preview yet. Open Library to browse.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("telegram-home-preview-status")
                )
            } else {
                LazyVerticalGrid(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .testTag("telegram-home-recent-grid"),
                    columns = GridCells.Adaptive(
                        if (isTv) 176.dp else if (adaptive.widthDp < 360.dp) 128.dp else 144.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(recentSaved, key = { "${it.chatId}:${it.messageId}" }) { item ->
                        MediaPosterCard(
                            media = item,
                            subtitle = "Saved Messages",
                            privacyMode = privacyMode,
                            cardWidth = null,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onPlay(item) },
                            onFocusChanged = { focused -> if (focused) onFocusedMedia(item) }
                        )
                    }
                }
            }
        }
    }
}
