package com.vaydelux.bousygram.core.cast

import java.io.File
import java.io.IOException

/** Uses the actual output pulled from the isolated Android native bridge. */
fun main(args: Array<String>) {
    val fixtures = File(args[0])
    val scratch = File(args[1])
    for (ext in listOf("mkv", "ts")) {
        val converted = File(fixtures, "converted.$ext.mp4")
        check(converted.length() in 1L..20L * 1024 * 1024)
        val directory = File(scratch, "segments-$ext")
        check(directory.mkdir())
        val store = RollingHlsSegmentStore(directory, { true }, { true },
            firstSequence = 1, capacitySegments = 2)
        var retired = 0
        var peakStore = 0L
        var accepted = 0
        RollingHlsStreamingSink(store, { true }, RollingFmp4SampleVerifier(), { incoming ->
            val oldest = store.snapshot().first()
            check(oldest.sequence < incoming.sequence)
            // Consume all bytes before acknowledging the oldest segment.
            store.open(oldest.sequence)!!.use { lease ->
                var read = 0L
                val bytes = ByteArray(4096)
                while (true) {
                    val count = lease.read(bytes, 0, bytes.size)
                    if (count < 0) break
                    read += count
                }
                check(read == oldest.size)
            }
            peakStore = maxOf(peakStore, store.retainedBytes())
            store.retireBefore(oldest.sequence + 1)
            retired++
            true
        }, { System.nanoTime() / 1_000_000L }).use { sink ->
            converted.inputStream().use { input ->
                // Deliberately split across box boundaries, not native packet sizes.
                val chunk = ByteArray(4093)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    check(count > 0)
                    sink.write(chunk, 0, count)
                    peakStore = maxOf(peakStore, store.retainedBytes())
                }
            }
            sink.finish()
            val remaining = store.snapshot()
            accepted = retired + remaining.size
            check(accepted == 12 && remaining.last().sequence == 12L)
            check(retired == 10 && remaining.size == 2)
            check(sink.initialization()?.isNotEmpty() == true)
            check(sink.assemblyHighWaterBytes() <= RollingHlsBudget.MAX_SEGMENT_BYTES)
            check(peakStore <= RollingHlsBudget.MAX_WINDOW_BYTES)
            println("PASS $ext: $accepted fragments; acknowledged=$retired; assembly_bytes=${sink.assemblyHighWaterBytes()}; retained_peak=$peakStore")
        }
        check(directory.listFiles()?.isEmpty() == true)
        check(directory.delete())

        // Truncated real output cannot finish or retain a partially valid store.
        val rejected = File(scratch, "rejected-$ext")
        check(rejected.mkdir())
        val rejectedStore = RollingHlsSegmentStore(rejected, { true }, { true }, firstSequence = 1)
        RollingHlsStreamingSink(rejectedStore, { true }, RollingFmp4SampleVerifier(), { incoming ->
            rejectedStore.retireBefore(incoming.sequence)
            true
        }, { System.nanoTime() / 1_000_000L }).use { sink ->
            val all = converted.readBytes()
            sink.write(all, 0, all.size - 1)
            try {
                sink.finish()
                error("Truncated real output accepted")
            } catch (_: IOException) { }
            check(rejectedStore.snapshot().isEmpty())
        }
        check(rejected.listFiles()?.isEmpty() == true)
        check(rejected.delete())
        println("PASS $ext: truncated output rejected and scratch reclaimed")
    }
}
