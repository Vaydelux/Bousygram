package com.vaydelux.bousygram.core.tdlib.cache

import com.vaydelux.bousygram.core.cache.CacheManager

/**
 * Backward compatibility facade delegating to unified [CacheManager].
 */
object OfflineCacheManager {
    const val DEFAULT_MAX_CACHE_SIZE = CacheManager.MAX_CACHE_LIMIT_BYTES

    fun isPinned(fileId: Int): Boolean = CacheManager.isPinned(fileId)

    fun pinFile(fileId: Int) = CacheManager.pinFile(fileId)

    fun unpinFile(fileId: Int) = CacheManager.unpinFile(fileId)

    fun markFileAccessed(fileId: Int) = CacheManager.markFileAccessed(fileId)

    fun triggerCacheCleanupIfNeeded() = CacheManager.triggerCacheCleanupIfNeeded()

    fun isWatched(fileId: Int): Boolean = CacheManager.isWatched(fileId)

    fun pruneTrackedCacheNow(): TrackedCacheCleanupResult {
        val result = CacheManager.pruneTrackedCacheNow()
        return TrackedCacheCleanupResult(
            deletedFiles = result.deletedFiles,
            freedBytes = result.freedBytes,
            remainingTrackedBytes = result.remainingTrackedBytes,
            limitBytes = result.limitBytes,
            initiallyOverLimit = result.initiallyOverLimit
        )
    }
}

typealias TrackedCacheCleanupResult = com.vaydelux.bousygram.core.cache.TrackedCacheCleanupResult
