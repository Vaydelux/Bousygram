package com.vaydelux.bousygram.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.vaydelux.bousygram.core.observability.InMemoryPlaybackTelemetryStorage
import com.vaydelux.bousygram.core.observability.PlaybackTelemetryRecorder
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramFileGateway
import com.vaydelux.bousygram.core.tdlib.server.TelegramFileGatewayRegistry
import com.vaydelux.bousygram.core.tdlib.server.TelegramGatewayFile
import com.vaydelux.bousygram.core.tdlib.server.TelegramGatewayResult
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

class TdLibDataSourceTest {

    private val testFileId = 777
    private val testFileSize = 16 * 1024L // 16 KB
    private lateinit var testBytes: ByteArray
    private lateinit var testGateway: TestFileGateway
    private lateinit var telemetryStorage: InMemoryPlaybackTelemetryStorage

    @Before
    fun setUp() {
        TelegramChunkScheduler.resetForTests()
        TelegramFileGatewayRegistry.reset()
        TelegramChunkScheduler.resumeBackgroundWork()

        telemetryStorage = InMemoryPlaybackTelemetryStorage()
        PlaybackTelemetryRecorder.setStorageForTesting(telemetryStorage)
        PlaybackTelemetryRecorder.clear()

        testBytes = ByteArray(testFileSize.toInt()) { index ->
            (index % 251).toByte()
        }
        testGateway = TestFileGateway(testFileId, testBytes)
        TelegramFileGatewayRegistry.installForTests(testGateway)
    }

    @After
    fun tearDown() {
        PlaybackSessionController.unbindActiveFile(testFileId)
        testGateway.cleanUp()
        TelegramChunkScheduler.resetForTests()
        TelegramFileGatewayRegistry.reset()
        PlaybackTelemetryRecorder.clear()
    }

