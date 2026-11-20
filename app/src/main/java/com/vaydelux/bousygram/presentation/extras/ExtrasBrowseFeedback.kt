package com.vaydelux.bousygram.presentation.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramCenteredStatus
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramStatusTone

/**
 * Refreshing keeps already-loaded posters visible. This is an activity
 * indicator rather than fabricated progress or a second blank skeleton.
 */
@Composable
internal fun ExtrasBrowseActivityRow(
    resultCount: Int,
    currentPage: Int,
    updating: Boolean,
    onRefresh: () -> Unit,
    minimumHeightDp: Int,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("extras-browse-activity"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "${resultCount.coerceAtLeast(0)} titles · page ${currentPage.coerceAtLeast(1)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (updating) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            Text("Updating…", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.heightIn(min = (if (compact) 40 else minimumHeightDp).dp)
                    .extrasFocusOutline().testTag("extras-refresh-titles")
            ) {
                BousygramIcon(
                    icon = BousygramIcons.Refresh,
                    contentDescription = if (compact) "Refresh titles" else null,
                    size = 16.dp
                )
                if (!compact) Text("Refresh")
            }
        }
    }
}

/** Full-viewport failure feedback rather than a blank grid after an API error. */
@Composable
internal fun ExtrasFailureScreen(
    message: String,
    isTv: Boolean,
    onRetry: () -> Unit,
    onBrowseAnime: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize().testTag("extras-failure-screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BousygramCenteredStatus(
                title = "This catalog couldn't load",
                message = message,
                tone = BousygramStatusTone.ERROR
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp)
                    .extrasFocusOutline().testTag("extras-failure-retry")
            ) {
                BousygramIcon(icon = BousygramIcons.Refresh,
                    contentDescription = null, size = 18.dp)
                Text("Try again")
            }
            if (onBrowseAnime != null) {
                OutlinedButton(
                    onClick = onBrowseAnime,
                    modifier = Modifier.heightIn(min = if (isTv) 56.dp else 48.dp)
                        .extrasFocusOutline().testTag("extras-failure-browse-anime")
                ) { Text("Explore Anime instead") }
            }
        }
    }
}
