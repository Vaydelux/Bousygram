package com.vaydelux.bousygram.data.repository

import java.math.BigInteger

/**
 * Deterministic eviction policy for per-Telegram-account TMDB downloaded
 * artwork. Coil's in-memory/HTTP caches are separate; this bounds only the
 * files downloaded explicitly by TmdbRepository.
 */
internal data class TmdbImageCacheEntry(
    val name: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long
)

internal object TmdbImageCachePolicy {
    const val MAX_BYTES = 64L * 1024L * 1024L
    const val MAX_FILES = 160
    private val safeFilename = Regex("""^[pb]_[A-Za-z0-9_-]+\.[A-Za-z0-9]{2,8}$""")

    fun evictions(
        entries: List<TmdbImageCacheEntry>,
        preserveName: String?,
        maxBytes: Long = MAX_BYTES,
        maxFiles: Int = MAX_FILES
    ): List<String> {
        require(maxBytes >= 0 && maxFiles >= 0)
        val eligible = entries.filter {
            it.sizeBytes >= 0 && safeFilename.matches(it.name)
        }.distinctBy { it.name }
        var count = eligible.size
        var bytes = eligible.fold(BigInteger.ZERO) { total, entry ->
            total + BigInteger.valueOf(entry.sizeBytes)
        }
        val budget = BigInteger.valueOf(maxBytes)
        val evicted = mutableListOf<String>()
        for (item in eligible.sortedWith(compareBy<TmdbImageCacheEntry> { it.modifiedAtMs }.thenBy { it.name })) {
            if (bytes <= budget && count <= maxFiles) break
            if (item.name == preserveName) continue
            evicted += item.name
            bytes -= BigInteger.valueOf(item.sizeBytes)
            count--
        }
        return evicted
    }
}
