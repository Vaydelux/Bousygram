package com.vaydelux.bousygram.core.cache

/**
 * Fixed-size rotating ID slice for TDLib GetFile requests. It avoids an
 * unbounded batch on small TVs and covers entries older than the first page.
 * A fresh cursor is maintained per active account in CacheManager.
 */
internal data class CacheInspectionPage(val ids: List<Int>, val nextCursor: Int)

internal fun selectCacheInspectionPage(
    orderedIds: List<Int>,
    cursor: Int,
    limit: Int
): CacheInspectionPage {
    if (orderedIds.isEmpty() || limit <= 0) return CacheInspectionPage(emptyList(), 0)
    val start = cursor.coerceAtLeast(0) % orderedIds.size
    val count = minOf(limit, orderedIds.size)
    val ids = List(count) { i -> orderedIds[(start + i) % orderedIds.size] }
    return CacheInspectionPage(ids, (start + count) % orderedIds.size)
}

/** Never classify a sub-256-file sample as the complete physical cache size. */
internal fun cacheQuotaPressureBytes(onDiskBytes: Long, inspectedBytes: Long): Long =
    maxOf(onDiskBytes.coerceAtLeast(0L), inspectedBytes.coerceAtLeast(0L))
