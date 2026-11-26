package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.IOException

/** Debug-only serial IO producer. The input must independently verify media
 * before returning each unit; this scheduler does not decode or validate MP4.
 * Calls are cooperative: caller bounds blocking input/IO and schedules retries.
 * At most one pending media unit, no catch-up bursts after a stall. */
internal class RollingHlsLiveProducer(
    private val input: VerifiedInput,
    private val store: RollingHlsLiveFileStore,
    private val targetUs: Long,
    private val nowUs: () -> Long,
    private val permitted: () -> Boolean
) : AutoCloseable {
    interface VerifiedInput : AutoCloseable {
        fun initialization(): ByteArray
        fun next(): Unit?
    }
    data class Unit(val sequence: Long, val startUs: Long, val durationUs: Long, val bytes: ByteArray)
    sealed interface Step {
        data class Published(val sequence: Long) : Step
        data class Waiting(val reason: Reason, val retryAfterUs: Long) : Step
        data object End : Step
    }
    enum class Reason { PACING, BACKPRESSURE }
    private var initialized = false
    private var ended = false
    private var closed = false
    private var nextSequence = 1L
    private var nextStartUs = 0L
    private var dueUs = 0L
    private var blockedSince: Long? = null
    private var pending: Unit? = null
    private var lastClock = -1L

    init { require(targetUs in 1_000_000L..12_000_000L && targetUs % 1_000_000L == 0L) }

    private fun access(): Long {
        val now = nowUs()
        if (closed || !permitted() || !store.available() || now < 0 || now < lastClock ||
            now > Long.MAX_VALUE - 60_000_000L) throw IOException("Live producer unavailable")
        lastClock = now
        return now
    }

    fun step(): Step {
        try {
            access()
            if (ended) return Step.End
            if (!initialized) {
                val header = input.initialization()
                try {
                    access()
                    if (!store.installInitialization(header)) throw IOException("Live initialization refused")
                    initialized = true
                } finally { header.fill(0) }
            }
            val item = pending ?: input.next()?.also { pending = it } ?: run {
                access()
                if (nextSequence == 1L || !store.markComplete(nextSequence - 1))
                    throw IOException("Live completion refused")
                ended = true
                return Step.End
            }
            if (item.sequence != nextSequence || nextSequence == Long.MAX_VALUE || item.startUs != nextStartUs ||
                !RollingHlsLiveRetention.acceptsDuration(targetUs, item.durationUs) || item.bytes.size !in 1..8 * 1024 * 1024 ||
                item.startUs > Long.MAX_VALUE - item.durationUs) throw IOException("Invalid verified live unit: sequence=${item.sequence}, startUs=${item.startUs}, durationUs=${item.durationUs}, expectedSequence=$nextSequence, expectedStartUs=$nextStartUs")
            val now = access()
            if (now < dueUs) return Step.Waiting(Reason.PACING, dueUs - now)
            val blocked = blockedSince
            if (blocked != null && now - blocked >= MAX_BLOCK_US) throw IOException("Live publication stalled")
            val segment = RollingHlsLiveRetention.Segment(item.sequence, item.durationUs, item.bytes.size.toLong())
            if (!store.publishPaced(segment, ByteArrayInputStream(item.bytes))) {
                access()
                if (blockedSince == null) blockedSince = now
                return Step.Waiting(Reason.BACKPRESSURE, RETRY_US)
            }
            val publishedAt = access()
            pending = null
            item.bytes.fill(0)
            nextSequence++
            nextStartUs += item.durationUs
            dueUs = publishedAt + item.durationUs
            blockedSince = null
            return Step.Published(item.sequence)
        } catch (error: Exception) {
            try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            pending?.bytes?.fill(0)
            pending = null
            try { input.close() } catch (error: Exception) {
                try { store.close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                throw error
            }
        }
        store.close()
    }

    companion object {
        private const val MAX_BLOCK_US = 45_000_000L
        private const val RETRY_US = 100_000L
    }
}
