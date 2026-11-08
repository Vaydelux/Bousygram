package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaydelux.bousygram.core.media.AudioEffectStatus
import com.vaydelux.bousygram.core.media.AudioProcessingProfile
import com.vaydelux.bousygram.core.media.PlayerEngine
import com.vaydelux.bousygram.core.media.SmartVolumeMode
import com.vaydelux.bousygram.presentation.components.BousygramChip

/** The equalizer no longer appears in playback. Smart Volume remains optional. */
@Composable
internal fun AudioProcessingControls(
    engine: PlayerEngine,
    profile: AudioProcessingProfile,
    castConnected: Boolean,
    firstPresetFocus: FocusRequester,
    onChange: (AudioProcessingProfile) -> Unit
) {
    val status by engine.smartVolumeStatus.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Smart Volume / Dialogue Boost",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            if (castConnected) "Bypassed · Cast receiver audio" else status.displayLabel,
            color = if (!castConnected && status == AudioEffectStatus.UNSUPPORTED)
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(SmartVolumeMode.entries) { mode ->
                BousygramChip(
                    text = mode.title,
                    selected = profile.smartVolume == mode,
                    modifier = if (mode == SmartVolumeMode.OFF)
                        Modifier.focusRequester(firstPresetFocus) else Modifier,
                    onClick = { onChange(profile.copy(smartVolume = mode)) }
                )
            }
        }
        Text(
            profile.smartVolume.detail + ". Passthrough and some native players may bypass this processing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
