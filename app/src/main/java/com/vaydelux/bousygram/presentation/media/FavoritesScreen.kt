package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.EmptyStateView
import com.vaydelux.bousygram.presentation.components.LoadingOverlay
import com.vaydelux.bousygram.presentation.components.MediaPosterCard
import com.vaydelux.bousygram.presentation.media.launch.LaunchOpeningIndicator
import com.vaydelux.bousygram.presentation.media.launch.rememberMediaLauncher
import com.vaydelux.bousygram.presentation.media.vm.FavoritesViewModel
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val launcher = rememberMediaLauncher(
        screenKey = "favorites",
        onPlay = onPlay,
        onError = { scope.launch { snackbarHost.showSnackbar(it) } }
    )
    val safeMode = remember { EnterprisePreferences.safeMode() }
    val adaptive = rememberAdaptiveMetrics()
    val horizontalPadding = if (adaptive.isCompact) 16.dp else 28.dp

    LaunchedEffect(viewModel) { viewModel.refresh() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = if (adaptive.isCompact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Favorites",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        style = if (adaptive.isCompact) {
                            androidx.compose.material3.MaterialTheme.typography.headlineSmall
                        } else {
                            androidx.compose.material3.MaterialTheme.typography.headlineLarge
                        }
                    )
                    Text(
                        text = if (state.isLoading) "Loading your saved videos…" else "${state.items.size} saved ${if (state.items.size == 1) "video" else "videos"}",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (adaptive.isCompact) {
                            androidx.compose.material3.MaterialTheme.typography.bodySmall
                        } else {
                            androidx.compose.material3.MaterialTheme.typography.bodyMedium
                        }
                    )
                }
                if (state.items.isNotEmpty()) {
                    BousygramButton(
                        text = "▶ Play All",
                        onClick = {
                            val first = state.items.firstOrNull() ?: return@BousygramButton
                            launcher.launch(first, state.items)
                        },
                        selected = true
                    )
                }
                if (!adaptive.isCompact) {
                    BousygramButton("Search", onOpenSearch)
                }
                BousygramButton("Refresh", viewModel::refresh)
            }

            if (state.unavailableCount > 0 && !state.isLoading) {
                Text(
                    "${state.unavailableCount} saved item(s) are no longer available in Telegram and are not shown.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
            state.error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.items.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    state.items.isEmpty() -> EmptyStateView(
                        title = "No favorites yet",
                        message = "Favorite a video from Home or your Telegram library. Your saved items will appear here across sources.",
                        actionText = "Browse videos",
                        onAction = onBack,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (adaptive.isCompact) 160.dp else 240.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        items(state.items, key = { "${it.chatId}:${it.messageId}" }) { item ->
                            FavoriteGridItem(
                                item = item,
                                safeMode = safeMode,
                                onPlay = {
                                    launcher.launch(item, state.items)
                                },
                                onRemove = { viewModel.removeFavorite(item) }
                            )
                        }
                    }
                }
            }
        }
        BackHandler(enabled = launcher.isPending) {
            launcher.cancel()
        }
        LaunchOpeningIndicator(
            launcher = launcher,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
        if (state.isLoading && state.items.isNotEmpty()) {
            LoadingOverlay(
                text = "Refreshing favorites…",
                modifier = Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background.copy(alpha = 0.64f))
            )
        }
    }
}
}

@Composable
private fun FavoriteGridItem(
    item: MediaItem,
    safeMode: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaPosterCard(
            media = item,
            subtitle = "Telegram",
            badge = "FAVORITE",
            privacyMode = safeMode,
            cardWidth = null,
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
            Text("Remove", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
        }
    }
}
