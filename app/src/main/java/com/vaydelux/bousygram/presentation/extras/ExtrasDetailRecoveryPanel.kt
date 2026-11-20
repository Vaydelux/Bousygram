package com.vaydelux.bousygram.presentation.extras

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.components.BousygramCenteredStatus
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramStatusTone
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec

/**
 * Keep known catalog metadata useful if the provider's detail endpoint fails.
 * It never claims source availability, loads a fake stream, or drops the
 * selected title. Users can retry, save this metadata, or go back safely.
 */
@Composable
internal fun ExtrasDetailRecoveryPanel(
    item: ExtrasItem,
    message: String,
    isTv: Boolean,
    isSaved: Boolean,
    libraryReady: Boolean,
    libraryBusy: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minHeight = if (isTv) 56.dp else 48.dp
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .testTag("extras-detail-error")
            .padding(horizontal = if (isTv) 32.dp else 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BousygramCenteredStatus(
            title = "More details couldn't load",
            message = message,
            tone = BousygramStatusTone.ERROR
        )
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("extras-detail-cached-preview"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                LocalBousygramStyleSpec.current.cardCorner
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "AVAILABLE CATALOG PREVIEW · ${item.type.provider}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val facts = listOf(item.year, item.rating.takeIf(String::isNotBlank)?.let {
                    "Rating $it"
                }).filterNotNull().filter(String::isNotBlank)
                if (facts.isNotEmpty()) {
                    Text(facts.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    item.description.ifBlank {
                        "The title's summary is not available from the current catalog page."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).heightIn(min = minHeight)
                    .extrasFocusOutline().testTag("extras-detail-retry")
            ) {
                BousygramIcon(icon = BousygramIcons.Refresh,
                    contentDescription = null, size = 17.dp)
                Text("Retry")
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).heightIn(min = minHeight)
                    .extrasFocusOutline().testTag("extras-detail-back-to-results")
            ) { Text("Go back") }
        }
        OutlinedButton(
            onClick = onToggleSaved,
            enabled = libraryReady && !libraryBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)
                .extrasFocusOutline().testTag("extras-detail-save-fallback")
        ) {
            BousygramIcon(
                icon = if (isSaved) BousygramIcons.Check else BousygramIcons.Saved,
                contentDescription = null, size = 18.dp
            )
            Text(
                if (!libraryReady) "Local library unavailable"
                else if (libraryBusy) "Saving…"
                else if (isSaved) "Saved to My Extras"
                else "Save this title for later"
            )
        }
    }
}
