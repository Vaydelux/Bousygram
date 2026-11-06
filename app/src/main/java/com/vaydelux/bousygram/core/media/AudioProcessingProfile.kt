package com.vaydelux.bousygram.core.media

import kotlin.math.ln

/**
 * In-playback only: tonal EQ and optional dynamic-range modes.
 * Normalization is not an offline EBU R128 / LUFS scan.
 */
enum class SmartVolumeMode(val title: String, val detail: String) {
    OFF("Off", "Original dynamic range"),
    NIGHT("Night Comfort", "Lower loud peaks and soften action scenes"),
    DIALOGUE_BOOST("Dialogue Boost", "Compress loud peaks while emphasizing speech"),
    AUTO_LEVEL("Smart Leveling", "Adaptive loudness where the decoder supports it");

    companion object {
        fun fromStored(raw: String?): SmartVolumeMode =
            entries.firstOrNull { it.name == raw } ?: OFF
    }
}

enum class CustomEqualizerLayout(val label: String, val bandIndices: List<Int>) {
    FIVE("5 bands", listOf(0, 2, 4, 6, 9)),
    TEN("10 bands", (0 until 10).toList())
}

data class AudioProcessingProfile(
    val preset: AudioEqualizerPreset = AudioEqualizerPreset.ORIGINAL,
    val customGainsDb: List<Float> = List(10) { 0f },
    val customLayout: CustomEqualizerLayout = CustomEqualizerLayout.FIVE,
    val smartVolume: SmartVolumeMode = SmartVolumeMode.OFF
) {
    val sanitizedGains: List<Float> get() = CUSTOM_FREQUENCIES_HZ.indices.map { index ->
        customGainsDb.getOrNull(index)?.takeIf { it.isFinite() }?.coerceIn(-6f, 6f) ?: 0f
    }

    fun editedBand(index: Int, gainDb: Float): AudioProcessingProfile {
        if (index !in CUSTOM_FREQUENCIES_HZ.indices || !gainDb.isFinite()) return this
        val values = sanitizedGains.toMutableList()
        values[index] = gainDb.coerceIn(-6f, 6f)
        return copy(preset = AudioEqualizerPreset.CUSTOM, customGainsDb = values)
    }

    fun resetCustom(): AudioProcessingProfile =
        copy(preset = AudioEqualizerPreset.CUSTOM, customGainsDb = List(10) { 0f })

    fun gainDbAt(frequencyHz: Float): Float {
        if (preset != AudioEqualizerPreset.CUSTOM) return preset.gainDbAt(frequencyHz)
        if (!frequencyHz.isFinite() || frequencyHz <= 0f) return 0f
        val bands = CUSTOM_FREQUENCIES_HZ
        val gains = sanitizedGains
        if (frequencyHz <= bands.first()) return gains.first()
        if (frequencyHz >= bands.last()) return gains.last()
        val upper = bands.indexOfFirst { it >= frequencyHz }
        val lower = upper - 1
        val fraction = ((ln(frequencyHz.toDouble()) - ln(bands[lower].toDouble())) /
            (ln(bands[upper].toDouble()) - ln(bands[lower].toDouble()))).toFloat()
        return (gains[lower] + (gains[upper] - gains[lower]) * fraction).coerceIn(-6f, 6f)
    }

    /** All positive boosts reserve equal negative preamp headroom on supported engines. */
    val preampDb: Float get() = if (preset == AudioEqualizerPreset.CUSTOM) {
        -sanitizedGains.maxOrNull()!!.coerceAtLeast(0f)
    } else preset.preampDb

    companion object {
        val CUSTOM_FREQUENCIES_HZ = floatArrayOf(
            60f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 12_000f, 16_000f
        )
    }
}
