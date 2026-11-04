package com.vaydelux.bousygram.core.media

/**
 * Readable user-selectable text caption presets. Bitmap/PGS and hardcoded
 * subtitles are not recolorable; their native rendering is left intact.
 */
enum class SubtitleColorPreset(
    val title: String,
    val textColor: Int,
    val backgroundColor: Int,
    val edgeColor: Int,
    val mpvTextRgb: String,
    val mpvEdgeRgb: String
) {
    WHITE("White", 0xFFFFFFFF.toInt(), 0xB8000000.toInt(), 0xFF000000.toInt(), "#FFFFFF", "#000000"),
    YELLOW("Yellow", 0xFFFFEA63.toInt(), 0xB8000000.toInt(), 0xFF000000.toInt(), "#FFEA63", "#000000"),
    BLACK("Black", 0xFF000000.toInt(), 0xE6FFFFFF.toInt(), 0xFFFFFFFF.toInt(), "#000000", "#FFFFFF");

    companion object {
        fun fromStored(value: String?): SubtitleColorPreset =
            entries.firstOrNull { it.name == value } ?: WHITE
    }
}

data class SubtitleAppearance(
    val percent: Int = 100,
    val preset: SubtitleColorPreset = SubtitleColorPreset.WHITE
) {
    val safePercent: Int get() = percent.coerceIn(75, 200)
    val exoFractionOfHeight: Float get() = 0.0533f * safePercent / 100f
    val mpvScale: Double get() = safePercent / 100.0
    fun changedPercent(next: Int): SubtitleAppearance = copy(percent = next.coerceIn(75, 200))
}
