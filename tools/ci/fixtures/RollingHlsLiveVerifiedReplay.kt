package com.vaydelux.bousygram.core.cast

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Host virtual-clock replay of actual Android JNI output using the actual
 * framing/sample verifier and paced live file store, not real playback. */
private const val SIX_SECONDS = 6_000_000L
private fun replay(file: File, expected: Long) {
    val directory = Files.createTempDirectory("live-verified").toFile()
    var now = 0L
    var count = 0L
    var peak = 0L
    var peakSlots = 0
    val store = RollingHlsLiveFileStore(directory, SIX_SECONDS, 8, 32L * 1024 * 1024,
        { now }, { true }, { true })
    try {
        file.inputStream().use { reference ->
            val framing = RollingFmp4Framing(reference) { true }
            val init = framing.readInit()
            RollingHlsLiveProducer(RollingHlsLiveVerifiedInput(file.inputStream()) { true },
                store, SIX_SECONDS, { now }, { true }).use { producer ->
                fun readNext() {
                    val fragment = checkNotNull(framing.readFragment())
                    check(fragment.sequence == count + 1)
                    store.open(fragment.sequence)!!.use { lease ->
                        val result = ByteArrayOutputStream()
                        val buffer = ByteArray(4093)
                        while (true) {
                            val n = lease.read(buffer)
                            if (n < 0) break
                            result.write(buffer, 0, n)
                        }
                        check(result.toByteArray().contentEquals(fragment.bytes))
                    }
                    count++
                }
                var published = 0L
                while (true) {
                    when (val step = producer.step()) {
                        is RollingHlsLiveProducer.Step.Published -> {
                            published++
                            check(step.sequence == published && published <= expected)
                            peak = maxOf(peak, store.retainedBytes())
                            peakSlots = maxOf(peakSlots, store.retainedCount())
                            check(directory.listFiles()!!.sumOf { it.length() } == store.retainedBytes())
                            if (published >= 3L) {
                                store.openPlaylist()!!.use { response ->
                                    val body = response.text()!!
                                    check(body.contains("#EXT-X-MEDIA-SEQUENCE:${published - 2}\n"))
                                    check(body.contains("segment/$published.m4s\n"))
                                    check(!body.contains("#EXT-X-ENDLIST"))
                                }
                                check(store.initialization()!!.contentEquals(init))
                                while (count < published) readNext()
                            }
                        }
                        is RollingHlsLiveProducer.Step.Waiting -> {
                            check(step.reason == RollingHlsLiveProducer.Reason.PACING)
                            check(step.retryAfterUs in 1L..SIX_SECONDS)
                            now += step.retryAfterUs
                        }
                        RollingHlsLiveProducer.Step.End -> break
                    }
                }
                check(published == expected && count == expected && framing.readFragment() == null)
                store.openPlaylist()!!.use { check(it.text()!!.endsWith("#EXT-X-ENDLIST\n")) }
                check(peakSlots <= 8 && peak <= 32L * 1024 * 1024)
            }
        }
        check(directory.listFiles()!!.isEmpty())
        println("LIVE_VERIFIED_REPLAY_PASS ${file.parentFile.name}/${file.name} fragments=$count peak_bytes=$peak peak_slots=$peakSlots scratch_remaining=0")
    } finally { directory.deleteRecursively() }
}

private fun termination(file: File) {
    val bytes = file.inputStream().use { input ->
        val framing = RollingFmp4Framing(input) { true }
        framing.readInit() + checkNotNull(framing.readFragment()).bytes
    }
    for (truncated in listOf(false, true)) {
        val directory = Files.createTempDirectory("live-termination").toFile()
        val store = RollingHlsLiveFileStore(directory, SIX_SECONDS, 8, 32L * 1024 * 1024,
            { 0L }, { true }, { true })
        try {
            val source = if (truncated) bytes.copyOf(bytes.size - 1) else bytes
            RollingHlsLiveProducer(RollingHlsLiveVerifiedInput(ByteArrayInputStream(source)) { true },
                store, SIX_SECONDS, { 0L }, { true }).use { producer ->
                if (truncated) {
                    try { producer.step(); error("Truncated source accepted") } catch (_: IOException) { }
                    check(store.openPlaylist() == null && directory.listFiles()!!.isEmpty())
                } else {
                    check(producer.step() == RollingHlsLiveProducer.Step.Published(1))
                    check(store.openPlaylist() == null)
                    check(producer.step() == RollingHlsLiveProducer.Step.End)
                    store.openPlaylist()!!.use { check(it.text()!!.endsWith("#EXT-X-ENDLIST\n")) }
                    store.open(1)!!.close()
                }
            }
            check(directory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
    println("LIVE_FINAL_AND_TRUNCATION_PASS")
}

fun main(args: Array<String>) {
    check(args.size == 1)
    val root = File(args[0])
    for ((subdir, count) in listOf("" to 12L, "extended" to 60L)) {
        for (ext in listOf("mkv", "ts")) replay(File(File(root, subdir), "converted.$ext.mp4"), count)
    }
    termination(File(root, "converted.mkv.mp4"))
}
