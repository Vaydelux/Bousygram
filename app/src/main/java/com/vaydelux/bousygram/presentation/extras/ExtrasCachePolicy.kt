package com.vaydelux.bousygram.presentation.extras

/** Conservative UTF-16 accounting for both retained response and request key. */
internal fun catalogCacheEntryKiB(keyChars: Int, valueChars: Int): Int {
    require(keyChars >= 0 && valueChars >= 0)
    val bytes = (keyChars.toLong() + valueChars.toLong()) * 2L
    return ((bytes + 1023L) / 1024L).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
}

/** Deadlines use elapsed realtime, not mutable wall time. */
internal fun catalogDeadline(nowMs: Long, ttlMs: Long): Long {
    require(nowMs >= 0 && ttlMs >= 0)
    return if (nowMs > Long.MAX_VALUE - ttlMs) Long.MAX_VALUE else nowMs + ttlMs
}
