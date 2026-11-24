package com.vaydelux.bousygram.core.tdlib.utils

import com.vaydelux.bousygram.core.cache.TvStorageBudget
import java.io.File
import java.nio.file.Files

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

    private data class Candidate(val file: File, val bytes: Long, val modified: Long)

    private fun isDirectDirectory(file: File): Boolean =
        file.isDirectory && !Files.isSymbolicLink(file.toPath())

    fun enforce(cacheDir: File, protectedFile: File? = null): ThumbnailPruneResult {
        val accounts = File(cacheDir, "accounts")
        if (!isDirectDirectory(cacheDir) || !isDirectDirectory(accounts))
            return ThumbnailPruneResult(0L, 0L, 0)
        val files = accounts.listFiles().orEmpty().asSequence()
            .filter { isDirectDirectory(it) && accountName.matches(it.name) }
            .flatMap { account ->
                sequenceOf(
                    File(account, "video_thumbnails") to posterName,
                    File(account, "seek_previews") to previewName
                )
            }
            .filter { (dir, _) -> isDirectDirectory(dir) }
            .flatMap { (dir, pattern) ->
                val canonicalDir = runCatching { dir.canonicalFile }.getOrNull()
                dir.listFiles().orEmpty().asSequence().filter { file ->
                    file.isFile && !Files.isSymbolicLink(file.toPath()) && pattern.matches(file.name) &&
                        canonicalDir != null &&
                        runCatching { file.canonicalFile.parentFile == canonicalDir }
                            .getOrDefault(false)
                }
            }
            .map { Candidate(it, it.length().coerceAtLeast(0L), it.lastModified()) }
            .toList()

        // Read modification time once per file, not on each sort comparison.
        var remaining = files.fold(0L) { total, entry ->
            if (total > Long.MAX_VALUE - entry.bytes) Long.MAX_VALUE else total + entry.bytes
        }
        val ceiling = TvStorageBudget.THUMBNAIL_DISK_CACHE_BYTES
        if (remaining <= ceiling) return ThumbnailPruneResult(0L, remaining, 0)
        val protectedPath = protectedFile?.let { runCatching { it.canonicalPath }.getOrNull() }
        var deleted = 0
        var freed = 0L
        for (entry in files.sortedWith(compareBy<Candidate> { it.modified }.thenBy { it.file.path })) {
            if (remaining <= ceiling) break
            val file = entry.file
            if (protectedPath != null &&
                runCatching { file.canonicalPath == protectedPath }.getOrDefault(false)) continue
            // Recheck ownership at deletion; only ordinary generated files
            // beneath the three direct directories are eligible.
            val dir = file.parentFile ?: continue
            val account = dir.parentFile ?: continue
            if (!isDirectDirectory(accounts) || !isDirectDirectory(account) ||
                !isDirectDirectory(dir) || Files.isSymbolicLink(file.toPath()) ||
                !file.isFile) continue
            if (file.delete()) {
                deleted++
                freed = if (freed > Long.MAX_VALUE - entry.bytes) Long.MAX_VALUE else freed + entry.bytes
                remaining = (remaining - entry.bytes).coerceAtLeast(0L)
            }
        }
        return ThumbnailPruneResult(freed, remaining, deleted)
    }
}
