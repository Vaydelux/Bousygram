package com.vaydelux.bousygram.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.core.tdlib.utils.ThumbnailExtractor
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import org.drinkless.tdlib.TdApi
import com.vaydelux.bousygram.data.database.RecentSearchQuery
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.SeriesGroup
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.media.mediaDisplayLabels
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

enum class ShellDestination(val label: String, val glyph: String) {
    Home("Home", "H"),
    Library("Library", "L"),
    Search("Search", "Q"),
    Me("Me", "M"),
    Continue("Continue", "C"),
    Favorites("Favorites", "F"),
    Offline("Offline", "O"),
    Sources("Library", "S"),
    Settings("Me", "G"),
    About("About", "i");

    companion object {
        val navItems = listOf(Home, Library, Search, Me)
        val sidebarItems = navItems
    }
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
            ShellDestination.Continue -> {
                drawCircle(color = tint, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
                val play = Path().apply {
                    moveTo(w * 0.44f, h * 0.36f)
                    lineTo(w * 0.64f, h * 0.50f)
                    lineTo(w * 0.44f, h * 0.64f)
                    close()
                }
                drawPath(play, color = tint)
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
            ShellDestination.Offline -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.20f),
                    end = Offset(w * 0.5f, h * 0.62f),
                    strokeWidth = w * 0.10f,
                    cap = StrokeCap.Round
                )
                val arrow = Path().apply {
                    moveTo(w * 0.32f, h * 0.46f)
                    lineTo(w * 0.50f, h * 0.64f)
                    lineTo(w * 0.68f, h * 0.46f)
                }
                drawPath(arrow, color = tint, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(w * 0.24f, h * 0.80f),
                    end = Offset(w * 0.76f, h * 0.80f),
                    strokeWidth = w * 0.10f,
                    cap = StrokeCap.Round
                )
            }
            ShellDestination.Library,
            ShellDestination.Sources -> {
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
            ShellDestination.Search -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.28f,
                    center = Offset(w * 0.42f, h * 0.42f),
                    style = stroke
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.63f, h * 0.63f),
                    end = Offset(w * 0.86f, h * 0.86f),
                    strokeWidth = w * 0.11f,
                    cap = StrokeCap.Round
                )
            }
            ShellDestination.Me,
            ShellDestination.Settings -> {
                val center = Offset(w * 0.50f, h * 0.36f)
                drawCircle(color = tint, radius = w * 0.18f, center = center, style = stroke)
                val body = Path().apply {
                    moveTo(w * 0.22f, h * 0.82f)
                    cubicTo(w * 0.25f, h * 0.60f, w * 0.75f, h * 0.60f, w * 0.78f, h * 0.82f)
                }
                drawPath(body, color = tint, style = stroke)
            }
            ShellDestination.About -> {
                val center = Offset(w * 0.50f, h * 0.50f)
                drawCircle(color = tint, radius = w * 0.38f, center = center, style = stroke)
                drawCircle(color = tint, radius = w * 0.05f, center = Offset(w * 0.50f, h * 0.32f))
                drawLine(
                    color = tint,
                    start = Offset(w * 0.50f, h * 0.44f),
                    end = Offset(w * 0.50f, h * 0.68f),
                    strokeWidth = w * 0.10f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun CenteredPlayBadge(
    modifier: Modifier = Modifier
) {
    val playBg = MaterialTheme.colorScheme.onPrimary
    val playIcon = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(playBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(13.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.28f, h * 0.18f)
                lineTo(w * 0.82f, h * 0.50f)
                lineTo(w * 0.28f, h * 0.82f)
                close()
            }
            drawPath(path, color = playIcon)
        }
    }
}

