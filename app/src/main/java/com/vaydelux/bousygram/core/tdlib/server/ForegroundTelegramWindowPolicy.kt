package com.vaydelux.bousygram.core.tdlib.server

import com.vaydelux.bousygram.core.cache.StoragePressurePolicy
import com.vaydelux.bousygram.core.cache.TvStorageBudget

/**
 * Experimental foreground TDLib DownloadFile windows for small-flash TVs.
 *
 * A positive DownloadFile limit caps ONE native request, not total bytes stored
 * by TDLib across multiple windows. This does not evict, move or delete native
 * files and does not replace the owner-aware pinned/offline cache manager.
 * Never enable for production until real ARM32/ARM64 storage and seek soaks
 * pass the device qualification gate.
 */
internal data class ForegroundTelegramWindow(val offset: Long, val limit: Int) {
    init {
        require(offset >= 0L && limit > 0)
        require(offset <= Long.MAX_VALUE - limit.toLong())
    }
    val endExclusive: Long get() = offset + limit.toLong()
}

internal object ForegroundTelegramWindowPolicy {
    private const val MIB = TvStorageBudget.MIB
    const val NORMAL_WINDOW_BYTES = 16L * MIB
    const val LOW_WINDOW_BYTES = 8L * MIB
    const val CRITICAL_WINDOW_BYTES = 4L * MIB
    const val ADVANCE_MARGIN_BYTES = 1L * MIB

    /** Keep foreground requests possible even when optional caching is paused. */
    fun windowSize(availableBytes: Long?): Long = when (StoragePressurePolicy.level(availableBytes)) {
        StoragePressurePolicy.Level.NORMAL -> NORMAL_WINDOW_BYTES
        StoragePressurePolicy.Level.LOW -> LOW_WINDOW_BYTES
        StoragePressurePolicy.Level.CRITICAL -> CRITICAL_WINDOW_BYTES
    }

    /** Use the exact extractor offset; distant MKV cues and MP4 moov remain seekable. */
    fun plan(offset: Long, knownSize: Long, availableBytes: Long?): ForegroundTelegramWindow? {
        if (offset < 0L || (knownSize > 0L && offset >= knownSize)) return null
        val remaining = if (knownSize > 0L) knownSize - offset else Long.MAX_VALUE - offset
        val limit = minOf(windowSize(availableBytes), remaining, Int.MAX_VALUE.toLong()).toInt()
        if (limit <= 0) return null
        return ForegroundTelegramWindow(offset, limit)
    }

    /**
     * A near-boundary renewal is driven by the *actual foreground read*, not a
     * separate speculative fetch. Do not rotate an active window on each
     * DataSource reopen, and do not request bytes beyond known EOF.
     */
    fun needsWindow(
        requestedOffset: Long,
        requestedBytes: Int,
        currentOffset: Long?,
        currentEndExclusive: Long?,
        knownSize: Long
    ): Boolean {
        if (requestedOffset < 0L || requestedBytes <= 0) return false
        if (knownSize > 0L && requestedOffset >= knownSize) return false
        if (currentOffset == null || currentEndExclusive == null) return true
        if (requestedOffset < currentOffset || requestedOffset >= currentEndExclusive) return true
        val end = if (requestedOffset > Long.MAX_VALUE - requestedBytes.toLong()) Long.MAX_VALUE
            else requestedOffset + requestedBytes.toLong()
        if (end > currentEndExclusive) return true
        if (knownSize > 0L && currentEndExclusive >= knownSize) return false
        return currentEndExclusive - end <= ADVANCE_MARGIN_BYTES
    }
}
