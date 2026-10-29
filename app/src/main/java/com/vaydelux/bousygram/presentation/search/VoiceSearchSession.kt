package com.vaydelux.bousygram.presentation.search

/** Invalidates callbacks when recognition is canceled, replaced, completed, or disposed. */
internal class VoiceSearchSession {
    private var generation = 0L
    private var activeGeneration: Long? = null
    private var disposed = false

    fun begin(): Long? {
        if (disposed) return null
        generation += 1L
        return generation.also { activeGeneration = it }
    }

    fun isActive(request: Long): Boolean = !disposed && activeGeneration == request

    fun replace(request: Long): Long? = if (isActive(request)) begin() else null

    fun complete(request: Long): Boolean {
        if (!isActive(request)) return false
        activeGeneration = null
        return true
    }

    fun cancel() {
        activeGeneration = null
    }

    fun dispose() {
        cancel()
        disposed = true
    }
}
