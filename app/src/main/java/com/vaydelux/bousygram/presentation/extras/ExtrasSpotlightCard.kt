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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramGlassLevel
import com.vaydelux.bousygram.presentation.components.BousygramIcon
import com.vaydelux.bousygram.presentation.components.BousygramIcons
import com.vaydelux.bousygram.presentation.components.BousygramStatusPill
import com.vaydelux.bousygram.presentation.components.LiquidGlassSurface
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
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
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val shape = RoundedCornerShape(styleSpec.panelCorner)
    val spotlightBody: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().padding(if (isTv) 18.dp else 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(if (isTv) 112.dp else 94.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        1.dp,
                        if (halo.isLight) Color(0xFF0F172A).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.16f),
                        RoundedCornerShape(14.dp)
                    ),
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Featured in this catalog", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.rating.isNotBlank()) {
                        BousygramStatusPill(
                            label = item.rating,
                            icon = BousygramIcons.Star,
                            gold = true
                        )
                    }
                    if (!item.year.isNullOrBlank()) {
                        BousygramStatusPill(label = item.year)
                    }
                    BousygramStatusPill(
                        label = "${item.type.provider} · ${position + 1}/${total.coerceAtLeast(1)}",
                        accent = true
                    )
                }
                Text(
                    item.title,
                    style = if (isTv) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("extras-spotlight-title")
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
                    BousygramButton(
                        text = "Details",
                        leadingIcon = BousygramIcons.Info,
                        selected = true,
                        onClick = onOpen,
                        modifier = Modifier.heightIn(min = minHeight)
                            .testTag("extras-spotlight-open")
                    )
                    BousygramButton(
                        text = "Next pick",
                        leadingIcon = BousygramIcons.Next,
                        enabled = total > 1,
                        onClick = onNext,
                        modifier = Modifier.heightIn(min = minHeight)
                            .testTag("extras-spotlight-next")
                    )
                }
            }
        }
    }
    if (styleSpec.isLiquidGlass) {
        LiquidGlassSurface(
            level = BousygramGlassLevel.PANEL,
            shape = shape,
            accentTinted = true,
            modifier = modifier.fillMaxWidth().testTag("extras-spotlight")
        ) {
            spotlightBody()
        }
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = shape,
            modifier = modifier.fillMaxWidth().testTag("extras-spotlight")
                .then(
                    if (halo.isHighContrast) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    else Modifier
                )
        ) {
            spotlightBody()
        }
    }
}
