package com.vaydelux.bousygram.core.cast

import java.io.IOException

/** One aligned 64 KiB window shared by all extractor DataSources in the
 * approved session. No disk writes or speculative requests beyond this page.
 * Small extractor reads must not generate one LAN round trip per byte. */
internal class RollingLanDirectReadWindow(private val size: Long) {
    private val page = ByteArray(RollingLanDirectMediaPolicy.MAX_READ_BYTES)
    private var base = -1L
    private var length = 0
    init { require(RollingLanDirectMediaPolicy.validSize(size)) }

    fun read(offset: Long, target: ByteArray, start: Int, count: Int,
             permitted: () -> Boolean, fetch: (Long, ByteArray, Int) -> Unit): Int {
        require(start >= 0 && count > 0 && start <= target.size - count)
        if (!permitted() || offset !in 0L until size) throw IOException("Direct window revoked or invalid")
        val aligned = offset - offset % page.size
        if (base != aligned) {
            clear()
            val n = minOf(page.size.toLong(), size - aligned).toInt()
            try {
                fetch(aligned, page, n)
                if (!permitted()) throw IOException("Direct window revoked")
                base = aligned
                length = n
            } catch (error: Exception) {
                clear()
                throw error
            }
        }
        val within = (offset - base).toInt()
        val n = minOf(count, length - within)
        check(n > 0)
        page.copyInto(target, start, within, within + n)
        if (!permitted()) {
            target.fill(0, start, start + n)
            clear()
            throw IOException("Direct window revoked")
        }
        return n
    }

    fun clear() {
        page.fill(0)
        base = -1L
        length = 0
    }
}
