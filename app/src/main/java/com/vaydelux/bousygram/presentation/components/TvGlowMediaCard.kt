package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.domain.model.MediaItem

/** Focus-glow poster used by older call sites. Visual language matches [MediaPosterCard]. */
@Composable
fun TvGlowMediaCard(
    media: MediaItem,
    channelLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewImageUrl: String? = null,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    val artwork = media.copy(thumbnailPath = previewImageUrl ?: media.thumbnailPath)
    MediaPosterCard(
        media = artwork,
        subtitle = channelLabel,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        cardWidth = 260.dp,
        onFocusChanged = onFocusChanged
    )
}
