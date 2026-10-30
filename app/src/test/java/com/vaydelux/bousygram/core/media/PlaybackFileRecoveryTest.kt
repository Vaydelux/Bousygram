package com.vaydelux.bousygram.core.media

import com.vaydelux.bousygram.core.tdlib.server.TelegramFileGateway
import com.vaydelux.bousygram.core.tdlib.server.TelegramGatewayFile
import com.vaydelux.bousygram.core.tdlib.server.TelegramGatewayResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackFileRecoveryTest {
    private fun file(id: Int, size: Long = 1234L) = TelegramGatewayFile(
        fileId = id, localPath = "", isDownloadingCompleted = false,
        downloadOffset = 0, downloadedPrefixSize = 0, size = size
    )

    private class Gateway(
        var messageResult: TelegramGatewayFile? = null,
        var fileResult: TelegramGatewayFile? = null,
        var failMessage: Boolean = false,
        var failFile: Boolean = false,
        var messageDelayMs: Long = 0
    ) : TelegramFileGateway {
        var messageCalls = 0
        var fileCalls = 0
        override suspend fun downloadFile(
            fileId: Int, priority: Int, offset: Long, limit: Int, synchronous: Boolean
        ): TelegramGatewayResult<TelegramGatewayFile?> = TelegramGatewayResult.Success(null)
        override suspend fun downloadedPrefixSize(fileId: Int, offset: Long) = 0L
        override suspend fun getFile(fileId: Int): TelegramGatewayFile? {
            fileCalls++
            if (failFile) error("TDLib GetFile failed")
            return fileResult
        }
        override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? = null
        override suspend fun reResolveFile(chatId: Long, messageId: Long): TelegramGatewayFile? {
            messageCalls++
            if (messageDelayMs > 0) delay(messageDelayMs)
            if (failMessage) error("TDLib GetMessage failed")
            return messageResult
        }
    }

    @Test fun changedFileIdComesFromTheMessageNotTheOldIndexedFile() = runTest {
        val gateway = Gateway(messageResult = file(999), fileResult = file(123))
        val found = PlaybackFileRecovery(gateway).refresh(1L, 2L, 123)
        assertEquals(999, found?.fileId)
        assertEquals(0, gateway.fileCalls)
    }

    @Test fun sameFileIdStillRefreshesItsAuthoritativeSize() = runTest {
        val gateway = Gateway(messageResult = file(123, 9_999L))
        assertEquals(9_999L, PlaybackFileRecovery(gateway).refresh(1, 2, 123)?.size)
    }

    @Test fun failedMessageLookupFallsBackToGetFileWithoutHanging() = runTest {
        val gateway = Gateway(failMessage = true, fileResult = file(123))
        assertEquals(123, PlaybackFileRecovery(gateway).refresh(1, 2, 123)?.fileId)
        assertEquals(1, gateway.fileCalls)
    }

    @Test fun failedBothLookupsReturnsNull() = runTest {
        val gateway = Gateway(failMessage = true, failFile = true)
        assertNull(PlaybackFileRecovery(gateway).refresh(1, 2, 123))
    }

    @Test fun timedOutMessageLookupFallsBackToNativeFile() = runTest {
        val gateway = Gateway(messageDelayMs = 3_000L, fileResult = file(123))
        val result = PlaybackFileRecovery(gateway, messageTimeoutMs = 100L).refresh(1, 2, 123)
        assertEquals(123, result?.fileId)
    }

    @Test fun invalidNativeFileIdentityIsNeverAdopted() = runTest {
        val gateway = Gateway(messageResult = file(-3), fileResult = file(22))
        assertEquals(22, PlaybackFileRecovery(gateway).refresh(1, 2, 22)?.fileId)
        gateway.fileResult = file(999)
        assertNull(PlaybackFileRecovery(gateway).refresh(1, 2, 22))
        assertNull(PlaybackFileRecovery(gateway).refresh(1, 2, 0))
    }

    @Test fun parentCancellationIsNotSilentlySwallowed() = runTest {
        val gateway = object : TelegramFileGateway {
            override suspend fun downloadFile(
                fileId: Int, priority: Int, offset: Long, limit: Int, synchronous: Boolean
            ): TelegramGatewayResult<TelegramGatewayFile?> = TelegramGatewayResult.Success(null)
            override suspend fun downloadedPrefixSize(fileId: Int, offset: Long) = 0L
            override suspend fun getFile(fileId: Int): TelegramGatewayFile? = null
            override suspend fun readFilePart(fileId: Int, offset: Long, limit: Int): ByteArray? = null
            override suspend fun reResolveFile(chatId: Long, messageId: Long): TelegramGatewayFile? =
                throw CancellationException("Player scope disposed")
        }
        try {
            PlaybackFileRecovery(gateway).refresh(1, 2, 10)
            throw AssertionError("Expected cancellation")
        } catch (cancelled: CancellationException) {
            assertEquals("Player scope disposed", cancelled.message)
        }
    }
}
