package com.vaydelux.bousygram.presentation.design.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramSectionTab
import com.vaydelux.bousygram.presentation.components.TvFocusSurface
import com.vaydelux.bousygram.presentation.design.contract.rememberStreamingVisualTokens
import com.vaydelux.bousygram.presentation.design.contract.streamingLayoutMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens

/**
 * Stateless visual adapters only. Do not import Telegram, TMDB, AniList,
 * MediaForge, repositories or player controllers into this package.
 */
@Composable
fun StreamingSectionHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    val palette = rememberStreamingVisualTokens()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BousygramUiTokens.compactSpacing)
    ) {
        Text(
            title, color = palette.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                subtitle, color = palette.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StreamingActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    // Reuse the existing TV focus node and minimum touch target.
    BousygramButton(
        text = label, onClick = onClick, enabled = enabled,
        selected = selected, modifier = modifier
    )
}

@Composable
fun StreamingFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BousygramSectionTab(
        label = label, active = selected, isTv = rememberAdaptiveMetrics().isTv,
        onClick = onClick, modifier = modifier
    )
}

enum class StreamingArtworkShape(val ratio: Float) {
    LANDSCAPE(16f / 9f),
    POSTER(2f / 3f)
}

/** Artwork is a slot: the caller owns the image/cache/network implementation. */
@Composable
fun StreamingArtwork(
    shape: StreamingArtworkShape,
    modifier: Modifier = Modifier,
    artwork: @Composable BoxScope.() -> Unit = {}
) {
    val palette = rememberStreamingVisualTokens()
    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(shape.ratio)
            .background(palette.raised, RoundedCornerShape(BousygramUiTokens.cardCorner))
            .testTag("streaming-artwork"),
        contentAlignment = Alignment.Center,
        content = artwork
    )
}

data class StreamingMediaCardModel(
    val key: String,
    val title: String,
    val subtitle: String,
    val progressFraction: Float? = null,
    val watched: Boolean = false
)

/** Generic media card; never infers a route, provider, rating or policy. */
@Composable
fun StreamingMediaCard(
    item: StreamingMediaCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: StreamingArtworkShape = StreamingArtworkShape.POSTER,
    selected: Boolean = false,
    enabled: Boolean = true,
    artwork: @Composable BoxScope.() -> Unit = {}
) {
    val palette = rememberStreamingVisualTokens()
    val fraction = item.progressFraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    TvFocusSurface(
        onClick = onClick, selected = selected, enabled = enabled,
        modifier = modifier
            .testTag("streaming-media-card-" + item.key)
            .semantics { stateDescription = if (item.watched) "Watched" else "Not watched" }
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StreamingArtwork(shape, artwork = artwork)
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .testTag("streaming-media-progress"),
                    color = palette.progressFill,
                    trackColor = palette.progressTrack
                )
            }
            Text(
                item.title, color = palette.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle, color = palette.textSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (item.watched) {
                Text(
                    "Watched", color = palette.textSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/** Static by default, so ARM32 and accessibility users incur no shimmer work. */
@Composable
fun StreamingSkeleton(
    modifier: Modifier = Modifier,
    shape: StreamingArtworkShape = StreamingArtworkShape.POSTER
) {
    val palette = rememberStreamingVisualTokens()
    Box(
        modifier.fillMaxWidth().aspectRatio(shape.ratio)
            .background(palette.raised, RoundedCornerShape(BousygramUiTokens.cardCorner))
            .testTag("streaming-skeleton")
    )
}

/** Shared empty/error/unavailable panel; the caller supplies real state/actions. */
@Composable
fun StreamingStatusPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val metrics = streamingLayoutMetrics(rememberAdaptiveMetrics())
    val palette = rememberStreamingVisualTokens()
    Surface(
        modifier = modifier.fillMaxWidth().testTag("streaming-status-panel"),
        shape = RoundedCornerShape(metrics.panelRadius),
        color = palette.surface,
        contentColor = palette.textPrimary
    ) {
        Column(
            Modifier.fillMaxWidth().padding(metrics.contentGap),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                color = if (isError) palette.error else palette.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(message, color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                StreamingActionButton(
                    actionLabel, onAction,
                    modifier = Modifier.heightIn(min = metrics.minControlHeight)
                        .testTag("streaming-status-action")
                )
            }
        }
    }
}
