package com.vaydelux.bousygram.core.cast

/** A common time origin preserves inter-track offsets without inventing DTS. */
internal class RemuxTimeline(
    private val trackCount: Int,
    private val maxDurationUs: Long,
    private val maxSamples: Int
) {
    private val first = LongArray(trackCount)
    private val last = LongArray(trackCount)
    private val lastPositiveDelta = LongArray(trackCount)
    private val counts = IntArray(trackCount)
    private var minimum = Long.MAX_VALUE
    private var maximum = Long.MIN_VALUE
    private var total = 0
    private var failed = false

    fun observe(track: Int, pts: Long): Boolean {
        if (failed) return false
        // A small negative AAC priming timestamp is legal. A large negative
        // value is outside this conservative remux profile.
        if (track !in counts.indices || pts < -1_000_000L || total >= maxSamples ||
            (counts[track] > 0 && pts < last[track])) {
            failed = true
            return false
        }
        minimum = minOf(minimum, pts)
        maximum = maxOf(maximum, pts)
        if (normalize(maximum, minimum, maxDurationUs) == null) {
            failed = true
            return false
        }
        if (counts[track] == 0) first[track] = pts
        else if (pts > last[track]) {
            lastPositiveDelta[track] = normalize(pts, last[track], maxDurationUs) ?: run {
                failed = true
                return false
            }
        }
        last[track] = pts
        counts[track]++
        total++
        return true
    }

    fun origin(): Long? = minimum.takeIf { !failed && counts.all { it > 0 } }
    fun matches(actual: IntArray): Boolean = origin() != null && counts.contentEquals(actual)
    fun sampleCounts(): IntArray = counts.copyOf()

    /** Estimated final sample duration from the last positive same-track delta. */
    fun endOfTrackUs(track: Int): Long? {
        val start = origin() ?: return null
        if (track !in counts.indices || lastPositiveDelta[track] <= 0L) return null
        val end = normalize(last[track], start, maxDurationUs) ?: return null
        if (lastPositiveDelta[track] > maxDurationUs - end) return null
        return end + lastPositiveDelta[track]
    }

    /** Compare track endpoints and counts relative to a common media origin. */
    fun preserves(actual: RemuxTimeline, toleranceUs: Long = 2_000L): Boolean {
        if (toleranceUs < 0L || trackCount != actual.trackCount || !matches(actual.counts)) return false
        val start = origin() ?: return false
        val otherStart = actual.origin() ?: return false
        return counts.indices.all { track ->
            val a = normalize(first[track], start, maxDurationUs) ?: return@all false
            val b = normalize(actual.first[track], otherStart, maxDurationUs) ?: return@all false
            val c = normalize(last[track], start, maxDurationUs) ?: return@all false
            val d = normalize(actual.last[track], otherStart, maxDurationUs) ?: return@all false
            kotlin.math.abs(a - b) <= toleranceUs && kotlin.math.abs(c - d) <= toleranceUs
        }
    }

    companion object {
        fun normalize(pts: Long, origin: Long, maximum: Long): Long? {
            if (maximum < 0 || pts < origin) return null
            val offset = try { Math.subtractExact(pts, origin) } catch (_: ArithmeticException) { return null }
            return offset.takeIf { it in 0L..maximum }
        }
    }
}
