package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.cache.TvStorageBudget

/**
 * Qualification-only producer command ownership. No TDLib commands are sent
 * here. A future scheduler integration must serialize *all* same-file
 * start/cancel paths and account transitions with the command boundary.
 *
 * Positive DownloadFile limits cap one native request, not TDLib's cumulative
 * disk usage. A reservation is held until a fresh measured snapshot confirms
 * that native activity has stopped; an uncertain command quarantines the slot.
 * Snapshot admission cannot coordinate unrelated cache writers.
 */
internal class RollingHlsDownloadOwnership(
    private val current: (AccountSnapshot) -> Boolean,
    private val nowMs: () -> Long,
    private val conflicts: (Int) -> Boolean
) {
    data class Storage(val appBytes: Long, val tdLibBytes: Long, val freeBytes: Long)

    // Reference identity fences stale commands, even for a reused file ID.
    class Lease internal constructor(val owner: AccountSnapshot, val fileId: Int, val size: Long)
    class Window internal constructor(val lease: Lease, val offset: Long, val limit: Int,
                                      internal val expiresAtMs: Long) {
        val endExclusive: Long get() = offset + limit.toLong()
    }
    enum class Phase { IDLE, RESERVED, ISSUING, ACTIVE, QUARANTINED }
    data class State(val phase: Phase, val reservedBytes: Long, val nextOffset: Long)

    private var owner: Lease? = null
    private var window: Window? = null
    private var phase = Phase.IDLE
    private var cursor = 0L
    private var retired = false

    @Synchronized
    fun acquire(account: AccountSnapshot, fileId: Int, size: Long): Lease? {
        if (owner != null || fileId <= 0 || size <= 0 || !current(account) || conflicts(fileId)) return null
        return Lease(account, fileId, size).also {
            owner = it
            cursor = 0L
            retired = false
        }
    }

    private fun usable(lease: Lease): Boolean =
        owner === lease && !retired && current(lease.owner) && !conflicts(lease.fileId)

    @Synchronized
    fun reserve(lease: Lease, offset: Long, storage: Storage): Window? {
        if (!usable(lease) || phase != Phase.IDLE || offset != cursor || offset >= lease.size) return null
        val remaining = lease.size - offset
        val limit = minOf(remaining, MAX_DOWNLOAD_BYTES).toInt()
        if (!RollingHlsBudget.admits(storage.appBytes, storage.tdLibBytes, storage.freeBytes, limit.toLong())) return null
        val now = nowMs()
        if (now < 0 || now > Long.MAX_VALUE - WINDOW_DEADLINE_MS) return null
        return Window(lease, offset, limit, now + WINDOW_DEADLINE_MS).also {
            window = it
            phase = Phase.RESERVED
        }
    }

    /**
     * Call under the real shared command lock immediately before dispatch.
     * No asynchronous operation may rely on a previous successful check.
     */
    @Synchronized
    fun beginIssue(ticket: Window, storage: Storage): Boolean {
        if (window !== ticket || phase != Phase.RESERVED || !usable(ticket.lease) ||
            nowMs() !in 0L until ticket.expiresAtMs ||
            !RollingHlsBudget.admits(storage.appBytes, storage.tdLibBytes, storage.freeBytes, ticket.limit.toLong())) return false
        phase = Phase.ISSUING
        return true
    }

    /**
     * A response can arrive after logout, playback takeover or timeout.
     * Even an apparent native failure may have been applied; never free that
     * reservation on the response alone.
     */
    @Synchronized
    fun acknowledge(ticket: Window, accepted: Boolean): Boolean {
        if (window !== ticket || phase != Phase.ISSUING) return false
        val valid = accepted && usable(ticket.lease) && nowMs() in 0L until ticket.expiresAtMs
        phase = if (valid) Phase.ACTIVE else Phase.QUARANTINED
        return valid
    }

    @Synchronized
    fun canRead(ticket: Window, offset: Long, count: Int): Boolean {
        if (window !== ticket || phase != Phase.ACTIVE || !usable(ticket.lease) ||
            nowMs() !in 0L until ticket.expiresAtMs) return false
        return offset == cursor && count in 1..MAX_READ_BYTES &&
            offset >= ticket.offset && offset < ticket.endExclusive &&
            count.toLong() <= ticket.endExclusive - offset
    }

    @Synchronized
    fun consumed(ticket: Window, offset: Long, count: Int): Boolean {
        if (!canRead(ticket, offset, count)) return false
        cursor += count
        return true
    }

    /**
     * Caller must establish via an account-bound fresh native status that the
     * exact window is inactive, and that measured storage includes its writes.
     * No proof is inferred from timeout, cancellation request or byte count.
     */
    @Synchronized
    fun confirmStopped(ticket: Window, storage: Storage, nativeInactive: Boolean): Boolean {
        if (window !== ticket || phase !in setOf(Phase.ACTIVE, Phase.QUARANTINED) ||
            !nativeInactive || !current(ticket.lease.owner)) return false
        if (!RollingHlsBudget.admits(storage.appBytes, storage.tdLibBytes, storage.freeBytes, 0L)) return false
        // A live producer cannot advance past a partial window. It may reserve
        // the unconsumed range again from the exact observed cursor.
        window = null
        phase = Phase.IDLE
        if (retired) clear()
        return true
    }

    /** Revocation never cancels a different account's or player's download. */
    @Synchronized
    fun retire(lease: Lease) {
        if (owner !== lease) return
        retired = true
        when (phase) {
            Phase.IDLE, Phase.RESERVED -> clear() // nothing dispatched
            Phase.ISSUING, Phase.ACTIVE, Phase.QUARANTINED -> phase = Phase.QUARANTINED
        }
    }

    /**
     * Only after old-account native teardown has been independently confirmed.
     * Reject an old lease's delayed cleanup after another lease is acquired.
     */
    @Synchronized
    fun confirmAccountTeardown(lease: Lease, nativeTeardownConfirmed: Boolean): Boolean {
        if (owner !== lease || current(lease.owner) || !nativeTeardownConfirmed) return false
        clear()
        return true
    }

    @Synchronized
    fun state(): State = State(phase, window?.limit?.toLong() ?: 0L, cursor)

    private fun clear() {
        owner = null
        window = null
        phase = Phase.IDLE
        cursor = 0L
        retired = false
    }

    companion object {
        const val MAX_DOWNLOAD_BYTES = 2L * TvStorageBudget.MIB
        const val MAX_READ_BYTES = 32 * 1024
        const val WINDOW_DEADLINE_MS = 15_000L
    }
}