    @Test
    fun rejectsUnauthorizedAccessWhenNoActiveSession() {
        val dataSource = TdLibDataSource()
        val uri = TdLibDataSource.buildUri(testFileId, testFileSize)
        val dataSpec = DataSpec(uri)

        // File is NOT active in PlaybackSessionController
        assertFalse(PlaybackSessionController.isFileActive(testFileId))

        try {
            dataSource.open(dataSpec)
            fail("Expected IOException when fileId is not tied to an active playback session")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("Direct TDLib data source access denied") == true)
        }
    }

    @Test
    fun allowsAccessWhenTiedToActiveSession() {
        PlaybackSessionController.bindActiveFile(testFileId)
        assertTrue(PlaybackSessionController.isFileActive(testFileId))

        val dataSource = TdLibDataSource()
        val uri = TdLibDataSource.buildUri(testFileId, testFileSize)
        val dataSpec = DataSpec(uri)

        val bytesRemaining = dataSource.open(dataSpec)
        assertEquals(testFileSize, bytesRemaining)
        dataSource.close()
    }

    @Test
    fun openSupportsExplicitPositionAndLength() {
        PlaybackSessionController.bindActiveFile(testFileId)
        val dataSource = TdLibDataSource()
        val uri = TdLibDataSource.buildUri(testFileId, testFileSize)

        // Open sub-range: position = 1024, length = 2048
        val dataSpec = DataSpec(uri, 1024L, 2048L)
        val remaining = dataSource.open(dataSpec)
        assertEquals(2048L, remaining)
        dataSource.close()

        // Open from position to end (length unset)
        val dataSpecToEnd = DataSpec(uri, 4096L, C.LENGTH_UNSET.toLong())
        val remainingToEnd = dataSource.open(dataSpecToEnd)
        assertEquals(testFileSize - 4096L, remainingToEnd)
        dataSource.close()
    }

    @Test
    fun readDeliversExpectedBytesAndNotifiesTelemetryTtfb() {
        PlaybackSessionController.bindActiveFile(testFileId)
        val dataSource = TdLibDataSource()
        val uri = TdLibDataSource.buildUri(testFileId, testFileSize)
        dataSource.open(DataSpec(uri, 0L, 1024L))

        assertFalse(PlaybackTelemetryRecorder.hasFirstByte(testFileId))

        val buffer = ByteArray(1024)
        val bytesRead = dataSource.read(buffer, 0, 1024)

        assertEquals(1024, bytesRead)
        assertArrayEquals(testBytes.copyOfRange(0, 1024), buffer)
        assertTrue(PlaybackTelemetryRecorder.hasFirstByte(testFileId))

        dataSource.close()
    }

    @Test
    fun readSupportsShortReadsCorrectly() {
        PlaybackSessionController.bindActiveFile(testFileId)
        // Configure gateway to deliver only 256 bytes per chunk request
        testGateway.maxChunkSizeOverride = 256

        val dataSource = TdLibDataSource()
        val uri = TdLibDataSource.buildUri(testFileId, testFileSize)
        dataSource.open(DataSpec(uri, 0L, 1024L))

        val buffer = ByteArray(1024)
        // First read requests 1024 bytes, but gateway returns short chunk of 256 bytes
        val firstRead = dataSource.read(buffer, 0, 1024)
        assertEquals(256, firstRead)
        assertArrayEquals(testBytes.copyOfRange(0, 256), buffer.copyOfRange(0, 256))

        // Second read requests remaining bytes starting at offset 256
        val secondRead = dataSource.read(buffer, 256, 768)
        assertEquals(256, secondRead)
        assertArrayEquals(testBytes.copyOfRange(256, 512), buffer.copyOfRange(256, 512))

        dataSource.close()
    }

    @Test
    fun seekBehaviorClosesPreviousWindowAndOpensAtNewPosition() {
        PlaybackSessionController.bindActiveFile(testFileId)
        val dataSource = TdLibDataSource()
        val uri = TdLibDataSource.buildUri(testFileId, testFileSize)

        // 1. Initial read at offset 0
        dataSource.open(DataSpec(uri, 0L, 512L))
        val initialBuffer = ByteArray(512)
        val read1 = dataSource.read(initialBuffer, 0, 512)
        assertEquals(512, read1)
        assertArrayEquals(testBytes.copyOfRange(0, 512), initialBuffer)

        // 2. Seek: close DataSource (cancels old prefetch/downloads)
        dataSource.close()
        val deadline = System.currentTimeMillis() + 2000
        while (testGateway.cancelCalls.get() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(testGateway.cancelCalls.get() > 0)

        // 3. Open at seek position (e.g., offset 8192)
        val seekSpec = DataSpec(uri, 8192L, 512L)
        val remaining = dataSource.open(seekSpec)
        assertEquals(512L, remaining)

        val seekBuffer = ByteArray(512)
        val read2 = dataSource.read(seekBuffer, 0, 512)
        assertEquals(512, read2)
        assertArrayEquals(testBytes.copyOfRange(8192, 8192 + 512), seekBuffer)

        dataSource.close()
    }

    @Test
    fun abortStopsReadCleanly() {
        PlaybackSessionController.bindActiveFile(testFileId)
        val dataSource = TdLibDataSource()
        val uri = TdLibDataSource.buildUri(testFileId, testFileSize)
        dataSource.open(DataSpec(uri, 0L, 1024L))

        // Abort stream by calling close()
        dataSource.close()

        val buffer = ByteArray(512)
        val result = dataSource.read(buffer, 0, 512)
        assertEquals(C.RESULT_END_OF_INPUT, result)
    }

    @Test
    fun isDirectPlayableDetectsTdLibAndLoopbackStreamUris() {
        assertTrue(TdLibDataSource.isDirectPlayable(Uri.parse("tdlib://123?fileId=123&size=456")))
        assertTrue(TdLibDataSource.isDirectPlayable(Uri.parse("http://127.0.0.1:8080/stream?fileId=123&size=456")))
        assertTrue(TdLibDataSource.isDirectPlayable(Uri.parse("http://localhost:9999/stream?fileId=123&size=456")))
        assertFalse(TdLibDataSource.isDirectPlayable(Uri.parse("https://example.com/video.mp4")))
        assertFalse(TdLibDataSource.isDirectPlayable(null))
    }

    private class TestFileGateway(
        private val fileId: Int,
        private val bytes: ByteArray
    ) : TelegramFileGateway {
        private val tempFile = File.createTempFile("tdlib_test_", ".dat").apply {
            writeBytes(bytes)
            deleteOnExit()
        }

        val cancelCalls = AtomicInteger(0)
        var maxChunkSizeOverride: Int? = null

        fun cleanUp() {
            runCatching { tempFile.delete() }
        }

        override suspend fun downloadFile(
            fileId: Int,
            priority: Int,
            offset: Long,
            limit: Int,
            synchronous: Boolean
        ): TelegramGatewayResult<TelegramGatewayFile?> {
            val isComplete = maxChunkSizeOverride == null
            val prefix = maxChunkSizeOverride?.toLong() ?: bytes.size.toLong()
            return TelegramGatewayResult.Success(
                TelegramGatewayFile(
                    fileId = fileId,
                    localPath = tempFile.absolutePath,
                    isDownloadingCompleted = isComplete,
                    downloadOffset = offset,
                    downloadedPrefixSize = prefix
                )
            )
        }

        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long {
            return maxChunkSizeOverride?.toLong() ?: bytes.size.toLong()
        }

        override suspend fun getFile(fileId: Int): TelegramGatewayFile {
            val isComplete = maxChunkSizeOverride == null
            val prefix = maxChunkSizeOverride?.toLong() ?: bytes.size.toLong()
            return TelegramGatewayFile(
                fileId = fileId,
                localPath = tempFile.absolutePath,
                isDownloadingCompleted = isComplete,
                downloadOffset = 0L,
                downloadedPrefixSize = prefix
            )
        }

        override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? {
            val start = offset.toInt()
            val effectiveLimit = maxChunkSizeOverride?.let { min(it, limit) } ?: limit
            val end = (start + effectiveLimit).coerceAtMost(bytes.size)
            return if (start in 0 until end) bytes.copyOfRange(start, end) else null
        }

        override suspend fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean): Boolean {
            cancelCalls.incrementAndGet()
            return true
        }

        override suspend fun reResolveFile(chatId: Long, messageId: Long): TelegramGatewayFile? = null
    }
}
