package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Qualification driver for one producer lease. The supplied mutex must be
 * shared with every competing same-file command (including cancel/remap).
 * Until that invariant is enforced across the real scheduler, do not install
 * this driver in live playback or call it for private media.
 */
internal class RollingHlsOwnedWindowDriver(
    private val ownership: RollingHlsDownloadOwnership,
    private val gateway: Gateway,
    private val commandLock: Mutex,
    private val measure: () -> RollingHlsDownloadOwnership.Storage,
    private val current: (AccountSnapshot) -> Boolean,
    private val commandOwner: Any? = null
) {
    data class Status(val fileId: Int, val size: Long, val active: Boolean)
    interface Gateway {
        suspend fun status(owner: AccountSnapshot, fileId: Int): Status?
        suspend fun start(owner: AccountSnapshot, fileId: Int, offset: Long, limit: Int): Status?
        suspend fun read(owner: AccountSnapshot, fileId: Int, offset: Long, count: Int): ByteArray?
    }

    sealed interface Open {
        data class Ready(val window: RollingHlsDownloadOwnership.Window) : Open
        data object Rejected : Open
        /** May have dispatched; no retry or release without stop/teardown proof. */
        data class Uncertain(val window: RollingHlsDownloadOwnership.Window) : Open
    }

    private fun matches(ticket: RollingHlsDownloadOwnership.Window, status: Status?): Boolean =
        status != null && status.fileId == ticket.lease.fileId && status.size == ticket.lease.size &&
            current(ticket.lease.owner)

    suspend fun open(lease: RollingHlsDownloadOwnership.Lease, offset: Long): Open =
        commandLock.withLock(owner = commandOwner) {
            if (!current(lease.owner)) return@withLock Open.Rejected
            // Never replace an existing native request, even without a scheduler lease.
            val before = gateway.status(lease.owner, lease.fileId)
            if (before == null || before.active || before.fileId != lease.fileId ||
                before.size != lease.size || !current(lease.owner)) return@withLock Open.Rejected
            val ticket = ownership.reserve(lease, offset, measure()) ?: return@withLock Open.Rejected
            if (!ownership.beginIssue(ticket, measure())) {
                ownership.retire(lease)
                return@withLock Open.Rejected
            }
            // A timeout can mean native dispatch succeeded but the callback was
            // lost. Do not acknowledge an uncertain exception as a completed command.
            val response = try {
                gateway.start(lease.owner, lease.fileId, ticket.offset, ticket.limit)
            } catch (cancelled: CancellationException) {
                ownership.retire(lease)
                throw cancelled
            } catch (_: Exception) {
                ownership.retire(lease)
                return@withLock Open.Uncertain(ticket)
            }
            // A null, mismatched, or stale response cannot prove that the
            // exact native command finished dispatching. Do not clear the
            // outstanding issue flag even if a later GetFile is inactive.
            if (!matches(ticket, response)) {
                ownership.retire(lease)
                return@withLock Open.Uncertain(ticket)
            }
            if (!ownership.acknowledge(ticket, true)) {
                ownership.retire(lease)
                return@withLock Open.Uncertain(ticket)
            }
            Open.Ready(ticket)
        }

    /** Missing data stays pending. Never expose bytes after revoke/takeover. */
    suspend fun read(ticket: RollingHlsDownloadOwnership.Window, offset: Long, count: Int): ByteArray? =
        commandLock.withLock(owner = commandOwner) {
            var bytes: ByteArray? = null
            try {
                currentCoroutineContext().ensureActive()
                if (!ownership.canRead(ticket, offset, count)) throw IOException("Producer window unavailable")
                val received = gateway.read(ticket.lease.owner, ticket.lease.fileId, offset, count)
                bytes = received
                currentCoroutineContext().ensureActive()
                if (!ownership.canRead(ticket, offset, count)) throw IOException("Producer ownership changed")
                if (received == null) return@withLock null
                if (received.size !in 1..count || !ownership.consumed(ticket, offset, received.size))
                    throw IOException("Invalid producer source response")
                currentCoroutineContext().ensureActive()
                received
            } catch (error: Exception) {
                bytes?.fill(0)
                // A failed or cancelled read must never resume this lease after
                // playback conflict clears or a consumed cursor was not delivered.
                ownership.retire(ticket.lease)
                throw error
            }
        }

    /** Never cancel a potentially shared native download. Inactive evidence
     * and refreshed accounting must be collected inside the command fence. */
    enum class Reconciliation { RECONCILED, WAITING, STORAGE_PRESSURE, UNPROVEN }

    suspend fun reconcile(ticket: RollingHlsDownloadOwnership.Window): Boolean =
        reconcileOutcome(ticket) == Reconciliation.RECONCILED

    /** Distinguish a temporarily active download from a storage limit or
     * uncertain command. Consumed bytes are NOT assumed reclaimed by TDLib. */
    suspend fun reconcileOutcome(ticket: RollingHlsDownloadOwnership.Window): Reconciliation =
        commandLock.withLock(owner = commandOwner) {
            if (!current(ticket.lease.owner) || ownership.pendingWindow(ticket.lease) !== ticket)
                return@withLock Reconciliation.UNPROVEN
            val status = gateway.status(ticket.lease.owner, ticket.lease.fileId)
            if (!matches(ticket, status)) return@withLock Reconciliation.UNPROVEN
            if (status!!.active) return@withLock Reconciliation.WAITING
            val storage = measure()
            if (!RollingHlsBudget.admits(storage.appBytes, storage.tdLibBytes, storage.freeBytes, 0L))
                return@withLock Reconciliation.STORAGE_PRESSURE
            if (ownership.confirmStopped(ticket, storage, nativeInactive = true))
                Reconciliation.RECONCILED
            else Reconciliation.UNPROVEN
        }
}
