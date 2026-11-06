package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaydelux.bousygram.core.media.AudioEffectStatus
import com.vaydelux.bousygram.core.media.AudioEqualizerPreset
import com.vaydelux.bousygram.core.media.AudioProcessingProfile
import com.vaydelux.bousygram.core.media.CustomEqualizerLayout
import com.vaydelux.bousygram.core.media.PlayerEngine
import com.vaydelux.bousygram.core.media.SmartVolumeMode
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import java.util.Locale

/** One focusable 10-foot audio pane; avoids a 14-item vertical preset list on TVs. */
@Composable
internal fun AudioProcessingControls(
    engine: PlayerEngine,
    profile: AudioProcessingProfile,
    castConnected: Boolean,
    firstPresetFocus: FocusRequester,
    onChange: (AudioProcessingProfile) -> Unit
) {
    val eqStatus by engine.audioEqualizerStatus.collectAsStateWithLifecycle()
    val volumeStatus by engine.smartVolumeStatus.collectAsStateWithLifecycle()
    val eqLabel = if (castConnected) "Bypassed · Cast receiver audio" else eqStatus.displayLabel
    val volumeLabel = if (castConnected) "Bypassed · Cast receiver audio" else volumeStatus.displayLabel
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sound profiles", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold)
        Text(
            eqLabel,
            color = if (!castConnected && eqStatus == AudioEffectStatus.UNSUPPORTED)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(AudioEqualizerPreset.entries) { preset ->
                val modifier = if (preset == AudioEqualizerPreset.ORIGINAL) {
                    Modifier.focusRequester(firstPresetFocus)
                } else Modifier
                BousygramChip(
                    text = preset.title,
                    selected = profile.preset == preset,
                    modifier = modifier,
                    onClick = { onChange(profile.copy(preset = preset)) }
                )
            }
        }
        Text(profile.preset.detail, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (profile.preset == AudioEqualizerPreset.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CustomEqualizerLayout.entries.forEach { layout ->
                    BousygramChip(layout.label, profile.customLayout == layout,
                        { onChange(profile.copy(customLayout = layout)) })
                }
                BousygramChip("Reset", false, { onChange(profile.resetCustom()) })
            }
            profile.customLayout.bandIndices.forEach { index ->
                val hz = AudioProcessingProfile.CUSTOM_FREQUENCIES_HZ[index]
                val value = profile.sanitizedGains[index]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (hz >= 1_000f) "${(hz / 1_000).toInt()}k" else "${hz.toInt()}",
                        modifier = Modifier.width(42.dp), color = MaterialTheme.colorScheme.onSurface)
                    BousygramButton("−", {
                        onChange(profile.editedBand(index, value - 1f))
                    })
                    Spacer(Modifier.weight(1f))
                    Text(String.format(Locale.US, "%.1f", value),
                        modifier = Modifier.width(40.dp),
                        color = MaterialTheme.colorScheme.onSurface)
                    BousygramButton("+", {
                        onChange(profile.editedBand(index, value + 1f))
                    })
                }
            }
        }
        Text("Smart Volume / Dialogue Boost", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold)
        Text(volumeLabel,
            color = if (!castConnected && volumeStatus == AudioEffectStatus.UNSUPPORTED)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SmartVolumeMode.entries) { mode ->
                BousygramChip(
                    text = mode.title,
                    selected = profile.smartVolume == mode,
                    onClick = { onChange(profile.copy(smartVolume = mode)) }
                )
            }
        }
        Text(profile.smartVolume.detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        Text(
            "EQ and Smart Volume process only local audio. Native routes, passthrough and Cast may bypass either effect.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
