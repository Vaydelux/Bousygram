package com.vaydelux.bousygram.core.cache

import android.os.StatFs
import com.vaydelux.bousygram.app.BousygramApp

/**
 * Free-space guard for OPTIONAL downloads. Never block the foreground
 * decoder's byte-range requests or delete an active TDLib file.
 */
internal object StoragePressurePolicy {
    const val LOW_FREE_BYTES = 500L * TvStorageBudget.MIB
    const val CRITICAL_FREE_BYTES = 250L * TvStorageBudget.MIB

    enum class Level { NORMAL, LOW, CRITICAL }

    /** An unavailable measurement must not authorize speculative downloads. */
    fun level(availableBytes: Long?): Level = when {
        availableBytes == null || availableBytes < CRITICAL_FREE_BYTES -> Level.CRITICAL
        availableBytes < LOW_FREE_BYTES -> Level.LOW
        else -> Level.NORMAL
    }

    fun allowSpeculativeDownload(availableBytes: Long?): Boolean =
        level(availableBytes) == Level.NORMAL

    /** Read-ahead is speculative; the foreground DataSource remains unrestricted. */
    fun boundedReadAheadBytes(requestedBytes: Long, availableBytes: Long?): Long =
        if (allowSpeculativeDownload(availableBytes)) {
            requestedBytes.coerceIn(0L, 32L * TvStorageBudget.MIB)
        } else 0L
}

/**
 * Cached StatFs snapshots keep D-pad navigation and player progress callbacks
 * from repeatedly making filesystem queries. The snapshot lasts at most 8s.
 * No account, secret or file path is persisted.
 */
internal object StoragePressureMonitor {
    private const val REFRESH_MS = 8_000L
    private val lock = Any()
    @Volatile private var sampledAtMs = Long.MIN_VALUE
    @Volatile private var cachedFreeBytes: Long? = null

    fun freeBytes(): Long? {
        val now = System.nanoTime() / 1_000_000L
        if (sampledAtMs != Long.MIN_VALUE && now - sampledAtMs in 0 until REFRESH_MS) {
            return cachedFreeBytes
        }
        return synchronized(lock) {
            val checkedAt = System.nanoTime() / 1_000_000L
            if (sampledAtMs != Long.MIN_VALUE && checkedAt - sampledAtMs in 0 until REFRESH_MS) {
                cachedFreeBytes
            } else {
                cachedFreeBytes = runCatching {
                    StatFs(BousygramApp.instance.filesDir.absolutePath).availableBytes
                }.getOrNull()
                sampledAtMs = checkedAt
                cachedFreeBytes
            }
        }
    }

    fun isLow(): Boolean = !StoragePressurePolicy.allowSpeculativeDownload(freeBytes())
    fun isCritical(): Boolean =
        StoragePressurePolicy.level(freeBytes()) == StoragePressurePolicy.Level.CRITICAL
}
