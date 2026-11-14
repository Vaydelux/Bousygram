package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramSectionTab
import com.vaydelux.bousygram.presentation.media.vm.ExplorerSection
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens

/**
 * A compact header, not a second home page. The library's primary content is
 * its grid; fixed decorative headings, descriptions and stacked stat panels
 * previously consumed much of a 540dp-high Android TV viewport.
 *
 * Short landscape / TV: two toolbar rows (and an optional forum action row).
 * Portrait: the same two rows, with overflow actions instead of wrapping.
 * Input controls remain accessible through touch, keyboard and D-pad.
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
    onSettings: () -> Unit
) {
    val density = explorerHeaderDensity(isTv, widthDp, heightDp)
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val effectiveWidth = widthDp / fontScale.coerceAtLeast(1f)
    val inlineActions = explorerHeaderShowInlineActions(effectiveWidth)
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
                OutlinedButton(
                    onClick = onGoUp,
                    modifier = Modifier.heightIn(min = minControl)
                        .testTag("telegram-explorer-go-up")
                ) { Text("‹ Back", maxLines = 1) }
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
                        style = if (isTv) MaterialTheme.typography.labelMedium
                            else MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                    }
                }
            }

            // When space is limited, exposing every option as a wide button
            // forces the grid off-screen. Keep Search immediately reachable.
            if (inlineActions) {
                if (hasResume) {
                    OutlinedButton(onClick = onResume, modifier = Modifier.heightIn(min = minControl)) {
                        Text("Continue", maxLines = 1)
                    }
                }
                OutlinedButton(onClick = onSearch, modifier = Modifier.heightIn(min = minControl)) {
                    Text("Search", maxLines = 1)
                }
            } else if (effectiveWidth >= 480.dp) {
                OutlinedButton(
                    onClick = onSearch,
                    modifier = Modifier.heightIn(min = minControl).testTag("telegram-explorer-search")
                ) { Text("Search", maxLines = 1) }
            }

            Box {
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.heightIn(min = minControl)
                        .testTag("telegram-explorer-more")
                ) {
                    Text("More", maxLines = 1)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (hasResume) {
                        DropdownMenuItem(text = { Text("Continue watching") }, onClick = {
                            menuExpanded = false
                            onResume()
                        })
                    }
                    DropdownMenuItem(text = { Text("Search media") }, onClick = {
                        menuExpanded = false
                        onSearch()
                    })
                    DropdownMenuItem(text = { Text("Favorites") }, onClick = {
                        menuExpanded = false
                        onFavorites()
                    })
                    DropdownMenuItem(text = { Text("Settings") }, onClick = {
                        menuExpanded = false
                        onSettings()
                    })
                    DropdownMenuItem(
                        text = {
                            Text(when {
                                showSources -> "Refresh channels and groups"
                                showTopics -> if (hasTopicError) "Retry forum topics"
                                    else "Refresh forum topics"
                                else -> "Refresh media"
                            })
                        },
                        enabled = !isLoading,
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

        // Favorites is a destination, not a Telegram source section. Keep its
        // entry pinned rather than hiding it behind More or a scrolled-off tab:
        // 320dp portrait, short landscape, tablets and Android TV all need an
        // immediately visible way to open the saved collection.
        Row(
            modifier = Modifier.fillMaxWidth().testTag("telegram-library-sections"),
            horizontalArrangement = Arrangement.spacedBy(BousygramUiTokens.compactSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BousygramSectionTab(
                label = "Favorites",
                active = false,
                isTv = isTv,
                onClick = onFavorites,
                modifier = Modifier.widthIn(min = if (isTv) 128.dp else 104.dp)
                    .testTag("telegram-library-tab-favorites")
            )
            Row(
                modifier = Modifier.weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .testTag("telegram-library-source-tabs"),
                horizontalArrangement = Arrangement.spacedBy(BousygramUiTokens.compactSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
            ExplorerSection.entries.forEach { entry ->
                val label = when (entry) {
                    ExplorerSection.SAVED -> if (effectiveWidth < 600.dp) "Saved" else "Saved messages"
                    ExplorerSection.CHANNELS -> "Channels"
                    ExplorerSection.GROUPS -> "Groups"
                }
                val count = when (entry) {
                    ExplorerSection.SAVED -> null
                    ExplorerSection.CHANNELS -> channelCount
                    ExplorerSection.GROUPS -> groupCount
                }
                val text = if (count != null && effectiveWidth >= 600.dp) "$label  $count" else label
                BousygramSectionTab(
                    label = text,
                    active = entry == section,
                    isTv = isTv,
                    onClick = { if (entry != section) onSelectTab(entry) },
                    modifier = Modifier.widthIn(min = if (isTv) 128.dp else 88.dp)
                        .testTag("telegram-library-tab-" + entry.name.lowercase())
                )
            }
            }
        }

        // Group "browse all" is a real navigation action, not just a decoration.
        // It remains visible in the only state where it is needed.
        if (showTopics) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBrowseAllMedia,
                    modifier = Modifier.weight(1f).heightIn(min = minControl)
                        .testTag("forum-browse-all-media")
                ) { Text("Browse group media", maxLines = 1) }
                if (hasTopicError) {
                    OutlinedButton(
                        onClick = onRefreshTopics,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f).heightIn(min = minControl)
                            .testTag("forum-retry-topics")
                    ) { Text(if (isLoading) "Retrying…" else "Retry topics", maxLines = 1) }
                }
            }
        }
    }
}
