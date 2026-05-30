package com.vaydelux.bousygram.core.tdlib.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TelegramChunkSchedulerTest {
    @After
    fun tearDown() {
        TelegramFileGatewayRegistry.reset()
        TelegramChunkScheduler.resumeBackgroundWork()
    }

    @Test
    fun fetchChunkReadsExactOrderedByteRange() = runBlocking {
        val bytes = ByteArray(TelegramChunkScheduler.CHUNK_SIZE_BYTES * 2) { index ->
            (index % 251).toByte()
        }
        val gateway = FakeTelegramFileGateway(bytes)
        TelegramFileGatewayRegistry.installForTests(gateway)

        val result = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(
                fileId = 10,
                offset = 128L,
                limit = 4096,
                priority = 32,
                purpose = TelegramChunkPurpose.Streaming
            )
        )

        val ready = result as TelegramChunkResult.Ready
        assertArrayEquals(bytes.copyOfRange(128, 128 + 4096), ready.bytes)
        assertEquals(TelegramChunkReadSource.DirectDisk, ready.source)
    }

    @Test
    fun duplicateChunkRequestsAreCoalesced() = runBlocking {
        val bytes = ByteArray(TelegramChunkScheduler.CHUNK_SIZE_BYTES) { 7 }
        val gateway = FakeTelegramFileGateway(bytes, downloadDelayMs = 150L)
        TelegramFileGatewayRegistry.installForTests(gateway)

        val first = async(Dispatchers.IO) {
            TelegramChunkScheduler.fetchChunk(
                TelegramChunkRequest(11, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
            )
        }
        val second = async(Dispatchers.IO) {
            TelegramChunkScheduler.fetchChunk(
                TelegramChunkRequest(11, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
            )
        }

        assertEquals(TelegramChunkResult.Ready::class, first.await()::class)
        assertEquals(TelegramChunkResult.Ready::class, second.await()::class)
        assertEquals(1, gateway.downloadCount(11, 0L, 4096))
    }

    @Test
    fun readFilePartFallbackIsUsedWhenDiskWindowIsUnsafe() = runBlocking {
        val bytes = ByteArray(8192) { index -> (index % 127).toByte() }
        val gateway = FakeTelegramFileGateway(
            bytes = bytes,
            completed = false,
            downloadOffset = 4096L,
            downloadedPrefixSize = 1024L
        )
        TelegramFileGatewayRegistry.installForTests(gateway)

        val result = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(12, 0L, 2048, 32, TelegramChunkPurpose.Streaming)
        )

        val ready = result as TelegramChunkResult.Ready
        assertArrayEquals(bytes.copyOfRange(0, 2048), ready.bytes)
        assertEquals(TelegramChunkReadSource.ReadFilePart, ready.source)
    }

    @Test
    fun activeChunkDownloadsAreSerializedPerFile() = runBlocking {
        val bytes = ByteArray(TelegramChunkScheduler.CHUNK_SIZE_BYTES * 8) { 3 }
        val gateway = FakeTelegramFileGateway(bytes, downloadDelayMs = 120L)
        TelegramFileGatewayRegistry.installForTests(gateway)

        val jobs = (0 until 12).map { index ->
            async(Dispatchers.IO) {
                TelegramChunkScheduler.fetchChunk(
                    TelegramChunkRequest(
                        fileId = 13,
                        offset = index * 1024L,
                        limit = 1024,
                        priority = 32,
                        purpose = TelegramChunkPurpose.Streaming
                    )
                )
            }
        }
        jobs.forEach { assertEquals(TelegramChunkResult.Ready::class, it.await()::class) }

        assertEquals(TelegramChunkScheduler.MAX_CONCURRENT_CHUNKS_PER_FILE, gateway.maxActiveDownloads.get())
    }

    private class FakeTelegramFileGateway(
        private val bytes: ByteArray,
        private val completed: Boolean = true,
        private val downloadOffset: Long = 0L,
        private val downloadedPrefixSize: Long = bytes.size.toLong(),
        private val downloadDelayMs: Long = 0L
    ) : TelegramFileGateway {
        private val tempFile = File.createTempFile("fake-tdlib", ".bin").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        private val counts = ConcurrentHashMap<String, AtomicInteger>()
        private val activeDownloads = AtomicInteger(0)
        val maxActiveDownloads = AtomicInteger(0)

        override suspend fun downloadFile(
            fileId: Int,
            priority: Int,
            offset: Long,
            limit: Int
        ): TelegramGatewayResult<TelegramGatewayFile?> {
            val active = activeDownloads.incrementAndGet()
            maxActiveDownloads.updateAndGet { previous -> maxOf(previous, active) }
            if (downloadDelayMs > 0L) {
                withContext(Dispatchers.IO) {
                    Thread.sleep(downloadDelayMs)
                }
            }
            return try {
                counts.getOrPut("$fileId:$offset:$limit") { AtomicInteger(0) }.incrementAndGet()
                TelegramGatewayResult.Success(file(fileId))
            } finally {
                activeDownloads.decrementAndGet()
            }
        }

        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long =
            bytes.size.toLong().coerceAtLeast(downloadedPrefixSize)

        override suspend fun getFile(fileId: Int): TelegramGatewayFile =
            file(fileId)

        override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? {
            val start = offset.toInt()
            val end = (offset + limit).toInt().coerceAtMost(bytes.size)
            return if (start in 0 until end && end - start == limit) bytes.copyOfRange(start, end) else null
        }

        fun downloadCount(fileId: Int, offset: Long, limit: Int): Int =
            counts["$fileId:$offset:$limit"]?.get() ?: 0

        private fun file(fileId: Int): TelegramGatewayFile =
            TelegramGatewayFile(
                fileId = fileId,
                localPath = tempFile.absolutePath,
                isDownloadingCompleted = completed,
                downloadOffset = downloadOffset,
                downloadedPrefixSize = downloadedPrefixSize
            )
    }
}
