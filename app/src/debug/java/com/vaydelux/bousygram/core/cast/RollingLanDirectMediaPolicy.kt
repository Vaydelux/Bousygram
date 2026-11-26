package com.vaydelux.bousygram.core.cast

/** Finite debug session; no staging or full-file cache. Container recognition
 * is not a codec compatibility guarantee. The receiver's extractor/decoder
 * decides whether the explicitly selected video can actually be played. */
internal object RollingLanDirectMediaPolicy {
    const val MAX_SOURCE_BYTES = 8L * 1024 * 1024 * 1024
    const val MAX_READ_BYTES = 64 * 1024
    fun validSize(size: Long) = size in 1L..MAX_SOURCE_BYTES
    fun validRange(size: Long, offset: Long, count: Int): Boolean =
        validSize(size) && offset in 0L until size && count in 1..MAX_READ_BYTES &&
            count.toLong() <= size - offset

    fun supportedHeader(header: ByteArray): Boolean {
        if (header.size >= 8 && header.copyOfRange(4, 8).contentEquals("ftyp".toByteArray(Charsets.US_ASCII))) return true
        if (header.size >= 4 && header[0] == 0x1a.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xdf.toByte() && header[3] == 0xa3.toByte()) return true
        return header.size >= 377 && listOf(0, 188, 376).all { header[it] == 0x47.toByte() }
    }
}
