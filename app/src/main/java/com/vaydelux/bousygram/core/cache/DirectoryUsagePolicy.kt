package com.vaydelux.bousygram.core.cache

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Stream directory entries instead of allocating a File array and queue for
 * every entry in a wide TDLib directory. Skip an excluded subtree before
 * visiting its children; never follow symbolic links.
 *
 * Logical file lengths only (not allocated disk blocks); inaccessible or
 * concurrently removed entries are best-effort omissions, as before.
 * Synchronous: call from IO, never the main/UI thread.
 */
internal fun directoryUsageBytes(root: File?, excludedSubtree: File? = null): Long {
    if (root == null || !root.exists()) return 0L
    val start = root.toPath().toAbsolutePath().normalize()
    val excluded = excludedSubtree?.toPath()?.toAbsolutePath()?.normalize()
    if (start == excluded) return 0L
    var total = 0L
    Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
            if (dir == excluded) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            if (file != excluded && attrs.isRegularFile) {
                val size = attrs.size().coerceAtLeast(0L)
                total = if (total > Long.MAX_VALUE - size) Long.MAX_VALUE else total + size
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
            FileVisitResult.CONTINUE

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult =
            FileVisitResult.CONTINUE
    })
    return total
}
