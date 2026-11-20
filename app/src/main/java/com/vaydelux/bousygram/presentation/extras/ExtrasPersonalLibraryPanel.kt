package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramCenteredStatus
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramInlineStatus
import com.vaydelux.bousygram.presentation.components.BousygramStatusTone
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec

/**
 * Offline, native personal discovery hub. No TMDB key or Telegram session is
 * required to browse saved metadata and recently explored titles.
 * Lazy composition caps bitmap usage even on long ARM32 sessions.
 */
@Composable
internal fun ExtrasPersonalLibraryPanel(
    snapshot: ExtrasLibrarySnapshot,
    ready: Boolean,
    busy: Boolean,
    error: String?,
    isAndroidTv: Boolean,
    onBrowse: () -> Unit,
    onOpen: (ExtrasItem) -> Unit,
    onRemove: (ExtrasItem) -> Unit,
    onClearRecent: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    var type by rememberSaveable { mutableStateOf("ALL") }
    var search by rememberSaveable { mutableStateOf("") }
    var confirmClearRecent by remember { mutableStateOf(false) }
    val available = if (section == 0) snapshot.saved else snapshot.recent
    val visible = remember(available, type, search) {
        available.filter { entry ->
            (type == "ALL" || entry.type == type) &&
                (search.isBlank() ||
                    entry.title.contains(search.trim(), ignoreCase = true) ||
                    entry.description.contains(search.trim(), ignoreCase = true))
        }
    }
    val controlHeight = if (isAndroidTv) 56.dp else 48.dp

    if (confirmClearRecent) {
        AlertDialog(
            onDismissRequest = { confirmClearRecent = false },
            title = { Text("Clear recently explored?") },
            text = {
                Text("This removes your recent title history on this device. Saved titles will stay in your library.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearRecent = false
                    onClearRecent()
                }) { Text("Clear recent") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearRecent = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().testTag("extras-personal-library"),
        verticalArrangement = Arrangement.spacedBy(if (isAndroidTv) 14.dp else 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BousygramIcon(
                icon = BousygramIcons.Saved, contentDescription = null,
                size = if (isAndroidTv) 30.dp else 24.dp,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text(
                    "My Extras",
                    style = if (isAndroidTv) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${snapshot.saved.size} saved · ${snapshot.recent.size} recently explored · On this device",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onBrowse,
                modifier = Modifier.heightIn(min = controlHeight).extrasFocusOutline()
                    .testTag("extras-library-browse")
            ) { Text("Browse") }
        }
        if (error != null) {
            BousygramInlineStatus(
                message = error,
                tone = BousygramStatusTone.ERROR,
                modifier = Modifier.testTag("extras-library-error"),
                trailingAction = {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.extrasFocusOutline()
                            .testTag("extras-library-retry")
                    ) { Text("Retry") }
                }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = section == 0,
                onClick = { section = 0 },
                modifier = Modifier.heightIn(min = controlHeight).extrasFocusOutline()
                    .testTag("extras-library-saved-tab"),
                label = { Text("Saved (${snapshot.saved.size})") }
            )
            FilterChip(
                selected = section == 1,
                onClick = { section = 1 },
                modifier = Modifier.heightIn(min = controlHeight).extrasFocusOutline()
                    .testTag("extras-library-recent-tab"),
                label = { Text("Recent (${snapshot.recent.size})") }
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (section == 1 && snapshot.recent.isNotEmpty()) {
            TextButton(
                onClick = { confirmClearRecent = true },
                enabled = !busy && ready,
                modifier = Modifier.heightIn(min = controlHeight).extrasFocusOutline()
                    .testTag("extras-library-clear-recent")
            ) { Text("Clear recent history") }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(100) },
            modifier = Modifier.fillMaxWidth().testTag("extras-library-search")
                .extrasFocusOutline(),
            label = { Text("Find a title in your library") },
            singleLine = true,
            shape = RoundedCornerShape(LocalBousygramStyleSpec.current.inputCorner),
            leadingIcon = {
                BousygramIcon(icon = BousygramIcons.Search, contentDescription = null, size = 18.dp)
            }
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().focusGroup().testTag("extras-library-types"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf("ALL" to "All", "MOVIES" to "Movies",
                "TV" to "TV shows", "ANIME" to "Anime")
            items(options.size) { index ->
                val option = options[index]
                FilterChip(
                    selected = type == option.first,
                    onClick = { type = option.first },
                    modifier = Modifier.heightIn(min = controlHeight).extrasFocusOutline()
                        .testTag("extras-library-type-${option.first.lowercase()}"),
                    label = { Text(option.second) }
                )
            }
        }
        if (!ready) {
            Box(Modifier.fillMaxSize().testTag("extras-library-loading"), contentAlignment = Alignment.Center) {
                BousygramCenteredStatus(
                    title = if (error != null) "Library unavailable" else "Opening your library",
                    message = if (error != null) "Existing data has been preserved."
                        else "Loading saved titles from local storage."
                )
            }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().testTag("extras-library-empty"), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BousygramCenteredStatus(
                        title = if (search.isNotBlank()) "No matching titles"
                            else if (section == 0) "Your collection starts here"
                            else "No recently explored titles",
                        message = if (search.isNotBlank()) "Try another search or choose a different category."
                            else if (section == 0) "Open a movie, TV show, or anime and select Save to My Extras."
                            else "Titles you successfully explore will appear here."
                    )
                    Button(
                        onClick = onBrowse,
                        modifier = Modifier.heightIn(min = controlHeight).extrasFocusOutline()
                            .testTag("extras-library-empty-browse")
                    ) { Text("Explore titles") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isAndroidTv) 180.dp else 142.dp),
                modifier = Modifier.fillMaxSize().focusGroup().testTag("extras-library-grid"),
                horizontalArrangement = Arrangement.spacedBy(if (isAndroidTv) 18.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(visible, key = { it.key }) { entry ->
                    val item = entry.toExtrasItem()
                    Column(
                        modifier = Modifier.fillMaxWidth().testTag("extras-library-item-${entry.key}"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExtrasPosterCard(item, onClick = { onOpen(item) })
                        if (section == 0) {
                            OutlinedButton(
                                onClick = { onRemove(item) },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = controlHeight)
                                    .extrasFocusOutline().testTag("extras-library-remove-${entry.key}")
                            ) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}
