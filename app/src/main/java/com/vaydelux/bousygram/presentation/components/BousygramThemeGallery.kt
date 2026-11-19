package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern
import com.vaydelux.bousygram.presentation.theme.BousygramCuratedLook
import com.vaydelux.bousygram.presentation.theme.BousygramCuratedLooks
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.BousygramUiTokens
import com.vaydelux.bousygram.presentation.theme.schemeFor

/**
 * One-tap curated aesthetic bundles combining Theme + Design Style + Backdrop Pattern.
 * Each bundle remains freely customizable below.
 */
@Composable
fun BousygramQuickLookBar(
    activeTheme: BousygramThemePreset,
    activeStyle: BousygramDesignStyle,
    activePattern: BousygramBackdropPattern,
    onApplyLook: (BousygramCuratedLook) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("bousygram-quick-looks"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BousygramCuratedLooks.forEach { look ->
            val isMatch = activeTheme == look.preset &&
                activeStyle == look.style &&
                activePattern == look.pattern
            val icon = when (look.id) {
                "material_you" -> BousygramIcons.Layers
                "iphone_cupertino", "iphone_midnight" -> BousygramIcons.Smartphone
                "cutesy_sakura" -> BousygramIcons.Flower
                "chow_paws" -> BousygramIcons.PawPrint
                else -> BousygramIcons.Wand
            }
            BousygramButton(
                text = look.title,
                selected = isMatch,
                leadingIcon = icon,
                tooltip = "${look.title} · ${look.subtitle}",
                onClick = { onApplyLook(look) },
                modifier = Modifier.testTag("quick-look-${look.id}")
            )
        }
    }
}

/**
 * Adaptive 4-style UI design language picker with miniature live wireframes
 * for Cinema Halo, Material You, iPhone Cupertino, and Cutesy Soft.
 */
