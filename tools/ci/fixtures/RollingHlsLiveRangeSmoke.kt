package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files

fun main() {
    val dir = Files.createTempDirectory("live-range").toFile()
    val data = ByteArray(96_123) { (it % 251).toByte() }
    var now = 0L
    var permitted = true
    val store = RollingHlsLiveFileStore(dir, 6_000_000, 8, 32L * 1024 * 1024,
        { now }, { permitted }, { true })
    fun read(lease: RollingHlsLiveFileStore.Lease): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096) { 99 }
        while (true) {
            val n = lease.read(buffer, 17, 4000)
            if (n < 0) break
            check(buffer.take(17).all { it == 99.toByte() })
            out.write(buffer, 17, n)
        }
        return out.toByteArray()
    }
    try {
        check(store.installInitialization(ByteArray(16)))
        for (n in 1L..4L) check(store.publish(RollingHlsLiveRetention.Segment(n, 6_000_000, data.size.toLong()), ByteArrayInputStream(data)))
        check(store.open(1, 0, 1) == null) // not advertised
        store.openPlaylist()!!.close()
        check(store.withdrawOldest())
        for ((offset, length) in listOf(-1L to 1L, data.size.toLong() to 1L, 0L to 0L, 1L to Long.MAX_VALUE))
            check(store.open(1, offset, length) == null)
        store.open(1, 17, 51_111)!!.use {
            check(it.length == 51_111L && read(it).contentEquals(data.copyOfRange(17, 51_128)))
        }
        store.open(1, 51_128)!!.use { check(read(it).contentEquals(data.copyOfRange(51_128, data.size))) }
        val pinned = store.open(1, 50, 32)!!
        now = 30_000_000
        check(store.open(1, 0, 1) == null && store.reclaim() && store.retainedCount() == 4)
        pinned.use { check(read(it).contentEquals(data.copyOfRange(50, 82))) }
        check(store.reclaim() && store.retainedCount() == 3)
        println("LIVE_RANGE_RETRY_PASS")
        val revoked = store.open(2, 10, 16)!!
        permitted = false
        val buffer = ByteArray(64) { 99 }
        revoked.use {
            try { it.read(buffer, 10, 16); error("Expected revocation") } catch (_: IOException) { }
        }
        check(buffer.sliceArray(10 until 26).all { it == 0.toByte() })
        check(buffer.take(10).all { it == 99.toByte() } && buffer.drop(26).all { it == 99.toByte() })
        store.close()
        check(dir.listFiles()!!.isEmpty())
        println("LIVE_RANGE_REVOKE_PASS")
    } finally { store.close(); dir.deleteRecursively() }
}
