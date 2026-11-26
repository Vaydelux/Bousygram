package com.vaydelux.bousygram.core.cast

/** Pure, bounded remote command mapping. No SDK state or networking. */
internal object ExtrasCastControlPolicy {
    fun seekTarget(positionMs: Long, durationMs: Long, deltaMs: Long): Long? {
        if (durationMs <= 0L) return null
        val base = positionMs.coerceIn(0L, durationMs)
        val delta = deltaMs.coerceIn(-60_000L, 60_000L)
        return if (delta >= 0L) base + minOf(delta, durationMs - base)
        else base - minOf(-delta, base)
    }

    fun activeTracks(active: LongArray, textIds: Set<Long>, selected: Long?): LongArray? {
        if (selected != null && selected !in textIds) return null
        return (active.filter { it !in textIds } + listOfNotNull(selected)).distinct().toLongArray()
    }
}
