package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.core.tdlib.client.TelegramConnectionState
import com.vaydelux.bousygram.presentation.components.BousygramBrandMark
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconButton
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramSectionTab
import com.vaydelux.bousygram.presentation.components.BousygramTooltipPlacement
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.tabular

/**
 * Compact Cinema Halo explorer toolbar. Keeps the media grid dominant above
 * the fold on 540dp-high Android TVs and short landscape phones while eliminating
 * text abbreviations ("Favs", "Chans") through Lucide iconography + tooltips.
 */
internal enum class ExplorerHeaderDensity { COMPACT, NORMAL }

internal fun explorerHeaderDensity(isTv: Boolean, widthDp: Dp, heightDp: Dp): ExplorerHeaderDensity =
    if (isTv || heightDp < 640.dp || widthDp > heightDp) ExplorerHeaderDensity.COMPACT
    else ExplorerHeaderDensity.NORMAL

internal fun explorerHeaderShowInlineActions(widthDp: Dp): Boolean = widthDp >= 930.dp

@Composable
internal fun TelegramExplorerCompactHeader(
    section: ExplorerSection,
    screenTitle: String,
    navigationLabel: String,
    folderTitle: String,
    collectionStatus: String,
    widthDp: Dp,
    heightDp: Dp,
    isTv: Boolean,
    privacyMode: Boolean,
    isLoading: Boolean,
    canGoUp: Boolean,
    showSources: Boolean,
    showTopics: Boolean,
    hasResume: Boolean,
    hasTopicError: Boolean,
    channelCount: Int,
    groupCount: Int,
    onSelectTab: (ExplorerSection) -> Unit,
    onGoUp: () -> Unit,
    onBrowseAllMedia: () -> Unit,
    onRefreshSources: () -> Unit,
    onRefreshTopics: () -> Unit,
    onRefreshMedia: () -> Unit,
    onResume: () -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onSettings: () -> Unit,
    onNavigate: () -> Unit,
    connection: TelegramConnectionState? = null,
    hasSourceError: Boolean = false
) {
    val density = explorerHeaderDensity(isTv, widthDp, heightDp)
    val fontScale = LocalDensity.current.fontScale
    val effectiveWidth = widthDp / fontScale.coerceAtLeast(1f)
    val inlineActions = explorerHeaderShowInlineActions(effectiveWidth)
    // On narrow phones (<380dp), collapse Favorites to a crisp 48dp Lucide heart
    // pill with tooltip while preserving exact "Saved" / "Channels" / "Groups"
    // text labels in the scrollable source row (zero abbreviations or cut-offs).
    val ultraCompactTabs = !isTv && effectiveWidth < 380.dp
    val compactTabs = !isTv && effectiveWidth < 520.dp
    val showCounts = isTv || effectiveWidth >= 600.dp
    val minControl = if (isTv) BousygramUiTokens.tvControlHeight
        else BousygramUiTokens.compactControlHeight
    val colors = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().testTag("telegram-explorer-toolbar"),
        verticalArrangement = Arrangement.spacedBy(if (density == ExplorerHeaderDensity.COMPACT) 6.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canGoUp) {
                if (isTv && effectiveWidth >= 760.dp) {
                    BousygramButton(
                        text = "Back",
                        leadingIcon = BousygramIcons.Back,
                        tooltip = "Go up a folder",
                        onClick = onGoUp,
                        modifier = Modifier.heightIn(min = minControl)
                            .testTag("telegram-explorer-go-up")
                    )
                } else {
                    BousygramIconButton(
                        icon = BousygramIcons.Back,
                        contentDescription = "Back",
                        tooltip = "Back",
                        tooltipDetail = "Go up",
                        minSize = minControl,
                        onClick = onGoUp,
                        modifier = Modifier.testTag("telegram-explorer-go-up")
                    )
                }
            } else if (isTv || effectiveWidth >= 420.dp) {
                BousygramBrandMark(
                    size = if (isTv) 36.dp else 32.dp,
                    showHalo = true
                )
            }

            Column(
                modifier = Modifier.weight(1f).testTag("telegram-library-collection"),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = screenTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (isTv) MaterialTheme.typography.titleLarge
                        else if (density == ExplorerHeaderDensity.COMPACT)
                            MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (privacyMode) "$folderTitle · Private · $collectionStatus"
                            else "$folderTitle · $collectionStatus",
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = (if (isTv) MaterialTheme.typography.labelMedium
                            else MaterialTheme.typography.labelSmall).tabular(),
                        color = colors.onSurfaceVariant
                    )
                    if (connection != null) {
                        Text(
                            text = when (connection) {
                                TelegramConnectionState.ONLINE -> "Online"
                                TelegramConnectionState.OFFLINE -> "Offline"
                                TelegramConnectionState.SYNCING -> "Syncing"
                                TelegramConnectionState.CONNECTING -> "Connecting"
                            },
                            modifier = Modifier.testTag("telegram-connection-status"),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (connection) {
                                TelegramConnectionState.ONLINE -> colors.primary
                                TelegramConnectionState.OFFLINE -> colors.error
                                else -> colors.onSurfaceVariant
                            }
                        )
                    }
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = colors.primary
                        )
                    }
                }
            }

            // Adaptive action cluster: full labeled pills on wide TV/tablets,
            // crisp Lucide icon buttons with tooltips on compact screens.
            if (inlineActions) {
                BousygramButton(
                    text = navigationLabel,
                    leadingIcon = if (navigationLabel.equals("Home", ignoreCase = true)) {
                        BousygramIcons.Home
                    } else {
                        BousygramIcons.Library
                    },
                    tooltip = "Go to $navigationLabel",
                    onClick = onNavigate,
                    modifier = Modifier.heightIn(min = minControl)
                )
                if (hasResume) {
                    BousygramButton(
                        text = "Continue",
                        leadingIcon = BousygramIcons.Continue,
                        tooltip = "Continue watching",
                        selected = true,
                        onClick = onResume,
                        modifier = Modifier.heightIn(min = minControl)
                    )
                }
                BousygramButton(
                    text = "Search",
                    leadingIcon = BousygramIcons.Search,
                    tooltip = "Search media",
                    onClick = onSearch,
                    modifier = Modifier.heightIn(min = minControl)
                        .testTag("telegram-explorer-search")
                )
            } else {
                if (hasResume && effectiveWidth >= 420.dp) {
                    BousygramIconButton(
                        icon = BousygramIcons.Continue,
                        contentDescription = "Continue watching",
                        tooltip = "Continue watching",
                        selected = true,
                        minSize = minControl,
                        onClick = onResume
                    )
                }
                if (effectiveWidth >= 360.dp) {
                    BousygramIconButton(
                        icon = BousygramIcons.Search,
                        contentDescription = "Search",
                        tooltip = "Search media",
                        minSize = minControl,
                        onClick = onSearch,
                        modifier = Modifier.testTag("telegram-explorer-search")
                    )
                }
            }

            Box {
                if (inlineActions) {
                    BousygramButton(
                        text = "More",
                        leadingIcon = BousygramIcons.MoreVertical,
                        tooltip = "More actions",
                        onClick = { menuExpanded = true },
                        modifier = Modifier.heightIn(min = minControl)
                            .testTag("telegram-explorer-more")
                    )
                } else {
                    BousygramIconButton(
                        icon = BousygramIcons.MoreVertical,
                        contentDescription = "More",
                        tooltip = "More actions",
                        minSize = minControl,
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("telegram-explorer-more")
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (hasResume) {
                        DropdownMenuItem(
                            leadingIcon = { BousygramIcon(BousygramIcons.Continue, null, size = 18.dp) },
                            text = { Text("Continue watching") },
                            onClick = {
                                menuExpanded = false
                                onResume()
                            }
                        )
                    }
                    DropdownMenuItem(
                        leadingIcon = { BousygramIcon(BousygramIcons.Search, null, size = 18.dp) },
                        text = { Text("Search media") },
                        onClick = {
                            menuExpanded = false
                            onSearch()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            BousygramIcon(
                                icon = if (navigationLabel.equals("Home", ignoreCase = true)) {
                                    BousygramIcons.Home
                                } else {
                                    BousygramIcons.Library
                                },
                                contentDescription = null,
                                size = 18.dp
                            )
                        },
                        text = { Text("Go to $navigationLabel") },
                        onClick = {
                            menuExpanded = false
                            onNavigate()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { BousygramIcon(BousygramIcons.Favorites, null, size = 18.dp) },
                        text = { Text("Favorites") },
                        onClick = {
                            menuExpanded = false
                            onFavorites()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { BousygramIcon(BousygramIcons.Settings, null, size = 18.dp) },
                        text = { Text("Settings") },
                        onClick = {
                            menuExpanded = false
                            onSettings()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = { BousygramIcon(BousygramIcons.Refresh, null, size = 18.dp) },
                        text = {
                            Text(when {
                                showSources -> if (isLoading || hasSourceError) "Retry channels and groups"
                                    else "Refresh channels and groups"
                                showTopics -> if (hasTopicError) "Retry forum topics"
                                    else "Refresh forum topics"
                                else -> "Refresh media"
                            })
                        },
                        enabled = showSources || !isLoading,
                        onClick = {
                            menuExpanded = false
                            when {
                                showSources -> onRefreshSources()
                                showTopics -> onRefreshTopics()
                                else -> onRefreshMedia()
                            }
                        }
                    )
                }
            }
        }

        // Favorites stays pinned alongside Telegram source tabs across 320dp
        // portrait, landscape phones, tablets, and Android TV. Lucide icons +
        // tooltips prevent text clipping without needing awkward abbreviations.
        Row(
            modifier = Modifier.fillMaxWidth().testTag("telegram-library-sections"),
            horizontalArrangement = Arrangement.spacedBy(BousygramUiTokens.compactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BousygramSectionTab(
                label = "Favorites",
                accessibilityLabel = "Favorites",
                icon = BousygramIcons.Favorites,
                iconOnly = ultraCompactTabs,
                compact = compactTabs,
                active = false,
                isTv = isTv,
                tooltip = "Favorites",
                tooltipDetail = "Saved cloud collection",
                tooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW,
                onClick = onFavorites,
                modifier = Modifier
                    .then(
                        if (ultraCompactTabs) Modifier
                        else Modifier.widthIn(min = if (isTv) 132.dp else if (compactTabs) 96.dp else 116.dp)
                    )
                    .testTag("telegram-library-tab-favorites")
            )
            Row(
                modifier = Modifier.weight(1f)
                    // Compact widths must keep all three primary sections
                    // visible; requiring sideways scrolling hides Groups
                    // from both first-time users and 10-ft remote navigation.
                    .then(if (compactTabs) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                    .testTag("telegram-library-source-tabs"),
                horizontalArrangement = Arrangement.spacedBy(BousygramUiTokens.compactSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExplorerSection.entries.forEach { entry ->
                    val isSelected = entry == section
                    val tabIcon = when (entry) {
                        ExplorerSection.SAVED -> BousygramIcons.Saved
                        ExplorerSection.CHANNELS -> BousygramIcons.Channel
                        ExplorerSection.GROUPS -> BousygramIcons.Group
                    }
                    val fullName = when (entry) {
                        ExplorerSection.SAVED -> "Saved messages"
                        ExplorerSection.CHANNELS -> "Channels"
                        ExplorerSection.GROUPS -> "Groups"
                    }
                    val displayLabel = when (entry) {
                        ExplorerSection.SAVED -> "Saved"
                        ExplorerSection.CHANNELS -> "Channels"
                        ExplorerSection.GROUPS -> "Groups"
                    }
                    val count = when (entry) {
                        ExplorerSection.SAVED -> null
                        ExplorerSection.CHANNELS -> channelCount
                        ExplorerSection.GROUPS -> groupCount
                    }
                    BousygramSectionTab(
                        label = displayLabel,
                        active = isSelected,
                        compact = compactTabs,
                        icon = if (compactTabs) null else tabIcon,
                        badgeCount = if (showCounts) count else null,
                        iconOnly = false,
                        accessibilityLabel = fullName,
                        tooltip = fullName,
                        tooltipDetail = count?.let { "$it joined" } ?: "Personal library",
                        tooltipPlacement = BousygramTooltipPlacement.AUTO_BELOW,
                        isTv = isTv,
                        onClick = { if (entry != section) onSelectTab(entry) },
                        modifier = Modifier
                            .then(
                                if (compactTabs) Modifier.weight(1f)
                                else Modifier.widthIn(min = if (isTv) 132.dp else 104.dp)
                            )
                            .testTag("telegram-library-tab-" + entry.name.lowercase())
                    )
                }
            }
        }

        // Forum "browse all" stays visible only when exploring forum topics.
        if (showTopics) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BousygramButton(
                    text = "Browse group media",
                    leadingIcon = BousygramIcons.Library,
                    tooltip = "View all media across topics",
                    onClick = onBrowseAllMedia,
                    modifier = Modifier.weight(1f).heightIn(min = minControl)
                        .testTag("forum-browse-all-media")
                )
                if (hasTopicError) {
                    BousygramButton(
                        text = if (isLoading) "Retrying…" else "Retry topics",
                        leadingIcon = BousygramIcons.Refresh,
                        enabled = showSources || !isLoading,
                        selected = true,
                        onClick = onRefreshTopics,
                        modifier = Modifier.weight(1f).heightIn(min = minControl)
                            .testTag("forum-retry-topics")
                    )
                }
            }
        }
    }
}
