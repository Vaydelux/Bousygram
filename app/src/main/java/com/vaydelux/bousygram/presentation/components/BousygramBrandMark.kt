package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Because the artwork already includes its cyan-to-blue circular halo,
 * surrounding chrome stays clean rather than adding a second neon glow ring.
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
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = CircleShape
                ),
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
