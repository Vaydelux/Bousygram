package com.vaydelux.bousygram.presentation.extras

/**
 * Pixel-based D-pad pointer motion scales from inexpensive 720p/1080p TVs to
 * high-resolution panels. Only real key repeats accelerate movement.
 */
internal object ExtrasTvCursorPolicy {
    fun step(widthPx: Int, heightPx: Int, repeatCount: Int): Int {
        val axis = maxOf(widthPx, heightPx).coerceAtLeast(1)
        val base = (axis / 75).coerceIn(18, 64)
        val multiplier = when {
            repeatCount >= 16 -> 4
            repeatCount >= 6 -> 2
            else -> 1
        }
        return base * multiplier
    }

    fun clamp(position: Int, delta: Int, extentPx: Int): Int =
        (position.toLong() + delta.toLong())
            .coerceIn(0L, (extentPx - 1).coerceAtLeast(0).toLong())
            .toInt()
}
