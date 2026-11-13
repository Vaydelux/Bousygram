package com.vaydelux.bousygram.presentation.design.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.design.contract.rememberStreamingVisualTokens
import com.vaydelux.bousygram.presentation.design.contract.streamingLayoutMetrics
import com.vaydelux.bousygram.presentation.design.primitives.StreamingActionButton
import com.vaydelux.bousygram.presentation.design.primitives.StreamingArtworkShape
import com.vaydelux.bousygram.presentation.design.primitives.StreamingFilterChip
import com.vaydelux.bousygram.presentation.design.primitives.StreamingMediaCard
import com.vaydelux.bousygram.presentation.design.primitives.StreamingMediaCardModel
import com.vaydelux.bousygram.presentation.design.primitives.StreamingSectionHeader
import com.vaydelux.bousygram.presentation.design.primitives.StreamingSkeleton
import com.vaydelux.bousygram.presentation.design.primitives.StreamingStatusPanel

/**
 * Opt-in, debug-only design contract gallery. All examples are local fixtures;
 * no TDLib, provider requests, playback engine, history or user data.
 *
 * Called only by MainActivity when BuildConfig.DEBUG and the explicit
 * design_preview intent extra are both set. It is NOT the production UI.
 */
@Composable
fun StreamingDesignGallery(onExit: () -> Unit) {
    val metrics = streamingLayoutMetrics(rememberAdaptiveMetrics())
    val palette = rememberStreamingVisualTokens()
    var tab by remember { mutableIntStateOf(0) }
    var selectedKey by remember { mutableIntStateOf(1) }
    val examples = remember {
        listOf(
            StreamingMediaCardModel("example-a", "Sample media", "Landscape · 12 min", 0.38f),
            StreamingMediaCardModel("example-b", "Sample collection", "Poster · 8 episodes"),
            StreamingMediaCardModel("example-c", "Sample finished item", "Watched", 1f, watched = true)
        )
    }
    Column(
        Modifier.fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(metrics.pagePadding)
            .testTag("streaming-design-gallery"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StreamingActionButton("Exit preview", onExit)
        }
        StreamingSectionHeader(
            "Streaming design preview",
            "Local fixtures · semantic theme roles · native TV and touch focus"
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Library", "Extras").forEachIndexed { index, label ->
                StreamingFilterChip(
                    label = label, selected = tab == index,
                    onClick = { tab = index },
                    modifier = Modifier.weight(1f).testTag("streaming-preview-tab-$index")
                )
            }
        }
        StreamingSectionHeader(
            title = if (tab == 0) "Continue watching" else "Discover",
            subtitle = if (tab == 0) "A local visual fixture — not real watch history"
                else "A local visual fixture — not live provider results"
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = (maxWidth / metrics.cardMinWidth).toInt().coerceIn(1, 3)
            Column(verticalArrangement = Arrangement.spacedBy(metrics.gridGap)) {
                examples.chunked(columns).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.gridGap)
                    ) {
                        row.forEachIndexed { index, item ->
                            val absoluteIndex = examples.indexOf(item)
                            StreamingMediaCard(
                                item = item, onClick = { selectedKey = absoluteIndex },
                                selected = selectedKey == absoluteIndex,
                                shape = if (tab == 0) StreamingArtworkShape.LANDSCAPE
                                    else StreamingArtworkShape.POSTER,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Artwork",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        StreamingSectionHeader("Loading, empty and failure states")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.gridGap)
        ) {
            StreamingSkeleton(
                modifier = Modifier.weight(1f),
                shape = StreamingArtworkShape.LANDSCAPE
            )
            StreamingSkeleton(
                modifier = Modifier.weight(1f),
                shape = StreamingArtworkShape.LANDSCAPE
            )
        }
        StreamingStatusPanel(
            title = "No items to display",
            message = "Choose a source or adjust filters to view available items.",
            actionLabel = "Try another view",
            onAction = { tab = 1 - tab }
        )
        StreamingStatusPanel(
            title = "Unable to load",
            message = "The example shows a recoverable error state without inventing playback status.",
            isError = true, actionLabel = "Retry",
            onAction = { selectedKey = 0 }
        )
        Spacer(Modifier.height(8.dp))
    }
}
