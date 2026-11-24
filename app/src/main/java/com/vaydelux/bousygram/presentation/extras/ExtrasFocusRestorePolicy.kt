package com.vaydelux.bousygram.presentation.extras

/** Retry transiently unattached lazy-grid nodes, then try a visible navigation fallback. */
internal suspend fun restoreExtrasFocus(
    nextFrame: suspend () -> Unit,
    preferred: () -> Boolean,
    fallback: () -> Boolean
): Boolean {
    repeat(3) {
        nextFrame()
        if (preferred()) return true
    }
    nextFrame()
    return fallback()
}
