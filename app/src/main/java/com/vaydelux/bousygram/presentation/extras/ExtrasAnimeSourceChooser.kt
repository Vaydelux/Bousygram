package com.vaydelux.bousygram.presentation.extras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

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
        Text(request.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Choose an anime web server before opening the player.",
            style = MaterialTheme.typography.titleMedium
        )
        ExtrasAnimeEmbedServerDropdown(
            server = selected,
            onSelected = onSelect,
            isTv = isTv,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "AniList #${request.anilistId} · Episode ${request.episode ?: 1}. " +
                "These external servers are not verified for availability or licensing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (isTv) 56.dp else 48.dp)
                .testTag("extras-anime-open-selected-server")
        ) {
            Text("Open ${selected.label} web player")
        }
        Text(
            "If a provider shows a blank page, use Choose server to return here. " +
                "A valid embed URL does not guarantee that a playable stream exists.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
