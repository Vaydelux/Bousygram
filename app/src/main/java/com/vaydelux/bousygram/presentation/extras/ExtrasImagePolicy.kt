package com.vaydelux.bousygram.presentation.extras

/** Conservative decoded-poster bounds so a long TV browsing session stays small. */
internal data class ExtrasArtworkSize(val width: Int, val height: Int) {
    val maximumArgbBytes: Long get() = width.toLong() * height.toLong() * 4
}

internal object ExtrasImagePolicy {
    // TMDB and AniList key art is portrait. Layout follows the artwork instead
    // of cropping its top/bottom to a fixed pixel height.
    const val POSTER_ASPECT_RATIO = 2f / 3f

    /** Decode cinematic backdrops at a bounded resolution on ARM32 TVs. */
    fun backdrop(lowMemory: Boolean): ExtrasArtworkSize =
        if (lowMemory) ExtrasArtworkSize(640, 360)
        else ExtrasArtworkSize(1280, 720)

    fun poster(
        lowMemory: Boolean,
        detail: Boolean,
        suggestion: Boolean = false
    ): ExtrasArtworkSize = when {
        suggestion && lowMemory -> ExtrasArtworkSize(64, 96)
        suggestion -> ExtrasArtworkSize(96, 144)
        lowMemory && detail -> ExtrasArtworkSize(360, 540)
        lowMemory -> ExtrasArtworkSize(240, 360)
        detail -> ExtrasArtworkSize(640, 960)
        else -> ExtrasArtworkSize(360, 540)
    }
}
