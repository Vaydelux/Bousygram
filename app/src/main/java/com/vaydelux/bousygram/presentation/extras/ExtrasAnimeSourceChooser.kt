package com.vaydelux.bousygram.presentation.extras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading

/**
 * No unverified third-party anime page is loaded until the user explicitly selects
 * and opens a server. Its D-pad picker remains native Compose, not inside WebView.
 */
@Composable
internal fun ExtrasAnimeSourceChooser(
    request: ExtrasEmbedRequest,
    selected: ExtrasAnimeEmbedServer,
    isTv: Boolean,
    onSelect: (ExtrasAnimeEmbedServer) -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit
) {
    require(request.anilistId > 0) { "Anime source selection requires an AniList title" }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(if (isTv) 28.dp else 16.dp)
            .testTag("extras-anime-server-chooser"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("extras-anime-source-back")) {
                Text("Back to anime")
            }
        }
        BousygramPageHeading(
            eyebrow = "Extras  /  Anime",
            title = request.title,
            subtitle = "Choose a source before opening an external player.",
            compact = !isTv
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (isTv) 24.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "PLAYBACK SOURCE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                ExtrasAnimeEmbedServerDropdown(
                    server = selected,
                    onSelected = onSelect,
                    isTv = isTv,
                    modifier = Modifier.fillMaxWidth(),
                    season = request.season ?: 1
                )
                Text(
                    "AniList #${request.anilistId} · Episode ${request.episode ?: 1}. " +
                        "External sources are not verified for availability or licensing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = if (isTv) 56.dp else 48.dp)
                        .testTag("extras-anime-open-selected-server")
                ) {
                    Text("Open ${selected.label} web player")
                }
            }
        }
        Text(
            "If a provider shows a blank page, use Choose server to return here. " +
                "An embed URL does not guarantee that a playable stream exists.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Native controls remain reachable even when a provider takes HTML fullscreen. */
@Composable
internal fun ExtrasFullscreenRecoveryControls(
    isAnime: Boolean,
    onExitFullscreen: () -> Unit,
    onChooseAnimeServer: () -> Unit,
    cursorEnabled: Boolean = false,
    onToggleCursor: (() -> Unit)? = null,
    initialFocusRequester: FocusRequester? = null
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(8.dp)
    ) {
        if (maxWidth < 420.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onExitFullscreen,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (initialFocusRequester != null)
                            Modifier.focusRequester(initialFocusRequester) else Modifier)
                        .testTag("extras-embed-exit-html-fullscreen")
                ) { Text("Exit fullscreen") }
                if (isAnime) {
                    OutlinedButton(
                        onClick = onChooseAnimeServer,
                        modifier = Modifier.fillMaxWidth().testTag("extras-anime-fullscreen-servers")
                    ) { Text("Choose server") }
                }
                if (onToggleCursor != null) {
                    OutlinedButton(
                        onClick = onToggleCursor,
                        modifier = Modifier.fillMaxWidth().testTag("extras-fullscreen-cursor-mode")
                    ) { Text(if (cursorEnabled) "Use D-pad" else "Use cursor") }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExitFullscreen,
                    modifier = Modifier
                        .then(if (initialFocusRequester != null)
                            Modifier.focusRequester(initialFocusRequester) else Modifier)
                        .testTag("extras-embed-exit-html-fullscreen")
                ) { Text("Exit fullscreen") }
                if (isAnime) {
                    OutlinedButton(
                        onClick = onChooseAnimeServer,
                        modifier = Modifier.testTag("extras-anime-fullscreen-servers")
                    ) { Text("Choose server") }
                }
                if (onToggleCursor != null) {
                    OutlinedButton(
                        onClick = onToggleCursor,
                        modifier = Modifier.testTag("extras-fullscreen-cursor-mode")
                    ) { Text(if (cursorEnabled) "Use D-pad" else "Use cursor") }
                }
            }
        }
    }
}
