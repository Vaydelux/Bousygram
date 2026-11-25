package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot

/**
 * Process-wide producer lifetime. No IO, JNI or TDLib call under this monitor.
 * Account invalidation immediately denies new work; it does not prove that a
 * running worker, native download or disk artifact has stopped.
 */
internal class RollingHlsProducerLifetime(private val current: (AccountSnapshot) -> Boolean) {
    class Ticket internal constructor(val owner: AccountSnapshot, val fileId: Int)
    enum class Phase { IDLE, PREPARING, RUNNING, RETIRING }
    private var ticket: Ticket? = null
    private var phase = Phase.IDLE
    private var worker = false

    @Synchronized fun acquire(owner: AccountSnapshot, fileId: Int): Ticket? {
        if (ticket != null || fileId <= 0 || !current(owner)) return null
        return Ticket(owner, fileId).also {
            ticket = it
            phase = Phase.PREPARING
        }
    }

    @Synchronized fun permitted(expected: Ticket): Boolean =
        ticket === expected && phase in setOf(Phase.PREPARING, Phase.RUNNING) && current(expected.owner)

    @Synchronized fun beginWorker(expected: Ticket): Boolean {
        if (!permitted(expected) || phase != Phase.PREPARING || worker) return false
        phase = Phase.RUNNING
        worker = true
        return true
    }

    @Synchronized fun retire(expected: Ticket) {
        if (ticket === expected) phase = Phase.RETIRING
    }

    @Synchronized fun workerExited(expected: Ticket) {
        if (ticket !== expected || !worker) return
        worker = false
        phase = Phase.RETIRING
    }

    /** The caller must reconcile native reservations and private scratch first.
     * An account change alone never authorizes releasing the process slot. */
    @Synchronized fun release(expected: Ticket, reservedBytes: Long, scratchClean: Boolean): Boolean {
        if (ticket !== expected || worker || phase != Phase.RETIRING ||
            reservedBytes != 0L || !scratchClean) return false
        ticket = null
        phase = Phase.IDLE
        return true
    }

    @Synchronized fun readyToRelease(expected: Ticket): Boolean =
        ticket === expected && !worker && phase == Phase.RETIRING

    @Synchronized fun phase(): Phase = phase
}
