package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reviews are readable, bounded, and D-pad reachable rather than giant text walls. */
@Composable
internal fun ExtrasReviewCard(
    review: ExtrasReview,
    collapsedLines: Int,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(review.author, review.content) {
        mutableStateOf(false)
    }
    var hasMore by remember(review.content, collapsedLines) { mutableStateOf(false) }
    Surface(
        modifier = modifier.widthIn(max = 720.dp).fillMaxWidth()
            .testTag("extras-review-card"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = review.author.ifBlank { "Community reviewer" } +
                    (if (review.score.isNotBlank()) " · ${review.score}" else ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                review.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedLines.coerceAtLeast(2),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("extras-review-content"),
                onTextLayout = { measured ->
                    if (!expanded && hasMore != measured.hasVisualOverflow) {
                        hasMore = measured.hasVisualOverflow
                    }
                }
            )
            if (hasMore) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.extrasFocusOutline()
                        .testTag("extras-review-expand")
                ) {
                    Text(if (expanded) "Show less" else "Read full review")
                }
            }
        }
    }
}
