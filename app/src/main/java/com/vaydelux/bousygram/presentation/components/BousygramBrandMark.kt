package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.R

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
    size: Dp = 40.dp
) {
    Image(
        painter = painterResource(R.drawable.ic_bousygram_mascot),
        contentDescription = "Bousygram mascot",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).testTag("bousygram-canonical-brand-mark")
    )
}
