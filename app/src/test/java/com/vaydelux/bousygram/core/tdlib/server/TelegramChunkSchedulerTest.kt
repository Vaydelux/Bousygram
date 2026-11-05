package com.vaydelux.bousygram.core.tdlib.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class TelegramChunkSchedulerTest {
    @Before
    fun setUp() {
        TelegramChunkScheduler.resetForTests()
        TelegramFileGatewayRegistry.reset()
        TelegramChunkScheduler.resumeBackgroundWork()
    }

    @After
    fun tearDown() {
        TelegramChunkScheduler.resetForTests()
        TelegramFileGatewayRegistry.reset()
        TelegramChunkScheduler.resumeBackgroundWork()
    }

    @Test
    fun networkThroughputIsNotEstimatedFromCachedReads() = runBlocking {
        assertEquals(0L, TelegramChunkScheduler.getAverageThroughput())
        val bytes = ByteArray(16 * 1024) { 7 }
        TelegramFileGatewayRegistry.installForTests(FakeTelegramFileGateway(bytes))
        val ready = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(610, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertTrue(ready is TelegramChunkResult.Ready)
        assertEquals(0L, TelegramChunkScheduler.getAverageThroughput())
    }

    @Test
    fun availablePrefixIsServedWithoutAnotherTdLibStatusQuery() = runBlocking {
        val bytes = ByteArray(16 * 1024) { 8 }
        val gateway = FakeTelegramFileGateway(
            bytes = bytes,
            completed = false,
            downloadedPrefixSize = 4096L,
            enforcePartialRanges = true
        )
        TelegramFileGatewayRegistry.installForTests(gateway)
        val result = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(612, 0L, 16 * 1024, 32, TelegramChunkPurpose.Streaming)
        )
        assertTrue(result is TelegramChunkResult.Ready)
        assertEquals(4096, (result as TelegramChunkResult.Ready).bytes.size)
        assertEquals(0, gateway.downloadedPrefixQueries.get())
    }

    @Test
    fun thousandsOfCatalogUpdatesHaveBoundedIdleFileStatusRetention() {
        val protectedFile = 10_000
        val lease = TelegramChunkScheduler.acquireLease(
            protectedFile, 0L, 4096L,
            TelegramStreamConsumer.ExoPlayer,
            TelegramChunkScheduler.PLAYBACK_PRIORITY
        )
        try {
            // Simulate browsing hundreds of chats on a low-memory TV.
            for (id in protectedFile..13_999) {
                TelegramChunkScheduler.onFileUpdated(
                    TelegramGatewayFile(
                        fileId = id,
                        localPath = "/tdlib/cache/$id",
                        isDownloadingCompleted = true,
                        downloadOffset = 0L,
                        downloadedPrefixSize = 1024L
                    )
                )
            }
            assertTrue(
                "Idle file statuses must remain within the low-memory watermark",
                TelegramChunkScheduler.retainedFileStatusCountForTests() <= 384
            )
            assertTrue(TelegramChunkScheduler.hasRetainedFileStatusForTests(protectedFile))
            assertFalse(TelegramChunkScheduler.hasRetainedFileStatusForTests(10_001))
        } finally {
            TelegramChunkScheduler.releaseLease(lease, TelegramLeaseReleaseReason.Completed)
        }

        // Released files can be evicted by the next sweep.
        for (id in 14_000..14_599) {
            TelegramChunkScheduler.onFileUpdated(
                TelegramGatewayFile(fileId = id, localPath = "/tdlib/cache/$id",
                    isDownloadingCompleted = true, downloadOffset = 0L, downloadedPrefixSize = 1024L)
            )
        }
        assertFalse(TelegramChunkScheduler.hasRetainedFileStatusForTests(protectedFile))
        assertEquals(0, TelegramChunkScheduler.activeLeaseCount(protectedFile))
        assertTrue(TelegramChunkScheduler.retainedFileStatusCountForTests() <= 384)
    }

    @Test
    fun recentlyRefreshedFileStatusSurvivesIdleStatusPruning() {
        for (id in 20_000..20_374) {
            TelegramChunkScheduler.onFileUpdated(TelegramGatewayFile(fileId = id, localPath = "/tdlib/cache/$id",
                isDownloadingCompleted = true, downloadOffset = 0L, downloadedPrefixSize = 1024L))
        }
        TelegramChunkScheduler.onFileUpdated(TelegramGatewayFile(fileId = 20_000, localPath = "/tdlib/cache/20000",
            isDownloadingCompleted = true, downloadOffset = 0L, downloadedPrefixSize = 1024L))
        for (id in 20_375..20_520) {
            TelegramChunkScheduler.onFileUpdated(TelegramGatewayFile(fileId = id, localPath = "/tdlib/cache/$id",
                isDownloadingCompleted = true, downloadOffset = 0L, downloadedPrefixSize = 1024L))
        }
        assertTrue(TelegramChunkScheduler.hasRetainedFileStatusForTests(20_000))
        assertFalse(TelegramChunkScheduler.hasRetainedFileStatusForTests(20_001))
        assertTrue(TelegramChunkScheduler.retainedFileStatusCountForTests() <= 384)
        TelegramChunkScheduler.resetForTests()
        assertEquals(0, TelegramChunkScheduler.retainedFileStatusCountForTests())
    }

    @Test
    fun networkBandwidthEstimateExpiresAfterFifteenSeconds() {
        assertFalse(TelegramChunkScheduler.isNetworkEstimateFresh(0L, 10_000L))
        assertTrue(TelegramChunkScheduler.isNetworkEstimateFresh(10_000L, 15_000L))
        assertTrue(TelegramChunkScheduler.isNetworkEstimateFresh(10_000L, 25_000L))
        assertFalse(TelegramChunkScheduler.isNetworkEstimateFresh(10_000L, 25_001L))
        assertFalse(TelegramChunkScheduler.isNetworkEstimateFresh(10_000L, 9_999L))
    }

    @Test
    fun downloadWindowProgressNeverCountsSeekJumpsAsNetworkBytes() {
        assertEquals(4096L, TelegramChunkScheduler.downloadedByteDelta(0L, 1024L, 0L, 5120L))
        assertEquals(0L, TelegramChunkScheduler.downloadedByteDelta(0L, 5120L, 8192L, 4096L))
        assertEquals(0L, TelegramChunkScheduler.downloadedByteDelta(0L, 5120L, 0L, 2048L))
    }

    @Test
    fun staleCachedFileIsMarkedForPreparationWhenTdLibReportsEviction() {
        TelegramChunkScheduler.onFileUpdated(
            TelegramGatewayFile(
                fileId = 2468, localPath = "/old/cache/video.ts",
                isDownloadingCompleted = true, downloadOffset = 0L, downloadedPrefixSize = 4096L
            )
        )
        assertEquals(PlaybackCacheState.Cached, TelegramChunkScheduler.cacheState(2468))
        TelegramChunkScheduler.onFileUpdated(
            TelegramGatewayFile(
                fileId = 2468, localPath = "",
                isDownloadingCompleted = false, downloadOffset = 0L, downloadedPrefixSize = 0L
            )
        )
        assertEquals(PlaybackCacheState.Preparing, TelegramChunkScheduler.cacheState(2468))
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
    fun offlineDownloadFailureDoesNotCreatePhantomWindowAndReconnectCanRetry() = runBlocking {
        val fileId = 617
        val bytes = ByteArray(128 * 1024) { index -> (index % 223).toByte() }
        val gateway = FakeTelegramFileGateway(
            bytes = bytes,
            completed = false,
            downloadedPrefixSize = 4096L,
            enforcePartialRanges = true
        ).apply { failTransientFileIdOnce = fileId }
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(fileId, bytes.size.toLong())

        // TDLib simulates a temporary disconnect before accepting the window.
        TelegramChunkScheduler.openPlayback(fileId, bytes.size.toLong()).join()
        assertEquals(PlaybackCacheState.Preparing, TelegramChunkScheduler.cacheState(fileId))
        assertFalse(
            "A failed native download must not leave a phantom active seek window",
            TelegramChunkScheduler.hasDownloadWindowForTests(fileId)
        )
        assertEquals(1, gateway.downloadRecords.size)

        // After connectivity returns, a second open starts a real foreground
        // window, instead of reporting an unusable pre-existing one as Ready.
        TelegramChunkScheduler.openPlayback(fileId, bytes.size.toLong()).join()
        assertTrue(TelegramChunkScheduler.hasDownloadWindowForTests(fileId))
        assertEquals(2, gateway.downloadRecords.size)
        assertEquals(TelegramChunkScheduler.PLAYBACK_PRIORITY, gateway.downloadRecords.last().priority)
        val chunk = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(fileId, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertTrue(chunk is TelegramChunkResult.Ready)
        assertArrayEquals(bytes.copyOfRange(0, 4096), (chunk as TelegramChunkResult.Ready).bytes)
    }

    @Test
    fun duplicateChunkRequestsAreCoalesced() = runBlocking {
        val bytes = ByteArray(TelegramChunkScheduler.CHUNK_SIZE_BYTES) { 7 }
        val gateway = FakeTelegramFileGateway(bytes, downloadDelayMs = 150L, simulateColdDownload = true)
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
        assertEquals(1, gateway.totalDownloadCalls())
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
        val gateway = FakeTelegramFileGateway(bytes, downloadDelayMs = 120L, simulateColdDownload = true)
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

    @Test
    fun metadataDurationProbesReadCachedHeadWithoutDownloadingMissingTail() = runBlocking {
        val size = 512 * 1024
        val bytes = ByteArray(size) { i -> (i % 239).toByte() }
        val gateway = FakeTelegramFileGateway(
            bytes = bytes,
            completed = false,
            downloadedPrefixSize = 8192L,
            enforcePartialRanges = true
        )
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(615, size.toLong())

        val cachedHead = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(615, 0L, 4096, 16, TelegramChunkPurpose.Metadata)
        )
        assertTrue(cachedHead is TelegramChunkResult.Ready)
        assertArrayEquals(bytes.copyOfRange(0, 4096), (cachedHead as TelegramChunkResult.Ready).bytes)

        val missingTail = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(615, size - 4096L, 4096, 16, TelegramChunkPurpose.Metadata)
        )
        assertTrue(missingTail is TelegramChunkResult.Pending)
        assertEquals(
            "Container metadata probes must not displace the active or upcoming video download",
            0, gateway.totalDownloadCalls()
        )

        // Foreground random-range playback is already covered separately by
        // testTailFirstRequest and seekOutsideWindowCancelsStaleDownloadAndStartsNewRangeAtHighPriority.
        // This test only asserts cache-only behavior; mixing modes in one fake
        // gateway can depend on asynchronous TDLib UpdateFile delivery.
    }

    @Test
    fun playbackKeepsOneDownloadWindowAndIgnoresBackgroundSteals() = runBlocking {
        val bytes = ByteArray(256 * 1024) { index -> (index % 97).toByte() }
        val gateway = FakeTelegramFileGateway(bytes, simulateColdDownload = true)
        TelegramFileGatewayRegistry.installForTests(gateway)

        val first = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(40, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        val second = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(40, 4096L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        val background = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(40, 8192L, 4096, 1, TelegramChunkPurpose.CacheAhead)
        )

        assertEquals(TelegramChunkResult.Ready::class, first::class)
        assertEquals(TelegramChunkResult.Ready::class, second::class)
        assertEquals(TelegramChunkResult.Ready::class, background::class)
        assertEquals(1, gateway.totalDownloadCalls())
    }

    @Test
    fun playbackCopiesTheTailOnceAndDoesNotOpenASecondDownload() = runBlocking {
        val size = 8 * 1024 * 1024
        val bytes = ByteArray(size) { index -> (index % 251).toByte() }
        val gateway = FakeTelegramFileGateway(bytes, simulateColdDownload = true)
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(77, size.toLong())

        val head = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(77, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        val tail = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(77, size - 4096L, 4096, 32, TelegramChunkPurpose.Streaming)
        )

        assertEquals(TelegramChunkResult.Ready::class, head::class)
        val readyTail = tail as TelegramChunkResult.Ready
        assertArrayEquals(bytes.copyOfRange(size - 4096, size), readyTail.bytes)
        assertEquals(1, gateway.totalDownloadCalls())
    }

    @Test
    fun tailFirstOrderingServesTailImmediatelyWithoutWaitingForCompletion() = runBlocking {
        val totalSize = 10 * 1024 * 1024 // 10 MB MP4
        val bytes = ByteArray(totalSize) { index -> (index % 251).toByte() }
        val tailOffset = totalSize - 4096L
        val gateway = FakeTelegramFileGateway(
            bytes = bytes,
            completed = false,
            downloadOffset = 0L,
            downloadedPrefixSize = 2L * 1024L * 1024L
        )
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(55, totalSize.toLong())

        // Startup must not displace the head with a speculative tail request.
        TelegramChunkScheduler.openPlayback(fileId = 55, totalSize = totalSize.toLong(), isMp4 = true).join()
        assertTrue(gateway.downloadRecords.all { it.offset == 0L })

        // Simulate TDLib delivering tail bytes first via UpdateFile
        TelegramChunkScheduler.onFileUpdated(
            TelegramGatewayFile(
                fileId = 55,
                localPath = gateway.tempFileAbsolutePath,
                isDownloadingCompleted = false,
                downloadOffset = totalSize - 2L * 1024L * 1024L,
                downloadedPrefixSize = 2L * 1024L * 1024L,
                size = totalSize.toLong()
            )
        )

        // Player requests tail first (to read moov atom at end of file)
        val tailResult = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(55, tailOffset, 4096, 32, TelegramChunkPurpose.Streaming)
        )

        val ready = tailResult as TelegramChunkResult.Ready
        assertArrayEquals(bytes.copyOfRange(tailOffset.toInt(), (tailOffset + 4096).toInt()), ready.bytes)
        // Cached tail bytes must be returned without starting a new download.
        assertTrue(gateway.downloadRecords.all { it.offset == 0L })
    }

    @Test
    fun seekOutsideWindowCancelsStaleDownloadAndStartsNewRangeAtHighPriority() = runBlocking {
        val totalSize = 100 * 1024 * 1024 // 100 MB file
        val bytes = ByteArray(totalSize) { index -> (index % 199).toByte() }
        val gateway = FakeTelegramFileGateway(bytes, completed = false, downloadOffset = 0L, downloadedPrefixSize = 4096L, enforcePartialRanges = true)
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(66, totalSize.toLong())

        // Initial playhead chunk at offset 0
        val initialResult = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(66, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertEquals(TelegramChunkResult.Ready::class, initialResult::class)

        // User seeks to 50 MB (far outside the 8MB playback window)
        val seekOffset = 50L * 1024L * 1024L
        val seekResult = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(66, seekOffset, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertEquals(TelegramChunkResult.Ready::class, seekResult::class)

        // Verify cancelDownloadFile was invoked on seek
        assertTrue(gateway.cancelledFiles.contains(66))
        assertTrue(gateway.cancelCalls.get() > 0)

        // Verify new download at seek offset was issued with PLAYBACK_PRIORITY (32)
        val seekDownload = gateway.downloadRecords.firstOrNull { it.fileId == 66 && it.offset == seekOffset }
        assertNotNull(seekDownload)
        assertEquals(TelegramChunkScheduler.PLAYBACK_PRIORITY, seekDownload!!.priority)
    }

    @Test
    fun prefetchRunsAtLowerPriorityThanAwaitedPlayback() = runBlocking {
        val bytes = ByteArray(256 * 1024) { 1 }
        val gateway = FakeTelegramFileGateway(bytes)
        TelegramFileGatewayRegistry.installForTests(gateway)

        TelegramChunkScheduler.prefetchRange(
            fileId = 80,
            offset = 0L,
            length = 4096L,
            purpose = TelegramChunkPurpose.CacheAhead,
            priority = TelegramChunkScheduler.PREFETCH_PRIORITY
        ).join()

        val prefetchDownload = gateway.downloadRecords.firstOrNull { it.fileId == 80 }
        assertNotNull(prefetchDownload)
        assertEquals(TelegramChunkScheduler.PREFETCH_PRIORITY, prefetchDownload!!.priority)
        assertTrue(prefetchDownload.priority < TelegramChunkScheduler.PLAYBACK_PRIORITY)
    }

    @Test
    fun downloadFailureReResolvesFileIdViaGetMessageAndRetriesOnce() = runBlocking {
        val totalSize = 1024 * 1024
        val bytes = ByteArray(totalSize) { 9 }
        val gateway = FakeTelegramFileGateway(bytes, completed = true, simulateColdDownload = true)
        val oldFileId = 100
        val newFileId = 101

        gateway.failFileIdOnce = oldFileId
        gateway.reResolveFileResult = TelegramGatewayFile(
            fileId = newFileId,
            localPath = gateway.tempFileAbsolutePath,
            isDownloadingCompleted = true,
            downloadOffset = 0L,
            downloadedPrefixSize = totalSize.toLong(),
            size = totalSize.toLong()
        )
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileContext(fileId = oldFileId, chatId = 12345L, messageId = 67890L)

        val result = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(oldFileId, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )

        assertEquals(TelegramChunkResult.Ready::class, result::class)
        assertEquals(1, gateway.reResolveCalls.get())
        // Verify subsequent requests for oldFileId use remapped fileId
        assertEquals(newFileId, TelegramChunkScheduler.resolveEffectiveFileId(oldFileId))
    }

    @Test
    fun testSeekMidFile() = runBlocking {
        val totalSize = 50 * 1024 * 1024 // 50 MB
        val bytes = ByteArray(totalSize) { index -> (index % 179).toByte() }
        val gateway = FakeTelegramFileGateway(bytes, completed = false, downloadOffset = 0L, downloadedPrefixSize = 8192L, enforcePartialRanges = true)
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(67, totalSize.toLong())

        // Initial playhead chunk at offset 0
        val initialResult = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(67, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertEquals(TelegramChunkResult.Ready::class, initialResult::class)

        // Mid-file seek to 25 MB
        val midFileOffset = 25L * 1024L * 1024L
        val seekResult = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(67, midFileOffset, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertEquals(TelegramChunkResult.Ready::class, seekResult::class)

        // Verify TDLib download cancellation was triggered on seek
        assertTrue(gateway.cancelledFiles.contains(67))
        assertTrue(gateway.cancelCalls.get() > 0)

        // Verify seek download was requested with PLAYBACK_PRIORITY (32) from mid-file offset
        val seekDownload = gateway.downloadRecords.firstOrNull { it.fileId == 67 && it.offset == midFileOffset }
        assertNotNull(seekDownload)
        assertEquals(TelegramChunkScheduler.PLAYBACK_PRIORITY, seekDownload!!.priority)
    }

    @Test
    fun testTailFirstRequest() = runBlocking {
        val totalSize = 16 * 1024 * 1024 // 16 MB MKV/MP4
        val bytes = ByteArray(totalSize) { index -> (index % 251).toByte() }
        val tailOffset = totalSize - 4096L
        val gateway = FakeTelegramFileGateway(
            bytes = bytes,
            completed = false,
            downloadOffset = 0L,
            downloadedPrefixSize = 2L * 1024L * 1024L,
            enforcePartialRanges = true
        )
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(70, totalSize.toLong())

        // The actual extractor request should trigger the first distant range.
        TelegramChunkScheduler.openPlayback(fileId = 70, totalSize = totalSize.toLong(), isMp4 = true).join()
        assertEquals(1, gateway.downloadRecords.size)
        assertEquals(0, gateway.downloadRecords.single().limit)
        assertEquals(0L, gateway.downloadRecords.single().offset)

        // Player requests the tail for MKV cues or MP4 moov metadata.
        val tailResult = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(70, tailOffset, 4096, 32, TelegramChunkPurpose.Streaming)
        )

        val ready = tailResult as TelegramChunkResult.Ready
        assertArrayEquals(bytes.copyOfRange(tailOffset.toInt(), (tailOffset + 4096).toInt()), ready.bytes)

        // Verify tail download was scheduled with priority 32
        val tailDownload = gateway.downloadRecords.firstOrNull { it.fileId == 70 && it.offset > 0L }
        assertNotNull(tailDownload)
        assertEquals(TelegramChunkScheduler.PLAYBACK_PRIORITY, tailDownload!!.priority)
        assertEquals(tailOffset, tailDownload.offset)
        assertEquals(0, tailDownload.limit)
    }

    @Test
    fun testClientAbort() = runBlocking {
        val totalSize = 20 * 1024 * 1024
        val bytes = ByteArray(totalSize) { index -> (index % 123).toByte() }
        val gateway = FakeTelegramFileGateway(bytes, completed = false, downloadDelayMs = 200L)
        TelegramFileGatewayRegistry.installForTests(gateway)
        TelegramChunkScheduler.noteFileSize(88, totalSize.toLong())

        // Open playback and start prefetch
        TelegramChunkScheduler.openPlayback(88, totalSize.toLong()).join()
        TelegramChunkScheduler.prefetchRange(
            fileId = 88,
            offset = 4096L,
            length = 1024L * 1024L,
            purpose = TelegramChunkPurpose.CacheAhead
        )

        // Client disconnects mid-range (e.g. ExoPlayer cancels)
        TelegramChunkScheduler.onClientDisconnected(88, offset = 4096L)

        // Allow scope coroutines to process cancellation
        kotlinx.coroutines.delay(100L)

        // Verify TDLib download was cancelled and gateway received cancel
        assertTrue(gateway.cancelledFiles.contains(88))
        assertTrue(gateway.cancelCalls.get() > 0)
        assertFalse(TelegramChunkScheduler.isPlaybackBusy(88))

        // Verify scheduler is not stuck: subsequent request succeeds immediately
        val nextResult = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(88, 0L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertEquals(TelegramChunkResult.Ready::class, nextResult::class)
    }

    @Test
    fun testSmallFiles() = runBlocking {
        // Case 1: Very small file (1.5 MB <= HEAD_BURST_BYTES 2 MB)
        val smallSize = (1.5 * 1024 * 1024).toInt()
        val smallBytes = ByteArray(smallSize) { 5 }
        val smallGateway = FakeTelegramFileGateway(smallBytes, completed = false)
        TelegramFileGatewayRegistry.installForTests(smallGateway)

        TelegramChunkScheduler.openPlayback(fileId = 91, totalSize = smallSize.toLong()).join()

        // Open-ended head keeps downloading sequentially; no separate tail download.
        val headDownload = smallGateway.downloadRecords.firstOrNull { it.fileId == 91 && it.offset == 0L }
        assertNotNull(headDownload)
        assertEquals(0, headDownload!!.limit)
        assertEquals(TelegramChunkScheduler.PLAYBACK_PRIORITY, headDownload.priority)

        // No separate tail download beyond file size
        val tailDownload = smallGateway.downloadRecords.firstOrNull { it.fileId == 91 && it.offset > 0L }
        org.junit.Assert.assertNull(tailDownload)

        // Case 2: File between 2 MB and 6 MB (3.5 MB)
        val mediumSize = (3.5 * 1024 * 1024).toInt()
        val mediumBytes = ByteArray(mediumSize) { 6 }
        val mediumGateway = FakeTelegramFileGateway(mediumBytes, completed = false)
        TelegramFileGatewayRegistry.installForTests(mediumGateway)

        TelegramChunkScheduler.openPlayback(fileId = 92, totalSize = mediumSize.toLong(), isMp4 = true).join()

        val medHead = mediumGateway.downloadRecords.firstOrNull { it.fileId == 92 && it.offset == 0L }
        assertNotNull(medHead)
        assertEquals(0, medHead!!.limit)
        assertTrue(mediumGateway.downloadRecords.all { it.offset == 0L })

        // Reopening a DataSource during sequential playback must not restart the
        // first 2 MB or evict the active TDLib download window.
        TelegramChunkScheduler.openPlayback(fileId = 92, totalSize = mediumSize.toLong(), isMp4 = true).join()
        assertEquals(1, mediumGateway.downloadRecords.size)
        val afterHead = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(92, 3L * 1024L * 1024L, 4096, 32, TelegramChunkPurpose.Streaming)
        )
        assertEquals(TelegramChunkResult.Ready::class, afterHead::class)
        assertEquals(1, mediumGateway.downloadRecords.size)

        val nonMp4Size = 8 * 1024 * 1024
        val nonMp4Gateway = FakeTelegramFileGateway(ByteArray(nonMp4Size) { 7 }, completed = false)
        TelegramFileGatewayRegistry.installForTests(nonMp4Gateway)
        TelegramChunkScheduler.openPlayback(fileId = 93, totalSize = nonMp4Size.toLong(), isMp4 = false).join()
        assertTrue(nonMp4Gateway.downloadRecords.all { it.offset == 0L })
        assertTrue(nonMp4Gateway.downloadRecords.all { it.limit == 0 })
    }

    @Test
    fun foregroundLeaseRemainsBusyWithoutRecentChunkProgress() {
        val player = TelegramChunkScheduler.acquireLease(
            502, 0L, 4096L, TelegramStreamConsumer.ExoPlayer, TelegramChunkScheduler.PLAYBACK_PRIORITY
        )
        assertTrue(TelegramChunkScheduler.isPlaybackBusy(502))
        TelegramChunkScheduler.releaseLease(player)
        assertFalse(TelegramChunkScheduler.isPlaybackBusy(502))

        val prefetch = TelegramChunkScheduler.acquireLease(
            503, 0L, 4096L, TelegramStreamConsumer.Prefetch, TelegramChunkScheduler.PREFETCH_PRIORITY
        )
        assertFalse(TelegramChunkScheduler.isPlaybackBusy(503))
        TelegramChunkScheduler.releaseLease(prefetch)
    }

    @Test
    fun datasourceSeekGapMustNotCancelCurrentPlaybackWindow() = runBlocking {
        val fileId = 504
        val gateway = FakeTelegramFileGateway(ByteArray(512 * 1024) { 9 })
        TelegramFileGatewayRegistry.installForTests(gateway)
        val session = com.vaydelux.bousygram.core.media.PlaybackSessionController.start(fileId)
        val reader = TelegramChunkScheduler.acquireLease(
            fileId, 0L, 4096L, TelegramStreamConsumer.ExoPlayer,
            TelegramChunkScheduler.PLAYBACK_PRIORITY
        )
        try {
            TelegramChunkScheduler.releaseLease(reader)
            TelegramChunkScheduler.onClientDisconnected(fileId, offset = 4096L)
            kotlinx.coroutines.delay(100L)
            assertEquals(0, TelegramChunkScheduler.activeLeaseCount(fileId))
            assertEquals(0, gateway.cancelCalls.get())
            assertTrue(com.vaydelux.bousygram.core.media.PlaybackSessionController.isCurrentPlaybackFile(fileId))
        } finally {
            com.vaydelux.bousygram.core.media.PlaybackSessionController.stop(
                session,
                com.vaydelux.bousygram.core.media.PlaybackCacheStopReason.ViewModelStop,
                cancelTdLibDownload = false
            )
        }
        TelegramChunkScheduler.onClientDisconnected(fileId, offset = 4096L)
        val deadline = System.currentTimeMillis() + 2_000L
        while (gateway.cancelCalls.get() == 0 && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10L)
        }
        assertEquals(1, gateway.cancelCalls.get())
    }

    @Test
    fun releasingOneLeaseDoesNotCancelWorkNeededByAnotherConsumer() = runBlocking {
        val gateway = FakeTelegramFileGateway(ByteArray(32 * 1024) { 1 })
        TelegramFileGatewayRegistry.installForTests(gateway)
        val exoLease = TelegramChunkScheduler.acquireLease(
            501, 0L, 4096L, TelegramStreamConsumer.ExoPlayer, TelegramChunkScheduler.PLAYBACK_PRIORITY
        )
        val httpLease = TelegramChunkScheduler.acquireLease(
            501, 0L, 4096L, TelegramStreamConsumer.LocalHttp, TelegramChunkScheduler.PLAYBACK_PRIORITY
        )

        TelegramChunkScheduler.releaseLease(exoLease)
        kotlinx.coroutines.delay(50L)
        assertEquals(1, TelegramChunkScheduler.activeLeaseCount(501))
        assertEquals(0, gateway.cancelCalls.get())

        TelegramChunkScheduler.releaseLease(httpLease)
        val deadline = System.currentTimeMillis() + 2_000L
        while (gateway.cancelCalls.get() == 0 && System.currentTimeMillis() < deadline) {
            kotlinx.coroutines.delay(10L)
        }
        assertEquals(0, TelegramChunkScheduler.activeLeaseCount(501))
        assertEquals(1, gateway.cancelCalls.get())
    }

    private class FakeTelegramFileGateway(
        private val bytes: ByteArray,
        private val completed: Boolean = true,
        private val downloadOffset: Long = 0L,
        private val downloadedPrefixSize: Long = bytes.size.toLong(),
        private val downloadDelayMs: Long = 0L,
        private val enforcePartialRanges: Boolean = false,
        private val simulateColdDownload: Boolean = false
    ) : TelegramFileGateway {
        val downloadedPrefixQueries = AtomicInteger(0)
        private val tempFile = File.createTempFile("fake-tdlib", ".bin").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
        val tempFileAbsolutePath: String get() = tempFile.absolutePath

        data class DownloadRecord(
            val fileId: Int,
            val priority: Int,
            val offset: Long,
            val limit: Int
        )

        val downloadRecords = CopyOnWriteArrayList<DownloadRecord>()
        val cancelCalls = AtomicInteger(0)
        val cancelledFiles = ConcurrentHashMap.newKeySet<Int>()
        var failFileIdOnce: Int? = null
        var failTransientFileIdOnce: Int? = null
        var reResolveFileResult: TelegramGatewayFile? = null
        val reResolveCalls = AtomicInteger(0)

        private val counts = ConcurrentHashMap<String, AtomicInteger>()
        private val activeDownloads = AtomicInteger(0)
        val maxActiveDownloads = AtomicInteger(0)

        override suspend fun downloadFile(
            fileId: Int,
            priority: Int,
            offset: Long,
            limit: Int,
            synchronous: Boolean
        ): TelegramGatewayResult<TelegramGatewayFile?> {
            val active = activeDownloads.incrementAndGet()
            maxActiveDownloads.updateAndGet { previous -> maxOf(previous, active) }
            if (downloadDelayMs > 0L) {
                withContext(Dispatchers.IO) {
                    Thread.sleep(downloadDelayMs)
                }
            }
            return try {
                downloadRecords.add(DownloadRecord(fileId, priority, offset, limit))
                if (failFileIdOnce == fileId) {
                    failFileIdOnce = null
                    TelegramGatewayResult.Failure(code = 400, message = "FILE_REFERENCE_EXPIRED", fatal = true)
                } else if (failTransientFileIdOnce == fileId) {
                    failTransientFileIdOnce = null
                    TelegramGatewayResult.Failure(code = 503, message = "Temporary network unavailable", fatal = false)
                } else {
                    counts.getOrPut("$fileId:$offset:$limit") { AtomicInteger(0) }.incrementAndGet()
                    TelegramGatewayResult.Success(file(fileId, offset = offset))
                }
            } finally {
                activeDownloads.decrementAndGet()
            }
        }

        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long {
            downloadedPrefixQueries.incrementAndGet()
            if (isStillCold(fileId)) return 0L
            if (!enforcePartialRanges || completed) return bytes.size.toLong()
            val windowStart = downloadRecords.lastOrNull { it.fileId == fileId }?.offset ?: downloadOffset
            return if (offset < windowStart) 0L
                else (downloadedPrefixSize - (offset - windowStart)).coerceAtLeast(0L)
        }

        override suspend fun getFile(fileId: Int): TelegramGatewayFile =
            file(fileId)

        override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? {
            if (isStillCold(fileId)) return null
            if (enforcePartialRanges && !completed) {
                val windowStart = downloadRecords.lastOrNull { it.fileId == fileId }?.offset ?: downloadOffset
                if (offset < windowStart || offset + limit > windowStart + downloadedPrefixSize) return null
            }
            val start = offset.toInt()
            val end = (offset + limit).toInt().coerceAtMost(bytes.size)
            return if (start in 0 until end && end - start == limit) bytes.copyOfRange(start, end) else null
        }

        override suspend fun cancelDownloadFile(fileId: Int, onlyIfPending: Boolean): Boolean {
            cancelCalls.incrementAndGet()
            cancelledFiles.add(fileId)
            return true
        }

        override suspend fun reResolveFile(chatId: Long, messageId: Long): TelegramGatewayFile? {
            reResolveCalls.incrementAndGet()
            return reResolveFileResult
        }

        fun totalDownloadCalls(): Int = counts.values.sumOf { it.get() }

        private fun isStillCold(fileId: Int): Boolean =
            simulateColdDownload && downloadRecords.none { it.fileId == fileId }

        private fun file(fileId: Int, offset: Long? = null): TelegramGatewayFile {
            val effOffset = offset ?: downloadRecords.lastOrNull { it.fileId == fileId }?.offset ?: downloadOffset
            return TelegramGatewayFile(
                fileId = fileId,
                localPath = tempFile.absolutePath,
                isDownloadingCompleted = completed && !isStillCold(fileId),
                downloadOffset = effOffset,
                downloadedPrefixSize = when {
                    isStillCold(fileId) -> 0L
                    completed -> bytes.size.toLong()
                    else -> downloadedPrefixSize
                }
            )
        }
    }
}
