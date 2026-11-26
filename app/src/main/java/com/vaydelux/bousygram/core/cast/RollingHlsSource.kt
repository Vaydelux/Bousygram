package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkPurpose
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkRequest
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkResult
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** One bounded, serialized random-access read. Missing bytes are not EOF. */
internal class RollingHlsSource(
    val size: Long,
    private val permitted: () -> Boolean,
    private val fetch: suspend (Long, Int) -> ByteArray?
) {
    sealed interface Read {
        data class Ready(val bytes: ByteArray) : Read
        data object Pending : Read
        data object End : Read
    }

    private val lock = Mutex()
    @Volatile private var closed = false

    init { require(size > 0L) }

    private fun checkAccess() {
        if (closed || !permitted()) throw IOException("HLS source authorization revoked")
    }

    suspend fun readAt(offset: Long, requested: Int): Read = lock.withLock {
        checkAccess()
        require(offset in 0L..size && requested > 0)
        if (offset == size) return@withLock Read.End
        val limit = minOf(requested.toLong(), MAX_READ_BYTES.toLong(), size - offset).toInt()
        val bytes = withTimeout(READ_TIMEOUT_MS) { fetch(offset, limit) }
        checkAccess()
        if (bytes == null) return@withLock Read.Pending
        if (bytes.isEmpty() || bytes.size > limit) throw IOException("Invalid HLS source read length")
        Read.Ready(bytes)
    }

    /** Cooperative revocation; a pending fetch is bounded by its timeout. */
    fun close() { closed = true }

    companion object {
        const val MAX_READ_BYTES = 256 * 1024
        private const val READ_TIMEOUT_MS = 5_000L
    }
}

/**
 * Qualification-only, observational adapter. Caller must first verify the
 * message, protection flags and file identity with the same account snapshot.
 * Metadata reads never initiate/reposition the foreground download window.
 * Missing input needs a future producer-owned scheduler protocol, not busy polling.
 */
internal fun telegramRollingHlsSource(
    owner: AccountSnapshot,
    fileId: Int,
    size: Long,
    stillOwnsRoute: () -> Boolean
): RollingHlsSource {
    require(fileId > 0)
    fun permitted(): Boolean = AccountSession.isCurrent(owner) && stillOwnsRoute() &&
        TelegramChunkScheduler.resolveEffectiveFileId(fileId) == fileId
    check(permitted()) { "HLS source owner is unavailable" }
    return RollingHlsSource(size, ::permitted) { offset, limit ->
        when (val result = TelegramChunkScheduler.fetchChunk(
            TelegramChunkRequest(fileId, offset, limit, TelegramChunkScheduler.PLAYBACK_PRIORITY,
                TelegramChunkPurpose.Metadata)
        )) {
            is TelegramChunkResult.Ready -> result.bytes
            TelegramChunkResult.Pending -> null
            is TelegramChunkResult.RetryableFailure -> null
            is TelegramChunkResult.FatalFailure -> throw IOException("HLS source read failed")
        }
    }
}
