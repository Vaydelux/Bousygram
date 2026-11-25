package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Synchronous, bounded JNI output consumer. No whole-output file or unbounded
 * queue. Exactly one init or fragment is assembled at a time; complete media
 * units are validated before entering the private store. Single IO worker.
 *
 * The acknowledgement callback must be cooperative, bounded and must never
 * discard unread media. This is a finite qualification sink, not HTTP delivery.
 * No consumer may expose these units to a receiver before production gates pass.
 */
internal class RollingHlsStreamingSink(
    private val store: RollingHlsSegmentStore,
    private val permitted: () -> Boolean,
    private val verifier: RollingHlsVerifiedFeed.Verifier,
    private val acknowledge: (RollingHlsSegmentStore.Segment) -> Boolean,
    private val nowMs: () -> Long,
    private val onInitialization: (ByteArray) -> Boolean = { true }
) : OutputStream() {
    private val header = ByteArray(8)
    private var headerBytes = 0
    private var box: ByteArray? = null
    private var boxBytes = 0
    private var boxType = ""
    private val parts = ArrayList<ByteArray>(6)
    private var unitBytes = 0
    private var init: ByteArray? = null
    private var sequence = 1L
    private var phase = 0 // ftyp, moov, prefix/moof, mdat
    private var prefixCount = 0
    private var closed = false
    private var finished = false
    private var highWater = 0L

    private fun access() {
        if (closed || finished || !permitted()) throw IOException("Streaming output revoked")
    }

    override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        try {
            access()
            var cursor = offset
            val end = offset + length
            while (cursor < end) {
                access()
                if (box == null) {
                    val take = minOf(8 - headerBytes, end - cursor)
                    bytes.copyInto(header, headerBytes, cursor, cursor + take)
                    headerBytes += take
                    cursor += take
                    if (headerBytes != 8) continue
                    beginBox()
                }
                val target = checkNotNull(box)
                val take = minOf(target.size - boxBytes, end - cursor)
                bytes.copyInto(target, boxBytes, cursor, cursor + take)
                boxBytes += take
                cursor += take
                if (boxBytes == target.size) {
                    box = null
                    boxBytes = 0
                    headerBytes = 0
                    consumeBox(target)
                }
            }
        } catch (error: Exception) {
            try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    private fun beginBox() {
        var size = 0L
        repeat(4) { size = (size shl 8) or (header[it].toLong() and 255) }
        boxType = String(header, 4, 4, Charsets.US_ASCII)
        val cap = if (phase < 2) MAX_INIT else RollingHlsBudget.MAX_SEGMENT_BYTES.toInt()
        if (size !in 8L..(cap - unitBytes).toLong()) throw IOException("Output box exceeds unit budget")
        when (phase) {
            0 -> if (boxType != "ftyp" || size < 16) throw IOException("Expected ftyp")
            1 -> if (boxType != "moov") throw IOException("Expected moov")
            2 -> when (boxType) {
                "styp", "sidx" -> if (prefixCount >= 4 || size > 64 * 1024 - unitBytes)
                    throw IOException("Prefix exceeds budget")
                "moof" -> if (size > 512 * 1024) throw IOException("moof exceeds budget")
                else -> throw IOException("Expected fragment")
            }
            3 -> if (boxType != "mdat" || size <= 8) throw IOException("Expected mdat")
            else -> throw IOException("Invalid output phase")
        }
        box = ByteArray(size.toInt()).also { header.copyInto(it) }
        boxBytes = 8
        highWater = maxOf(highWater, unitBytes.toLong() + size)
    }

    private fun consumeBox(bytes: ByteArray) {
        parts.add(bytes)
        unitBytes += bytes.size
        when (phase) {
            0 -> phase = 1
            1 -> {
                val unit = join()
                if (!verifier.verifyInit(unit.copyOf())) throw IOException("Initialization verification failed")
                access()
                if (!onInitialization(unit.copyOf())) throw IOException("Delivery initialization refused")
                access()
                init = unit
                phase = 2
            }
            2 -> if (boxType == "moof") phase = 3 else prefixCount++
            3 -> {
                val unit = join()
                val header = checkNotNull(init)
                try {
                    val timing = verifier.verifyFragment(header.copyOf(), sequence, unit.copyOf())
                        ?: throw IOException("Fragment sample verification failed")
                    access()
                    val segment = RollingHlsSegmentStore.Segment(sequence, timing.startUs, timing.durationUs, unit.size.toLong())
                    publish(segment, unit)
                    sequence++
                } finally {
                    unit.fill(0)
                }
                prefixCount = 0
                phase = 2
            }
        }
    }

    private fun join(): ByteArray {
        val unit = ByteArray(unitBytes)
        var offset = 0
        for (part in parts) {
            part.copyInto(unit, offset)
            offset += part.size
            part.fill(0)
        }
        parts.clear()
        unitBytes = 0
        return unit
    }

    private fun publish(segment: RollingHlsSegmentStore.Segment, unit: ByteArray) {
        val start = nowMs()
        if (start < 0 || start > Long.MAX_VALUE - BACKPRESSURE_MS) throw IOException("Invalid clock")
        val deadline = start + BACKPRESSURE_MS
        repeat(MAX_ADMISSION_ATTEMPTS) {
            access()
            if (nowMs() !in start until deadline) throw IOException("Output backpressure deadline")
            when (store.publish(segment, ByteArrayInputStream(unit))) {
                RollingHlsSegmentStore.PublishResult.STORED -> return
                RollingHlsSegmentStore.PublishResult.BACKPRESSURE ->
                    if (!acknowledge(segment)) throw IOException("Output consumer did not acknowledge")
                else -> throw IOException("Segment store refused output")
            }
        }
        throw IOException("Output backpressure retry limit")
    }

    /** Only call after JNI returned successful transfer and flushed output. */
    fun finish() {
        try {
            access()
            if (phase != 2 || headerBytes != 0 || box != null || parts.isNotEmpty() || sequence <= 1)
                throw IOException("Incomplete native output")
            finished = true
        } catch (error: Exception) {
            try { close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            throw error
        }
    }

    fun initialization(): ByteArray? {
        if (closed || !permitted()) throw IOException("Output revoked")
        return init?.copyOf()
    }

    /** Excludes copies, parser allocations, init, native heap and store bytes. */
    fun assemblyHighWaterBytes(): Long = highWater

    override fun close() {
        if (!closed) {
            closed = true
            header.fill(0)
            box?.fill(0)
            box = null
            parts.forEach { it.fill(0) }
            parts.clear()
            init?.fill(0)
            init = null
        }
        store.close()
    }

    companion object {
        private const val MAX_INIT = 512 * 1024
        private const val BACKPRESSURE_MS = 5_000L
        private const val MAX_ADMISSION_ATTEMPTS = 8
    }
}