@Composable
fun ImagePlaceholderIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.12f, h * 0.16f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
            style = stroke
        )
        drawCircle(
            color = tint,
            radius = w * 0.08f,
            center = Offset(w * 0.65f, h * 0.38f)
        )
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.72f)
            lineTo(w * 0.42f, h * 0.48f)
            lineTo(w * 0.56f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.52f)
            lineTo(w * 0.78f, h * 0.64f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
fun CastIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Screen outline: top, right, partial bottom, partial left
        val screenPath = Path().apply {
            moveTo(w * 0.12f, h * 0.46f)
            lineTo(w * 0.12f, h * 0.24f)
            quadraticTo(w * 0.12f, h * 0.18f, w * 0.18f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.18f)
            quadraticTo(w * 0.88f, h * 0.18f, w * 0.88f, h * 0.24f)
            lineTo(w * 0.88f, h * 0.74f)
            quadraticTo(w * 0.88f, h * 0.80f, w * 0.82f, h * 0.80f)
            lineTo(w * 0.58f, h * 0.80f)
        }
        drawPath(screenPath, color = tint, style = stroke)

        // Cast waves from bottom-left corner
        drawCircle(
            color = tint,
            radius = w * 0.05f,
            center = Offset(w * 0.14f, h * 0.80f)
        )

        val r1 = w * 0.20f
        drawArc(
            color = tint,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.14f - r1, h * 0.80f - r1),
            size = Size(r1 * 2f, r1 * 2f),
            style = stroke
        )

        val r2 = w * 0.35f
        drawArc(
            color = tint,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.14f - r2, h * 0.80f - r2),
            size = Size(r2 * 2f, r2 * 2f),
            style = stroke
        )
    }
}

