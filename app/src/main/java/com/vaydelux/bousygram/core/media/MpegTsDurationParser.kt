package com.vaydelux.bousygram.core.media

/**
 * Bounded MPEG transport stream duration recovery using PCR timestamps from
 * already-cached head and tail windows. No guessing from bytes downloaded or bitrate.
 *
 * TS lacks a mandatory file-level duration header. This is a conservative fallback:
 * if either window lacks PCRs for the same PID, or timestamps indicate a likely
 * discontinuity, no duration is returned.
 */
internal object MpegTsDurationParser {
    private const val PCR_WRAP = 1L shl 33
    private const val MAX_DURATION_MS = 24L * 60 * 60 * 1_000L

    private data class Pcr(val pid: Int, val ticks90k: Long)

    fun durationMs(head: ByteArray, tail: ByteArray): Long? {
        if (head.size < 376 || tail.size < 376) return null
        val first = scanPcr(head).firstOrNull() ?: return null
        val last = scanPcr(tail).lastOrNull { it.pid == first.pid } ?: return null
        val deltaTicks = (last.ticks90k - first.ticks90k + PCR_WRAP) % PCR_WRAP
        val ms = deltaTicks * 1_000L / 90_000L
        return ms.takeIf { it in 1_000L..MAX_DURATION_MS }
    }

    private fun scanPcr(bytes: ByteArray): List<Pcr> {
        val candidates = listOf(188, 192, 204)
        val samples = mutableListOf<Pcr>()
        for (stride in candidates) {
            val start = (0 until minOf(stride * 2, bytes.size - stride))
                .firstOrNull { offset ->
                    (bytes[offset].toInt() and 0xff) == 0x47 &&
                        (bytes[offset + stride].toInt() and 0xff) == 0x47
                } ?: continue
            var offset = start
            while (offset + 12 <= bytes.size) {
                if ((bytes[offset].toInt() and 0xff) != 0x47) break
                val pid = ((bytes[offset + 1].toInt() and 0x1f) shl 8) or
                    (bytes[offset + 2].toInt() and 0xff)
                val adaptationControl = (bytes[offset + 3].toInt() ushr 4) and 3
                if (adaptationControl == 2 || adaptationControl == 3) {
                    val length = bytes[offset + 4].toInt() and 0xff
                    if (length >= 7 && (bytes[offset + 5].toInt() and 0x10) != 0) {
                        var base = 0L
                        for (i in 0..3) {
                            base = (base shl 8) or (bytes[offset + 6 + i].toLong() and 0xffL)
                        }
                        base = ((base shl 1) or
                            ((bytes[offset + 10].toLong() ushr 7) and 1L)) and (PCR_WRAP - 1)
                        samples.add(Pcr(pid, base))
                    }
                }
                offset += stride
            }
            if (samples.isNotEmpty()) break
        }
        return samples
    }
}
