package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern
import com.vaydelux.bousygram.presentation.theme.BousygramHaloColors
import com.vaydelux.bousygram.presentation.theme.LocalBousygramBackdropPattern
import com.vaydelux.bousygram.presentation.theme.LocalBousygramHaloColors

/**
 * Theme-aware native Compose backdrop supporting ambient glow, architectural
 * patterns, iPhone aurora mesh, and cutesy kawaii / Chow paw patterns.
 *
 * This legacy component name is retained for compatibility with callers.
 * Draw-only caching avoids an always-running animation loop, WebView layer,
 * GPU blur, timer, or retained bitmap — safe on 32-bit/low-RAM Android TVs.
 * High-contrast mode suppresses decorative layers entirely.
 */
@Composable
fun PremiumGridBackdrop(
    modifier: Modifier = Modifier,
    patternOverride: BousygramBackdropPattern? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    val pattern = patternOverride ?: LocalBousygramBackdropPattern.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("bousygram-premium-backdrop")
    ) {
        if (!halo.isHighContrast) {
            Box(
                Modifier
                    .matchParentSize()
                    .drawBackdropPattern(
                        pattern = pattern,
                        halo = halo,
                        primary = colors.primary,
                        secondary = colors.secondary,
                        tertiary = colors.tertiary,
                        previewBoost = false
                    )
            )
        }
        content()
    }
}

/**
 * Miniature live Canvas preview of any [BousygramBackdropPattern] used inside
 * the Settings Appearance Studio picker cards.
 */
@Composable
fun BousygramBackdropPreviewCanvas(
    pattern: BousygramBackdropPattern,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    Box(
        modifier = modifier
            .background(colors.background)
            .drawBackdropPattern(
                pattern = pattern,
                halo = halo,
                primary = colors.primary,
                secondary = colors.secondary,
                tertiary = colors.tertiary,
                previewBoost = true
            )
    )
}

