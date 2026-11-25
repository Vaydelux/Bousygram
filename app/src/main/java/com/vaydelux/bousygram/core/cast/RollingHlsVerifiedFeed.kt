package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Finite private qualification feed. The verifier is an independent trusted
 * caller dependency, not a structural parser or a production media validator.
 * No playlist, HTTP publication or native producer is enabled here.
 *
 * Owns input and store; serial IO caller only. One pending media unit stays
 * in memory on backpressure, and cannot be replaced before admission succeeds.
 * Any invalid unit revokes the whole store. An already-read unit cannot be
 * retracted: callers must not expose this qualification store to receivers.
 */
internal class RollingHlsVerifiedFeed(
    private val input: InputStream,
    private val store: RollingHlsSegmentStore,
    private val permitted: () -> Boolean,
    private val verifier: Verifier
) : AutoCloseable {
    interface Verifier {
        fun verifyInit(init: ByteArray): Boolean
        fun verifyFragment(init: ByteArray, sequence: Long, bytes: ByteArray): Timing?
    }
    data class Timing(val startUs: Long, val durationUs: Long)
    sealed interface Step {
        data class Stored(val segment: RollingHlsSegmentStore.Segment) : Step
        data object Backpressure : Step
        data object End : Step
    }
    private data class Pending(val segment: RollingHlsSegmentStore.Segment, val bytes: ByteArray)
    private val framing = RollingFmp4Framing(input, permitted)
    private var init: ByteArray? = null
    private var pending: Pending? = null
    private var closed = false
    private var ended = false
    private var published = 0L

    private fun access() {
        if (closed || !permitted()) throw IOException("Qualification feed revoked")
    }

    fun step(): Step {
        try {
            access()
            if (ended) return Step.End
            val header = init ?: framing.readInit().also {
                if (!verifier.verifyInit(it.copyOf())) throw IOException("Initialization verification failed")
                access()
                init = it
            }
            val item = pending ?: run {
                val fragment = framing.readFragment()
                if (fragment == null) {
                    if (published == 0L) throw IOException("No media units")
                    ended = true
                    return Step.End
                }
                val timing = verifier.verifyFragment(header.copyOf(), fragment.sequence, fragment.bytes.copyOf())
                    ?: throw IOException("Media verification failed")
                access()
                if (timing.startUs < 0L || timing.durationUs !in 1L..RollingHlsBudget.MAX_SEGMENT_DURATION_US ||
                    timing.startUs > Long.MAX_VALUE - timing.durationUs)
                    throw IOException("Invalid verified timing")
                Pending(RollingHlsSegmentStore.Segment(fragment.sequence, timing.startUs,
                    timing.durationUs, fragment.bytes.size.toLong()), fragment.bytes).also { pending = it }
            }
            access()
            return when (store.publish(item.segment, ByteArrayInputStream(item.bytes))) {
                RollingHlsSegmentStore.PublishResult.STORED -> {
                    pending = null
                    item.bytes.fill(0)
                    published++
                    access()
                    Step.Stored(item.segment)
                }
                RollingHlsSegmentStore.PublishResult.BACKPRESSURE -> Step.Backpressure
                else -> throw IOException("Segment storage refused verified unit")
            }
        } catch (error: Exception) {
            try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    /** Init is private, memory-bounded and never returned by reference. */
    fun initialization(): ByteArray? {
        access()
        return init?.copyOf()
    }

    override fun close() {
        if (!closed) {
            closed = true
            pending?.bytes?.fill(0)
            pending = null
            init?.fill(0)
            init = null
            try { input.close() } finally { store.close() }
        } else {
            // Retry failed file deletion or reader-pinned cleanup.
            store.close()
        }
    }
}
