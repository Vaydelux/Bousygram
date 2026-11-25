package com.vaydelux.bousygram.core.cast

/**
 * Qualification-only RFC 8216 section 6.2.2 retention planner. One IO owner.
 * The caller must serve withdrawn resources until their deadline and confirm
 * physical deletion before reclaiming capacity. No actual files or HTTP here.
 */
internal class RollingHlsLiveRetention(
    private val targetUs: Long,
    private val capacity: Int,
    private val byteCap: Long,
    private val nowUs: () -> Long
) {
    data class Segment(val sequence: Long, val durationUs: Long, val bytes: Long)
    private data class Entry(val segment: Segment, var longestPlaylistUs: Long = 0,
                             var untilUs: Long? = null, var readers: Int = 0)
    private val entries = linkedMapOf<Long, Entry>()
    private var next = 1L
    private var lastClock = -1L
    private var stopped = false
    private var bytes = 0L

    init {
        require(targetUs in 1_000_000L..12_000_000L)
        require(capacity in 1..32 && byteCap in 1L..32L * 1024 * 1024)
    }

    private fun clock(): Long? {
        if (stopped) return null
        val value = nowUs()
        if (value < 0 || value < lastClock || value > Long.MAX_VALUE - 500_000_000L) {
            stopped = true
            return null
        }
        lastClock = value
        return value
    }

    fun append(segment: Segment): Boolean {
        if (clock() == null || segment.sequence != next || next == Long.MAX_VALUE ||
            segment.durationUs !in 1L..targetUs || segment.bytes !in 1L..8L * 1024 * 1024 ||
            entries.size >= capacity || segment.bytes > byteCap - bytes) return false
        entries[next] = Entry(segment)
        next++
        bytes += segment.bytes
        return true
    }

    /** Record conservatively BEFORE making this playlist visible to clients. */
    fun advertise(): List<Segment>? {
        if (clock() == null) return null
        val visible = entries.values.filter { it.untilUs == null }
        val duration = visible.sumOf { it.segment.durationUs }
        if (duration < 3 * targetUs) return null
        visible.forEach { it.longestPlaylistUs = maxOf(it.longestPlaylistUs, duration) }
        return visible.map { it.segment }
    }

    /** Withdraw only an advertised oldest entry while preserving 3 targets. */
    fun withdrawOldest(): Boolean {
        val now = clock() ?: return false
        val visible = entries.values.filter { it.untilUs == null }
        val oldest = visible.firstOrNull() ?: return false
        if (oldest.longestPlaylistUs == 0L ||
            visible.drop(1).sumOf { it.segment.durationUs } < 3 * targetUs) return false
        oldest.untilUs = now + oldest.segment.durationUs + oldest.longestPlaylistUs
        return true
    }

    /** Withdrawn entries remain available until their deadline. A lease pins
     * bytes even after the deadline. A failed deletion cannot free capacity. */
    fun acquire(sequence: Long): Boolean {
        val now = clock() ?: return false
        val entry = entries[sequence] ?: return false
        if (entry.longestPlaylistUs == 0L || entry.readers != 0 ||
            entry.untilUs?.let { now >= it } == true) return false
        entry.readers++
        return true
    }

    fun release(sequence: Long): Boolean {
        val entry = entries[sequence] ?: return false
        if (entry.readers != 1) return false
        entry.readers = 0
        return true
    }

    fun eligible(): List<Long> {
        val now = clock() ?: return emptyList()
        return entries.values.filter { it.readers == 0 && it.untilUs?.let { until -> now >= until } == true }
            .map { it.segment.sequence }
    }

    /** The IO owner must only call after verified physical deletion. */
    fun confirmDeleted(sequence: Long): Boolean {
        if (sequence !in eligible()) return false
        bytes -= checkNotNull(entries.remove(sequence)).segment.bytes
        return true
    }

    fun retainedBytes() = bytes
    fun retainedCount() = entries.size
}