private fun Modifier.drawBackdropPattern(
    pattern: BousygramBackdropPattern,
    halo: BousygramHaloColors,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    previewBoost: Boolean
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val maxDim = maxOf(w, h).coerceAtLeast(1f)
    val light = halo.isLight
    val boost = if (previewBoost) 2.2f else 1f

    val primaryGlow = Brush.radialGradient(
        colors = listOf(
            halo.haloStart.copy(alpha = ((if (light) 0.08f else 0.15f) * boost).coerceAtMost(0.45f)),
            halo.haloEnd.copy(alpha = ((if (light) 0.03f else 0.06f) * boost).coerceAtMost(0.24f)),
            Color.Transparent
        ),
        center = Offset(w * 0.84f, h * 0.12f),
        radius = maxDim * 0.82f
    )
    val secondaryGlow = Brush.radialGradient(
        colors = listOf(
            halo.haloEnd.copy(alpha = ((if (light) 0.055f else 0.10f) * boost).coerceAtMost(0.36f)),
            Color.Transparent
        ),
        center = Offset(w * 0.08f, h * 0.90f),
        radius = maxDim * 0.72f
    )
    val warmAccent = Brush.radialGradient(
        colors = listOf(
            halo.goldAccent.copy(alpha = ((if (light) 0.035f else 0.05f) * boost).coerceAtMost(0.22f)),
            Color.Transparent
        ),
        center = Offset(w * 0.50f, h * 0.04f),
        radius = maxDim * 0.50f
    )

    // Pre-cache pattern Paths once per layout size so draw frames allocate zero objects.
    val wavePaths: List<Path> = if (pattern == BousygramBackdropPattern.TOPO_WAVES) {
        val count = if (previewBoost) 4 else 6
        List(count) { idx ->
            val t = (idx + 1).toFloat() / (count + 1).toFloat()
            val yBase = h * (0.14f + t * 0.74f)
            val amp = h * (if (previewBoost) 0.12f else 0.065f) * (if (idx % 2 == 0) 1f else -0.85f)
            Path().apply {
                moveTo(-w * 0.05f, yBase)
                cubicTo(
                    w * 0.25f, yBase - amp,
                    w * 0.45f, yBase + amp * 1.15f,
                    w * 0.72f, yBase - amp * 0.55f
                )
                cubicTo(
                    w * 0.86f, yBase - amp * 1.1f,
                    w * 0.95f, yBase + amp * 0.45f,
                    w * 1.06f, yBase - amp * 0.2f
                )
            }
        }
    } else emptyList()

    val meshBrushes: List<Brush> = if (pattern == BousygramBackdropPattern.IOS_MESH) {
        val a1 = ((if (light) 0.19f else 0.26f) * (if (previewBoost) 1.55f else 1f)).coerceAtMost(0.55f)
        val a2 = ((if (light) 0.15f else 0.22f) * (if (previewBoost) 1.55f else 1f)).coerceAtMost(0.48f)
        val a3 = ((if (light) 0.12f else 0.16f) * (if (previewBoost) 1.55f else 1f)).coerceAtMost(0.40f)
        listOf(
            Brush.radialGradient(
                listOf(primary.copy(alpha = a1), Color.Transparent),
                center = Offset(w * 0.18f, h * 0.18f),
                radius = maxDim * 0.62f
            ),
            Brush.radialGradient(
                listOf(secondary.copy(alpha = a2), Color.Transparent),
                center = Offset(w * 0.84f, h * 0.26f),
                radius = maxDim * 0.58f
            ),
            Brush.radialGradient(
                listOf(tertiary.copy(alpha = a3), Color.Transparent),
                center = Offset(w * 0.76f, h * 0.86f),
                radius = maxDim * 0.56f
            ),
            Brush.radialGradient(
                listOf(halo.haloStart.copy(alpha = a2 * 0.85f), Color.Transparent),
                center = Offset(w * 0.28f, h * 0.84f),
                radius = maxDim * 0.52f
            ),
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (light) 0.20f else 0.045f),
                    Color.Transparent,
                    Color.White.copy(alpha = if (light) 0.10f else 0.025f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )
    } else emptyList()

    val kawaiiHeartPath = if (pattern == BousygramBackdropPattern.CUTESY_KAWAII ||
        pattern == BousygramBackdropPattern.CUTESY_PAWS
    ) buildUnitHeartPath() else null
    val kawaiiSparklePath = if (pattern == BousygramBackdropPattern.CUTESY_KAWAII ||
        pattern == BousygramBackdropPattern.CUTESY_PAWS
    ) buildUnitSparklePath() else null

    onDrawBehind {
        when (pattern) {
            BousygramBackdropPattern.AMBIENT_HALO -> {
                drawRect(primaryGlow)
                drawRect(secondaryGlow)
                drawRect(warmAccent)
            }
            BousygramBackdropPattern.DOT_MATRIX -> {
                drawRect(primaryGlow)
                drawRect(secondaryGlow)
                val step = if (previewBoost) 9.dp.toPx() else 28.dp.toPx()
                val radius = if (previewBoost) 1.35.dp.toPx() else 1.55.dp.toPx()
                val baseAlpha = if (previewBoost) 0.34f else if (light) 0.11f else 0.14f
                val dotPrimary = primary.copy(alpha = baseAlpha)
                val dotSecondary = secondary.copy(alpha = baseAlpha * 0.8f)
                var row = 0
                var y = step * 0.5f
                while (y < h) {
                    val xOffset = if (row % 2 == 0) 0f else step * 0.5f
                    var x = step * 0.5f + xOffset
                    var col = 0
                    while (x < w) {
                        drawCircle(
                            color = if ((row + col) % 3 == 0) dotSecondary else dotPrimary,
                            radius = radius,
                            center = Offset(x, y)
                        )
                        x += step
                        col++
                    }
                    y += step
                    row++
                }
            }
            BousygramBackdropPattern.GEOMETRIC_GRID -> {
                drawRect(primaryGlow)
                drawRect(secondaryGlow)
                val pitch = if (previewBoost) 14.dp.toPx() else 44.dp.toPx()
                val lineAlpha = if (previewBoost) 0.26f else if (light) 0.075f else 0.095f
                val strokePx = if (previewBoost) 0.85.dp.toPx() else 0.9.dp.toPx()
                val lineColor = halo.haloStart.copy(alpha = lineAlpha)
                val crossColor = secondary.copy(alpha = (lineAlpha * 1.9f).coerceAtMost(0.45f))
                var x = pitch
                while (x < w) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, h), strokeWidth = strokePx)
                    x += pitch
                }
                var y = pitch
                while (y < h) {
                    drawLine(lineColor, Offset(0f, y), Offset(w, y), strokeWidth = strokePx)
                    y += pitch
                }
                // Subtle diamond accents at staggered intersections
                val d = if (previewBoost) 2.2.dp.toPx() else 3.2.dp.toPx()
                var gx = pitch
                var ix = 0
                while (gx < w) {
                    var gy = pitch
                    var iy = 0
                    while (gy < h) {
                        if ((ix + iy) % 2 == 0) {
                            drawLine(crossColor, Offset(gx - d, gy), Offset(gx + d, gy), strokeWidth = strokePx)
                            drawLine(crossColor, Offset(gx, gy - d), Offset(gx, gy + d), strokeWidth = strokePx)
                        }
                        gy += pitch
                        iy++
                    }
                    gx += pitch
                    ix++
                }
            }
            BousygramBackdropPattern.TOPO_WAVES -> {
                drawRect(primaryGlow)
                drawRect(secondaryGlow)
                drawRect(warmAccent)
                val strokeW = if (previewBoost) 1.6.dp.toPx() else 1.8.dp.toPx()
                val alpha = if (previewBoost) 0.44f else if (light) 0.14f else 0.18f
                wavePaths.forEachIndexed { idx, path ->
                    val waveColor = when (idx % 3) {
                        0 -> primary.copy(alpha = alpha)
                        1 -> secondary.copy(alpha = alpha * 0.9f)
                        else -> tertiary.copy(alpha = alpha * 0.85f)
                    }
                    drawPath(
                        path = path,
                        color = waveColor,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
            }
            BousygramBackdropPattern.IOS_MESH -> {
                meshBrushes.forEach { brush -> drawRect(brush) }
            }
            BousygramBackdropPattern.CUTESY_KAWAII -> {
                drawRect(primaryGlow)
                drawRect(secondaryGlow)
                drawRect(warmAccent)
                val motifAlpha = if (previewBoost) 0.46f else if (light) 0.14f else 0.17f
                val bubbleAlpha = if (previewBoost) 0.24f else if (light) 0.08f else 0.10f
                val heartColor = primary.copy(alpha = motifAlpha)
                val starColor = tertiary.copy(alpha = motifAlpha * 1.05f)
                val cloudColor = secondary.copy(alpha = motifAlpha * 0.92f)
                val bubbleColor = secondary.copy(alpha = bubbleAlpha)

                // Soft dreamy bubbles
                val bubbles = listOf(
                    Triple(0.14f, 0.24f, 0.09f),
                    Triple(0.86f, 0.22f, 0.11f),
                    Triple(0.74f, 0.78f, 0.08f),
                    Triple(0.20f, 0.80f, 0.10f),
                    Triple(0.50f, 0.50f, 0.07f)
                )
                bubbles.forEach { (fx, fy, fr) ->
                    val r = minOf(w, h) * fr
                    drawCircle(bubbleColor, radius = r, center = Offset(w * fx, h * fy))
                    drawCircle(
                        color = primary.copy(alpha = motifAlpha * 0.55f),
                        radius = r,
                        center = Offset(w * fx, h * fy),
                        style = Stroke(width = if (previewBoost) 1.dp.toPx() else 1.2.dp.toPx())
                    )
                }

                // Scattered puffy clouds
                val cloudScale = if (previewBoost) minOf(w, h) * 0.17f else 26.dp.toPx()
                drawCuteCloud(Offset(w * 0.22f, h * 0.16f), cloudScale, cloudColor)
                drawCuteCloud(Offset(w * 0.78f, h * 0.66f), cloudScale * 0.9f, cloudColor)
                if (!previewBoost) {
                    drawCuteCloud(Offset(w * 0.56f, h * 0.88f), cloudScale * 0.85f, cloudColor)
                }

                // Scattered kawaii hearts & 4-point sparkles
                val unit = if (previewBoost) minOf(w, h) * 0.16f else 18.dp.toPx()
                val heartSpots = if (previewBoost) {
                    listOf(Triple(0.76f, 0.30f, -14f), Triple(0.28f, 0.72f, 12f))
                } else {
                    listOf(
                        Triple(0.10f, 0.42f, -15f),
                        Triple(0.38f, 0.20f, 12f),
                        Triple(0.88f, 0.46f, -10f),
                        Triple(0.66f, 0.26f, 16f),
                        Triple(0.26f, 0.82f, -12f),
                        Triple(0.84f, 0.84f, 10f)
                    )
                }
                kawaiiHeartPath?.let { hp ->
                    heartSpots.forEach { (fx, fy, deg) ->
                        drawScaledMotif(hp, Offset(w * fx, h * fy), unit, deg, heartColor)
                    }
                }
                val sparkleSpots = if (previewBoost) {
                    listOf(Triple(0.50f, 0.44f, 0.85f), Triple(0.82f, 0.76f, 0.65f), Triple(0.18f, 0.44f, 0.60f))
                } else {
                    listOf(
                        Triple(0.18f, 0.26f, 0.95f),
                        Triple(0.52f, 0.14f, 0.75f),
                        Triple(0.82f, 0.18f, 1.05f),
                        Triple(0.46f, 0.54f, 0.80f),
                        Triple(0.14f, 0.68f, 0.70f),
                        Triple(0.70f, 0.58f, 0.90f),
                        Triple(0.92f, 0.72f, 0.75f),
                        Triple(0.40f, 0.86f, 0.85f)
                    )
                }
                kawaiiSparklePath?.let { sp ->
                    sparkleSpots.forEach { (fx, fy, sc) ->
                        drawScaledMotif(sp, Offset(w * fx, h * fy), unit * sc, 0f, starColor)
                    }
                }
            }
            BousygramBackdropPattern.CUTESY_PAWS -> {
                drawRect(primaryGlow)
                drawRect(secondaryGlow)
                drawRect(warmAccent)
                val pawAlpha = if (previewBoost) 0.44f else if (light) 0.12f else 0.15f
                val pawPrimary = primary.copy(alpha = pawAlpha)
                val pawSecondary = secondary.copy(alpha = pawAlpha * 0.92f)
                val pawWarm = tertiary.copy(alpha = pawAlpha * 0.95f)
                val pawSize = if (previewBoost) minOf(w, h) * 0.19f else 22.dp.toPx()

                val pawSpots = if (previewBoost) {
                    listOf(
                        Triple(0.26f, 0.34f, -18f),
                        Triple(0.72f, 0.62f, 16f),
                        Triple(0.70f, 0.24f, -8f)
                    )
                } else {
                    listOf(
                        Triple(0.10f, 0.20f, -20f),
                        Triple(0.26f, 0.44f, 14f),
                        Triple(0.44f, 0.18f, -12f),
                        Triple(0.64f, 0.36f, 18f),
                        Triple(0.86f, 0.22f, -16f),
                        Triple(0.14f, 0.74f, 12f),
                        Triple(0.38f, 0.78f, -18f),
                        Triple(0.60f, 0.70f, 15f),
                        Triple(0.84f, 0.80f, -14f),
                        Triple(0.92f, 0.50f, 20f)
                    )
                }
                pawSpots.forEachIndexed { idx, (fx, fy, deg) ->
                    val tint = when (idx % 3) {
                        0 -> pawPrimary
                        1 -> pawSecondary
                        else -> pawWarm
                    }
                    drawCutePawPrint(Offset(w * fx, h * fy), pawSize, deg, tint)
                }
                // Sprinkle tiny companion sparkles & hearts around the Chow paws
                kawaiiSparklePath?.let { sp ->
                    drawScaledMotif(sp, Offset(w * 0.50f, h * 0.48f), pawSize * 0.62f, 0f, pawWarm)
                    if (!previewBoost) {
                        drawScaledMotif(sp, Offset(w * 0.22f, h * 0.60f), pawSize * 0.55f, 0f, pawPrimary)
                        drawScaledMotif(sp, Offset(w * 0.76f, h * 0.46f), pawSize * 0.58f, 0f, pawSecondary)
                    }
                }
                kawaiiHeartPath?.let { hp ->
                    drawScaledMotif(hp, Offset(w * 0.24f, h * 0.76f), pawSize * 0.55f, -10f, pawPrimary)
                    if (!previewBoost) {
                        drawScaledMotif(hp, Offset(w * 0.74f, h * 0.18f), pawSize * 0.58f, 12f, pawPrimary)
                    }
                }
            }
        }
    }
}

