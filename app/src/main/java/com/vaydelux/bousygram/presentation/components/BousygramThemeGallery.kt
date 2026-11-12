package com.vaydelux.bousygram.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset

/** Adaptive color-preview gallery: identical cards, semantic states, and focus
 * behavior for phone, tablet, ARM32 TV and ARM64 TV. No nested lazy scrolling. */
@Composable
fun BousygramThemeGallery(
    selected: BousygramThemePreset,
    onSelect: (BousygramThemePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = rememberAdaptiveMetrics()
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
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
                    val tagline = when (preset) {
                        BousygramThemePreset.DARK -> "Cyan · Navy"
                        BousygramThemePreset.AURORA -> "Violet · Teal"
                        BousygramThemePreset.OBSIDIAN -> "Silver · Graphite"
                        BousygramThemePreset.EVERGREEN -> "Mint · Forest"
                        BousygramThemePreset.EMBER -> "Peach · Rose"
                        BousygramThemePreset.HIGH_CONTRAST -> "High visibility"
                        BousygramThemePreset.LIGHT -> "Blue · Ice"
                    }
                    TvFocusSurface(
                        onClick = { onSelect(preset) },
                        selected = isSelected,
                        modifier = Modifier.weight(1f)
                            .heightIn(min = if (metrics.isTv) 124.dp else 100.dp)
                            .testTag("theme-preset-" + preset.id)
                    ) { focused ->
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(22.dp)
                                    .background(preset.background, RoundedCornerShape(8.dp))
                                    .border(1.dp, preset.outline.copy(alpha = 0.65f),
                                        RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (color in listOf(preset.primary, preset.secondary, preset.tertiary)) {
                                        Box(Modifier.size(12.dp).background(color, CircleShape))
                                    }
                                }
                            }
                            Text(
                                // Selection is already announced via semantics and
                                // focus styling; avoid clipping titles such as High contrast.
                                text = preset.displayName,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = if (metrics.isTv) MaterialTheme.typography.titleMedium
                                    else MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected || focused) FontWeight.Bold
                                    else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tagline,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = if (metrics.isTv) MaterialTheme.typography.bodyMedium
                                    else MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
