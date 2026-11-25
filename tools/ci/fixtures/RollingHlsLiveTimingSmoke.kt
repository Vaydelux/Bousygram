package com.vaydelux.bousygram.core.cast

import java.nio.file.Files
import java.io.IOException

fun main() {
    val target = 6_000_000L
    check(RollingHlsLiveRetention.acceptsDuration(target, 6_000_333))
    check(RollingHlsLiveRetention.acceptsDuration(target, 6_499_999))
    check(!RollingHlsLiveRetention.acceptsDuration(target, 6_500_000))
    check(!RollingHlsLiveRetention.acceptsDuration(12_000_000, 12_000_001))
    check(!RollingHlsLiveRetention.acceptsDuration(target, Long.MAX_VALUE))
    check(!RollingHlsLiveRetention.acceptsDuration(6_000_001, target))
    println("LIVE_ROUNDED_TARGET_PASS")
    for (bad in listOf(false, true)) {
        val dir = Files.createTempDirectory("live-timing").toFile()
        var now = 0L
        var cursor = 0L
        var index = 0
        val durations = listOf(6_000_333L, 5_999_667L, 6_000_000L, if (bad) 6_500_000L else 6_000_333L)
        val input = object : RollingHlsLiveProducer.VerifiedInput {
            override fun initialization() = ByteArray(16)
            override fun next(): RollingHlsLiveProducer.Unit? {
                if (index == durations.size) return null
                val duration = durations[index++]
                return RollingHlsLiveProducer.Unit(index.toLong(), cursor, duration, ByteArray(64)).also { cursor += duration }
            }
            override fun close() = Unit
        }
        val store = RollingHlsLiveFileStore(dir, target, 8, 32L * 1024 * 1024, { now }, { true }, { true })
        try {
            RollingHlsLiveProducer(input, store, target, { now }, { true }).use { producer ->
                repeat(3) { n ->
                    check(producer.step() == RollingHlsLiveProducer.Step.Published(n + 1L))
                    if (n < 2) {
                        val wait = producer.step() as RollingHlsLiveProducer.Step.Waiting
                        check(wait.retryAfterUs == durations[n])
                        now += wait.retryAfterUs
                    } else now += durations[n]
                }
                store.openPlaylist()!!.use { check(it.text()!!.contains("#EXTINF:6.000333,")) }
                if (bad) {
                    try { producer.step(); error("Expected rejection") } catch (_: IOException) { }
                    check(dir.listFiles()!!.isEmpty())
                } else {
                    check(producer.step() == RollingHlsLiveProducer.Step.Published(4))
                    check(producer.step() == RollingHlsLiveProducer.Step.End)
                    store.openPlaylist()!!.use { check(it.text()!!.endsWith("#EXT-X-ENDLIST\n")) }
                }
            }
            check(dir.listFiles()!!.isEmpty())
        } finally { dir.deleteRecursively() }
    }
    println("LIVE_EXACT_TIMING_AND_REJECTION_PASS")
}
