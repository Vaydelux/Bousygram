package com.vaydelux.bousygram.core.tdlib.client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks in-flight native TDLib responses independently of callback delivery.
 *
 * TDLib is allowed to deliver a response after a client has been replaced. Those responses
 * must never complete an operation started by an earlier account/client generation.
 * Removal is atomic, so a response and invalidation cannot both complete the same request.
 */
internal class PendingTdRequests {
    internal data class Ticket(val id: Long, val generation: Long)
    private class Entry(val ticket: Ticket, val onInvalidated: () -> Unit)

    private val nextId = AtomicLong()
    private val active = ConcurrentHashMap<Long, Entry>()

    fun register(generation: Long, onInvalidated: () -> Unit): Ticket {
        val ticket = Ticket(nextId.incrementAndGet(), generation)
        active[ticket.id] = Entry(ticket, onInvalidated)
        return ticket
    }

    /** Returns true for the sole callback allowed to complete this request. */
    fun finish(ticket: Ticket): Boolean {
        val entry = active[ticket.id] ?: return false
        return entry.ticket == ticket && active.remove(ticket.id, entry)
    }

    fun forget(ticket: Ticket) {
        val entry = active[ticket.id] ?: return
        if (entry.ticket == ticket) active.remove(ticket.id, entry)
    }

    fun isPending(ticket: Ticket): Boolean = active[ticket.id]?.ticket == ticket

    /** Invalidate *before* reusing native client state or activating a different account. */
    fun invalidateAll() {
        active.values.forEach { entry ->
            if (active.remove(entry.ticket.id, entry)) entry.onInvalidated()
        }
    }

    internal fun pendingCount(): Int = active.size
}
