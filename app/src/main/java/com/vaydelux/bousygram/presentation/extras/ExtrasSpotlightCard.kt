package com.vaydelux.bousygram.presentation.extras

import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.vaydelux.bousygram.core.media.PlayerBufferConfig
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec

/**
 * Featured in the current provider results, never editorial or sponsored.
 * This spotlight reuses already-loaded public catalog metadata, so browsing
 * it makes no additional API calls or timed auto-advance requests.
 */
@Composable
internal fun ExtrasSpotlightCard(
    item: ExtrasItem,
    position: Int,
    total: Int,
    isTv: Boolean,
    onOpen: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val target = remember(context) {
        ExtrasImagePolicy.poster(
            PlayerBufferConfig.isLowRamDevice(context) || !Process.is64Bit(),
            detail = true,
            suggestion = false
        )
    }
    val request = remember(context, item.poster, target) {
        ImageRequest.Builder(context)
            .data(item.poster)
            .size(target.width, target.height)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    val minHeight = if (isTv) 56.dp else 48.dp
    val shape = RoundedCornerShape(LocalBousygramStyleSpec.current.panelCorner)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
        modifier = modifier.fillMaxWidth().testTag("extras-spotlight")
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(if (isTv) 16.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(if (isTv) 108.dp else 92.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center
            ) {
                if (item.poster.isNullOrBlank()) {
                    BousygramIcon(icon = BousygramIcons.Film,
                        contentDescription = null, size = 26.dp)
                } else {
                    AsyncImage(
                        model = request, contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    "SPOTLIGHT · ${item.type.provider} · ${position + 1}/${total.coerceAtLeast(1)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    item.title,
                    style = if (isTv) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("extras-spotlight-title")
                )
                Text(
                    listOf(item.year, item.rating.takeIf { it.isNotBlank() }?.let { "★ $it" })
                        .filterNotNull().filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (item.description.isNotBlank()) {
                    Text(
                        item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpen,
                        modifier = Modifier.heightIn(min = minHeight)
                            .extrasFocusOutline().testTag("extras-spotlight-open")
                    ) { Text("Details") }
                    OutlinedButton(
                        onClick = onNext,
                        enabled = total > 1,
                        modifier = Modifier.heightIn(min = minHeight)
                            .extrasFocusOutline().testTag("extras-spotlight-next")
                    ) { Text("Next pick") }
                }
            }
        }
    }
}
