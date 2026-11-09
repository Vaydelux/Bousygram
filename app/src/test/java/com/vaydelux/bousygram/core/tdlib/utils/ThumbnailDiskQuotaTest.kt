package com.vaydelux.bousygram.core.tdlib.utils

import com.vaydelux.bousygram.core.cache.TvStorageBudget
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThumbnailDiskQuotaTest {
    @get:Rule val temp = TemporaryFolder()

    private fun makeFile(relative: String, mib: Long, touched: Long): File {
        val file = File(temp.root, relative)
        require(file.parentFile.mkdirs() || file.parentFile.isDirectory)
        RandomAccessFile(file, "rw").use { it.setLength(mib * TvStorageBudget.MIB) }
        check(file.setLastModified(touched))
        return file
    }

    @Test fun evictsOldestGeneratedImagesAcrossAccountsAndRetainsOtherFiles() {
        val oldest = makeFile("accounts/1/video_thumbnails/thumb_1.png", 8L, 1_000L)
        val middle = makeFile("accounts/1/seek_previews/seek_1_10000.png", 8L, 2_000L)
        val current = makeFile("accounts/2/video_thumbnails/thumb_2.png", 8L, 3_000L)
        val privateDb = makeFile("accounts/1/video_thumbnails/personal.db", 30L, 500L)
        val unrelated = makeFile("webview/cache.bin", 40L, 500L)
        val result = ThumbnailDiskQuota.enforce(temp.root, current)
        assertEquals(1, result.deletedFiles)
        assertEquals(8L * TvStorageBudget.MIB, result.freedBytes)
        assertEquals(TvStorageBudget.THUMBNAIL_DISK_CACHE_BYTES, result.remainingBytes)
        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(current.exists())
        assertTrue(privateDb.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun protectedFileIsNeverRemovedEvenWhenLargerThanBudget() {
        val playingPreview = makeFile("accounts/1/seek_previews/seek_8_30000.png", 18L, 1_000L)
        val result = ThumbnailDiskQuota.enforce(temp.root, playingPreview)
        assertEquals(0, result.deletedFiles)
        assertTrue(result.overBudget)
        assertTrue(playingPreview.exists())
    }

    @Test fun noAccountDirectoryAndAlreadySmallCacheAreNoOps() {
        assertEquals(0, ThumbnailDiskQuota.enforce(temp.root).deletedFiles)
        val single = makeFile("accounts/2/video_thumbnails/thumb_50.png", 2L, 1_000L)
        val result = ThumbnailDiskQuota.enforce(temp.root)
        assertEquals(0, result.deletedFiles)
        assertEquals(2L * TvStorageBudget.MIB, result.remainingBytes)
        assertTrue(single.exists())
    }

    @Test fun ignoresUnrecognizedAccountDirectoriesAndUnknownCacheFileNames() {
        val unknownAccount = makeFile("accounts/abc/video_thumbnails/thumb_1.png", 70L, 10L)
        val unknownFile = makeFile("accounts/1/video_thumbnails/document.png", 70L, 20L)
        val old = makeFile("accounts/1/video_thumbnails/thumb_1.png", 9L, 30L)
        val newest = makeFile("accounts/1/seek_previews/seek_1_1000.png", 9L, 40L)
        val result = ThumbnailDiskQuota.enforce(temp.root)
        assertEquals(1, result.deletedFiles)
        assertFalse(old.exists())
        assertTrue(newest.exists())
        assertTrue(unknownAccount.exists())
        assertTrue(unknownFile.exists())
    }
}
