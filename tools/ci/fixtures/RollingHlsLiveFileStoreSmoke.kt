package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files

private const val SECOND = 1_000_000L
private fun units(file: File): Pair<ByteArray, List<ByteArray>> {
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
    val fragments = mutableListOf<ByteArray>()
    while (offset < data.size) fragments.add(box("moof") + box("mdat"))
    check(fragments.size == 12)
    return init to fragments
}

fun main(args: Array<String>) {
    check(args.size == 1)
    val (init, media) = units(File(args[0]))
    val dir = Files.createTempDirectory("live-store-smoke").toFile()
    var now = 0L
    var allowed = true
    var deleteAllowed = true
    val store = RollingHlsLiveFileStore(dir, 6 * SECOND, 8, 32L * 1024 * 1024,
        { now }, { allowed }, { true }, { deleteAllowed && it.delete() })
    fun publish(seq: Long, bytes: ByteArray = media[(seq - 1).toInt()]) = store.publish(
        RollingHlsLiveRetention.Segment(seq, 6 * SECOND, bytes.size.toLong()), ByteArrayInputStream(bytes))
    fun playlist(): String = store.openPlaylist()!!.use { it.text()!! }
    fun read(lease: RollingHlsLiveFileStore.Lease): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4093)
        while (true) {
            val n = lease.read(buffer)
            if (n < 0) break
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }
    try {
        check(store.installInitialization(init))
        check(store.initialization()!!.contentEquals(init))
        check(!store.installInitialization(init))
        check(!publish(1, byteArrayOf()))
        val wrong = RollingHlsLiveRetention.Segment(1, 6 * SECOND, 3)
        check(!store.publish(wrong, ByteArrayInputStream(byteArrayOf(1, 2))))
        check(!store.publish(wrong, ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))))
        check(dir.listFiles()!!.isEmpty())
        for (seq in 1L..3L) check(publish(seq))
        check(playlist().contains("#EXT-X-MEDIA-SEQUENCE:1\n"))
        check(publish(4))
        val old = store.openPlaylist()!!
        check(old.text()!!.contains("segment/1.m4s"))
        check(store.openPlaylist() == null && !store.withdrawOldest())
        old.close()
        for (seq in 2L..4L) store.open(seq)!!.use { check(read(it).contentEquals(media[(seq - 1).toInt()])) }
        check(store.withdrawOldest())
        check(!playlist().contains("segment/1.m4s"))
        val pinned = store.open(1)!!
        check(store.open(1) == null)
        check(read(pinned).contentEquals(media[0]))
        now = 29 * SECOND
        check(store.reclaim() && store.retainedCount() == 4)
        now = 30 * SECOND
        check(store.reclaim() && store.retainedCount() == 4 && store.open(1) == null)
        pinned.close()
        deleteAllowed = false
        val before = store.retainedBytes()
        check(!store.reclaim() && store.retainedBytes() == before && store.retainedCount() == 4)
        deleteAllowed = true
        check(store.reclaim() && store.retainedCount() == 3)
        println("FILE_GRACE_DELETE_PASS")
        var peak = store.retainedBytes()
        for (seq in 5L..12L) {
            now += 6 * SECOND
            check(store.reclaim())
            check(publish(seq))
            peak = maxOf(peak, store.retainedBytes())
            check(playlist().contains("segment/$seq.m4s"))
            store.open(seq)!!.use { check(read(it).contentEquals(media[(seq - 1).toInt()])) }
            check(store.withdrawOldest())
            check(dir.listFiles()!!.sumOf { it.length() } == store.retainedBytes())
            check(store.retainedCount() <= 8)
        }
        for (seq in 2L..4L) check(store.open(seq) == null)
        println("REAL_FRAGMENT_FILE_PASS fragments=12 peak_bytes=$peak retained=${store.retainedCount()}")
        val outstanding = store.open(12)!!
        allowed = false
        val buffer = ByteArray(32) { 9 }
        try { outstanding.read(buffer); error("expected revocation") } catch (_: IOException) { }
        check(buffer.all { it == 0.toByte() } && store.openPlaylist() == null && store.initialization() == null)
        try { store.close(); error("expected pinned cleanup") } catch (_: IOException) { }
        outstanding.close()
        store.close()
        check(dir.listFiles()!!.isEmpty() && store.retainedBytes() == 0L)
        println("REVOKE_CLEANUP_PASS")
    } finally { dir.deleteRecursively() }
}
