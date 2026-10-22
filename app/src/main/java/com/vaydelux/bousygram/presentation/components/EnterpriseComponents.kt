package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.media.mediaDisplayLabels
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

enum class ShellDestination(val label: String, val glyph: String) {
    Home("Home", "H"),
    Library("Library", "L"),
    Favorites("Favorites", "F"),
    Search("Search", "S"),
    Settings("Settings", "G")
}

@Composable
fun ShellNavIcon(
    destination: ShellDestination,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.105f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (destination) {
            ShellDestination.Home -> {
                val roof = Path().apply {
                    moveTo(w * 0.14f, h * 0.48f)
                    lineTo(w * 0.50f, h * 0.16f)
                    lineTo(w * 0.86f, h * 0.48f)
                }
                drawPath(roof, color = tint, style = stroke)
                val body = Path().apply {
                    moveTo(w * 0.24f, h * 0.44f)
                    lineTo(w * 0.24f, h * 0.84f)
                    lineTo(w * 0.76f, h * 0.84f)
                    lineTo(w * 0.76f, h * 0.44f)
                }
                drawPath(body, color = tint, style = stroke)
            }
            ShellDestination.Library -> {
                val cell = w * 0.30f
                val gap = w * 0.10f
                val start = w * 0.15f
                val radius = CornerRadius(w * 0.07f, w * 0.07f)
                for (r in 0..1) {
                    for (c in 0..1) {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(start + c * (cell + gap), start + r * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                            style = stroke
                        )
                    }
                }
            }
            ShellDestination.Favorites -> {
                val heart = Path().apply {
                    moveTo(w * 0.50f, h * 0.82f)
                    cubicTo(w * 0.15f, h * 0.60f, w * 0.12f, h * 0.26f, w * 0.33f, h * 0.22f)
                    cubicTo(w * 0.44f, h * 0.20f, w * 0.50f, h * 0.30f, w * 0.50f, h * 0.34f)
                    cubicTo(w * 0.50f, h * 0.30f, w * 0.56f, h * 0.20f, w * 0.67f, h * 0.22f)
                    cubicTo(w * 0.88f, h * 0.26f, w * 0.85f, h * 0.60f, w * 0.50f, h * 0.82f)
                    close()
                }
                drawPath(heart, color = tint, style = stroke)
            }
            ShellDestination.Search -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.27f,
                    center = Offset(w * 0.43f, h * 0.43f),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.63f, h * 0.63f),
                    end = Offset(w * 0.84f, h * 0.84f),
                    strokeWidth = w * 0.11f,
                    cap = StrokeCap.Round
                )
            }
            ShellDestination.Settings -> {
                val center = Offset(w * 0.50f, h * 0.50f)
                drawCircle(color = tint, radius = w * 0.15f, center = center, style = stroke)
                drawCircle(color = tint, radius = w * 0.31f, center = center, style = stroke)
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60).toDouble())
                    val r1 = w * 0.31f
                    val r2 = w * 0.42f
                    drawLine(
                        color = tint,
                        start = Offset(
                            center.x + (r1 * cos(angle)).toFloat(),
                            center.y + (r1 * sin(angle)).toFloat()
                        ),
                        end = Offset(
                            center.x + (r2 * cos(angle)).toFloat(),
                            center.y + (r2 * sin(angle)).toFloat()
                        ),
                        strokeWidth = w * 0.10f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
fun BousygramAdaptiveShell(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    showResume: Boolean = false,
    onResume: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val adaptive = rememberAdaptiveMetrics()
    val useRail = adaptive.useTvChrome
    if (useRail) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            GlassNavigationRail(
                selected = selected,
                onSelect = onSelect,
                showResume = showResume,
                onResume = onResume
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            GlassTopBar(
                showResume = showResume,
                onResume = onResume,
                modifier = Modifier.statusBarsPadding()
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
            GlassBottomBar(
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun GlassNavigationRail(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    showResume: Boolean,
    onResume: () -> Unit
) {
    val shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(114.dp)
            .fillMaxHeight()
            .padding(top = 12.dp, bottom = 12.dp, end = 8.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surface.copy(alpha = 0.88f),
                        colors.surfaceVariant.copy(alpha = 0.56f),
                        colors.surface.copy(alpha = 0.88f)
                    )
                )
            )
            .border(1.dp, colors.outlineVariant, shape)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(colors.primary, colors.secondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶",
                color = colors.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Bousygram",
            color = colors.onSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        ShellDestination.entries.forEach { destination ->
            RailDestination(
                destination = destination,
                selected = destination == selected,
                onClick = { onSelect(destination) }
            )
        }
        Spacer(Modifier.weight(1f))
        if (showResume) {
            BousygramButton("Resume", onResume, selected = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RailDestination(
    destination: ShellDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    TvFocusSurface(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    ) { focused ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                            focused -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                ShellNavIcon(
                    destination = destination,
                    tint = if (selected || focused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                destination.label,
                color = if (selected || focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GlassTopBar(
    showResume: Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surface.copy(alpha = 0.92f),
                        colors.background.copy(alpha = 0.75f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(colors.primary, colors.secondary))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "▶",
                color = colors.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Bousygram",
                color = colors.onBackground,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Telegram Cinema Streamer",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (showResume) {
            BousygramButton("▶ Resume", onResume, selected = true)
        }
    }
}

@Composable
private fun GlassBottomBar(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = colors.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        color = colors.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            ShellDestination.entries.forEach { destination ->
                val isSelected = destination == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = {
                        ShellNavIcon(
                            destination = destination,
                            tint = if (isSelected) colors.secondary else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.secondary,
                        selectedTextColor = colors.secondary,
                        indicatorColor = colors.primary.copy(alpha = 0.24f),
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
fun BousygramScaffold(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val backgroundBrush = Brush.verticalGradient(
        listOf(colors.background, colors.surface.copy(alpha = 0.55f), colors.background)
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
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
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.035f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "focusScale"
    )
    val border by animateColorAsState(
        targetValue = when {
            focused -> colors.primary
            selected -> colors.secondary
            else -> colors.outlineVariant
        },
        label = "focusBorder"
    )
    val surface = when {
        selected && focused -> colors.primary.copy(alpha = 0.34f)
        selected -> colors.primary.copy(alpha = 0.24f)
        focused -> colors.surfaceVariant.copy(alpha = 0.94f)
        else -> colors.surface.copy(alpha = 0.80f)
    }
    Surface(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 18.dp else 0.dp,
                shape = shape,
                ambientColor = colors.primary.copy(alpha = 0.45f),
                spotColor = colors.primary.copy(alpha = 0.35f),
                clip = false
            )
            .border(if (focused || selected) 1.5.dp else 1.dp, border, shape)
            .clip(shape)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                enabled = enabled,
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled),
        color = surface,
        shape = shape
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
        modifier = modifier.heightIn(min = 44.dp)
    ) { focused ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                color = when {
                    selected -> MaterialTheme.colorScheme.secondary
                    focused -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            .heightIn(min = 60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.secondary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
fun rememberMiniThumbnail(chatId: Long, messageId: Long, bytes: ByteArray?): android.graphics.Bitmap? {
    val size = bytes?.size ?: 0
    return remember(chatId, messageId, size) {
        if (bytes == null || bytes.isEmpty()) {
            null
        } else {
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}

@Composable
fun MediaPosterCard(
    media: MediaItem,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    privacyMode: Boolean = false,
    cardWidth: Dp? = 260.dp,
    progress: Float? = null,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val miniThumbnail = rememberMiniThumbnail(
        media.chatId,
        media.messageId,
        if (privacyMode) null else media.miniThumbnailBytes
    )
    val imageData = if (privacyMode) null else media.thumbnailPath?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail
    val context = LocalContext.current
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }
    val labels = mediaDisplayLabels(media, subtitle)
    val resumeFraction = remember(progress, privacyMode, media.fileId, media.durationSeconds, media.trustedDurationMs) {
        if (privacyMode) {
            null
        } else if (progress != null) {
            progress.coerceIn(0.02f, 0.98f)
        } else {
            val durationMs = media.trustedDurationMs.takeIf { it > 0L }
                ?: media.durationSeconds.takeIf { it > 0 }?.times(1000L)
            if (durationMs == null) {
                null
            } else {
                val position = PlaybackProgressTracker.getMessagePosition(media.chatId, media.messageId, durationMs)
                    .takeIf { it > 0 }
                    ?: media.fileId?.let { PlaybackProgressTracker.getPosition(it, durationMs) }
                    ?: 0
                if (position <= 0) null else (position.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 0.98f)
            }
        }
    }
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)
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
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (imageData != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = if (privacyMode) "Private video artwork" else media.title,
                        contentScale = ContentScale.Crop,
                        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                                )
                            )
                    )
                } else {
                    PosterFallback(privacyMode = privacyMode)
                }
                if (focused) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.88f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.75f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                badge?.let {
                    GlassPill(
                        text = it,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        accent = true
                    )
                }
                if (!privacyMode) {
                    GlassPill(
                        text = labels.durationLabel,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    )
                }
                resumeFraction?.let { fraction ->
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.5.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = Color.Black.copy(alpha = 0.45f)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = if (privacyMode) "Private video" else labels.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (privacyMode) "Details hidden" else labels.subtitle,
                        color = if (focused) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!privacyMode && !labels.fileSizeLabel.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = labels.fileSizeLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Box(
        modifier = modifier
            .background(
                if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.84f)
                else MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                RoundedCornerShape(999.dp)
            )
            .border(
                1.dp,
                if (accent) MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun PosterFallback(privacyMode: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.surfaceVariant,
                        colors.primary.copy(alpha = 0.18f),
                        colors.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surface.copy(alpha = 0.68f))
                    .border(1.dp, colors.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (privacyMode) "●" else "▶",
                    color = colors.secondary,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Text(
                if (privacyMode) "Hidden preview" else "Telegram Video",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall
            )
        }
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
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        EnterprisePanel(modifier = Modifier.widthIn(max = 460.dp)) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (actionText != null && onAction != null) {
                    Spacer(Modifier.height(18.dp))
                    BousygramButton(actionText, onAction, selected = true)
                }
            }
        }
    }
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    rounded: Dp = 18.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rounded))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
fun LoadingOverlay(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        EnterprisePanel {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun EnterprisePanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(24.dp)
        ),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp
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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 220.dp)
            )
        }
    }
}
