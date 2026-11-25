package com.vaydelux.bousygram.core.cast

import java.io.IOException
import java.security.MessageDigest

/**
 * Independent sample-table gate for the pinned H.264/AAC fragmented MP4
 * producer subset. No decoder: does not prove AAC syntax, pixel correctness,
 * source packet conservation, or presentation/edit-list equivalence.
 * One instance per sequential output. Fail closed on unsupported layout.
 */
internal class RollingFmp4SampleVerifier : RollingHlsVerifiedFeed.Verifier {
    private data class Box(val start: Int, val end: Int, val type: String) {
        val payload: Int get() = start + 8
    }
    private data class Defaults(val duration: Long, val size: Long, val flags: Long)
    private data class Track(val id: Long, val scale: Long, val video: Boolean,
                             val nalLength: Int, val defaults: Defaults)
    private data class Range(val start: Long, val end: Long)
    private var tracks = emptyMap<Long, Track>()
    private var initHash: ByteArray? = null
    private var nextSequence = 1L
    private var nextDecode = emptyMap<Long, Long>()
    private var videoOrigin: Long? = null
    private var failed = false

    private fun requireMedia(ok: Boolean) { if (!ok) throw IOException("Unsupported or invalid fMP4 sample layout") }
    private fun u8(b: ByteArray, i: Int): Int {
        requireMedia(i in b.indices)
        return b[i].toInt() and 255
    }
    private fun u32(b: ByteArray, i: Int): Long {
        requireMedia(i >= 0 && i <= b.size - 4)
        var v = 0L
        repeat(4) { v = (v shl 8) or u8(b, i + it).toLong() }
        return v
    }
    private fun u64(b: ByteArray, i: Int): Long {
        val high = u32(b, i)
        requireMedia(high <= Int.MAX_VALUE.toLong())
        return (high shl 32) or u32(b, i + 4)
    }
    private fun tag(b: ByteArray, i: Int): String {
        requireMedia(i >= 0 && i <= b.size - 4)
        return String(b, i, 4, Charsets.US_ASCII)
    }
    private fun boxes(b: ByteArray, from: Int, end: Int): List<Box> {
        requireMedia(from >= 0 && end <= b.size && from <= end)
        val result = ArrayList<Box>()
        var pos = from
        while (pos < end) {
            requireMedia(end - pos >= 8 && result.size < 128)
            val size = u32(b, pos)
            requireMedia(size in 8L..(end - pos).toLong())
            val next = pos + size.toInt()
            result.add(Box(pos, next, tag(b, pos + 4)))
            pos = next
        }
        return result
    }
    private fun children(b: ByteArray, box: Box) = boxes(b, box.payload, box.end)
    private fun one(list: List<Box>, type: String): Box =
        list.singleOrNull { it.type == type } ?: throw IOException("Missing/duplicate $type")
    private fun field(b: ByteArray, box: Box, offset: Int): Long {
        requireMedia(offset >= 0 && offset <= box.end - box.payload - 4)
        return u32(b, box.payload + offset)
    }
    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    override fun verifyInit(init: ByteArray): Boolean {
        if (failed || initHash != null) return false
        return try {
            requireMedia(init.size in 16..512 * 1024)
            val top = boxes(init, 0, init.size)
            requireMedia(top.map { it.type } == listOf("ftyp", "moov"))
            val moov = children(init, top[1])
            val trex = children(init, one(moov, "mvex")).filter { it.type == "trex" }
            requireMedia(trex.size == 2)
            val defaults = trex.associate { box ->
                    requireMedia(box.end - box.payload == 24 && field(init, box, 0) == 0L &&
                        field(init, box, 8) == 1L)
                    field(init, box, 4) to Defaults(field(init, box, 12), field(init, box, 16), field(init, box, 20))
                }
            val traks = moov.filter { it.type == "trak" }
            requireMedia(traks.size == 2 && defaults.size == 2)
            val found = traks.map { trak ->
                val fields = children(init, trak)
                val tkhd = one(fields, "tkhd")
                val version = field(init, tkhd, 0) ushr 24
                requireMedia(version in 0L..1L)
                val id = field(init, tkhd, if (version == 0L) 12 else 20)
                val mdia = children(init, one(fields, "mdia"))
                val mdhd = one(mdia, "mdhd")
                val mv = field(init, mdhd, 0) ushr 24
                requireMedia(mv in 0L..1L)
                val scale = field(init, mdhd, if (mv == 0L) 12 else 20)
                val handler = one(mdia, "hdlr")
                requireMedia(handler.end - handler.payload >= 12)
                val kind = tag(init, handler.payload + 8)
                requireMedia(id > 0 && scale in 1L..1_000_000L && kind in listOf("vide", "soun"))
                val stbl = children(init, one(children(init, one(mdia, "minf")), "stbl"))
                val stsd = one(stbl, "stsd")
                requireMedia(field(init, stsd, 0) == 0L && field(init, stsd, 4) == 1L)
                val entry = boxes(init, stsd.payload + 8, stsd.end).single()
                val video = kind == "vide"
                requireMedia(entry.type == if (video) "avc1" else "mp4a")
                val childStart = entry.payload + if (video) 78 else 28
                val codec = one(boxes(init, childStart, entry.end), if (video) "avcC" else "esds")
                val nal = if (video) {
                    requireMedia(codec.end - codec.payload >= 7 && u8(init, codec.payload) == 1)
                    val n = (u8(init, codec.payload + 4) and 3) + 1
                    requireMedia(n in listOf(1, 2, 4))
                    n
                } else {
                    requireMedia(codec.end - codec.payload >= 8)
                    0
                }
                Track(id, scale, video, nal, defaults[id] ?: throw IOException("Missing track defaults"))
            }
            requireMedia(found.count { it.video } == 1 && found.map { it.id }.toSet() == defaults.keys)
            tracks = found.associateBy { it.id }
            initHash = hash(init)
            true
        } catch (_: Exception) { failed = true; false }
    }