@Composable
fun BousygramAdaptiveShell(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenCast: (() -> Unit)? = null,
    showResume: Boolean = false,
    onResume: () -> Unit = {},
    navFocusRequester: FocusRequester? = null,
    content: @Composable () -> Unit
) {
    val adaptive = rememberAdaptiveMetrics()

    when {
        adaptive.isTvLayout -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TvTopNavBar(
                    selected = selected,
                    onSelect = onSelect,
                    onOpenSearch = onOpenSearch,
                    onOpenProfile = onOpenSettings,
                    focusRequester = navFocusRequester
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    content()
                }
            }
        }
        adaptive.isTabletLayout -> {
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                TabletNavRail(
                    selected = selected,
                    onSelect = onSelect,
                    onOpenSearch = onOpenSearch,
                    onOpenProfile = onOpenSettings,
                    focusRequester = navFocusRequester
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    content()
                }
            }
        }
        else -> {
            // Phone layout: clean header, content, bottom navigation
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
            ) {
                PhoneTopBar(
                    onOpenSearch = onOpenSearch,
                    onOpenProfile = onOpenSettings,
                    onOpenCast = onOpenCast
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
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
}

@Composable
fun TabletNavRail(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val colors = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = colors.surface,
        contentColor = colors.onSurface,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.primary.copy(alpha = 0.16f))
                        .border(1.dp, colors.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 1.dp)
                        )
                    }
                }
            }
        }
    ) {
        Spacer(Modifier.height(12.dp))
        ShellDestination.navItems.forEach { destination ->
            val isSelected = when (destination) {
                ShellDestination.Home -> selected == ShellDestination.Home
                ShellDestination.Library -> selected == ShellDestination.Library || selected == ShellDestination.Sources
                ShellDestination.Search -> selected == ShellDestination.Search
                ShellDestination.Me -> selected == ShellDestination.Me || selected == ShellDestination.Settings
                else -> destination == selected
            }
            NavigationRailItem(
                modifier = if (isSelected && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
                selected = isSelected,
                onClick = {
                    if (destination == ShellDestination.Search) onOpenSearch() else onSelect(destination)
                },
                icon = {
                    ShellNavIcon(
                        destination = destination,
                        tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        destination.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primary.copy(alpha = 0.20f),
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun TvTopNavBar(
    selected: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Logo Badge + "Bousygram" title + [Home, Library, Search]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceVariant)
                        .border(1.dp, colors.primary.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, colors.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 1.dp)
                        )
                    }
                }
                Text(
                    text = "Bousygram",
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold
                )
            }

            // Top Nav items
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(ShellDestination.Home, ShellDestination.Library, ShellDestination.Search).forEach { dest ->
                    val isSelected = when (dest) {
                        ShellDestination.Home -> selected == ShellDestination.Home
                        ShellDestination.Library -> selected == ShellDestination.Library || selected == ShellDestination.Sources
                        ShellDestination.Search -> selected == ShellDestination.Search
                        else -> false
                    }
                    var focused by remember { mutableStateOf(false) }
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .then(if (isSelected && focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    focused -> colors.primary.copy(alpha = 0.22f)
                                    isSelected -> colors.onSurface.copy(alpha = 0.08f)
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                width = if (focused) 1.5.dp else 0.dp,
                                color = if (focused) colors.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(
                                interactionSource = interaction,
                                indication = ripple(color = colors.primary),
                                onClick = {
                                    if (dest == ShellDestination.Search) onOpenSearch() else onSelect(dest)
                                }
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dest.label,
                            color = when {
                                isSelected -> colors.primary
                                focused -> colors.onSurface
                                else -> colors.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Right: Profile action button
        var profileFocused by remember { mutableStateOf(false) }
        val profileInteraction = remember { MutableInteractionSource() }
        val isProfileSelected = selected == ShellDestination.Me || selected == ShellDestination.Settings
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    when {
                        profileFocused -> colors.primary.copy(alpha = 0.22f)
                        isProfileSelected -> colors.onSurface.copy(alpha = 0.08f)
                        else -> colors.surfaceVariant
                    }
                )
                .border(
                    width = if (profileFocused) 1.5.dp else 1.dp,
                    color = if (profileFocused) colors.primary else colors.outlineVariant,
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = profileInteraction,
                    indication = ripple(color = colors.primary),
                    onClick = onOpenProfile
                )
                .onFocusChanged { profileFocused = it.isFocused }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            ShellNavIcon(
                destination = ShellDestination.Me,
                tint = if (profileFocused || isProfileSelected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun PhoneTopBar(
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCast: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.primary.copy(alpha = 0.42f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = colors.primary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Bousygram",
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onOpenCast != null) {
                TopBarIconButton(onClick = onOpenCast, contentDescription = "Cast Receiver Info") { tint ->
                    CastIcon(tint = tint)
                }
            }
            TopBarIconButton(onClick = onOpenSearch, contentDescription = "Search") { tint ->
                ShellNavIcon(destination = ShellDestination.Search, tint = tint)
            }
            TopBarIconButton(onClick = onOpenProfile, contentDescription = "Profile") { tint ->
                ShellNavIcon(destination = ShellDestination.Me, tint = tint)
            }
        }
    }
}

@Composable
fun TopBarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: @Composable (tint: Color) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val tint = if (focused) colors.primary else colors.onSurfaceVariant
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) colors.surfaceVariant else Color.Transparent)
            .border(
                width = if (focused) 1.dp else 0.dp,
                color = if (focused) colors.primary.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                onClick = onClick
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        icon(tint)
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
        color = colors.surface,
        tonalElevation = 4.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            ShellDestination.navItems.forEach { destination ->
                val isSelected = when (destination) {
                    ShellDestination.Home -> selected == ShellDestination.Home
                    ShellDestination.Library -> selected == ShellDestination.Library || selected == ShellDestination.Sources
                    ShellDestination.Search -> selected == ShellDestination.Search
                    ShellDestination.Me -> selected == ShellDestination.Me || selected == ShellDestination.Settings
                    else -> destination == selected
                }
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(destination) },
                    icon = {
                        ShellNavIcon(
                            destination = destination,
                            tint = if (isSelected) colors.primary else colors.onSurfaceVariant,
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
                        selectedIconColor = colors.primary,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primary.copy(alpha = 0.18f),
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
    // Respect Android's global animation setting and keep focus transitions fast.
    val motionDisabled = android.os.Build.VERSION.SDK_INT >= 26 &&
        !android.animation.ValueAnimator.areAnimatorsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (motionDisabled) 1f else if (focused) 1.025f else 1f,
        animationSpec = tween(durationMillis = if (motionDisabled) 0 else 145),
        label = "focusScale"
    )
    val border by animateColorAsState(
        targetValue = when {
            focused -> colors.primary
            selected -> colors.secondary
            else -> colors.outlineVariant.copy(alpha = 0.5f)
        },
        label = "focusBorder"
    )
    val surface = when {
        selected && focused -> colors.primary.copy(alpha = 0.38f)
        selected -> colors.primary.copy(alpha = 0.24f)
        focused -> colors.surfaceVariant.copy(alpha = 0.98f)
        else -> colors.surface.copy(alpha = 0.82f)
    }
    Surface(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .border(
                width = if (focused) 3.5.dp else if (selected) 2.dp else 1.dp,
                color = border,
                shape = shape
            )
            .clip(shape)
            // clickable itself owns the D-pad focus target; do not add a second
            // focusable node that can hide the active focus outline.
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = colors.primary),
                enabled = enabled,
                onClick = onClick
            ),
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
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
    // TDLib thumbnail paths can become stale after native cache cleanup.
    // Treat missing local files as absent rather than permanently showing a blank Coil error.
    val preferredThumbnail = (media.tmdbPosterPath ?: media.thumbnailPath)?.takeIf { path ->
        path.startsWith("http://") || path.startsWith("https://") ||
            path.startsWith("content://") || File(path).isFile
    }
    val context = LocalContext.current
    val isTsWithoutPoster = !privacyMode && preferredThumbnail == null &&
        miniThumbnail == null && media.fileId != null &&
        VideoFormatSupport.isTransportStream(media.fileName, media.mimeType)
    val localTsFrame by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = media.fileId,
        key2 = isTsWithoutPoster,
        key3 = context
    ) {
        val fileId = media.fileId
        val owner = AccountSession.current()
        if (!isTsWithoutPoster || fileId == null || owner == null) return@produceState
        // Subscribe BEFORE the initial probe: the file might complete while we
        // are querying TDLib. Only matching completion events wake this card.
        val completions = Channel<Unit>(Channel.CONFLATED)
        val handler: (TdApi.Object?) -> Unit = { update ->
            val file = (update as? TdApi.UpdateFile)?.file
            if (file?.id == fileId && file.local?.isDownloadingCompleted == true) {
                completions.trySend(Unit)
            }
        }
        suspend fun extract(): android.graphics.Bitmap? = try {
            ThumbnailExtractor.getCachedTransportStreamThumbnail(context, fileId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("MediaPosterCard", "Local TS artwork unavailable", failure)
            null
        }
        TdLibClient.addUpdateHandler(handler)
        try {
            value = extract()
            if (value == null) {
                // Event-driven instead of polling or downloading full Telegram videos
                // for thumbnails. One initial probe and up to two completions.
                var attempts = 0
                for (ignored in completions) {
                    if (!AccountSession.isCurrent(owner)) break
                    value = extract()
                    if (value != null || ++attempts >= 2) break
                }
            }
        } finally {
            TdLibClient.removeUpdateHandler(handler)
            completions.close()
        }
    }
    val imageData = if (privacyMode) null else preferredThumbnail?.let { path ->
        if (path.startsWith("http")) path else File(path)
    } ?: miniThumbnail ?: localTsFrame
    val imageRequest = remember(imageData) {
        ImageRequest.Builder(context)
            .data(imageData)
            .crossfade(false)
            .size(640, 360)
            .build()
    }
    val labels = mediaDisplayLabels(media, subtitle)
    val durationMs = remember(media.trustedDurationMs, media.durationSeconds) {
        media.trustedDurationMs.takeIf { it > 0L }
            ?: media.durationSeconds.takeIf { it > 0 }?.times(1000L)
            ?: 0L
    }
    val isWatched = remember(media.chatId, media.messageId, durationMs) {
        PlaybackProgressTracker.isWatched(media.chatId, media.messageId, durationMs)
    }
    val resumeFraction = remember(progress, privacyMode, media.chatId, media.messageId, media.fileId, durationMs, isWatched) {
        if (privacyMode || isWatched) {
            null
        } else if (progress != null) {
            progress.coerceIn(0.02f, 0.98f)
        } else if (durationMs > 0L) {
            val position = PlaybackProgressTracker.getMessagePosition(media.chatId, media.messageId, durationMs)
                .takeIf { it > 0 }
                ?: media.fileId?.let { PlaybackProgressTracker.getPosition(it, durationMs) }
                ?: 0
            if (position <= 0) null else (position.toFloat() / durationMs.toFloat()).coerceIn(0.02f, 0.98f)
        } else {
            null
        }
    }
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)
            .graphicsLayer {
                if (isWatched && !focused) alpha = 0.58f
            }
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
                            .border(1.5.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f), CircleShape),
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
                if (isWatched) {
                    GlassPill(
                        text = "✓ Watched",
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        accent = true
                    )
                } else {
                    val displayBadge = badge ?: when (media.type) {
                        com.vaydelux.bousygram.domain.model.MediaType.Audio -> media.docType ?: "AUDIO"
                        com.vaydelux.bousygram.domain.model.MediaType.Document -> media.docType ?: "DOC"
                        else -> if (VideoFormatSupport.isTransportStream(media.fileName, media.mimeType)) "TS" else null
                    }
                    displayBadge?.let {
                        GlassPill(
                            text = it,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            accent = true
                        )
                    }
                }
                if (!isWatched && resumeFraction != null && resumeFraction in 0.05f..0.95f) {
                    GlassPill(
                        text = "Resume ${(resumeFraction * 100).toInt()}%",
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
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
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
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
fun SeriesPosterCard(
    series: SeriesGroup,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    cardWidth: Dp? = 260.dp,
    onFocusChanged: (Boolean) -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val firstEpisode = series.episodes.firstOrNull()
    val miniThumbnail = firstEpisode?.let {
        rememberMiniThumbnail(
            it.chatId,
            it.messageId,
            if (privacyMode) null else it.miniThumbnailBytes
        )
    }
    val preferredThumbnail = firstEpisode?.tmdbPosterPath ?: firstEpisode?.tmdbBackdropPath ?: firstEpisode?.thumbnailPath
    val imageData = if (privacyMode) null else preferredThumbnail?.let { path ->
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
                        contentDescription = if (privacyMode) "Private series artwork" else series.title,
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
                GlassPill(
                    text = "${series.episodes.size} Episodes",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    accent = true
                )
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = if (privacyMode) "Private series" else series.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${series.totalSeasons} Season(s)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun GlassPill(
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
fun NoResultsIllustration(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val primary = colors.primary
    val secondary = colors.secondary
    val surfaceVariant = colors.surfaceVariant
    val outline = colors.outlineVariant

    Box(
        modifier = modifier
            .size(96.dp)
            .testTag("no_results_illustration"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = Offset(w * 0.5f, h * 0.5f)

            // Soft radial glow aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = 0.24f),
                        secondary.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = w * 0.48f
                ),
                radius = w * 0.48f,
                center = center
            )

            // Film slate / media frame in background
            val frameLeft = w * 0.16f
            val frameTop = h * 0.22f
            val frameWidth = w * 0.54f
            val frameHeight = h * 0.44f
            val corner = CornerRadius(w * 0.08f, w * 0.08f)

            drawRoundRect(
                color = surfaceVariant.copy(alpha = 0.85f),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameWidth, frameHeight),
                cornerRadius = corner
            )
            drawRoundRect(
                color = outline.copy(alpha = 0.75f),
                topLeft = Offset(frameLeft, frameTop),
                size = Size(frameWidth, frameHeight),
                cornerRadius = corner,
                style = Stroke(width = w * 0.025f)
            )

            // Film sprocket dots along top and bottom of media frame
            val dotRadius = w * 0.018f
            for (i in 0..3) {
                val dotX = frameLeft + frameWidth * (0.18f + i * 0.21f)
                drawCircle(
                    color = primary.copy(alpha = 0.45f),
                    radius = dotRadius,
                    center = Offset(dotX, frameTop + frameHeight * 0.16f)
                )
                drawCircle(
                    color = primary.copy(alpha = 0.45f),
                    radius = dotRadius,
                    center = Offset(dotX, frameTop + frameHeight * 0.84f)
                )
            }

            // Magnifying glass overlapping bottom-right of the media frame
            val lensCenter = Offset(w * 0.60f, h * 0.56f)
            val lensRadius = w * 0.19f

            drawCircle(
                color = surfaceVariant,
                radius = lensRadius,
                center = lensCenter
            )
            drawCircle(
                color = primary,
                radius = lensRadius,
                center = lensCenter,
                style = Stroke(width = w * 0.042f, cap = StrokeCap.Round)
            )

            // Magnifying glass handle
            val handleStart = Offset(
                lensCenter.x + lensRadius * 0.72f,
                lensCenter.y + lensRadius * 0.72f
            )
            val handleEnd = Offset(w * 0.86f, h * 0.82f)
            drawLine(
                color = primary,
                start = handleStart,
                end = handleEnd,
                strokeWidth = w * 0.055f,
                cap = StrokeCap.Round
            )

            // "X" inside magnifying glass lens indicating no matching media
            val crossSpan = lensRadius * 0.36f
            drawLine(
                color = secondary,
                start = Offset(lensCenter.x - crossSpan, lensCenter.y - crossSpan),
                end = Offset(lensCenter.x + crossSpan, lensCenter.y + crossSpan),
                strokeWidth = w * 0.036f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = secondary,
                start = Offset(lensCenter.x + crossSpan, lensCenter.y - crossSpan),
                end = Offset(lensCenter.x - crossSpan, lensCenter.y + crossSpan),
                strokeWidth = w * 0.036f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun NoSearchResultsEmptyState(
    query: String,
    modifier: Modifier = Modifier,
    title: String = "No results found",
    scopeLabel: String? = null,
    message: String? = null,
    onClearQuery: (() -> Unit)? = null,
    primaryActionText: String? = "Clear search",
    onPrimaryAction: (() -> Unit)? = onClearQuery,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    suggestedQueries: List<String> = emptyList(),
    suggestedSearches: List<String> = suggestedQueries,
    onSelectSuggestedQuery: ((String) -> Unit)? = null,
    onSelectSuggestion: ((String) -> Unit)? = onSelectSuggestedQuery
) {
    val colors = MaterialTheme.colorScheme
    val resolvedMessage = message ?: if (query.isNotBlank()) {
        val scopeSuffix = if (!scopeLabel.isNullOrBlank()) " in $scopeLabel" else ""
        "We couldn't find any videos matching \"$query\"$scopeSuffix. Check your spelling, try shorter keywords, or search across all Telegram channels."
    } else {
        "No matching media items were found. Try searching with different keywords or switching sources."
    }
    Box(
        modifier = modifier
            .padding(24.dp)
            .testTag("no_results_empty_state"),
        contentAlignment = Alignment.Center
    ) {
        EnterprisePanel(modifier = Modifier.widthIn(max = 520.dp)) {
            Column(
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                NoResultsIllustration()
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("no_results_title")
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = resolvedMessage,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("no_results_message")
                )
                if ((primaryActionText != null && onPrimaryAction != null) ||
                    (secondaryActionText != null && onSecondaryAction != null)
                ) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (primaryActionText != null && onPrimaryAction != null) {
                            BousygramButton(
                                text = primaryActionText,
                                onClick = onPrimaryAction,
                                selected = true,
                                modifier = Modifier.testTag("no_results_primary_action")
                            )
                        }
                        if (secondaryActionText != null && onSecondaryAction != null) {
                            BousygramButton(
                                text = secondaryActionText,
                                onClick = onSecondaryAction,
                                selected = false,
                                modifier = Modifier.testTag("no_results_secondary_action")
                            )
                        }
                    }
                }
                val filteredSuggestions = remember(suggestedSearches, query) {
                    suggestedSearches
                        .filter { it.isNotBlank() && !it.equals(query.trim(), ignoreCase = true) }
                        .distinctBy { it.lowercase() }
                        .take(4)
                }
                if (filteredSuggestions.isNotEmpty() && onSelectSuggestion != null) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Try a recent search:",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(filteredSuggestions, key = { it.lowercase() }) { suggestion ->
                            BousygramChip(
                                text = suggestion,
                                selected = false,
                                onClick = { onSelectSuggestion(suggestion) },
                                modifier = Modifier.testTag("no_results_suggestion_${suggestion.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    illustration: (@Composable () -> Unit)? = null
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
                if (illustration != null) {
                    illustration()
                    Spacer(Modifier.height(12.dp))
                }
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

/** Android's Remove Animations preference sets the global animator scale to zero. */
internal fun shouldAnimateShimmer(animatorScale: Float): Boolean =
    animatorScale.isFinite() && animatorScale > 0f

@Composable
fun rememberShimmerBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    val baseColor = colors.surfaceVariant.copy(alpha = 0.36f)
    val highlightColor = colors.primary.copy(alpha = 0.22f)
    val midColor = colors.surfaceVariant.copy(alpha = 0.68f)
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        val scale = runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        shouldAnimateShimmer(scale)
    }
    if (!animationsEnabled) {
        // No looping transition or moving shimmer while system animations are disabled.
        return Brush.linearGradient(
            colors = listOf(baseColor, midColor, highlightColor, midColor, baseColor),
            start = Offset.Zero,
            end = Offset(480f, 150f)
        )
    }
    val transition = rememberInfiniteTransition(label = "shimmerSweep")
    val translateAnim by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(baseColor, midColor, highlightColor, midColor, baseColor),
        start = Offset(translateAnim - 300f, translateAnim - 150f),
        end = Offset(translateAnim + 300f, translateAnim + 150f)
    )
}

@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier,
    rounded: Dp = 18.dp
) {
    val shimmerBrush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(rounded))
            .background(shimmerBrush)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(rounded)
            )
    )
}

@Composable
fun MediaPosterSkeletonCard(
    modifier: Modifier = Modifier,
    cardWidth: Dp? = null
) {
    val shimmerBrush = rememberShimmerBrush()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = modifier
            .then(if (cardWidth != null) Modifier.width(cardWidth) else Modifier)
            .border(
                width = 1.dp,
                color = colors.outlineVariant.copy(alpha = 0.38f),
                shape = shape
            )
            .clip(shape)
            .testTag("media_poster_skeleton_card"),
        color = colors.surface.copy(alpha = 0.78f),
        shape = shape
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(shimmerBrush)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .width(46.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.background.copy(alpha = 0.55f))
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.52f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun BrowseGridShimmerSkeleton(
    columns: GridCells,
    modifier: Modifier = Modifier,
    itemCount: Int = 9,
    spacing: Dp = 12.dp
) {
    LazyVerticalGrid(
        columns = columns,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(bottom = 24.dp),
        userScrollEnabled = false,
        modifier = modifier
            .fillMaxSize()
            .testTag("browse_grid_shimmer_skeleton")
    ) {
        items(itemCount) {
            MediaPosterSkeletonCard(
                cardWidth = null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SidebarListShimmerSkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6
) {
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sidebar_shimmer_skeleton"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(itemCount) { idx ->
            val widthFraction = when (idx % 3) {
                0 -> 0.88f
                1 -> 0.72f
                else -> 0.80f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .height(13.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
            }
        }
    }
}

@Composable
fun RecentHistoryIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(
            color = tint,
            radius = w * 0.38f,
            center = Offset(w * 0.50f, h * 0.50f),
            style = stroke
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.50f),
            end = Offset(w * 0.50f, h * 0.28f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.50f),
            end = Offset(w * 0.66f, h * 0.58f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun RecentSearchesSection(
    recentSearches: List<RecentSearchQuery>,
    activeQuery: String,
    onSelectQuery: (String) -> Unit,
    onClearActiveQuery: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveQuery: ((String) -> Unit)? = null,
    onOpenFullSearch: ((String) -> Unit)? = null
) {
    if (recentSearches.isEmpty() && activeQuery.isBlank()) return
    val colors = MaterialTheme.colorScheme
    val handleClearHistory = {
        onClearHistory()
        if (activeQuery.isNotBlank()) {
            onClearActiveQuery()
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recent_searches_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                RecentHistoryIcon(tint = colors.primary)
                Text(
                    text = "Recent Searches",
                    color = colors.onBackground,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                if (activeQuery.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.primary.copy(alpha = 0.20f)
                    ) {
                        Text(
                            text = "Active: \"$activeQuery\"",
                            color = colors.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "• Tap a query to re-execute search",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (recentSearches.isNotEmpty()) {
                TvFocusSurface(
                    onClick = handleClearHistory,
                    selected = false,
                    modifier = Modifier.testTag("clear_history_button")
                ) { focused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "✕",
                            color = if (focused) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Clear History",
                            color = if (focused) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recent_searches_quick_bar")
        ) {
            if (activeQuery.isNotBlank()) {
                item(key = "__clear_active_filter__") {
                    TvFocusSurface(
                        onClick = onClearActiveQuery,
                        selected = true,
                        modifier = Modifier.testTag("clear_quick_search_filter_chip")
                    ) { focused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "✕ Clear \"$activeQuery\"",
                                color = if (focused) colors.onSurface else colors.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (onOpenFullSearch != null) {
                    item(key = "__open_full_search__") {
                        TvFocusSurface(
                            onClick = { onOpenFullSearch(activeQuery) },
                            selected = false,
                            modifier = Modifier.testTag("open_full_search_chip")
                        ) { focused ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Search all Telegram for \"$activeQuery\" →",
                                    color = if (focused) colors.primary else colors.secondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
            items(recentSearches, key = { it.normalizedQuery }) { entry ->
                val isSelected = activeQuery.equals(entry.query, ignoreCase = true)
                TvFocusSurface(
                    onClick = { onSelectQuery(entry.query) },
                    selected = isSelected,
                    modifier = Modifier.testTag("recent_search_chip_${entry.normalizedQuery}")
                ) { focused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = if (isSelected) "↻" else "⌕",
                            color = if (isSelected) colors.primary else if (focused) colors.onSurface else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = entry.query,
                            color = if (isSelected) colors.primary else colors.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entry.resultCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) colors.primary.copy(alpha = 0.24f) else colors.surfaceVariant
                            ) {
                                Text(
                                    text = "${entry.resultCount}",
                                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (onRemoveQuery != null && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onRemoveQuery(entry.query) }
                                    .padding(horizontal = 3.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "×",
                                    color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            if (recentSearches.isNotEmpty()) {
                item(key = "__clear_recent_searches__") {
                    TvFocusSurface(
                        onClick = handleClearHistory,
                        selected = false,
                        modifier = Modifier.testTag("clear_recent_searches_button")
                    ) { focused ->
                        Text(
                            text = "Clear History",
                            color = if (focused) colors.error else colors.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentSearchesQuickBar(
    recentSearches: List<RecentSearchQuery>,
    activeQuery: String,
    onSelectQuery: (String) -> Unit,
    onClearActiveQuery: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onRemoveQuery: ((String) -> Unit)? = null,
    onOpenFullSearch: ((String) -> Unit)? = null
) {
    RecentSearchesSection(
        recentSearches = recentSearches,
        activeQuery = activeQuery,
        onSelectQuery = onSelectQuery,
        onClearActiveQuery = onClearActiveQuery,
        onClearHistory = onClearHistory,
        modifier = modifier,
        onRemoveQuery = onRemoveQuery,
        onOpenFullSearch = onOpenFullSearch
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
