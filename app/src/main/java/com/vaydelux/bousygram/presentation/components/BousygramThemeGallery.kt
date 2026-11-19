package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.schemeFor

/** Adaptive color-preview gallery: identical cards, semantic states, and focus
 * behavior for phone, tablet, ARM32 TV and ARM64 TV. No nested lazy scrolling. */
@Composable
fun BousygramThemeGallery(
    selected: BousygramThemePreset,
    onSelect: (BousygramThemePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = rememberAdaptiveMetrics()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columns = (maxWidth / (180.dp * metrics.fontScale.coerceIn(1f, 2f))).toInt().coerceIn(1, 3)
        Column(
            modifier = Modifier.fillMaxWidth().testTag("bousygram-theme-gallery"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BousygramThemePreset.entries.chunked(columns).forEach { themes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                        }
                        TvFocusSurface(
                            onClick = { onSelect(preset) },
                            selected = isSelected,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = if (metrics.isTv) 128.dp else 106.dp)
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
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 32.dp)
                                            .background(preset.background, RoundedCornerShape(8.dp))
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) preset.primary
                                                    else preset.outline.copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            for (color in listOf(preset.primary, preset.secondary, preset.tertiary)) {
                                                Box(Modifier.size(12.dp).background(color, CircleShape))
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        previewScheme.surfaceContainerHigh,
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
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
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = preset.displayName,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                            style = if (metrics.isTv) MaterialTheme.typography.titleMedium
                                                else MaterialTheme.typography.labelLarge,
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
                                                size = 16.dp
                                            )
                                        }
                                    }
                                    Text(
                                        text = tagline,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = if (metrics.isTv) MaterialTheme.typography.bodySmall
                                            else MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    repeat(columns - themes.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
