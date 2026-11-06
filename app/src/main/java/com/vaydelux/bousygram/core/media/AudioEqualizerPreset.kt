package com.vaydelux.bousygram.core.media

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Conservative tonal presets for locally decoded Telegram streams.
 * Frequencies are 60, 170, 400, 1k, 2.4k, 6k and 12k Hz.
 * This changes sound during playback only; it never changes the source or TDLib bytes.
 * Bounded boosts protect headroom. "Night" is tonal only, NOT loudness compression.
 */
enum class AudioEqualizerPreset(
    val title: String,
    val detail: String,
    private val gainBandsDb: List<Float>
) {
    ORIGINAL("Original", "No processing", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    CINEMA("Cinema", "Warm bass and spacious detail", listOf(1.5f, 1f, -1f, 0f, 1f, 1.5f, 0.5f)),
    DIALOGUE("Dialogue", "Clearer speech and less rumble", listOf(-3f, -2f, -1f, 1.5f, 2.5f, 1.2f, -1f)),
    MUSIC("Music", "Balanced, detailed listening", listOf(1f, 1.5f, 0f, 0f, 1f, 1f, 0.5f)),
    BASS_BOOST("Deep Bass", "Added low-end weight", listOf(3f, 2f, 1f, 0f, -0.5f, 0f, 0f)),
    NIGHT("Night", "Less booming bass, easier dialogue", listOf(-2.5f, -1.5f, 0f, 1.5f, 1f, 0f, -1f)),
    VOCAL("Vocal Focus", "Bring voices forward", listOf(-2f, -1f, 0f, 2f, 2f, 1f, -1f)),
    ROCK("Rock", "Punch and guitar presence", listOf(2f, 1f, 0f, 0.5f, 1.5f, 2f, 1f)),
    POP("Pop", "Bright, energetic tone", listOf(1.2f, 1f, 0f, 0.7f, 1.5f, 1.2f, 1f)),
    JAZZ("Jazz", "Natural warmth and shimmer", listOf(1f, 0.5f, 0f, 0.7f, 1f, 1.5f, 0.5f)),
    CLASSICAL("Classical", "Detailed, restrained lows", listOf(-0.5f, -0.2f, 0f, 0.6f, 0.8f, 1.4f, 1f)),
    GAMING("Gaming", "Effects and spatial cues", listOf(1.5f, 1f, -0.5f, 0.7f, 1.2f, 2f, 0.8f)),
    LIVE("Live", "Stage presence and clarity", listOf(0f, 0f, 0.5f, 1.2f, 1.7f, 1.5f, 0f)),
    PODCAST("Podcast", "Speech-focused casual listening", listOf(-3f, -1.5f, 0f, 1.5f, 2f, 0.5f, -1f)),
    CUSTOM("Custom", "Your own adjustable 5/10-band equalizer", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f));

    val isEnabled: Boolean get() = this != ORIGINAL

    /** Interpolate in logarithmic frequency space for varying native EQ band layouts. */
    fun gainDbAt(frequencyHz: Float): Float {
        if (!isEnabled || !frequencyHz.isFinite() || frequencyHz <= 0f) return 0f
        val bands = FREQUENCIES_HZ
        if (frequencyHz <= bands.first()) return gainBandsDb.first()
        if (frequencyHz >= bands.last()) return gainBandsDb.last()
        val upper = bands.indexOfFirst { it >= frequencyHz }
        val lower = upper - 1
        val ratio = (ln(frequencyHz.toDouble()) - ln(bands[lower].toDouble())) /
            (ln(bands[upper].toDouble()) - ln(bands[lower].toDouble()))
        return (gainBandsDb[lower] + (gainBandsDb[upper] - gainBandsDb[lower]) * ratio.toFloat())
            .coerceIn(-4f, 3f)
    }

    /** Headroom for positive EQ boosts on engines that expose preamplification. */
    val preampDb: Float get() =
        -gainBandsDb.maxOrNull()!!.coerceAtLeast(0f).coerceAtMost(3f)

    companion object {
        val FREQUENCIES_HZ = floatArrayOf(60f, 170f, 400f, 1_000f, 2_400f, 6_000f, 12_000f)
        fun fromStored(raw: String?): AudioEqualizerPreset =
            entries.firstOrNull { it.name == raw } ?: ORIGINAL
    }
}

/** Android AudioFX reports band gains and range in millibels (100 mB = 1 dB). */
internal fun audioEqMillibels(
    preset: AudioEqualizerPreset, frequencyHz: Float, minMb: Int, maxMb: Int
): Short {
    val min = minOf(minMb, maxMb).coerceAtLeast(Short.MIN_VALUE.toInt())
    val max = maxOf(minMb, maxMb).coerceAtMost(Short.MAX_VALUE.toInt())
    return (preset.gainDbAt(frequencyHz) * 100f).roundToInt().coerceIn(min, max).toShort()
}
