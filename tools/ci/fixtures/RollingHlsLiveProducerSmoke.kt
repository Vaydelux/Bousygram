package com.vaydelux.bousygram.core.cast

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files

private const val SECOND = 1_000_000L
private fun fixture(file: File): Pair<ByteArray, List<ByteArray>> {
    val data = file.readBytes()
    check(data.size in 1..20 * 1024 * 1024)
    var offset = 0
    fun box(type: String): ByteArray {
        check(offset <= data.size - 8)
        val size = ByteBuffer.wrap(data, offset, 4).int
        check(size in 8..8 * 1024 * 1024 && size <= data.size - offset)
        check(String(data, offset + 4, 4, Charsets.US_ASCII) == type)
        return data.copyOfRange(offset, offset + size).also { offset += size }
    }
    val init = box("ftyp") + box("moov")
    val media = mutableListOf<ByteArray>()
    while (offset < data.size) media.add(box("moof") + box("mdat"))
    check(media.size == 12)
    return init to media
}
private class Input(private val init: ByteArray, private val media: List<ByteArray>) : RollingHlsLiveProducer.VerifiedInput {
    var reads = 0
    var closed = false
    override fun initialization() = init.copyOf()
    override fun next(): RollingHlsLiveProducer.Unit? {
        check(!closed)
        if (reads == media.size) return null
        val n = reads++
        return RollingHlsLiveProducer.Unit(n + 1L, n * 6 * SECOND, 6 * SECOND, media[n].copyOf())
    }
    override fun close() { closed = true }
}

fun main(args: Array<String>) {
    check(args.size == 1)
    val (init, media) = fixture(File(args[0]))
    var now = 0L
    fun scenario(block: (RollingHlsLiveProducer, RollingHlsLiveFileStore, Input, File) -> Unit) {
        now = 0L
        val directory = Files.createTempDirectory("live-producer").toFile()
        val input = Input(init, media)
        val store = RollingHlsLiveFileStore(directory, 6 * SECOND, 8, 32L * 1024 * 1024, { now }, { true }, { true })
        try {
            RollingHlsLiveProducer(input, store, 6 * SECOND, { now }, { true }).use { producer ->
                block(producer, store, input, directory)
            }
            check(input.closed && directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
    scenario { producer, store, input, directory ->
        var peak = 0L
        var peakSlots = 0
        for (seq in 1L..12L) {
            check(producer.step() == RollingHlsLiveProducer.Step.Published(seq))
            peak = maxOf(peak, store.retainedBytes())
            peakSlots = maxOf(peakSlots, store.retainedCount())
            check(directory.listFiles()!!.sumOf { it.length() } == store.retainedBytes())
            if (seq >= 3) {
                store.openPlaylist()!!.use { response ->
                    val body = response.text()!!
                    check(body.contains("#EXT-X-MEDIA-SEQUENCE:${seq - 2}\n"))
                    check(body.contains("segment/$seq.m4s\n"))
                    check(!body.contains("#EXT-X-ENDLIST"))
                }
                store.open(seq)!!.use { lease ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4093)
                    while (true) { val n = lease.read(buffer); if (n < 0) break; out.write(buffer, 0, n) }
                    check(out.toByteArray().contentEquals(media[(seq - 1).toInt()]))
                }
            }
            if (seq < 12) {
                check(producer.step() == RollingHlsLiveProducer.Step.Waiting(RollingHlsLiveProducer.Reason.PACING, 6 * SECOND))
                val reads = input.reads
                now += 1
                check(producer.step() is RollingHlsLiveProducer.Step.Waiting && input.reads == reads)
                now += 6 * SECOND - 1
            }
        }
        check(producer.step() == RollingHlsLiveProducer.Step.End && producer.step() == RollingHlsLiveProducer.Step.End)
        store.openPlaylist()!!.use { check(it.text()!!.endsWith("#EXT-X-ENDLIST\n")) }
        check(!store.withdrawOldest() && peakSlots <= 8)
        println("PACED_REAL_FILE_PASS fragments=12 peak_bytes=$peak peak_slots=$peakSlots")
    }
    scenario { producer, store, input, _ ->
        repeat(3) { check(producer.step() is RollingHlsLiveProducer.Step.Published); now += 6 * SECOND }
        check(producer.step() == RollingHlsLiveProducer.Step.Waiting(RollingHlsLiveProducer.Reason.BACKPRESSURE, 100_000))
        val reads = input.reads
        val playlist = store.openPlaylist()!!
        now += 10 * SECOND
        check(producer.step() is RollingHlsLiveProducer.Step.Waiting && input.reads == reads)
        playlist.close()
        check(producer.step() == RollingHlsLiveProducer.Step.Published(4))
        check(producer.step() == RollingHlsLiveProducer.Step.Waiting(RollingHlsLiveProducer.Reason.PACING, 6 * SECOND))
        println("STALE_PLAYLIST_AND_NO_BURST_PASS")
    }
    scenario { producer, _, _, directory ->
        repeat(3) { check(producer.step() is RollingHlsLiveProducer.Step.Published); now += 6 * SECOND }
        check(producer.step() is RollingHlsLiveProducer.Step.Waiting)
        now += 45 * SECOND
        try { producer.step(); error("Expected bounded stall") } catch (_: IOException) { }
        check(directory.listFiles()!!.isEmpty())
        println("BOUNDED_STALL_CLEANUP_PASS")
    }
}
