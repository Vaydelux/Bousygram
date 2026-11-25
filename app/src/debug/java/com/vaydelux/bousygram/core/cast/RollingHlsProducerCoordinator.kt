package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.server.SharedTelegramFileMutationFence
import com.vaydelux.bousygram.core.tdlib.server.TelegramFileMutationFence
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * One process-wide qualification binding. No live caller, native library load
 * or receiver registration. Keep the slot across logout and worker cancellation
 * until reservation and scratch cleanup are independently confirmed.
 */
internal object RollingHlsProducerCoordinator {
    private val lifetime = RollingHlsProducerLifetime(AccountSession::isCurrent)
    private val fence = SharedTelegramFileMutationFence.instance

    class Session internal constructor(
        private val ticket: RollingHlsProducerLifetime.Ticket,
        private val claim: TelegramFileMutationFence.Claim,
        private val ownership: RollingHlsDownloadOwnership,
        private val lease: RollingHlsDownloadOwnership.Lease,
        private val driver: RollingHlsOwnedWindowDriver,
        private val routeAllowed: () -> Boolean
    ) {
        fun active(): Boolean = lifetime.permitted(ticket) && fence.isCurrent(claim) && routeAllowed()

        /** Run the one conversion worker exactly once, on the caller's IO dispatcher.
         * A synchronous native call must have returned before the block exits. */
        suspend fun <T> runWorker(block: suspend (RollingHlsOwnedWindowDriver,
            RollingHlsDownloadOwnership.Lease) -> T): T {
            check(active() && lifetime.beginWorker(ticket)) { "Producer unavailable" }
            try {
                check(active())
                return block(driver, lease)
            } finally {
                retire()
                lifetime.workerExited(ticket)
            }
        }

        fun retire() {
            lifetime.retire(ticket)
            ownership.retire(lease)
        }

        suspend fun reconcile(window: RollingHlsDownloadOwnership.Window): Boolean {
            if (window.lease !== lease) return false
            return driver.reconcile(window)
        }

        /** Must be called only on independently confirmed old-client teardown.
         * A lost callback or account invalidation is NOT sufficient. */
        fun confirmOldAccountTeardown(nativeTeardownConfirmed: Boolean): Boolean =
            ownership.confirmAccountTeardown(lease, nativeTeardownConfirmed)

        /** Call after worker exit and actual scratch/reader cleanup. If the
         * native outcome is uncertain, keep this session and retry reconciliation.
         * Release the claim before the global slot becomes available. */
        suspend fun releaseIfClean(scratchClean: Boolean): Boolean = withContext(NonCancellable) {
            retire()
            if (!scratchClean || ownership.state().reservedBytes != 0L ||
                !lifetime.readyToRelease(ticket)) return@withContext false
            fence.release(claim)
            lifetime.release(ticket, ownership.state().reservedBytes, scratchClean)
        }
    }

    suspend fun acquire(owner: AccountSnapshot, fileId: Int, size: Long,
                        routeAllowed: () -> Boolean, conflicts: (Int) -> Boolean,
                        measure: () -> RollingHlsDownloadOwnership.Storage): Session? {
        if (size <= 0 || !routeAllowed()) return null
        val ticket = lifetime.acquire(owner, fileId) ?: return null
        var claim: TelegramFileMutationFence.Claim? = null
        var handedOff = false
        try {
            val acquired = fence.acquire(owner, fileId) {
                !lifetime.permitted(ticket) || !routeAllowed() || conflicts(fileId)
            } ?: return null
            claim = acquired
            val ownership = RollingHlsDownloadOwnership(AccountSession::isCurrent,
                { System.nanoTime() / 1_000_000L },
                { id -> !lifetime.permitted(ticket) || !fence.isCurrent(acquired) ||
                    !routeAllowed() || conflicts(id) })
            val lease = ownership.acquire(owner, fileId, size) ?: return null
            val driver = RollingHlsOwnedWindowDriver(ownership, AccountBoundCastFileGateway(acquired),
                acquired.lock, measure, AccountSession::isCurrent, commandOwner = acquired)
            val session = Session(ticket, acquired, ownership, lease, driver, routeAllowed)
            if (!session.active()) return null
            handedOff = true
            return session
        } finally {
            if (!handedOff) withContext(NonCancellable) {
                claim?.let { fence.release(it) }
                lifetime.retire(ticket)
                lifetime.release(ticket, 0L, scratchClean = true)
            }
        }
    }
}
