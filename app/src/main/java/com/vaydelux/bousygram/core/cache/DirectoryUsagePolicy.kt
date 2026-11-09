package com.vaydelux.bousygram.core.cache

import java.io.File
import java.nio.file.Files
import java.util.ArrayDeque

/**
 * Iteratively measure app-owned directory contents, optionally excluding one
 * subtree. The exclusion prevents a TDLib media cache from being traversed
 * again when computing persistent filesDir usage.
 *
 * Does not follow symbolic links, which could escape the app directory or
 * produce recursive traversal loops. No path/filename logging.
 * This is synchronous: call from IO, never the main/UI thread.
 */
internal fun directoryUsageBytes(root: File?, excludedSubtree: File? = null): Long {
    if (root == null || !root.exists()) return 0L
    val excluded = excludedSubtree?.absolutePath
    val pending = ArrayDeque<File>()
    pending.add(root)
    var total = 0L
    while (!pending.isEmpty()) {
        val current = pending.removeLast()
        if (excluded != null && current.absolutePath == excluded) continue
        if (Files.isSymbolicLink(current.toPath())) continue
        if (current.isFile) {
            val size = current.length().coerceAtLeast(0L)
            total = if (total > Long.MAX_VALUE - size) Long.MAX_VALUE else total + size
        } else if (current.isDirectory) {
            current.listFiles()?.forEach(pending::addLast)
        }
    }
    return total
}
