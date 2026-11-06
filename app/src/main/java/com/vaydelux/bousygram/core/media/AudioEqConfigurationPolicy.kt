package com.vaydelux.bousygram.core.media

/**
 * Value that defines actual Equalizer DSP parameters. Layout changes and
 * Smart Volume selection are presentation/other-effect state, not new EQ data.
 * Only an edited Custom curve or changed preset requires writing EQ bands.
 */
internal data class AudioEqConfiguration(
    val preset: AudioEqualizerPreset,
    val customGainsDb: List<Float>
)

internal fun audioEqConfiguration(profile: AudioProcessingProfile): AudioEqConfiguration =
    AudioEqConfiguration(
        preset = profile.preset,
        customGainsDb = if (profile.preset == AudioEqualizerPreset.CUSTOM)
            profile.sanitizedGains else emptyList()
    )
