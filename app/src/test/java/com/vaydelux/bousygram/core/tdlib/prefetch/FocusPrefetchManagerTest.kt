package com.vaydelux.bousygram.core.tdlib.prefetch

import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.tdlib.server.TelegramFileGateway
import com.vaydelux.bousygram.core.tdlib.server.TelegramFileGatewayRegistry
import com.vaydelux.bousygram.core.tdlib.server.TelegramGatewayFile
import com.vaydelux.bousygram.core.tdlib.server.TelegramGatewayResult
import com.vaydelux.bousygram.domain.model.MediaItem
import com.vaydelux.bousygram.domain.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class FocusPrefetchManagerTest {

    private lateinit var gateway: TestTelegramFileGateway

    @Before
    fun setUp() {
        FocusPrefetchManager.resetForTests()
        PlaybackSessionController.setVideoPlaying(false)
        gateway = TestTelegramFileGateway()
        TelegramFileGatewayRegistry.installForTests(gateway)
    }

    @After
    fun tearDown() {
        FocusPrefetchManager.resetForTests()
        PlaybackSessionController.setVideoPlaying(false)
        TelegramFileGatewayRegistry.reset()
    }

    @Test
    fun testDebounceCancelsIfFocusMovedBefore400Ms() = runBlocking {
        val item1 = createMediaItem(fileId = 101, title = "Video 1")
        val item2 = createMediaItem(fileId = 102, title = "Video 2")

        // Focus item 1 for only 100 ms (< 400 ms debounce)
        FocusPrefetchManager.onCardFocused(item1)
        Thread.sleep(100)

        // Switch focus to item 2 and hold for 550 ms (> 400 ms debounce)
        FocusPrefetchManager.onCardFocused(item2)
        Thread.sleep(550)

        // Item 1 must NOT have any download calls
        val item1Downloads = gateway.downloadRecords.filter { it.fileId == 101 }
        assertEquals("Item 1 should have 0 downloads because focus moved before 400ms", 0, item1Downloads.size)

        // Item 2 must have download calls
        val item2Downloads = gateway.downloadRecords.filter { it.fileId == 102 }
        assertTrue("Item 2 should have downloaded head", item2Downloads.isNotEmpty())
    }

    @Test
    fun testPrefetchDownloadsFirst2MbAtLowPriority() = runBlocking {
        val item = createMediaItem(
            fileId = 201,
            title = "Documentary.mkv",
            mimeType = "video/x-matroska",
            fileSizeBytes = 50L * 1024L * 1024L
        )

        FocusPrefetchManager.onCardFocused(item)
        Thread.sleep(550)

        val records = gateway.downloadRecords.filter { it.fileId == 201 }
        assertTrue("Should have recorded download", records.isNotEmpty())
        val head = records.first()
        assertEquals(201, head.fileId)
        assertEquals(0L, head.offset)
        assertEquals(2 * 1024 * 1024, head.limit)
        assertEquals(FocusPrefetchManager.LOW_PRIORITY, head.priority)
    }

    @Test
    fun testMp4PrefetchDownloadsFirstAndLast2Mb() = runBlocking {
        val totalSize = 20L * 1024L * 1024L
        val item = createMediaItem(
            fileId = 301,
            title = "Movie.mp4",
            fileName = "Movie.mp4",
            mimeType = "video/mp4",
            fileSizeBytes = totalSize
        )

        FocusPrefetchManager.onCardFocused(item)
        Thread.sleep(600)

        val records = gateway.downloadRecords.filter { it.fileId == 301 }
        assertEquals("MP4 should request both head and tail", 2, records.size)

        val head = records[0]
        assertEquals(0L, head.offset)
        assertEquals(2 * 1024 * 1024, head.limit)
        assertEquals(FocusPrefetchManager.LOW_PRIORITY, head.priority)

        val tail = records[1]
        val expectedTailOffset = totalSize - (2L * 1024L * 1024L)
        assertEquals(expectedTailOffset, tail.offset)
        assertEquals(2 * 1024 * 1024, tail.limit)
        assertEquals(FocusPrefetchManager.LOW_PRIORITY, tail.priority)
    }

    @Test
    fun testNonMp4DoesNotDownloadTail() = runBlocking {
        val totalSize = 20L * 1024L * 1024L
        val item = createMediaItem(
            fileId = 401,
            title = "Episode.mkv",
            fileName = "Episode.mkv",
            mimeType = "video/x-matroska",
            fileSizeBytes = totalSize
        )

        FocusPrefetchManager.onCardFocused(item)
        Thread.sleep(600)

        val records = gateway.downloadRecords.filter { it.fileId == 401 }
        assertEquals("Non-MP4 container should only request head", 1, records.size)
        assertEquals(0L, records[0].offset)
    }

    @Test
    fun testCancelWhenFocusMovesAway() = runBlocking {
        val item = createMediaItem(fileId = 501, title = "Clip.mp4")

        FocusPrefetchManager.onCardFocused(item)
        Thread.sleep(550)

        // Clear focus
        FocusPrefetchManager.onFocusCleared()
        Thread.sleep(100)

        assertTrue(
            "Cancelled files should include 501 when focus is cleared",
            gateway.cancelledFiles.contains(501)
        )
        assertFalse("isPrefetching should be false after focus cleared", FocusPrefetchManager.isPrefetching(501))
    }

    @Test
    fun testNeverPrefetchWhilePlaying() = runBlocking {
        PlaybackSessionController.setVideoPlaying(true)
        val item = createMediaItem(fileId = 601, title = "Clip.mp4")

        FocusPrefetchManager.onCardFocused(item)
        Thread.sleep(550)

        val records = gateway.downloadRecords.filter { it.fileId == 601 }
        assertEquals("Should never prefetch while video playback is active", 0, records.size)
    }

    @Test
    fun testPrefetchCancelledWhenPlaybackStarts() = runBlocking {
        val item = createMediaItem(fileId = 701, title = "Feature.mp4")

        FocusPrefetchManager.onCardFocused(item)
        Thread.sleep(550)

        // Start playback
        PlaybackSessionController.setVideoPlaying(true)
        Thread.sleep(100)

        assertTrue(
            "Active prefetch must be cancelled when playback starts",
            gateway.cancelledFiles.contains(701)
        )
        assertFalse(FocusPrefetchManager.isPrefetching(701))
    }

    @Test
    fun testMaxConcurrencyLimitConstant() {
        assertEquals("Concurrency limit must be at most 2", 2, FocusPrefetchManager.MAX_CONCURRENT_PREFETCH)
    }

    private fun createMediaItem(
        fileId: Int,
        title: String,
        fileName: String? = null,
        mimeType: String? = null,
        fileSizeBytes: Long = 10L * 1024L * 1024L
    ): MediaItem = MediaItem(
        chatId = 1000L,
        messageId = fileId.toLong(),
        date = 0,
        type = MediaType.Video,
        title = title,
        fileId = fileId,
        fileName = fileName ?: title,
        mimeType = mimeType,
        fileSizeBytes = fileSizeBytes
    )

    private class TestTelegramFileGateway : TelegramFileGateway {
        data class DownloadRecord(
            val fileId: Int,
            val priority: Int,
            val offset: Long,
            val limit: Int
        )

        val downloadRecords = CopyOnWriteArrayList<DownloadRecord>()
        val cancelledFiles = ConcurrentHashMap.newKeySet<Int>()
        private val tempFile = File.createTempFile("test_prefetch", ".bin").apply {
            deleteOnExit()
            writeBytes(ByteArray(1024))
        }

        override suspend fun downloadFile(
            fileId: Int,
            priority: Int,
            offset: Long,
            limit: Int,
            synchronous: Boolean
        ): TelegramGatewayResult<TelegramGatewayFile?> {
            downloadRecords.add(DownloadRecord(fileId, priority, offset, limit))
            return TelegramGatewayResult.Success(
                TelegramGatewayFile(
                    fileId = fileId,
                    localPath = tempFile.absolutePath,
                    isDownloadingCompleted = false,
                    downloadOffset = offset,
                    downloadedPrefixSize = limit.toLong()
                )
            )
        }

        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long = 2L * 1024L * 1024L

        override suspend fun getFile(fileId: Int): TelegramGatewayFile = TelegramGatewayFile(
            fileId = fileId,
            localPath = tempFile.absolutePath,
            isDownloadingCompleted = false,
            downloadOffset = 0L,
            downloadedPrefixSize = 2L * 1024L * 1024L,
            size = 20L * 1024L * 1024L
        )

        override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? =
            ByteArray(limit)

        override suspend fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean): Boolean {
            cancelledFiles.add(fileId)
            return true
        }
    }
}
