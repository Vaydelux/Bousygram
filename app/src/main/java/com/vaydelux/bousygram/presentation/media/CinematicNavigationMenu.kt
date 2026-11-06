package com.vaydelux.bousygram.presentation.media

import android.view.KeyEvent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val menuText = Color.White
private val faintText = Color.White.copy(alpha = 0.74f)
private val focusSurface = Color.White.copy(alpha = 0.16f)

@Composable
private fun MenuAction(
    label: String,
    description: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val surface by animateColorAsState(
        if (focused) focusSurface else Color.Transparent,
        animationSpec = tween(durationMillis = 110),
        label = "cinematic-menu-item-focus"
    )
    val ring by animateColorAsState(
        if (focused) menuText else Color.Transparent,
        animationSpec = tween(durationMillis = 110),
        label = "cinematic-menu-focus-ring"
    )
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(surface, RoundedCornerShape(8.dp))
            .border(if (focused) 2.dp else 0.dp, ring, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics { contentDescription = description }
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = menuText, style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A single focus-contained dropdown with one active section. Rows are replaced, not
 * stacked, so low-memory TV GPUs do not animate two overlapping focus trees.
 * Android Back is routed from PlayerScreen; D-pad Left additionally returns to groups.
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
    var helpExpanded by remember(section) { mutableStateOf(false) }
    val menuScroll = rememberScrollState()
    // Restore the category the user opened rather than forcing Playback
    // every time Back returns to the root. The same focus requester belongs
    // to the first back row in each submenu.
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
            // Focus the row after composition attaches it, without a
            // guessed fixed 80ms timer or focus race with slide animations.
            withFrameNanos { }
            val requester = if (section == CinematicMenuSection.ROOT) rootFocus else firstFocus
            runCatching { requester.requestFocus() }
        }
    }
    Column(
        modifier
            .widthIn(min = 216.dp, max = 290.dp)
            .background(Color.Black.copy(alpha = 0.84f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .animateContentSize(animationSpec = tween(durationMillis = 170))
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
            .verticalScroll(menuScroll)
            .padding(8.dp)
            .testTag("cinematic_navigation_menu"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            cinematicMenuTitle(section),
            color = faintText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics { heading() }
                .testTag("cinematic_menu_heading")
        )
        if (section == CinematicMenuSection.ROOT) {
            MenuAction("Playback  ›", "Open playback settings and seek controls",
                "cinematic_menu_group_playback",
                {
                    lastRootSection = CinematicMenuSection.PLAYBACK
                    onSectionChange(CinematicMenuSection.PLAYBACK)
                },
                Modifier.focusRequester(firstFocus))
            MenuAction("Library  ›", "Open episode and queue navigation",
                "cinematic_menu_group_library",
                {
                    lastRootSection = CinematicMenuSection.LIBRARY
                    onSectionChange(CinematicMenuSection.LIBRARY)
                }, Modifier.focusRequester(libraryFocus))
            MenuAction("Advanced  ›", "Open decoder and accessibility options",
                "cinematic_menu_group_advanced",
                {
                    lastRootSection = CinematicMenuSection.ADVANCED
                    onSectionChange(CinematicMenuSection.ADVANCED)
                }, Modifier.focusRequester(advancedFocus))
            MenuAction("Close", "Close playback navigation",
                "cinematic_menu_close", onClose)
        } else {
            MenuAction("‹  All options", "Return to playback navigation categories",
                "cinematic_menu_back", { onSectionChange(CinematicMenuSection.ROOT) },
                Modifier.focusRequester(firstFocus))
            when (section) {
                CinematicMenuSection.PLAYBACK -> {
                    MenuAction("Audio / CC", "Choose audio and subtitles",
                        "cinematic_menu_tracks", onTracks)
                    MenuAction("Speed · $speedLabel", "Change playback speed",
                        "cinematic_menu_speed", onSpeed)
                    MenuAction("Aspect · $aspectLabel", "Change video aspect ratio",
                        "cinematic_menu_aspect", onAspect)
                    MenuAction("−30 seconds", "Seek backward thirty seconds",
                        "cinematic_menu_seek_back", { onSeekBy(-30_000L) })
                    MenuAction("+30 seconds", "Seek forward thirty seconds",
                        "cinematic_menu_seek_forward", { onSeekBy(30_000L) })
                }
                CinematicMenuSection.LIBRARY -> {
                    if (onEpisodes != null) {
                        MenuAction("More episodes", "Browse this source queue",
                            "cinematic_menu_episodes", onEpisodes)
                    }
                    MenuAction("Previous video", "Play previous item in queue",
                        "cinematic_menu_previous", onPrevious)
                    MenuAction("Next video", "Play next item in queue",
                        "cinematic_menu_next", onNext)
                }
                CinematicMenuSection.ADVANCED -> {
                    if (onDecoder != null) {
                        MenuAction("Switch decoder", "Switch playback decoder",
                            "cinematic_menu_decoder", onDecoder)
                    }
                    if (onDiagnostics != null) {
                        MenuAction("Playback diagnostics", "Toggle debug playback diagnostics",
                            "cinematic_menu_diagnostics", onDiagnostics)
                    }
                    MenuAction("Controls guide", "Show touch and TV remote controls",
                        "cinematic_menu_help", { helpExpanded = !helpExpanded })
                    if (helpExpanded) {
                        Text(
                            "TV: Left/Right seek ±10s with controls hidden. OK shows controls. " +
                                "Back returns to the menu or hides controls. " +
                                "Touch: single tap play/pause; double tap left/right seeks ±10s. " +
                                "Drag the thin timeline to scrub.",
                            color = faintText, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("cinematic_menu_help_text")
                        )
                    }
                }
                else -> Unit
            }
        }
    }
}
