package com.vaydelux.bousygram.presentation.media

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIconRef
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.rememberBousygramMotionPolicy
import com.vaydelux.bousygram.presentation.theme.tabular

@Composable
private fun MenuAction(
    label: String,
    description: String,
    tag: String,
    icon: BousygramIconRef,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    showChevron: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val motion = rememberBousygramMotionPolicy()
    var focused by remember { mutableStateOf(false) }

    val surface by animateColorAsState(
        targetValue = if (focused) colors.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = motion.duration(110)),
        label = "cinematic-menu-item-focus"
    )
    val ring by animateColorAsState(
        targetValue = if (focused) colors.primary else Color.Transparent,
        animationSpec = tween(durationMillis = motion.duration(110)),
        label = "cinematic-menu-focus-ring"
    )
    val fgColor = if (focused) colors.onPrimaryContainer else colors.onSurface
    val rowShape = RoundedCornerShape(BousygramUiTokens.tabCorner)

    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(rowShape)
            .background(surface)
            .border(if (focused) 2.dp else 0.dp, ring, rowShape)
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics { contentDescription = description }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BousygramIcon(
            icon = icon,
            contentDescription = null,
            tint = if (focused) colors.onPrimaryContainer else colors.onSurfaceVariant,
            size = 18.dp
        )
        Text(
            text = label,
            color = fgColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!badge.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(BousygramUiTokens.pillCorner),
                color = if (focused) colors.primary.copy(alpha = 0.24f) else colors.surfaceContainerHighest,
                border = BorderStroke(1.dp, if (focused) halo.haloStart.copy(alpha = 0.6f) else colors.outlineVariant)
            ) {
                Text(
                    text = badge,
                    color = fgColor,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (showChevron) {
            BousygramIcon(
                icon = BousygramIcons.ChevronRight,
                contentDescription = null,
                tint = if (focused) colors.onPrimaryContainer else colors.onSurfaceVariant,
                size = 16.dp
            )
        }
    }
}

/**
 * Unified Cinema Halo focus-contained dropdown with one active section.
 * Rows are replaced, not stacked, keeping low-memory TV GPUs smooth.
 * Android Back is routed from PlayerScreen; D-pad Left returns to categories.
 */
