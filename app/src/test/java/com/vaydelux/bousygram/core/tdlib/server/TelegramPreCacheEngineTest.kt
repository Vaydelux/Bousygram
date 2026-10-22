package com.vaydelux.bousygram.core.tdlib.server

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelegramPreCacheEngineTest {
    @After
    fun tearDown() {
        TelegramPreCacheEngine.stopAllCaching(cancelTdLibDownload = false)
        TelegramChunkScheduler.resetForTests()
        TelegramFileGatewayRegistry.reset()
        TelegramChunkScheduler.resumeBackgroundWork()
    }

    @Test
    fun cacheAheadIsTrackedAndStopCachingCancelsIt() = runBlocking {
        TelegramFileGatewayRegistry.installForTests(SlowTelegramFileGateway())

        TelegramPreCacheEngine.cacheAhead(
            fileId = 21,
            totalSize = 128L * 1024L * 1024L,
            positionMs = 0L,
            durationMs = 60_000L
        )

        assertTrue(awaitCaching(21))

        TelegramPreCacheEngine.stopCaching(21, cancelTdLibDownload = false)

        assertFalse(awaitCaching(21))
    }

    @Test
    fun stopCachingOneFileDoesNotCancelAnotherFile() = runBlocking {
        TelegramFileGatewayRegistry.installForTests(SlowTelegramFileGateway())

        TelegramPreCacheEngine.cacheAhead(31, 128L * 1024L * 1024L, 0L, 60_000L)
        TelegramPreCacheEngine.cacheAhead(32, 128L * 1024L * 1024L, 0L, 60_000L)

        assertTrue(awaitCaching(31))
        assertTrue(awaitCaching(32))

        TelegramPreCacheEngine.stopCaching(31, cancelTdLibDownload = false)

        assertFalse(awaitCaching(31))
        assertTrue(awaitCaching(32))
    }

    private suspend fun awaitCaching(fileId: Int): Boolean {
        repeat(20) {
            if (TelegramPreCacheEngine.isCachingForTests(fileId)) return true
            delay(25L)
        }
        return false
    }

    private class SlowTelegramFileGateway : TelegramFileGateway {
        override suspend fun downloadFile(
            fileId: Int,
            priority: Int,
            offset: Long,
            limit: Int,
            synchronous: Boolean
        ): TelegramGatewayResult<TelegramGatewayFile?> {
            delay(10_000L)
            return TelegramGatewayResult.Success(file(fileId))
        }

        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long): Long = 0L

        override suspend fun getFile(fileId: Int): TelegramGatewayFile = file(fileId)

        override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? = null

        private fun file(fileId: Int): TelegramGatewayFile =
            TelegramGatewayFile(
                fileId = fileId,
                localPath = "",
                isDownloadingCompleted = false,
                downloadOffset = 0L,
                downloadedPrefixSize = 0L
            )
    }
}
