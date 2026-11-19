package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.R
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors

/**
 * The only in-app visual brand mark.
 *
 * The same approved Chow Chow and paper-plane mascot is used in the Android
 * launcher foreground, legacy launcher, README, and the favicon source.
 * Never replace it with a generic play triangle or unrelated gradient glyph.
 */
@Composable
fun BousygramBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showHalo: Boolean = false
) {
    val halo = LocalBousygramHaloColors.current
    if (!showHalo || halo.isHighContrast) {
        Image(
            painter = painterResource(R.drawable.ic_bousygram_mascot),
            contentDescription = "Bousygram mascot",
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).testTag("bousygram-canonical-brand-mark")
        )
    } else {
        val ringPadding = (size * 0.08f).coerceAtLeast(3.dp)
        Box(
            modifier = modifier
                .size(size + ringPadding * 2)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            halo.haloStart.copy(alpha = if (halo.isLight) 0.14f else 0.24f),
                            halo.haloEnd.copy(alpha = if (halo.isLight) 0.05f else 0.10f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            halo.haloStart.copy(alpha = 0.78f),
                            halo.haloEnd.copy(alpha = 0.58f),
                            halo.goldAccent.copy(alpha = 0.45f)
                        )
                    ),
                    shape = CircleShape
                )
                .padding(ringPadding),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_bousygram_mascot),
                contentDescription = "Bousygram mascot",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .testTag("bousygram-canonical-brand-mark")
            )
        }
    }
}
