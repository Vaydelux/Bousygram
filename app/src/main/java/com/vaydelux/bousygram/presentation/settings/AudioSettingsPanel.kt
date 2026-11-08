package com.vaydelux.bousygram.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.core.media.SmartVolumeMode
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.presentation.components.BousygramChip
import com.vaydelux.bousygram.presentation.components.EnterprisePanel

/** Only optional dynamic-range features remain. Tonal equalizer is retired. */
@Composable
internal fun AudioSettingsPanel(isTv: Boolean) {
    val profile by EnterprisePreferences.audioProcessingProfileFlow.collectAsState()
    EnterprisePanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(if (isTv) 24.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Smart Volume / Dialogue Boost",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Optional dialogue and loudness processing for local playback.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SmartVolumeMode.entries) { mode ->
                    BousygramChip(
                        text = mode.title,
                        selected = profile.smartVolume == mode,
                        onClick = {
                            EnterprisePreferences.setAudioProcessingProfile(profile.copy(smartVolume = mode))
                        }
                    )
                }
            }
            Text(
                profile.smartVolume.detail +
                    ". Availability depends on the player and audio output.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
