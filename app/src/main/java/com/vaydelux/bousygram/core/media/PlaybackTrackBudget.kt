package com.vaydelux.bousygram.core.media

/**
 * Media containers can advertise arbitrarily many tracks and long metadata labels.
 * Bound the per-engine Compose track menu on constrained Android TVs without
 * altering native demuxer track IDs, decoding, or the underlying selectable tracks.
 */
internal object PlaybackTrackBudget {
    const val MAX_UI_TRACKS = 128
    const val MAX_LABEL_CHARS = 96
    const val MAX_LANGUAGE_CHARS = 24

    fun boundedCount(nativeCount: Int): Int = nativeCount.coerceIn(0, MAX_UI_TRACKS)

    fun language(raw: String?): String? =
        raw?.take(MAX_LANGUAGE_CHARS)?.trim()?.takeIf(String::isNotEmpty)

    fun label(raw: String?, fallback: String): String =
        raw?.take(MAX_LABEL_CHARS)?.trim()?.takeIf(String::isNotEmpty)
            ?: fallback.take(MAX_LABEL_CHARS)
}
