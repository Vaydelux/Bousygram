package com.vaydelux.bousygram.presentation.extras

import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens

/**
 * Detail hero uses backdrop metadata already returned by the TMDB/AniList
 * detail request; no additional catalog call, timer or background process.
 * Coil is explicitly bounded for 32-bit/low-memory Android TV hardware.
 */
@Composable
internal fun ExtrasDetailBackdrop(
    info: ExtrasDetail,
    compact: Boolean,
    shortViewport: Boolean,
    modifier: Modifier = Modifier
) {
    val url = info.backdrop?.takeIf { it.isNotBlank() } ?: return
    val context = LocalContext.current
    val lowMemory = remember(context) {
        PlayerBufferConfig.isLowRamDevice(context) || !Process.is64Bit()
    }
    val target = ExtrasImagePolicy.backdrop(lowMemory)
    val request = remember(context, url, target) {
        ImageRequest.Builder(context)
            .data(url)
            .size(target.width, target.height)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(BousygramUiTokens.cardCorner)
    Box(
        modifier = modifier.fillMaxWidth()
            .height(if (shortViewport) 118.dp else if (compact) 164.dp else 218.dp)
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .border(1.dp, colors.outlineVariant, shape)
            .testTag("extras-detail-backdrop")
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Background adapts to light, dark and high-contrast presets so hero
        // labels never depend on the source image's brightness.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        colors.background.copy(alpha = 0.35f),
                        colors.background.copy(alpha = 0.96f)
                    )
                )
            )
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                info.item.type.label + "  ·  " + info.item.type.provider,
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                info.item.title,
                style = if (shortViewport) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
