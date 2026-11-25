package com.vaydelux.bousygram.core.cast

import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Incrementally splits a finite fMP4 muxer stream into init and media units.
 * Structural gate only: an independent sample/timestamp/codec verifier MUST
 * qualify the resulting unit before the rolling store can publish it.
 * Strict subset: ordinary 32-bit box sizes, one moof/mdat per unit, optional
 * styp/sidx prefix. Unknown boxes and truncated or oversized units fail closed.
 * IO-only; the caller owns the input and enforces a producer deadline.
 */
internal class RollingFmp4Framing(private val input: InputStream, private val permitted: () -> Boolean) {
    data class Fragment(val sequence: Long, val bytes: ByteArray)
    private data class Header(val size: Int, val type: String, val raw: ByteArray)
    private var initialized = false
    private var failed = false
    private var lastSequence: Long? = null

    private fun access() { if (failed || !permitted()) throw IOException("Fragment input revoked") }

    private fun readExactly(bytes: ByteArray, offset: Int, length: Int) {
        var cursor = offset
        val end = offset + length
        while (cursor < end) {
            access()
            val count = input.read(bytes, cursor, minOf(64 * 1024, end - cursor))
            if (count <= 0 || count > end - cursor) throw IOException("Truncated fMP4 box")
            cursor += count
        }
        access()
    }

    private fun header(allowEof: Boolean = false): Header? {
        access()
        val first = input.read()
        if (first < 0) {
            access()
            if (allowEof) return null
            throw IOException("Missing fMP4 box")
        }
        val raw = ByteArray(8)
        raw[0] = first.toByte()
        readExactly(raw, 1, 7)
        val size = uint32(raw, 0)
        if (size !in 8L..RollingHlsBudget.MAX_SEGMENT_BYTES) throw IOException("Unsupported fMP4 box size")
        return Header(size.toInt(), type(raw, 4), raw)
    }

    private fun box(h: Header, remaining: Int): ByteArray {
        if (h.size > remaining) throw IOException("fMP4 unit exceeds budget")
        val bytes = ByteArray(h.size)
        h.raw.copyInto(bytes)
        readExactly(bytes, 8, h.size - 8)
        return bytes
    }

    private inline fun <T> guarded(action: () -> T): T {
        try { access(); return action() }
        catch (error: Exception) { failed = true; throw error }
    }

    fun readInit(): ByteArray = guarded {
        check(!initialized)
        val ftyp = header()!!
        if (ftyp.type != "ftyp") throw IOException("Expected ftyp")
        val first = box(ftyp, MAX_INIT_BYTES)
        if (first.size < 16) throw IOException("Invalid ftyp")
        val moov = header()!!
        if (moov.type != "moov") throw IOException("Expected moov")
        val second = box(moov, MAX_INIT_BYTES - first.size)
        if ("mvex" !in children(second).map { it.second }) throw IOException("Missing fragment defaults")
        initialized = true
        first + second
    }

    fun readFragment(): Fragment? = guarded {
        check(initialized)
        var h = header(allowEof = true) ?: return@guarded null
        val parts = mutableListOf<ByteArray>()
        var total = 0
        while (h.type == "styp" || h.type == "sidx") {
            if (parts.size >= 4) throw IOException("Too many fragment prefix boxes")
            val part = box(h, MAX_PREFIX_BYTES - total)
            parts.add(part)
            total += part.size
            h = header()!!
        }
        if (h.type != "moof") throw IOException("Expected moof")
        val moof = box(h, minOf(MAX_MOOF_BYTES, MAX_MEDIA_BYTES - total))
        val sequence = fragmentSequence(moof)
        val previous = lastSequence
        if (sequence == 0L || (previous != null && sequence != previous + 1L)) throw IOException("Noncontiguous fragment sequence")
        total += moof.size
        parts.add(moof)
        val mdat = header()!!
        if (mdat.type != "mdat" || mdat.size <= 8) throw IOException("Expected nonempty mdat")
        // Allocate exactly one complete unit, then stream the payload directly
        // into it; never allocate another full mdat-sized temporary buffer.
        if (mdat.size > MAX_MEDIA_BYTES - total) throw IOException("Fragment exceeds byte budget")
        val bytes = ByteArray(total + mdat.size)
        var offset = 0
        for (part in parts) { part.copyInto(bytes, offset); offset += part.size }
        mdat.raw.copyInto(bytes, offset)
        readExactly(bytes, offset + 8, mdat.size - 8)
        access()
        lastSequence = sequence
        Fragment(sequence, bytes)
    }

    private fun fragmentSequence(moof: ByteArray): Long {
        val boxes = children(moof)
        val mfhd = boxes.filter { it.second == "mfhd" }.singleOrNull()
            ?: throw IOException("Missing or duplicate mfhd")
        if (mfhd.first.size != 16 || uint32(mfhd.first, 8) != 0L) throw IOException("Invalid mfhd")
        val tracks = boxes.filter { it.second == "traf" }
        if (tracks.size !in 1..2) throw IOException("Unsupported track fragment count")
        for ((traf, _) in tracks) {
            val types = children(traf).map { it.second }
            if (types.count { it == "tfhd" } != 1 || types.count { it == "tfdt" } != 1 ||
                types.none { it == "trun" }) throw IOException("Missing track fragment fields")
        }
        return uint32(mfhd.first, 12)
    }

    private fun children(parent: ByteArray): List<Pair<ByteArray, String>> {
        val result = mutableListOf<Pair<ByteArray, String>>()
        var offset = 8
        while (offset < parent.size) {
            if (parent.size - offset < 8 || result.size >= 128) throw IOException("Invalid child box list")
            val size = uint32(parent, offset)
            if (size !in 8L..(parent.size - offset).toLong()) throw IOException("Invalid child box size")
            val end = offset + size.toInt()
            result.add(parent.copyOfRange(offset, end) to type(parent, offset + 4))
            offset = end
        }
        return result
    }

    private fun type(bytes: ByteArray, offset: Int) = String(bytes, offset, 4, StandardCharsets.US_ASCII)
    private fun uint32(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        repeat(4) { value = (value shl 8) or (bytes[offset + it].toLong() and 255L) }
        return value
    }

    companion object {
        private const val MAX_INIT_BYTES = 512 * 1024
        private const val MAX_PREFIX_BYTES = 64 * 1024
        private const val MAX_MOOF_BYTES = 512 * 1024
        private const val MAX_MEDIA_BYTES = (8 * 1024 * 1024)
    }
}
