package com.vaydelux.bousygram.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.core.media.AudioEqualizerPreset
import com.vaydelux.bousygram.core.media.AudioProcessingProfile
import com.vaydelux.bousygram.core.media.CustomEqualizerLayout
import com.vaydelux.bousygram.core.media.SmartVolumeMode
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.presentation.components.BousygramButton
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.EnterprisePanel
import java.util.Locale

/**
 * One scrollable settings section; horizontal preset rows and +/- buttons preserve
 * TV D-pad accessibility while touch devices also get continuous band sliders.
 */
@Composable
internal fun AudioSettingsPanel(isTv: Boolean) {
    val profile by EnterprisePreferences.audioProcessingProfileFlow.collectAsState()
    val apply = { next: AudioProcessingProfile ->
        EnterprisePreferences.setAudioProcessingProfile(next)
    }
    EnterprisePanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Audio equalizer", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Text(
                "Live local playback only; original Telegram files are unchanged. Effects depend on the audio output.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AudioEqualizerPreset.entries) { preset ->
                    BousygramChip(
                        text = preset.title,
                        selected = profile.preset == preset,
                        onClick = { apply(profile.copy(preset = preset)) }
                    )
                }
            }
            Text(profile.preset.detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (profile.preset == AudioEqualizerPreset.CUSTOM) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CustomEqualizerLayout.entries.forEach { layout ->
                        BousygramChip(
                            text = layout.label,
                            selected = profile.customLayout == layout,
                            onClick = { apply(profile.copy(customLayout = layout)) }
                        )
                    }
                    BousygramChip(
                        text = "Reset Flat",
                        selected = false,
                        onClick = { apply(profile.resetCustom()) }
                    )
                }
                profile.customLayout.bandIndices.forEach { index ->
                    val hz = AudioProcessingProfile.CUSTOM_FREQUENCIES_HZ[index]
                    val gain = profile.sanitizedGains[index]
                    val label = if (hz >= 1_000) "${(hz / 1_000).toInt()}k"
                        else "${hz.toInt()}"
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(label, modifier = Modifier.width(46.dp),
                            color = MaterialTheme.colorScheme.onSurface)
                        BousygramButton("−", {
                            apply(profile.editedBand(index, gain - 1f))
                        })
                        if (!isTv) {
                            Slider(
                                value = gain,
                                onValueChange = { apply(profile.editedBand(index, it)) },
                                valueRange = -6f..6f,
                                steps = 11,
                                modifier = Modifier.weight(1f)
                            )
                        } else Spacer(Modifier.weight(1f))
                        Text(String.format(Locale.US, "%.1f dB", gain),
                            modifier = Modifier.width(65.dp),
                            color = MaterialTheme.colorScheme.onSurface)
                        BousygramButton("+", {
                            apply(profile.editedBand(index, gain + 1f))
                        })
                    }
                }
            }

            Text("Smart Volume / Dialogue Boost",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SmartVolumeMode.entries) { mode ->
                    BousygramChip(
                        text = mode.title,
                        selected = profile.smartVolume == mode,
                        onClick = { apply(profile.copy(smartVolume = mode)) }
                    )
                }
            }
            Text(
                profile.smartVolume.detail +
                    ". Decoder compression support varies: the player shows Applied, Bypassed or Unsupported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
