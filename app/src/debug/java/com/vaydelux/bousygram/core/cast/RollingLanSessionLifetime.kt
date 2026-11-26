package com.vaydelux.bousygram.core.cast

/** Only locally selected direct-video mode may use the extended lifetime.
 * Enrollment and approval windows remain short and single-use. */
internal enum class RollingLanSessionLifetime(val operationMs: Long, val playbackMs: Long) {
    QUALIFICATION(180_000L, 120_000L),
    DIRECT_VIDEO(4 * 60 * 60 * 1000L + 30_000L, 4 * 60 * 60 * 1000L);

    fun deadline(now: Long): Long = Math.addExact(now, operationMs)
    fun isActive(now: Long, started: Long): Boolean =
        now >= started && now - started < operationMs
}
