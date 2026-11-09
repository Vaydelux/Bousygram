package com.vaydelux.bousygram.core.cache

/**
 * Disposable MEDIA cache allocation for small-storage Android TV devices.
 *
 * These ceilings are separate: Telegram's protected TDLib downloads, Coil
 * image-cache entries, and locally generated thumbnails. They are eviction
 * targets, not a cap on installed APK/native libraries, databases, WebView
 * site data, user-pinned downloads or active video payloads.
 */
object TvStorageBudget {
    const val MIB = 1024L * 1024L
    const val TOTAL_MEDIA_CACHE_BYTES = 200L * MIB
    const val POSTER_DISK_CACHE_BYTES = 24L * MIB
    const val THUMBNAIL_DISK_CACHE_BYTES = 16L * MIB
    const val TELEGRAM_MEDIA_CACHE_BYTES =
        TOTAL_MEDIA_CACHE_BYTES - POSTER_DISK_CACHE_BYTES - THUMBNAIL_DISK_CACHE_BYTES
}