/** Unit-centered heart path spanning roughly [-0.5..0.5]. */
private fun buildUnitHeartPath(): Path = Path().apply {
    moveTo(0f, 0.38f)
    cubicTo(-0.55f, 0.05f, -0.58f, -0.45f, -0.22f, -0.45f)
    cubicTo(-0.06f, -0.45f, 0f, -0.28f, 0f, -0.22f)
    cubicTo(0f, -0.28f, 0.06f, -0.45f, 0.22f, -0.45f)
    cubicTo(0.58f, -0.45f, 0.55f, 0.05f, 0f, 0.38f)
    close()
}

/** Unit-centered 4-point kawaii sparkle path spanning [-0.5..0.5]. */
private fun buildUnitSparklePath(): Path = Path().apply {
    moveTo(0f, -0.5f)
    quadraticTo(0.08f, -0.08f, 0.5f, 0f)
    quadraticTo(0.08f, 0.08f, 0f, 0.5f)
    quadraticTo(-0.08f, 0.08f, -0.5f, 0f)
    quadraticTo(-0.08f, -0.08f, 0f, -0.5f)
    close()
}

private fun DrawScope.drawScaledMotif(
    unitPath: Path,
    center: Offset,
    sizePx: Float,
    rotationDeg: Float,
    color: Color
) {
    withTransform({
        translate(left = center.x, top = center.y)
        rotate(degrees = rotationDeg, pivot = Offset.Zero)
        scale(scaleX = sizePx, scaleY = sizePx, pivot = Offset.Zero)
    }) {
        drawPath(path = unitPath, color = color)
    }
}

