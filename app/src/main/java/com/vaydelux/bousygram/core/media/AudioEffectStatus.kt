package com.vaydelux.bousygram.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A preference is not proof that a real native audio effect is in use. */
enum class AudioEffectStatus(val displayLabel: String) {
    BYPASSED("Original · not processing"),
    PENDING("Waiting for audio output"),
    APPLIED("Applied to local audio"),
    UNSUPPORTED("Unavailable on current audio route");

    companion object {
        val DEFAULT: StateFlow<AudioEffectStatus> = MutableStateFlow(BYPASSED)
    }
}

/** mpv serializes its requested audio filters via the 'af' property. */
internal enum class FilterVerification { PRESENT, ABSENT, UNVERIFIABLE }

/**
 * The native read-back is authoritative when it confirms absence. A previous
 * add attempt can have partially succeeded even if the command raised, so an
 * unreadable state must still trigger a best-effort removal.
 */
internal fun mayNeedMpvFilterRemoval(
    verification: FilterVerification,
    possiblyInstalled: Boolean
): Boolean =
    verification == FilterVerification.PRESENT ||
        (verification == FilterVerification.UNVERIFIABLE && possiblyInstalled)

internal fun verifyNamedMpvAudioFilter(filters: String?, label: String): FilterVerification {
    if (filters == null) return FilterVerification.UNVERIFIABLE
    // Match the whole named label, not a prefix of a different filter.
    val token = "@$label:"
    return if (filters.contains(token)) FilterVerification.PRESENT else FilterVerification.ABSENT
}

/**
 * AudioFX's graphic EQ offers no separate preamp. Apply the corresponding
 * negative headroom adjustment to each native band to prevent positive boosts.
 * This trades a little output level for reduced clipping risk.
 */
internal fun safeAudioFxBandMillibels(
    preset: AudioEqualizerPreset,
    frequencyHz: Float,
    minimum: Int,
    maximum: Int
): Short {
    val low = minOf(minimum, maximum).coerceAtLeast(Short.MIN_VALUE.toInt())
    val high = maxOf(minimum, maximum).coerceAtMost(Short.MAX_VALUE.toInt())
    val withHeadroomDb = preset.gainDbAt(frequencyHz) + preset.preampDb
    return (withHeadroomDb * 100f).toInt().coerceIn(low, high).toShort()
}

internal fun safeAudioFxBandMillibels(
    profile: AudioProcessingProfile, frequencyHz: Float, minimum: Int, maximum: Int
): Short {
    val low = minOf(minimum, maximum).coerceAtLeast(Short.MIN_VALUE.toInt())
    val high = maxOf(minimum, maximum).coerceAtMost(Short.MAX_VALUE.toInt())
    val withHeadroomDb = profile.gainDbAt(frequencyHz) + profile.preampDb
    return (withHeadroomDb * 100f).toInt().coerceIn(low, high).toShort()
}
