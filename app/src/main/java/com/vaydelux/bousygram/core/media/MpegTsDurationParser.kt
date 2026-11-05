package com.vaydelux.bousygram.core.media

/**
 * Conservative MPEG transport-stream duration recovery using PCR timestamps
 * from already-cached head and tail windows. No speculative byte downloads.
 *
 * TS does not require a global duration or seek index. Return no estimate when
 * a selected PCR PID advertises a discontinuity, packets are malformed, or the
 * timestamps cannot establish a plausible single timeline.
 */
internal object MpegTsDurationParser {
    private const val PCR_WRAP = 1L shl 33
    private const val MAX_DURATION_MS = 24L * 60 * 60 * 1_000L

    private data class Pcr(val pid: Int, val ticks90k: Long)
    private data class Window(val pcrs: List<Pcr>, val discontinuityPids: Set<Int>)

    fun durationMs(head: ByteArray, tail: ByteArray): Long? {
        if (head.size < 376 || tail.size < 376) return null
        val headWindow = scanPcr(head)
        val first = headWindow.pcrs.firstOrNull() ?: return null
        val tailWindow = scanPcr(tail)
        val last = tailWindow.pcrs.lastOrNull { it.pid == first.pid } ?: return null
        // The discontinuity flag can be carried on a separate adaptation-only
        // packet; ignoring it can turn a decoder clock reset into a false duration.
        if (first.pid in headWindow.discontinuityPids ||
            first.pid in tailWindow.discontinuityPids
        ) return null
        val deltaTicks = (last.ticks90k - first.ticks90k + PCR_WRAP) % PCR_WRAP
        val ms = deltaTicks * 1_000L / 90_000L
        return ms.takeIf { it in 1_000L..MAX_DURATION_MS }
    }

    private fun scanPcr(bytes: ByteArray): Window {
        // M2TS and FEC-protected TS use 192/204 byte strides while the actual
        // MPEG-TS packet is always 188 bytes starting at the sync byte.
        for (stride in listOf(188, 192, 204)) {
            val start = (0 until minOf(stride * 2, bytes.size - stride))
                .firstOrNull { offset ->
                    (bytes[offset].toInt() and 0xff) == 0x47 &&
                        (bytes[offset + stride].toInt() and 0xff) == 0x47
                } ?: continue
            val samples = mutableListOf<Pcr>()
            val discontinuities = mutableSetOf<Int>()
            var offset = start
            while (offset + 188 <= bytes.size) {
                if ((bytes[offset].toInt() and 0xff) != 0x47) break
                // Transport-error packets must not be used as timestamp evidence.
                if ((bytes[offset + 1].toInt() and 0x80) != 0) {
                    offset += stride
                    continue
                }
                val pid = ((bytes[offset + 1].toInt() and 0x1f) shl 8) or
                    (bytes[offset + 2].toInt() and 0xff)
                val adaptationControl = (bytes[offset + 3].toInt() ushr 4) and 3
                if (adaptationControl == 2 || adaptationControl == 3) {
                    val length = bytes[offset + 4].toInt() and 0xff
                    // An adaptation field must fit inside the 188-byte packet.
                    // Otherwise a corrupt length could make us interpret bytes
                    // from the next packet as a PCR and invent a seek timeline.
                    if (length in 1..183) {
                        val flags = bytes[offset + 5].toInt() and 0xff
                        if ((flags and 0x80) != 0) discontinuities.add(pid)
                        if (length >= 7 && (flags and 0x10) != 0) {
                            var base = 0L
                            for (i in 0..3) {
                                base = (base shl 8) or (bytes[offset + 6 + i].toLong() and 0xffL)
                            }
                            base = ((base shl 1) or
                                ((bytes[offset + 10].toLong() ushr 7) and 1L)) and (PCR_WRAP - 1)
                            samples.add(Pcr(pid, base))
                        }
                    }
                }
                offset += stride
            }
            if (samples.isNotEmpty()) return Window(samples, discontinuities)
        }
        return Window(emptyList(), emptySet())
    }
}
