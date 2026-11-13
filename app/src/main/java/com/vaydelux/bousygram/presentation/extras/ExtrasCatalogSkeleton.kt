package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * Non-animated placeholders reserve the same layout area as actual posters.
 * Animation-free rendering avoids unnecessary recomposition on low-RAM ARM32 TVs.
 * No provider metadata, Telegram client, or network access is required.
 */
@Composable
internal fun ExtrasCatalogLoadingSkeleton(
    isTv: Boolean,
    compact: Boolean,
    shortViewport: Boolean = false
) {
    val minWidth = when {
        isTv -> 160.dp
        compact -> 132.dp
        else -> 155.dp
    }
    Column(
        Modifier.fillMaxSize().testTag("extras-catalog-loading")
            .semantics { stateDescription = "Loading catalog" }
    ) {
        Text(
            "Loading titles…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minWidth),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(8) { index ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .testTag("extras-loading-poster-$index"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkeletonShape(Modifier.fillMaxWidth().height(if (shortViewport) 154.dp else 205.dp))
                    SkeletonShape(Modifier.fillMaxWidth().height(13.dp))
                    SkeletonShape(Modifier.fillMaxWidth(0.62f).height(10.dp))
                }
            }
        }
    }
}

@Composable
internal fun ExtrasDetailLoadingSkeleton(compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize()
            .padding(horizontal = if (compact) 14.dp else 28.dp, vertical = 18.dp)
            .testTag("extras-detail-loading")
            .semantics { stateDescription = "Loading details" },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Loading details…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (compact) {
            SkeletonShape(Modifier.width(155.dp).height(225.dp))
            SkeletonShape(Modifier.fillMaxWidth(0.83f).height(22.dp))
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                SkeletonShape(Modifier.width(185.dp).height(270.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SkeletonShape(Modifier.fillMaxWidth(0.90f).height(25.dp))
                    SkeletonShape(Modifier.fillMaxWidth(0.60f).height(16.dp))
                    SkeletonShape(Modifier.fillMaxWidth().height(14.dp))
                }
            }
        }
        SkeletonShape(Modifier.fillMaxWidth().height(14.dp))
        SkeletonShape(Modifier.fillMaxWidth(0.95f).height(14.dp))
        SkeletonShape(Modifier.fillMaxWidth(0.70f).height(14.dp))
    }
}

@Composable
private fun SkeletonShape(modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .background(color = colors.surfaceContainerHigh, shape = shape)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.45f), shape)
    )
}