@Composable
internal fun CinematicNavigationMenu(
    section: CinematicMenuSection,
    isTv: Boolean,
    onSectionChange: (CinematicMenuSection) -> Unit,
    onClose: () -> Unit,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    speedLabel: String,
    onAspect: () -> Unit,
    aspectLabel: String,
    onEpisodes: (() -> Unit)?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onDecoder: (() -> Unit)?,
    onDiagnostics: (() -> Unit)?,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val motion = rememberBousygramMotionPolicy()
    var helpExpanded by remember(section) { mutableStateOf(false) }
    val menuScroll = rememberScrollState()

    var lastRootSection by remember { mutableStateOf(CinematicMenuSection.PLAYBACK) }
    val libraryFocus = remember { FocusRequester() }
    val advancedFocus = remember { FocusRequester() }
    val rootFocus = when (lastRootSection) {
        CinematicMenuSection.LIBRARY -> libraryFocus
        CinematicMenuSection.ADVANCED -> advancedFocus
        else -> firstFocus
    }

    LaunchedEffect(section, isTv) {
        menuScroll.scrollTo(0)
        if (isTv) {
            withFrameNanos { }
            val requester = if (section == CinematicMenuSection.ROOT) rootFocus else firstFocus
            runCatching { requester.requestFocus() }
        }
    }

    val panelShape = RoundedCornerShape(BousygramUiTokens.panelCorner)
    Surface(
        shape = panelShape,
        color = colors.surfaceContainerHigh.copy(alpha = if (halo.isHighContrast) 0.99f else 0.95f),
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, halo.glassBorder),
        shadowElevation = if (motion.lowRam) 0.dp else 14.dp,
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .animateContentSize(animationSpec = tween(durationMillis = motion.duration(170)))
            .onPreviewKeyEvent { key ->
                if (key.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    key.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT &&
                    section != CinematicMenuSection.ROOT
                ) {
                    onSectionChange(CinematicMenuSection.ROOT)
                    true
                } else false
            }
            .focusGroup()
            .testTag("cinematic_navigation_menu")
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(menuScroll)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = cinematicMenuTitle(section),
                color = colors.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { heading() }
                    .testTag("cinematic_menu_heading")
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .height(1.dp)
                    .background(colors.outlineVariant.copy(alpha = 0.50f))
            )

            if (section == CinematicMenuSection.ROOT) {
                MenuAction(
                    label = "Playback",
                    description = "Open playback settings and seek controls",
                    tag = "cinematic_menu_group_playback",
                    icon = BousygramIcons.Options,
                    showChevron = true,
                    onClick = {
                        lastRootSection = CinematicMenuSection.PLAYBACK
                        onSectionChange(CinematicMenuSection.PLAYBACK)
                    },
                    modifier = Modifier.focusRequester(firstFocus)
                )
                MenuAction(
                    label = "Library",
                    description = "Open episode and queue navigation",
                    tag = "cinematic_menu_group_library",
                    icon = BousygramIcons.Episodes,
                    showChevron = true,
                    onClick = {
                        lastRootSection = CinematicMenuSection.LIBRARY
                        onSectionChange(CinematicMenuSection.LIBRARY)
                    },
                    modifier = Modifier.focusRequester(libraryFocus)
                )
                MenuAction(
                    label = "Advanced",
                    description = "Open decoder and accessibility options",
                    tag = "cinematic_menu_group_advanced",
                    icon = BousygramIcons.Decoder,
                    showChevron = true,
                    onClick = {
                        lastRootSection = CinematicMenuSection.ADVANCED
                        onSectionChange(CinematicMenuSection.ADVANCED)
                    },
                    modifier = Modifier.focusRequester(advancedFocus)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .height(1.dp)
                        .background(colors.outlineVariant.copy(alpha = 0.40f))
                )
                MenuAction(
                    label = "Close",
                    description = "Close playback navigation",
                    tag = "cinematic_menu_close",
                    icon = BousygramIcons.Close,
                    onClick = onClose
                )
            } else {
                MenuAction(
                    label = "All options",
                    description = "Return to playback navigation categories",
                    tag = "cinematic_menu_back",
                    icon = BousygramIcons.ChevronLeft,
                    onClick = { onSectionChange(CinematicMenuSection.ROOT) },
                    modifier = Modifier.focusRequester(firstFocus)
                )
                when (section) {
                    CinematicMenuSection.PLAYBACK -> {
                        MenuAction(
                            label = "Audio / CC",
                            description = "Choose audio and subtitles",
                            tag = "cinematic_menu_tracks",
                            icon = BousygramIcons.Subtitles,
                            onClick = onTracks
                        )
                        MenuAction(
                            label = "Speed",
                            description = "Change playback speed",
                            tag = "cinematic_menu_speed",
                            icon = BousygramIcons.Speed,
                            badge = speedLabel,
                            onClick = onSpeed
                        )
                        MenuAction(
                            label = "Aspect",
                            description = "Change video aspect ratio",
                            tag = "cinematic_menu_aspect",
                            icon = BousygramIcons.Aspect,
                            badge = aspectLabel,
                            onClick = onAspect
                        )
                        MenuAction(
                            label = "Seek back 30s",
                            description = "Seek backward thirty seconds",
                            tag = "cinematic_menu_seek_back",
                            icon = BousygramIcons.SeekBack,
                            badge = "-30s",
                            onClick = { onSeekBy(-30_000L) }
                        )
                        MenuAction(
                            label = "Seek forward 30s",
                            description = "Seek forward thirty seconds",
                            tag = "cinematic_menu_seek_forward",
                            icon = BousygramIcons.SeekForward,
                            badge = "+30s",
                            onClick = { onSeekBy(30_000L) }
                        )
                    }
                    CinematicMenuSection.LIBRARY -> {
                        if (onEpisodes != null) {
                            MenuAction(
                                label = "More episodes",
                                description = "Browse this source queue",
                                tag = "cinematic_menu_episodes",
                                icon = BousygramIcons.Episodes,
                                onClick = onEpisodes
                            )
                        }
                        MenuAction(
                            label = "Previous video",
                            description = "Play previous item in queue",
                            tag = "cinematic_menu_previous",
                            icon = BousygramIcons.Previous,
                            onClick = onPrevious
                        )
                        MenuAction(
                            label = "Next video",
                            description = "Play next item in queue",
                            tag = "cinematic_menu_next",
                            icon = BousygramIcons.Next,
                            onClick = onNext
                        )
                    }
                    CinematicMenuSection.ADVANCED -> {
                        if (onDecoder != null) {
                            MenuAction(
                                label = "Switch decoder",
                                description = "Switch playback decoder",
                                tag = "cinematic_menu_decoder",
                                icon = BousygramIcons.Decoder,
                                onClick = onDecoder
                            )
                        }
                        if (onDiagnostics != null) {
                            MenuAction(
                                label = "Playback diagnostics",
                                description = "Toggle debug playback diagnostics",
                                tag = "cinematic_menu_diagnostics",
                                icon = BousygramIcons.Diagnostics,
                                onClick = onDiagnostics
                            )
                        }
                        MenuAction(
                            label = "Controls guide",
                            description = "Show touch and TV remote controls",
                            tag = "cinematic_menu_help",
                            icon = BousygramIcons.Help,
                            onClick = { helpExpanded = !helpExpanded }
                        )
                        if (helpExpanded) {
                            Text(
                                "TV: Left/Right seek ±10s with controls hidden. OK shows controls. " +
                                    "Back returns to the menu or hides controls. " +
                                    "Touch: single tap play/pause; double tap left/right seeks ±10s. " +
                                    "Drag the timeline to scrub.",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("cinematic_menu_help_text")
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
