package com.vaydelux.bousygram.core.tdlib.utils

import com.vaydelux.bousygram.core.cache.TvStorageBudget
import java.io.File

internal data class ThumbnailPruneResult(
    val freedBytes: Long,
    val remainingBytes: Long,
    val deletedFiles: Int
) {
    val overBudget: Boolean
        get() = remainingBytes > TvStorageBudget.THUMBNAIL_DISK_CACHE_BYTES
}

/**
 * Recycle only Bousygram-generated thumbnail/seek-preview PNGs.
 *
 * Walks known account-scoped cache folders, never TDLib databases/media,
 * offline downloads, WebView data or user-owned files. A currently written
 * thumbnail can be protected from eviction until its AtomicFile closes.
 * Runs only on an I/O worker while the thumbnail writer's lock is held.
 */
internal object ThumbnailDiskQuota {
    private val accountName = Regex("[1-9][0-9]*")
    private val posterName = Regex("thumb_[0-9]+\\.png")
    private val previewName = Regex("seek_[0-9]+_[0-9]+\\.png")

    fun enforce(cacheDir: File, protectedFile: File? = null): ThumbnailPruneResult {
        val accounts = File(cacheDir, "accounts")
        if (!accounts.isDirectory) return ThumbnailPruneResult(0L, 0L, 0)
        val files = accounts.listFiles().orEmpty().asSequence()
            .filter { it.isDirectory && accountName.matches(it.name) }
            .flatMap { account ->
                sequenceOf(
                    File(account, "video_thumbnails") to posterName,
                    File(account, "seek_previews") to previewName
                )
            }
            .filter { (dir, _) -> dir.isDirectory }
            .flatMap { (dir, pattern) ->
                val canonicalDir = runCatching { dir.canonicalFile }.getOrNull()
                dir.listFiles().orEmpty().asSequence().filter { file ->
                    file.isFile && pattern.matches(file.name) &&
                        canonicalDir != null &&
                        runCatching { file.canonicalFile.parentFile == canonicalDir }
                            .getOrDefault(false)
                }
            }
            .toList()

        var remaining = files.sumOf { it.length().coerceAtLeast(0L) }
        val ceiling = TvStorageBudget.THUMBNAIL_DISK_CACHE_BYTES
        if (remaining <= ceiling) return ThumbnailPruneResult(0L, remaining, 0)
        val protectedPath = protectedFile?.let { runCatching { it.canonicalPath }.getOrNull() }
        var deleted = 0
        var freed = 0L
        for (file in files.sortedWith(compareBy<File> { it.lastModified() }.thenBy { it.path })) {
            if (remaining <= ceiling) break
            if (protectedPath != null &&
                runCatching { file.canonicalPath == protectedPath }.getOrDefault(false)) continue
            val bytes = file.length().coerceAtLeast(0L)
            if (file.delete()) {
                deleted++
                freed += bytes
                remaining = (remaining - bytes).coerceAtLeast(0L)
            }
        }
        return ThumbnailPruneResult(freed, remaining, deleted)
    }
}
