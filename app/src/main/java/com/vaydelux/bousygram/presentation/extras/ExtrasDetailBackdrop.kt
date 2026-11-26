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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.vaydelux.bousygram.core.media.PlayerBufferConfig
import com.vaydelux.bousygram.presentation.components.BousygramStatusPill
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramStyleSpec

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
    modifier: Modifier = Modifier,
    heightDp: Int? = null
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
    val halo = LocalBousygramHaloColors.current
    val styleSpec = LocalBousygramStyleSpec.current
    val shape = RoundedCornerShape(styleSpec.panelCorner)
    Box(
        modifier = modifier.fillMaxWidth()
            .height((heightDp ?: if (shortViewport) 118 else if (compact) 164 else 218).dp)
            .clip(shape)
            .background(colors.surfaceContainerLow)
            .then(
                if (halo.isHighContrast) Modifier.border(1.dp, colors.outline, shape)
                else if (styleSpec.isLiquidGlass) Modifier.border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            if (halo.isLight) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.24f),
                            if (halo.isLight) Color(0xFF0F172A).copy(alpha = 0.10f) else Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape
                )
                else Modifier
            )
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
            BousygramStatusPill(
                label = info.item.type.label + " · " + info.item.type.provider,
                accent = true
            )
            // Title and synopsis belong to the profile block below this hero,
            // not on top of the artwork: no duplicated headings on small TVs.
        }
    }
}
