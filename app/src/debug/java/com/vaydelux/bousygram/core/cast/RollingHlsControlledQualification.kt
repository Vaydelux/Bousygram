package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.File
import java.io.IOException

/**
 * Isolated producer/controlled-receiver contract qualification.
 * The caller owns its private directory and provides a trusted simulated
 * consumer. No LAN listener, actual player, native library load or TLS.
 * The consumer must not acknowledge playback merely because it read bytes.
 */
internal class RollingHlsControlledQualification(
    owner: AccountSnapshot,
    fileId: Int,
    directory: File,
    current: (AccountSnapshot) -> Boolean,
    routeAllowed: () -> Boolean,
    nowMs: () -> Long,
    admitsAdditionalBytes: (Long) -> Boolean,
    private val acceptInitialization: (ByteArray, Int) -> Unit,
    private val consume: (RollingHlsSegmentStore.Segment, ByteArray, Int) -> Unit,
    private val consumed: (RollingHlsSegmentStore.Segment) -> Boolean,
    verifier: RollingHlsVerifiedFeed.Verifier = RollingFmp4SampleVerifier()
) : AutoCloseable {
    @Volatile private var stopped = false
    private val ownerSnapshot = owner
    private val routeAllowedNow = routeAllowed
    private val currentOwner = current
    private val store = RollingHlsSegmentStore(directory,
        { !stopped && currentOwner(ownerSnapshot) && routeAllowedNow() }, admitsAdditionalBytes,
        firstSequence = 1, capacitySegments = 2)
    private val delivery = RollingHlsDeliverySession(owner, fileId, store, current,
        { !stopped && routeAllowedNow() }, nowMs)
    private val dispatcher = RollingHlsDeliveryDispatcher(delivery)
    private val sink = RollingHlsStreamingSink(store, { delivery.active() }, verifier,
        { incoming -> consumeOldest(incoming.sequence) }, nowMs,
        { bytes ->
            try { delivery.installInitialization(bytes) && receiveInitialization(acceptInitialization) }
            finally { bytes.fill(0) }
        })
    private var finished = false
    private var initialized = false
    private var acknowledged = 0L
    private var finalConsumed = false

    private fun readRequest(path: String) =
        RollingHlsDeliveryDispatcher.Request("GET", delivery.resourceId, path, delivery.readToken)

    /** Trusted qualification consumer receives init separately before media. */
    private fun consumeOldest(before: Long): Boolean {
        if (!delivery.active() || !initialized) return false
        val first = store.snapshot().firstOrNull() ?: return false
        if (first.sequence >= before || store.snapshot().size < 2) return false
        if (!transferAndConsume(first)) return false
        val result = dispatcher.dispatch(RollingHlsDeliveryDispatcher.Request(
            "POST", delivery.resourceId, "ack/${first.sequence}", controlToken = delivery.controlToken))
        result.use {
            if (it.status != 204) return false
        }
        acknowledged++
        return true
    }

    private fun transferAndConsume(segment: RollingHlsSegmentStore.Segment): Boolean {
        dispatcher.dispatch(readRequest("segment/${segment.sequence}.m4s")).use { response ->
            if (response.status != 200 || response.length != segment.size) return false
            var received = 0L
            response.transmit { bytes, count ->
                consume(segment, bytes, count)
                received += count
            }
            if (received != segment.size) return false
        }
        // A separate trusted assertion of consumption, not GET completion.
        return consumed(segment) && delivery.active()
    }

    /** Arbitrary callback boundaries, including init and multiple fragments
     * in one write. Init is delivered synchronously from its verified hook. */
    fun write(bytes: ByteArray, offset: Int, count: Int) {
        if (stopped || finished) throw IOException("Qualification is closed")
        try { sink.write(bytes, offset, count) }
        catch (error: Exception) {
            try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    /** Separate initialization delivery before first segment backpressure. */
    private fun receiveInitialization(accept: (ByteArray, Int) -> Unit): Boolean {
        if (initialized || !delivery.active()) return false
        dispatcher.dispatch(readRequest("init.mp4")).use { response ->
            if (response.status != 200 || response.length == null) return false
            var count = 0L
            response.transmit { bytes, length -> accept(bytes, length); count += length }
            if (count != response.length || !delivery.active()) return false
        }
        initialized = true
        return true
    }

    fun finish() {
        try {
            sink.finish()
            val last = store.snapshot().lastOrNull() ?: throw IOException("Missing final media")
            if (!initialized || !delivery.markComplete(last.sequence)) throw IOException("Completion refused")
            finished = true
        } catch (error: Exception) {
            try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    fun playlist(): String? = delivery.playlist(delivery.resourceId, delivery.readToken)

    /** Drain remaining nonfinal segments; keep final available until close. */
    fun drainAcknowledged(): Boolean {
        if (!finished || !delivery.active()) return false
        while (store.snapshot().size > 1) {
            if (!consumeOldest(Long.MAX_VALUE)) return false
        }
        return true
    }

    /** Deliver the final segment once after draining all earlier segments.
     * Keep it in the store for the completed playlist until session close.
     * A failed assertion never falsely reports completed consumption. */
    fun consumeFinal(): Boolean {
        if (!finished || finalConsumed || !delivery.active() || !drainAcknowledged()) return false
        val last = store.snapshot().singleOrNull() ?: return false
        if (!transferAndConsume(last)) return false
        finalConsumed = true
        return true
    }

    fun snapshot() = store.snapshot()
    fun acknowledgedCount() = acknowledged
    fun retainedBytes() = store.retainedBytes()

    override fun close() {
        stopped = true
        try { delivery.close() } finally { sink.close() }
    }
}
