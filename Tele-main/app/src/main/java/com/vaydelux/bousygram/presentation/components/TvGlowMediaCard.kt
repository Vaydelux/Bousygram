package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.media.mediaDisplayLabels
import java.io.File

private val TelegramGlow = Color(0xFF0088CC)

@Composable
fun TvGlowMediaCard(
    media: MediaItem,
    channelLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    previewImageUrl: String? = null,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1f,
        animationSpec = tween(200),
        label = "cardScale"
    )
    val cardGlowElevation by animateFloatAsState(
        targetValue = if (isFocused) 24f else 0f,
        animationSpec = tween(200),
        label = "cardGlow"
    )
    val highlightBorderColor by animateColorAsState(
        targetValue = if (isFocused) TelegramGlow else Color.Transparent,
        animationSpec = tween(200),
        label = "cardBorder"
    )

    val imageData = previewImageUrl
        ?: media.thumbnailPath?.let { path ->
            if (path.startsWith("http")) path else File(path)
        }
    val labels = mediaDisplayLabels(media, channelLabel)

    Box(
        modifier = modifier
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .scale(scaleFactor)
            .shadow(
                elevation = cardGlowElevation.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = TelegramGlow,
                spotColor = TelegramGlow,
                clip = false
            )
            .border(2.dp, highlightBorderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .focusable()
            .clickable(onClick = onClick)
            .width(260.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageData)
                            .crossfade(true)
                            .build(),
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF2C2C2E), Color(0xFF3A3A3C), Color(0xFF2C2C2E))
                                        )
                                    )
                            )
                        },
                        error = {
                            Text("No preview", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    )
                } else {
                    Text("No preview", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    text = labels.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${labels.subtitle} • ${labels.durationLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) TelegramGlow else Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
