package com.vaydelux.bousygram.core.cast

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Private, sequential rolling segment storage. No network, no muxer and no HLS
 * playlist exposure. The future producer must verify actual container bytes,
 * codec configuration, sync samples and timestamps BEFORE calling publish.
 *
 * All operations are IO-only; callbacks must be non-reentrant. One producer
 * per session. A private, exclusively owned empty scratch directory is required.
 * Admission is a shared-cache snapshot; only the local window has a hard cap.
 */
internal class RollingHlsSegmentStore(
    private val directory: File,
    private val permitted: () -> Boolean,
    private val admitsAdditionalBytes: (Long) -> Boolean,
    firstSequence: Long = 0L,
    firstStartUs: Long = 0L,
    private val capacityBytes: Long = RollingHlsBudget.MAX_WINDOW_BYTES,
    private val capacitySegments: Int = RollingHlsBudget.MAX_WINDOW_SEGMENTS
) : AutoCloseable {
    data class Segment(val sequence: Long, val startUs: Long, val durationUs: Long, val size: Long)
    enum class PublishResult { STORED, BACKPRESSURE, DENIED, INVALID }

    private class Entry(val info: Segment, val file: File) {
        var readers = 0
        var retired = false
    }

    private val entries = linkedMapOf<Long, Entry>()
    private val failedDeletes = mutableSetOf<File>()
    private var nextSequence = firstSequence
    private var nextStartUs = firstStartUs
    private var retiredBefore = firstSequence
    private var storedBytes = 0L
    @Volatile private var stopped = false
    @Volatile private var cleanupFailed = false

    init {
        require(firstSequence in 0L until Long.MAX_VALUE && firstStartUs >= 0L)
        require(capacityBytes in 1L..RollingHlsBudget.MAX_WINDOW_BYTES)
        require(capacitySegments in 1..RollingHlsBudget.MAX_WINDOW_SEGMENTS)
        require(directory.isDirectory && directory.listFiles()?.isEmpty() == true) {
            "An exclusively owned empty segment directory is required"
        }
    }

    private fun active(): Boolean = !stopped && !cleanupFailed && permitted()

    @Synchronized fun snapshot(): List<Segment> =
        if (active()) entries.values.filterNot { it.retired }.map { it.info } else emptyList()

    @Synchronized fun retainedBytes(): Long = storedBytes

    /**
     * Copies exactly the declared bytes using a fixed 64 KiB buffer. The input
     * is caller-owned and must be a finite verified segment, never a live stream.
     * Nothing is published until the copy is complete and admission rechecked.
     * A full window backpressures the producer; no unread media is evicted.
     */
    @Synchronized fun publish(segment: Segment, input: InputStream): PublishResult {
        if (!active()) return PublishResult.DENIED
        if (segment.sequence != nextSequence || nextSequence == Long.MAX_VALUE ||
            segment.startUs != nextStartUs || segment.durationUs !in 1L..RollingHlsBudget.MAX_SEGMENT_DURATION_US ||
            segment.startUs > Long.MAX_VALUE - segment.durationUs ||
            segment.size !in 1L..RollingHlsBudget.MAX_SEGMENT_BYTES || segment.size > capacityBytes
        ) return PublishResult.INVALID
        if (entries.size >= capacitySegments || segment.size > capacityBytes - storedBytes ||
            !admitsAdditionalBytes(segment.size)
        ) return PublishResult.BACKPRESSURE

        val staging = File.createTempFile("segment-", ".part", directory)
        var published = false
        try {
            staging.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var remaining = segment.size
                while (remaining > 0) {
                    if (!active()) return PublishResult.DENIED
                    val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    if (count <= 0 || count.toLong() > remaining) return PublishResult.INVALID
                    if (!active()) return PublishResult.DENIED
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                if (input.read() != -1) return PublishResult.INVALID
            }
            if (!active()) return PublishResult.DENIED
            // Measurement now includes staging; do not reserve its bytes twice.
            if (!admitsAdditionalBytes(0L)) return PublishResult.BACKPRESSURE
            if (staging.length() != segment.size) return PublishResult.INVALID
            entries[segment.sequence] = Entry(segment, staging)
            storedBytes += segment.size
            nextSequence++
            nextStartUs += segment.durationUs
            published = true
            return PublishResult.STORED
        } finally {
            if (!published && !removeFile(staging)) {
                failedDeletes.add(staging)
                cleanupFailed = true
                throw IOException("Unpublished segment cleanup failed")
            }
        }
    }

    /** Acknowledge only segments no longer needed by the consumer. */
    @Synchronized fun retireBefore(sequence: Long) {
        require(sequence in retiredBefore..nextSequence)
        retiredBefore = sequence
        entries.values.filter { it.info.sequence < sequence }.forEach { it.retired = true }
        reclaimRetired()
    }

    /** Retired segments cannot be reopened; in-flight readers pin their bytes. */
    @Synchronized fun open(sequence: Long): Lease? {
        if (!active()) return null
        val entry = entries[sequence]?.takeUnless { it.retired } ?: return null
        if (!entry.file.isFile || entry.file.length() != entry.info.size || entry.readers != 0) return null
        val stream = FileInputStream(entry.file)
        entry.readers++
        return Lease(entry.info, stream, ::active) { release(sequence) }
    }

    private @Synchronized fun release(sequence: Long) {
        val entry = entries[sequence] ?: return
        check(entry.readers > 0)
        entry.readers--
        reclaimRetired()
    }

    private fun removeFile(file: File): Boolean = runCatching { !file.exists() || file.delete() }.getOrDefault(false)

    /** Caller holds the store monitor. Failed deletion keeps bytes accounted. */
    private fun reclaimRetired() {
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.retired && entry.readers == 0) {
                if (removeFile(entry.file)) {
                    storedBytes -= entry.info.size
                    iterator.remove()
                } else cleanupFailed = true
            }
        }
        if (cleanupFailed) throw IOException("Rolling segment cleanup failed")
    }

    /** Revoke first. Active leases stop reading and reclaim upon closing. */
    @Synchronized override fun close() {
        stopped = true
        entries.values.forEach { it.retired = true }
        val iterator = failedDeletes.iterator()
        while (iterator.hasNext()) if (removeFile(iterator.next())) iterator.remove()
        // Allow a retry of earlier cleanup failures, never a restart of publishing.
        cleanupFailed = failedDeletes.isNotEmpty()
        reclaimRetired()
    }

    class Lease internal constructor(
        val segment: Segment,
        private val input: FileInputStream,
        private val permitted: () -> Boolean,
        private val release: () -> Unit
    ) : AutoCloseable {
        private var remaining = segment.size
        private var closed = false

        @Synchronized fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length in 1..64 * 1024 && offset <= buffer.size - length)
            if (closed || !permitted()) throw IOException("Segment lease revoked")
            if (remaining == 0L) return -1
            val count = input.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count <= 0 || !permitted()) throw IOException("Segment truncated or revoked")
            remaining -= count
            return count
        }

        @Synchronized override fun close() {
            if (closed) return
            closed = true
            try { input.close() } finally { release() }
        }
    }
}