@Composable
fun BousygramDesignStyleGallery(
    selected: BousygramDesignStyle,
    onSelect: (BousygramDesignStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = rememberAdaptiveMetrics()
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = (maxWidth / (164.dp * metrics.fontScale.coerceIn(1f, 2f))).toInt().coerceIn(2, 4)
        Column(
            modifier = Modifier.fillMaxWidth().testTag("bousygram-design-style-gallery"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BousygramDesignStyle.entries.chunked(columns).forEach { styles ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    styles.forEach { style ->
                        val isSelected = selected == style
                        val icon = when (style) {
                            BousygramDesignStyle.CINEMA_HALO -> BousygramIcons.Wand
                            BousygramDesignStyle.MATERIAL_EXPRESSIVE -> BousygramIcons.Layers
                            BousygramDesignStyle.IPHONE_GLASS -> BousygramIcons.Smartphone
                            BousygramDesignStyle.COZY_KAWAII -> BousygramIcons.Flower
                        }
                        TvFocusSurface(
                            onClick = { onSelect(style) },
                            selected = isSelected,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = if (metrics.isTv) 114.dp else 104.dp)
                                .testTag("design-style-${style.id}")
                        ) { focused ->
                            BousygramTooltipBox(
                                tooltip = style.displayName,
                                detail = style.description,
                                icon = icon,
                                focused = focused,
                                placement = BousygramTooltipPlacement.AUTO_ABOVE
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DesignStyleMiniMockup(style = style)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        BousygramIcon(
                                            icon = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) colors.onPrimaryContainer else colors.primary,
                                            size = 15.dp
                                        )
                                        Text(
                                            text = style.displayName,
                                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            BousygramIcon(
                                                icon = BousygramIcons.Check,
                                                contentDescription = "Selected",
                                                tint = colors.onPrimaryContainer,
                                                size = 15.dp
                                            )
                                        }
                                    }
                                    Text(
                                        text = style.shortTagline,
                                        color = if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.85f)
                                            else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - styles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun DesignStyleMiniMockup(style: BousygramDesignStyle) {
    val colors = MaterialTheme.colorScheme
    val outerCorner = when (style) {
        BousygramDesignStyle.CINEMA_HALO -> 8.dp
        BousygramDesignStyle.MATERIAL_EXPRESSIVE -> 12.dp
        BousygramDesignStyle.IPHONE_GLASS -> 11.dp
        BousygramDesignStyle.COZY_KAWAII -> 15.dp
    }
    val pillCorner = when (style) {
        BousygramDesignStyle.CINEMA_HALO -> 5.dp
        BousygramDesignStyle.MATERIAL_EXPRESSIVE -> BousygramUiTokens.pillCorner
        BousygramDesignStyle.IPHONE_GLASS -> 6.dp
        BousygramDesignStyle.COZY_KAWAII -> BousygramUiTokens.pillCorner
    }
    val borderColor = when (style) {
        BousygramDesignStyle.MATERIAL_EXPRESSIVE -> colors.surfaceContainerHighest
        BousygramDesignStyle.COZY_KAWAII -> colors.primary.copy(alpha = 0.45f)
        else -> colors.outlineVariant.copy(alpha = 0.70f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(outerCorner))
            .background(colors.background.copy(alpha = 0.85f))
            .border(
                width = if (style == BousygramDesignStyle.COZY_KAWAII) 1.5.dp else 0.75.dp,
                color = borderColor,
                shape = RoundedCornerShape(outerCorner)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(pillCorner))
                    .background(colors.primary.copy(alpha = 0.75f))
            )
            Box(
                Modifier
                    .width(22.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(pillCorner))
                    .background(colors.secondary.copy(alpha = 0.55f))
            )
        }
        // Bottom chrome signature: floating capsule (iPhone/Cutesy) vs tonal bar (Material/Halo)
        Box(
            modifier = Modifier
                .fillMaxWidth(if (style == BousygramDesignStyle.IPHONE_GLASS || style == BousygramDesignStyle.COZY_KAWAII) 0.72f else 1f)
                .height(8.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(if (style == BousygramDesignStyle.IPHONE_GLASS || style == BousygramDesignStyle.COZY_KAWAII) BousygramUiTokens.pillCorner else 4.dp))
                .background(colors.surfaceContainerHighest)
        )
    }
}

/**
 * Live Canvas preview gallery for all 7 selectable patterned, iOS mesh, and
 * cutesy backgrounds. Uses drawWithCache with zero bitmaps or timers.
 */
@Composable
fun BousygramBackdropGallery(
    selected: BousygramBackdropPattern,
    onSelect: (BousygramBackdropPattern) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = rememberAdaptiveMetrics()
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = (maxWidth / (132.dp * metrics.fontScale.coerceIn(1f, 2f))).toInt().coerceIn(2, 4)
        Column(
            modifier = Modifier.fillMaxWidth().testTag("bousygram-backdrop-gallery"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BousygramBackdropPattern.entries.chunked(columns).forEach { patterns ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    patterns.forEach { pattern ->
                        val isSelected = selected == pattern
                        val icon = when (pattern) {
                            BousygramBackdropPattern.AMBIENT_HALO -> BousygramIcons.Theme
                            BousygramBackdropPattern.DOT_MATRIX,
                            BousygramBackdropPattern.GEOMETRIC_GRID -> BousygramIcons.GridPattern
                            BousygramBackdropPattern.TOPO_WAVES -> BousygramIcons.Waves
                            BousygramBackdropPattern.IOS_MESH -> BousygramIcons.Smartphone
                            BousygramBackdropPattern.CUTESY_KAWAII -> BousygramIcons.Flower
                            BousygramBackdropPattern.CUTESY_PAWS -> BousygramIcons.PawPrint
                        }
                        TvFocusSurface(
                            onClick = { onSelect(pattern) },
                            selected = isSelected,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = if (metrics.isTv) 102.dp else 94.dp)
                                .testTag("backdrop-pattern-${pattern.id}")
                        ) { focused ->
                            BousygramTooltipBox(
                                tooltip = pattern.displayName,
                                detail = pattern.shortTagline,
                                icon = icon,
                                focused = focused,
                                placement = BousygramTooltipPlacement.AUTO_ABOVE
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(9.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BousygramBackdropPreviewCanvas(
                                        pattern = pattern,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) colors.primary else colors.outlineVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = pattern.displayName,
                                            color = if (isSelected) colors.onPrimaryContainer else colors.onSurface,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            BousygramIcon(
                                                icon = BousygramIcons.Check,
                                                contentDescription = "Selected",
                                                tint = colors.onPrimaryContainer,
                                                size = 14.dp
                                            )
                                        }
                                    }
                                    Text(
                                        text = pattern.shortTagline,
                                        color = if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.85f)
                                            else colors.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - patterns.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Adaptive 16-theme color-preview gallery: identical cards, semantic states,
 * and focus behavior for phone, tablet, ARM32 TV and ARM64 TV. Compact grid
 * density ensures all 16 theme cards remain reachable without nested scrolling.
 */
@Composable
fun BousygramThemeGallery(
    selected: BousygramThemePreset,
    onSelect: (BousygramThemePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = rememberAdaptiveMetrics()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = (maxWidth / (108.dp * metrics.fontScale.coerceIn(1f, 2f))).toInt().coerceIn(2, 4)
        val compactCard = columns >= 3 && !metrics.isTv
        Column(
            modifier = Modifier.fillMaxWidth().testTag("bousygram-theme-gallery"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BousygramThemePreset.entries.chunked(columns).forEach { themes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themes.forEach { preset ->
                        val isSelected = selected == preset
                        val previewScheme = remember(preset) { schemeFor(preset) }
                        val tagline = when (preset) {
                            BousygramThemePreset.DARK -> "Cyan · Navy"
                            BousygramThemePreset.AURORA -> "Violet · Teal"
                            BousygramThemePreset.OBSIDIAN -> "Silver · Graphite"
                            BousygramThemePreset.EVERGREEN -> "Mint · Forest"
                            BousygramThemePreset.EMBER -> "Peach · Rose"
                            BousygramThemePreset.HIGH_CONTRAST -> "High visibility"
                            BousygramThemePreset.LIGHT -> "Blue · Ice"
                            BousygramThemePreset.OCEAN -> "Cyan · Marine"
                            BousygramThemePreset.PLUM -> "Rose · Lavender"
                            BousygramThemePreset.IVORY -> "Warm · Light"
                            BousygramThemePreset.SAKURA -> "Cutesy · Pink"
                            BousygramThemePreset.MATCHA -> "Pastel · Sage"
                            BousygramThemePreset.COTTON_CANDY -> "Kawaii · Night"
                            BousygramThemePreset.MATERIAL_YOU -> "M3 · Tonal"
                            BousygramThemePreset.CUPERTINO -> "iOS · Light"
                            BousygramThemePreset.MIDNIGHT_GLASS -> "iOS · OLED"
                        }
                        TvFocusSurface(
                            onClick = { onSelect(preset) },
                            selected = isSelected,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = if (metrics.isTv) 76.dp else 58.dp)
                                .testTag("theme-preset-" + preset.id)
                        ) { focused ->
                            BousygramTooltipBox(
                                tooltip = preset.displayName,
                                detail = if (isSelected) "Active" else tagline,
                                icon = BousygramIcons.Theme,
                                focused = focused,
                                placement = BousygramTooltipPlacement.AUTO_ABOVE
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = if (compactCard) 8.dp else 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 22.dp)
                                            .background(preset.background, RoundedCornerShape(7.dp))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) preset.primary
                                                    else preset.outline.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(7.dp)
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            for (color in listOf(preset.primary, preset.secondary, preset.tertiary)) {
                                                Box(Modifier.size(9.dp).background(color, CircleShape))
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        previewScheme.surfaceContainerHigh,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "Aa",
                                                    color = previewScheme.onSurface,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = preset.displayName,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                            style = if (metrics.isTv) MaterialTheme.typography.titleSmall
                                                else MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isSelected || focused) FontWeight.Bold
                                                else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isSelected) {
                                            BousygramIcon(
                                                icon = BousygramIcons.Check,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                size = 14.dp
                                            )
                                        }
                                    }
                                    if (!compactCard) {
                                        Text(
                                            text = tagline,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat(columns - themes.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
