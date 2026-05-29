package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.media.mediaDisplayLabels
import java.io.File

private val FocusBlue = Color(0xFF5B6BF8)
private val AccentMint = Color(0xFF00D4B1)
private val Panel = Color(0xFF111318)
private val PanelRaised = Color(0xFF1A1D27)

val EnterpriseBackground = Brush.verticalGradient(
    listOf(Color(0xFF08090C), Color(0xFF10131A), Color(0xFF08090C))
)

@Composable
fun BousygramScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(EnterpriseBackground)
    ) {
        content()
    }
}

@Composable
fun TvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    width: Dp? = null,
    content: @Composable (focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        targetValue = when {
            focused -> FocusBlue
            selected -> AccentMint
            else -> Color.Transparent
        },
        label = "focusBorder"
    )
    val surface = when {
        selected -> FocusBlue.copy(alpha = 0.26f)
        focused -> PanelRaised
        else -> Panel.copy(alpha = 0.88f)
    }
    Surface(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .graphicsLayer {
                scaleX = if (focused) 1.035f else 1f
                scaleY = if (focused) 1.035f else 1f
            }
            .shadow(if (focused) 18.dp else 0.dp, RoundedCornerShape(8.dp), clip = false)
            .border(2.dp, border, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled),
        color = surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        content(focused)
    }
}

@Composable
fun BousygramButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    TvFocusSurface(
        onClick = onClick,
        selected = selected,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp)
    ) { focused ->
        Text(
            text = text,
            color = if (selected || focused) Color.White else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
fun BousygramChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BousygramButton(text = text, selected = selected, onClick = onClick, modifier = modifier)
}

@Composable
fun BousygramTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            cursorColor = FocusBlue,
            focusedBorderColor = FocusBlue,
            unfocusedBorderColor = Color(0xFF2A2D3A),
            focusedLabelColor = AccentMint,
            unfocusedLabelColor = Color(0xFF8A8FA8)
        )
    )
}

@Composable
fun MediaPosterCard(
    media: MediaItem,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    privacyMode: Boolean = false,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val imageData = media.thumbnailPath?.let { path -> if (path.startsWith("http")) path else File(path) }
    val labels = mediaDisplayLabels(media, subtitle)
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier
            .width(260.dp)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
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
                        loading = { ShimmerCard(Modifier.fillMaxSize(), rounded = 0.dp) },
                        error = { PosterFallback() },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PosterFallback()
                }
                badge?.let {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(it, color = AccentMint, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(labels.durationLabel, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = if (privacyMode) "Private video" else labels.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (privacyMode) subtitle else labels.subtitle,
                    color = if (focused) AccentMint else Color(0xFF8A8FA8),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                labels.fileSizeLabel?.let { fileSize ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = fileSize,
                        color = Color(0xFF8A8FA8),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterFallback() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1D2330), Color(0xFF121620)))),
        contentAlignment = Alignment.Center
    ) {
        Text("No preview", color = Color(0xFF8A8FA8), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = Color(0xFF8A8FA8),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            BousygramButton(actionText, onAction)
        }
    }
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    rounded: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rounded))
            .background(PanelRaised.copy(alpha = alpha))
    )
}

@Composable
fun LoadingOverlay(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = FocusBlue)
        Spacer(Modifier.height(12.dp))
        Text(text, color = Color(0xFFB8BED4), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun EnterprisePanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Panel.copy(alpha = 0.94f),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 4.dp
    ) {
        content()
    }
}

@Composable
fun FeatureRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TvFocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(value, color = AccentMint, style = MaterialTheme.typography.bodySmall)
        }
    }
}