private fun DrawScope.drawCuteCloud(center: Offset, widthPx: Float, color: Color) {
    val r = widthPx * 0.26f
    drawCircle(color, radius = r * 0.92f, center = Offset(center.x - r * 1.05f, center.y + r * 0.18f))
    drawCircle(color, radius = r * 1.18f, center = Offset(center.x - r * 0.12f, center.y - r * 0.22f))
    drawCircle(color, radius = r * 0.96f, center = Offset(center.x + r * 0.88f, center.y + r * 0.10f))
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - r * 1.75f, center.y + r * 0.05f),
        size = Size(r * 3.45f, r * 1.05f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.52f, r * 0.52f)
    )
}

private fun DrawScope.drawCutePawPrint(
    center: Offset,
    sizePx: Float,
    rotationDeg: Float,
    color: Color
) {
    rotate(degrees = rotationDeg, pivot = center) {
        // Plump main pad
        drawOval(
            color = color,
            topLeft = Offset(center.x - sizePx * 0.34f, center.y - sizePx * 0.06f),
            size = Size(sizePx * 0.68f, sizePx * 0.52f)
        )
        // Four cute toe beans arranged in a gentle arc
        val toeW = sizePx * 0.22f
        val toeH = sizePx * 0.27f
        val toes = listOf(
            Offset(-sizePx * 0.38f, -sizePx * 0.14f),
            Offset(-sizePx * 0.14f, -sizePx * 0.36f),
            Offset(sizePx * 0.14f, -sizePx * 0.36f),
            Offset(sizePx * 0.38f, -sizePx * 0.14f)
        )
        toes.forEach { offset ->
            drawOval(
                color = color,
                topLeft = Offset(center.x + offset.x - toeW / 2f, center.y + offset.y - toeH / 2f),
                size = Size(toeW, toeH)
            )
        }
    }
}
