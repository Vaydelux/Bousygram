package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
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
import com.vaydelux.bousygram.presentation.components.BrowseGridShimmerSkeleton
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
import com.vaydelux.bousygram.presentation.components.PremiumGridBackdrop
import com.vaydelux.bousygram.presentation.theme.bousygramResponsiveTokens
import com.vaydelux.bousygram.presentation.components.BousygramPageHeading
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
    val responsive = bousygramResponsiveTokens(adaptive.profile)
    val horizontalPadding = responsive.pagePadding

    LaunchedEffect(viewModel) { viewModel.refresh() }

    PremiumGridBackdrop(modifier = modifier) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { insets ->
        Box(modifier = Modifier.fillMaxSize().padding(insets)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = if (adaptive.isCompact) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BousygramPageHeading(
                    eyebrow = "Telegram media  /  Your collection",
                    title = "Favorites",
                    subtitle = if (state.isLoading) "Loading your saved videos…"
                        else "${state.items.size} saved ${if (state.items.size == 1) "video" else "videos"}",
                    compact = adaptive.isCompact
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BousygramButton("← Back", onClick = { if (!launcher.cancel()) onBack() })
                    BousygramButton("Search", onOpenSearch)
                    BousygramButton("Refresh", viewModel::refresh, enabled = !state.isLoading)
                    if (state.items.isNotEmpty()) {
                        BousygramButton(
                            text = "▶ Play all",
                            onClick = { state.items.firstOrNull()?.let { launcher.launch(it, state.items) } },
                            selected = true,
                            enabled = !state.isLoading && !launcher.isPending
                        )
                    }
                }
            }

            if (state.unavailableCount > 0 && !state.isLoading) {
                Text(
                    "${state.unavailableCount} saved item(s) are no longer available in Telegram and are not shown.",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            }
            if (state.error != null && state.items.isNotEmpty()) {
                Text(state.error.orEmpty(), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading && state.items.isEmpty() -> BrowseGridShimmerSkeleton(
                        columns = GridCells.Adaptive(minSize = responsive.cardMinWidth),
                        modifier = Modifier.fillMaxSize(),
                        itemCount = if (adaptive.isCompact) 6 else 9
                    )
                    state.error != null && state.items.isEmpty() -> EmptyStateView(
                        title = "Couldn't load favorites",
                        message = state.error.orEmpty(),
                        actionText = "Try again",
                        onAction = viewModel::refresh,
                        modifier = Modifier.fillMaxSize()
                    )
                    state.items.isEmpty() -> EmptyStateView(
                        title = "No favorites yet",
                        message = "Favorite a video from Home or your Telegram library. Your saved items will appear here across sources.",
                        actionText = "Browse videos",
                        onAction = onBack,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = responsive.cardMinWidth),
                        horizontalArrangement = Arrangement.spacedBy(responsive.gridGap),
                        verticalArrangement = Arrangement.spacedBy(responsive.gridGap),
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
        TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Remove from favorites",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
