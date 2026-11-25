package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSessionController
import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Owns exactly one verified remux artifact. The future caller must capture the
 * owner BEFORE reading its authorized, non-protected TDLib file. This class is
 * not a substitute for message-level permission or source provenance checks.
 * No arbitrary paths accepted: only a successful remux output.
 */
internal class CastRemuxArtifact private constructor(
    val owner: AccountSnapshot,
    val fileId: Int,
    private val output: BoundedTelegramRemuxer.Output,
    private val accounts: AccountSessionController
) : AutoCloseable {
    val size: Long = output.file.length()
    private val modified = output.file.lastModified()
    @Volatile private var closed = false

    fun isAvailable(): Boolean = !closed && accounts.isCurrent(owner) &&
        size in 1L..BoundedTelegramRemuxer.MAX_OUTPUT_BYTES &&
        output.file.isFile && output.file.length() == size &&
        output.file.lastModified() == modified

    /** Ranges are offsets into the generated MP4, never into the TDLib source. */
    suspend fun stream(start: Long, end: Long, permitted: () -> Boolean,
                       write: suspend (ByteArray, Int) -> Unit) {
        if (start < 0 || end < start || end >= size || !isAvailable() || !permitted()) {
            throw IOException("Remux range is unavailable")
        }
        RandomAccessFile(output.file, "r").use { file ->
            if (file.length() != size) throw IOException("Remux output changed")
            file.seek(start)
            var remaining = end - start + 1
            val buffer = ByteArray(64 * 1024)
            while (remaining > 0) {
                if (!isAvailable() || !permitted()) throw IOException("Remux authorization expired")
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                file.readFully(buffer, 0, count)
                if (!isAvailable() || !permitted()) throw IOException("Remux authorization expired")
                write(buffer, count)
                remaining -= count
            }
        }
    }

    @Synchronized override fun close() {
        closed = true
        output.close()
    }

    companion object {
        /** Ownership transfers only if a current account and valid output are present. */
        fun adopt(owner: AccountSnapshot, fileId: Int, output: BoundedTelegramRemuxer.Output,
                  accounts: AccountSessionController = AccountSession): CastRemuxArtifact? {
            if (fileId <= 0 || !accounts.isCurrent(owner)) return null
            return CastRemuxArtifact(owner, fileId, output, accounts).takeIf { it.isAvailable() }
        }
    }
}
