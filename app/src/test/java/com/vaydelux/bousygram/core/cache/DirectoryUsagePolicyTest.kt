package com.vaydelux.bousygram.core.cache

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryUsagePolicyTest {
    @Test fun excludesMediaSubtreeWithoutWalkingItAgain() {
        val root = Files.createTempDirectory("bousy-cache-accounting").toFile()
        try {
            val media = File(root, "tdlib_files").apply { mkdirs() }
            val other = File(root, "settings").apply { mkdirs() }
            File(media, "movie.bin").writeBytes(ByteArray(29))
            File(media, "poster.bin").writeBytes(ByteArray(13))
            File(other, "prefs.bin").writeBytes(ByteArray(11))
            File(root, "metadata.bin").writeBytes(ByteArray(5))
            assertEquals(58L, directoryUsageBytes(root))
            assertEquals(42L, directoryUsageBytes(media))
            assertEquals(16L, directoryUsageBytes(root, media))
            assertEquals(0L, directoryUsageBytes(media, media))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun missingAndEmptyDirectoriesReturnZero() {
        val root = Files.createTempDirectory("bousy-cache-empty").toFile()
        try {
            assertEquals(0L, directoryUsageBytes(root))
            assertEquals(0L, directoryUsageBytes(File(root, "missing")))
            assertEquals(0L, directoryUsageBytes(null))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun scansLargeFlatDirectoryWithBoundedTraversalState() {
        val root = Files.createTempDirectory("bousy-cache-many").toFile()
        try {
            repeat(400) { index ->
                File(root, "item-$index.bin").writeBytes(ByteArray(9))
            }
            assertEquals(3600L, directoryUsageBytes(root))
            assertTrue(root.listFiles()!!.size == 400)
        } finally {
            root.deleteRecursively()
        }
    }
}
