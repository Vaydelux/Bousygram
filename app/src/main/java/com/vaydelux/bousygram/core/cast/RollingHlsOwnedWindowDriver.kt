package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import kotlinx.coroutines.CancellationException
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
            // A null response cannot prove the command returned at native level.
            if (response == null) {
                ownership.retire(lease)
                return@withLock Open.Uncertain(ticket)
            }
            if (!ownership.acknowledge(ticket, matches(ticket, response))) {
                ownership.retire(lease)
                return@withLock Open.Uncertain(ticket)
            }
            Open.Ready(ticket)
        }

    /** Missing data stays pending. Never expose bytes after revoke/takeover. */
    suspend fun read(ticket: RollingHlsDownloadOwnership.Window, offset: Long, count: Int): ByteArray? =
        commandLock.withLock(owner = commandOwner) {
            if (!ownership.canRead(ticket, offset, count)) throw IOException("Producer window unavailable")
            val bytes = gateway.read(ticket.lease.owner, ticket.lease.fileId, offset, count)
            if (!ownership.canRead(ticket, offset, count)) {
                bytes?.fill(0)
                throw IOException("Producer ownership changed")
            }
            if (bytes == null) return@withLock null
            if (bytes.size !in 1..count || !ownership.consumed(ticket, offset, bytes.size)) {
                bytes.fill(0)
                ownership.retire(ticket.lease)
                throw IOException("Invalid producer source response")
            }
            bytes
        }

    /** Never cancel a potentially shared native download. Inactive evidence
     * and refreshed accounting must be collected inside the command fence. */
    suspend fun reconcile(ticket: RollingHlsDownloadOwnership.Window): Boolean =
        commandLock.withLock(owner = commandOwner) {
            if (!current(ticket.lease.owner)) return@withLock false
            val status = gateway.status(ticket.lease.owner, ticket.lease.fileId)
            if (!matches(ticket, status) || status!!.active) return@withLock false
            ownership.confirmStopped(ticket, measure(), nativeInactive = true)
        }
}
