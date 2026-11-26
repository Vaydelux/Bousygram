package com.vaydelux.bousygram.core.cast

/** Narrow qualification profile. Header admission is not full media validation. */
internal object RemuxContainerPolicy {
    fun permits(header: ByteArray): Boolean {
        if (header.size < 8) return false
        val ebml = header[0] == 0x1a.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xdf.toByte() && header[3] == 0xa3.toByte()
        val mp4 = header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
        return ebml || mp4
    }
}
