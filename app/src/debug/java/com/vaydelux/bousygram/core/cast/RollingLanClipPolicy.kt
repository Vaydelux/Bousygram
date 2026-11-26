package com.vaydelux.bousygram.core.cast

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Explicit opt-in local fMP4 profile. Not a general-purpose transcoder.
 * Keep the synthetic qualification contract unchanged and reject unsupported
 * media rather than allowing an unbounded source or silently truncating it. */
internal class RollingLanClipPolicy(private val synthetic: Boolean) {
    private var count = 0L
    private var endUs = 0L

    fun accept(sequence: Long, startUs: Long, durationUs: Long): Boolean {
        if (sequence != count + 1 || sequence > 12 || startUs != endUs ||
            !RollingHlsLiveRetention.acceptsDuration(6_000_000, durationUs) ||
            durationUs > MAX_DURATION_US - endUs) return false
        count++
        endUs += durationUs
        return true
    }

    fun complete(): Boolean = if (synthetic) count == 12L else count in 1L..12L

    companion object {
        const val MAX_DURATION_US = 78_000_000L
        const val MAX_INPUT_BYTES = 16L * 1024 * 1024

        /** No full-file copy, seek or trust in provider-reported file length. */
        fun bounded(input: InputStream, permitted: () -> Boolean): InputStream =
            object : FilterInputStream(input) {
                private var consumed = 0L
                private fun access() {
                    if (!permitted()) throw IOException("Local media access revoked")
                }
                override fun read(): Int {
                    access()
                    val value = input.read()
                    access()
                    if (value >= 0 && ++consumed > MAX_INPUT_BYTES) throw IOException("Local media exceeds input limit")
                    return value
                }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                    require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
                    if (length == 0) return 0
                    access()
                    val count = input.read(bytes, offset, minOf(length.toLong(), MAX_INPUT_BYTES - consumed + 1).toInt())
                    access()
                    if (count > 0) {
                        consumed += count
                        if (consumed > MAX_INPUT_BYTES) throw IOException("Local media exceeds input limit")
                    }
                    return count
                }
                override fun skip(n: Long): Long = throw IOException("Sequential media only")
                override fun markSupported(): Boolean = false
                override fun reset(): Unit = throw IOException("Sequential media only")
            }
    }
}
