package com.vaydelux.bousygram.core.cast

import java.io.IOException
import kotlinx.coroutines.delay

/**
 * Sequential source adapter for one owned producer worker. No TDLib calls
 * outside the driver. Renew a bounded native window only after the previous
 * command is known inactive and its reservation reconciled.
 *
 * All fetches are serialized by RollingHlsSource; this adapter is not a random
 * access or retrying network downloader. Missing data is bounded, never EOF.
 * Session retirement/reconciliation remains the coordinator's responsibility.
 */
internal class RollingHlsOwnedSource(
    private val driver: RollingHlsOwnedWindowDriver,
    private val lease: RollingHlsDownloadOwnership.Lease,
    private val permitted: () -> Boolean,
    private val waitForData: suspend (Long) -> Unit = { delay(it) }
) {
    private var window: RollingHlsDownloadOwnership.Window? = null
    private var cursor = 0L
    private var failed = false
    private var opened = false

    fun source(): RollingHlsSource {
        check(!opened)
        opened = true
        return RollingHlsSource(lease.size, { !failed && permitted() }, ::fetch)
    }

    private fun access() {
        if (failed || !permitted()) throw IOException("Owned source revoked")
    }

    private suspend fun fetch(offset: Long, count: Int): ByteArray? {
        try {
            access()
            if (offset != cursor || count <= 0 || cursor >= lease.size)
                throw IOException("Owned source requires sequential reads")
            val previous = window
            if (previous != null && cursor == previous.endExclusive) {
                var reconciled = false
                for (attempt in 0 until ATTEMPTS) {
                    access()
                    if (driver.reconcile(previous)) {
                        reconciled = true
                        break
                    }
                    if (attempt + 1 < ATTEMPTS) waitForData(RETRY_MS)
                }
                if (!reconciled) throw IOException("Previous native window remains active")
                window = null
            }
            val current = window ?: when (val result = driver.open(lease, cursor)) {
                is RollingHlsOwnedWindowDriver.Open.Ready -> result.window.also { window = it }
                is RollingHlsOwnedWindowDriver.Open.Uncertain ->
                    throw IOException("Native window outcome uncertain")
                RollingHlsOwnedWindowDriver.Open.Rejected ->
                    throw IOException("Native window admission rejected")
            }
            val take = minOf(count.toLong(), RollingHlsDownloadOwnership.MAX_READ_BYTES.toLong(),
                current.endExclusive - cursor).toInt()
            if (take <= 0) throw IOException("Invalid owned source window")
            for (attempt in 0 until ATTEMPTS) {
                access()
                val bytes = driver.read(current, cursor, take)
                if (!permitted()) {
                    bytes?.fill(0)
                    throw IOException("Owned source revoked during read")
                }
                if (bytes != null) {
                    cursor += bytes.size
                    return bytes
                }
                if (attempt + 1 < ATTEMPTS) waitForData(RETRY_MS)
            }
            return null
        } catch (error: Exception) {
            failed = true
            throw error
        }
    }

    companion object {
        private const val ATTEMPTS = 6
        private const val RETRY_MS = 100L
    }
}