    private fun add(a: Long, b: Long): Long {
        requireMedia(a >= 0 && b >= 0 && a <= Long.MAX_VALUE - b)
        return a + b
    }
    private fun micros(value: Long, scale: Long): Long {
        requireMedia(value >= 0 && value <= Long.MAX_VALUE / 1_000_000L)
        return value * 1_000_000L / scale
    }

    /** Check every length-prefixed NAL boundary; first video sample must
     * actually contain an IDR, not merely claim sync in the sample flags. */
    private fun videoSample(b: ByteArray, start: Long, end: Long, nal: Int, first: Boolean) {
        var pos = start.toInt()
        var idr = false
        var count = 0
        while (pos < end) {
            requireMedia(end - pos >= nal && ++count <= 4096)
            var size = 0L
            repeat(nal) { size = (size shl 8) or u8(b, pos++).toLong() }
            requireMedia(size in 1L..(end - pos))
            val header = u8(b, pos)
            requireMedia(header and 0x80 == 0 && (header and 31) in 1..23)
            if (header and 31 == 5) idr = true
            pos += size.toInt()
        }
        requireMedia(pos.toLong() == end && (!first || idr))
    }

    override fun verifyFragment(init: ByteArray, sequence: Long, bytes: ByteArray): RollingHlsVerifiedFeed.Timing? {
        if (failed) return null
        return try {
            requireMedia(initHash?.contentEquals(hash(init)) == true && sequence == nextSequence &&
                bytes.size.toLong() in 1L..RollingHlsBudget.MAX_SEGMENT_BYTES)
            val top = boxes(bytes, 0, bytes.size)
            requireMedia(top.size in 2..6 && top.dropLast(2).all { it.type in listOf("styp", "sidx") } &&
                top[top.size - 2].type == "moof" && top.last().type == "mdat")
            val moof = top[top.size - 2]
            val mdat = top.last()
            val children = children(bytes, moof)
            val mfhd = one(children, "mfhd")
            requireMedia(mfhd.end - mfhd.payload == 8 && field(bytes, mfhd, 0) == 0L &&
                field(bytes, mfhd, 4) == sequence)
            val trafs = children.filter { it.type == "traf" }
            requireMedia(trafs.size == tracks.size)
            val ends = mutableMapOf<Long, Long>()
            val ranges = ArrayList<Range>()
            var videoStart = -1L
            var videoEnd = -1L
            var sampleCount = 0
            for (traf in trafs) {
                val fields = children(bytes, traf)
                val tfhd = one(fields, "tfhd")
                val flags = field(bytes, tfhd, 0)
                requireMedia(flags and 0x020000L != 0L && flags and 0xfffdffc5L == 0L)
                val id = field(bytes, tfhd, 4)
                val track = tracks[id] ?: throw IOException("Unknown track")
                requireMedia(id !in ends)
                var cursor = 8
                fun default(flag: Long, fallback: Long): Long =
                    if (flags and flag != 0L) field(bytes, tfhd, cursor).also { cursor += 4 } else fallback
                requireMedia(default(2, 1) == 1L)
                val durationDefault = default(8, track.defaults.duration)
                val sizeDefault = default(16, track.defaults.size)
                val flagDefault = default(32, track.defaults.flags)
                requireMedia(tfhd.payload + cursor == tfhd.end)
                val tfdt = one(fields, "tfdt")
                val version = field(bytes, tfdt, 0)
                val start = when (version) {
                    0L -> { requireMedia(tfdt.end - tfdt.payload == 8); field(bytes, tfdt, 4) }
                    0x01000000L -> { requireMedia(tfdt.end - tfdt.payload == 12); u64(bytes, tfdt.payload + 4) }
                    else -> throw IOException("Invalid tfdt")
                }
                requireMedia(nextDecode[id]?.let { it == start } ?: true)
                var decode = start
                var first = true
                val runs = fields.filter { it.type == "trun" }
                requireMedia(runs.isNotEmpty())
                for (run in runs) {
                    val full = field(bytes, run, 0)
                    val runFlags = full and 0xffffff
                    val runVersion = full ushr 24
                    requireMedia(runVersion in 0L..1L && runFlags and 0xfff0faL == 0L &&
                        runFlags and 1L != 0L && !(runFlags and 4L != 0L && runFlags and 0x400L != 0L))
                    val count = field(bytes, run, 4)
                    requireMedia(count in 1L..(20_000 - sampleCount).toLong())
                    sampleCount += count.toInt()
                    var data = add(moof.start.toLong(), field(bytes, run, 8))
                    requireMedia(data in mdat.payload.toLong() until mdat.end.toLong())
                    val runStart = data
                    var offset = 12
                    val firstFlags = if (runFlags and 4L != 0L) field(bytes, run, offset).also { offset += 4 } else flagDefault
                    repeat(count.toInt()) { index ->
                        fun sample(flag: Long, fallback: Long): Long =
                            if (runFlags and flag != 0L) field(bytes, run, offset).also { offset += 4 } else fallback
                        val duration = sample(0x100, durationDefault)
                        val size = sample(0x200, sizeDefault)
                        val sampleFlags = sample(0x400, if (index == 0) firstFlags else flagDefault)
                        // Read and bound the composition offset; presentation/edit
                        // list equivalence is an independent qualification gate.
                        val composition = sample(0x800, 0)
                        val signed = if (runVersion == 1L) composition.toInt().toLong() else composition
                        requireMedia(duration > 0 && duration <= track.scale * 12 &&
                            size > 0 && size <= 2L * 1024 * 1024 && signed in -track.scale * 12..track.scale * 12)
                        val end = add(data, size)
                        requireMedia(end <= mdat.end)
                        if (track.video) {
                            if (first) requireMedia(sampleFlags and 0x10000L == 0L && (sampleFlags ushr 24) and 3L != 1L)
                            videoSample(bytes, data, end, track.nalLength, first)
                        }
                        first = false
                        data = end
                        decode = add(decode, duration)
                    }
                    requireMedia(run.payload + offset == run.end)
                    ranges.add(Range(runStart, data))
                }
                ends[id] = decode
                if (track.video) {
                    val origin = videoOrigin ?: start
                    requireMedia(start >= origin && decode > start)
                    videoStart = micros(start - origin, track.scale)
                    videoEnd = micros(decode - origin, track.scale)
                    if (videoOrigin == null) videoOrigin = origin
                }
            }
            var payload = mdat.payload.toLong()
            for (range in ranges.sortedBy { it.start }) {
                requireMedia(range.start == payload)
                payload = range.end
            }
            requireMedia(payload == mdat.end.toLong() && ends.keys == tracks.keys &&
                videoStart >= 0 && videoEnd - videoStart in 1L..RollingHlsBudget.MAX_SEGMENT_DURATION_US)
            nextDecode = ends
            nextSequence = add(sequence, 1)
            RollingHlsVerifiedFeed.Timing(videoStart, videoEnd - videoStart)
        } catch (_: Exception) { failed = true; null }
    }
}
