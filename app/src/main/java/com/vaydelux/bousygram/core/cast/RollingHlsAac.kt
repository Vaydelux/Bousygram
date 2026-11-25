package com.vaydelux.bousygram.core.cast

/** Strict single AAC-LC ADTS access unit normalization for the remux boundary. */
internal object RollingHlsAac {
    data class Config(val frequencyIndex: Int, val channels: Int) {
        init { require(frequencyIndex in 0..12 && channels in 1..2) }
        fun audioSpecificConfig(): ByteArray = byteArrayOf(
            ((2 shl 3) or (frequencyIndex shr 1)).toByte(),
            (((frequencyIndex and 1) shl 7) or (channels shl 3)).toByte()
        )
    }
    data class Frame(val config: Config, val payload: ByteArray)

    /** Refuse CRC, MPEG-2, non-LC, channel configurations above stereo, multiple
     * raw data blocks, partial frames and midstream configuration changes. */
    fun normalize(frame: ByteArray, expected: Config? = null): Frame? {
        if (frame.size !in 8..8191) return null
        fun b(i: Int) = frame[i].toInt() and 0xff
        if (b(0) != 0xff || b(1) != 0xf1 || (b(2) shr 6) != 1 || (b(6) and 3) != 0) return null
        val index = (b(2) shr 2) and 15
        val channels = ((b(2) and 1) shl 2) or (b(3) shr 6)
        if (index !in 0..12 || channels !in 1..2) return null
        val length = ((b(3) and 3) shl 11) or (b(4) shl 3) or (b(5) shr 5)
        if (length != frame.size) return null
        val config = Config(index, channels)
        if (expected != null && config != expected) return null
        return Frame(config, frame.copyOfRange(7, frame.size))
    }
}
