package com.vaydelux.bousygram.presentation.extras

/** Conservative decoded-poster bounds so a long TV browsing session stays small. */
internal data class ExtrasArtworkSize(val width: Int, val height: Int) {
    val maximumArgbBytes: Long get() = width.toLong() * height.toLong() * 4
}

internal object ExtrasImagePolicy {
    fun poster(lowMemory: Boolean, detail: Boolean): ExtrasArtworkSize =
        when {
            lowMemory && detail -> ExtrasArtworkSize(360, 540)
            lowMemory -> ExtrasArtworkSize(240, 360)
            detail -> ExtrasArtworkSize(640, 960)
            else -> ExtrasArtworkSize(360, 540)
        }
}
