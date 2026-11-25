package com.vaydelux.bousygram.core.cast

/** A common time origin preserves inter-track offsets without inventing DTS. */
internal class RemuxTimeline(
    private val trackCount: Int,
    private val maxDurationUs: Long,
    private val maxSamples: Int
) {
    private val last = LongArray(trackCount)
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
        last[track] = pts
        counts[track]++
        total++
        return true
    }

    fun origin(): Long? = minimum.takeIf { !failed && counts.all { it > 0 } }
    fun matches(actual: IntArray): Boolean = origin() != null && counts.contentEquals(actual)

    companion object {
        fun normalize(pts: Long, origin: Long, maximum: Long): Long? {
            if (maximum < 0 || pts < origin) return null
            val offset = try { Math.subtractExact(pts, origin) } catch (_: ArithmeticException) { return null }
            return offset.takeIf { it in 0L..maximum }
        }
    }
}
