package com.vaydelux.bousygram.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Playback surfaces sit over arbitrary dark or bright video frames. Always
 * derive the progress colors from the selected theme, but lift light-theme
 * foregrounds enough to remain visible on a dark video scrim.
 *
 * Shared by Telegram player chrome, Extras native playback and progress HUDs.
 */
@Immutable
data class BousygramPlaybackPalette(
    val played: Color,
    val playedEnd: Color,
    val buffered: Color,
    val unplayed: Color,
    val focusRing: Color,
    val thumb: Color
)

@Composable
fun rememberBousygramPlaybackPalette(): BousygramPlaybackPalette {
    val scheme = MaterialTheme.colorScheme
    val halo = LocalBousygramHaloColors.current
    return remember(scheme, halo) {
        // Avoid hard-coded cyan when Sakura, Matcha, Cupertino or Material You
        // is active. Video scrims provide the background contrast.
        val played = if (halo.isLight) lerp(scheme.primary, Color.White, 0.42f)
            else halo.haloStart
        val playedEnd = if (halo.isLight) lerp(scheme.secondary, Color.White, 0.36f)
            else halo.haloEnd
        BousygramPlaybackPalette(
            played = played,
            playedEnd = playedEnd,
            // Light schemes place the transport on a pale surface container;
            // white tracks disappear there. Use on-surface ink in that case.
            buffered = if (halo.isLight) scheme.primary.copy(alpha = 0.52f)
                else Color.White.copy(alpha = if (halo.isHighContrast) 0.70f else 0.48f),
            unplayed = if (halo.isLight) scheme.onSurface.copy(alpha = 0.38f)
                else Color.White.copy(alpha = if (halo.isHighContrast) 0.38f else 0.24f),
            focusRing = if (halo.isLight) scheme.primary else halo.focusRing,
            thumb = if (halo.isLight) scheme.primary else Color.White
        )
    }
}
