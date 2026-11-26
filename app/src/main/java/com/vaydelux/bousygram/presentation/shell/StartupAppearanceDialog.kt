package com.vaydelux.bousygram.presentation.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramBackdropGallery
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramDesignStyleGallery
import com.vaydelux.bousygram.presentation.components.BousygramGlassLevel
import com.vaydelux.bousygram.presentation.components.BousygramIosAppearanceControls
import com.vaydelux.bousygram.presentation.components.BousygramQuickLookBar
import com.vaydelux.bousygram.presentation.components.BousygramThemeGallery
import com.vaydelux.bousygram.presentation.components.LiquidGlassSurface
import com.vaydelux.bousygram.presentation.theme.BousygramBackdropPattern
import com.vaydelux.bousygram.presentation.theme.BousygramDesignStyle
import com.vaydelux.bousygram.presentation.theme.BousygramThemePreset
import com.vaydelux.bousygram.presentation.theme.LocalBousygramAppearanceController
import com.vaydelux.bousygram.presentation.theme.LocalBousygramReduceTransparency

/**
 * The single pre-login Appearance Studio. Uses exactly the same galleries
 * and live theme provider as Settings, Telegram, Extras and playback.
 * Opening it never touches Telegram runtime or network services.
 */
@Composable
internal fun StartupAppearanceDialog(
    themePreset: BousygramThemePreset,
    designStyle: BousygramDesignStyle,
    backdropPattern: BousygramBackdropPattern,
    onThemePresetChange: (BousygramThemePreset) -> Unit,
    onDesignStyleChange: (BousygramDesignStyle) -> Unit,
    onBackdropPatternChange: (BousygramBackdropPattern) -> Unit,
    onDismiss: () -> Unit
) {
    val metrics = rememberAdaptiveMetrics()
    val controller = LocalBousygramAppearanceController.current
    val initialReduce = LocalBousygramReduceTransparency.current
    var localReduceTransparency by remember(initialReduce) { mutableStateOf(initialReduce) }
    val activeReduceTransparency = controller?.reduceTransparency ?: localReduceTransparency
    val maxHeight = (metrics.heightDp * 0.88f).coerceAtLeast(200.dp)
    Dialog(onDismissRequest = onDismiss) {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 680.dp)
                .heightIn(max = maxHeight)
                .testTag("startup-appearance-studio"),
            level = BousygramGlassLevel.POPOVER,
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
                    .padding(if (metrics.isShortWindow) 12.dp else 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Appearance Studio",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "iOS 27 Liquid Glass · One look across Telegram, Extras and players",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    BousygramButton(text = "Done", onClick = onDismiss)
                }
                BousygramIosAppearanceControls(
                    activePreset = themePreset,
                    reduceTransparency = activeReduceTransparency,
                    onSelectPreset = onThemePresetChange,
                    onReduceTransparencyChange = { enabled ->
                        localReduceTransparency = enabled
                        controller?.onReduceTransparencyChange?.invoke(enabled)
                    }
                )
                Text("Quick looks", style = MaterialTheme.typography.titleMedium)
                BousygramQuickLookBar(
                    activeTheme = themePreset,
                    activeStyle = designStyle,
                    activePattern = backdropPattern,
                    onApplyLook = { look ->
                        EnterprisePreferences.setVisualAppearance(
                            look.preset.id, look.style.id, look.pattern.id
                        )
                        onThemePresetChange(look.preset)
                        onDesignStyleChange(look.style)
                        onBackdropPatternChange(look.pattern)
                    }
                )
                Text("Color theme", style = MaterialTheme.typography.titleMedium)
                BousygramThemeGallery(
                    selected = themePreset,
                    onSelect = onThemePresetChange
                )
                Text("Design language", style = MaterialTheme.typography.titleMedium)
                BousygramDesignStyleGallery(
                    selected = designStyle,
                    onSelect = onDesignStyleChange
                )
                Text("Background pattern", style = MaterialTheme.typography.titleMedium)
                BousygramBackdropGallery(
                    selected = backdropPattern,
                    onSelect = onBackdropPatternChange
                )
                Text(
                    "Changes are saved automatically and apply to both experiences.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
