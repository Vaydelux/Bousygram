package com.vaydelux.bousygram.core.cast

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** Debug qualification only. The caller supplies verified media and owns a
 * private empty directory. One outstanding playlist response; hold its lease
 * until the response is flushed or abandoned. No network route is registered. */
internal class RollingHlsLiveFileStore(
    private val directory: File,
    private val targetUs: Long,
    private val capacity: Int,
    private val byteCap: Long,
    private val nowUs: () -> Long,
    private val permitted: () -> Boolean,
    private val admit: (Long) -> Boolean,
    private val delete: (File) -> Boolean = { it.delete() }
) : AutoCloseable {
    private val planner = RollingHlsLiveRetention(targetUs, capacity, byteCap, nowUs)
    private data class Entry(val file: File, val size: Long)
    private val files = linkedMapOf<Long, Entry>()
    private val readers = mutableSetOf<Long>()
    private val orphans = mutableSetOf<File>()
    private var playlist: Playlist? = null
    private var initialization: ByteArray? = null
    @Volatile private var revoked = false
    private var lastClock = -1L
    init {
        require(targetUs % 1_000_000L == 0L)
        require(directory.isDirectory && directory.listFiles()?.isEmpty() == true)
    }

    private fun active(): Boolean {
        if (revoked) return false
        val now = nowUs()
        if (!permitted() || now < 0 || now < lastClock || now > Long.MAX_VALUE - 500_000_000L) {
            revoked = true
            return false
        }
        lastClock = now
        return !revoked
    }

    @Synchronized fun publish(segment: RollingHlsLiveRetention.Segment, input: InputStream): Boolean {
        if (!active() || segment.bytes !in 1L..8L * 1024 * 1024 ||
            files.size >= capacity || segment.bytes > byteCap - planner.retainedBytes() || !admit(segment.bytes)) return false
        val file = File.createTempFile("live-", ".part", directory)
        var stored = false
        try {
            if (!copyExact(file, input, segment.bytes)) return false
            if (!active() || file.length() != segment.bytes || !admit(0) || !planner.append(segment)) return false
            files[segment.sequence] = Entry(file, segment.bytes)
            stored = true
            return true
        } finally {
            if (!stored && !remove(file)) {
                orphans.add(file)
                revoked = true
                throw IOException("Unpublished scratch cleanup failed")
            }
        }
    }

    private fun copyExact(file: File, input: InputStream, size: Long): Boolean {
        val buffer = ByteArray(64 * 1024)
        try {
            val output = file.outputStream()
            try {
                var left = size
                while (left > 0) {
                    if (!active()) return false
                    val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                    if (n <= 0 || n > left || !active()) return false
                    output.write(buffer, 0, n)
                    left -= n
                }
                return input.read() == -1
            } finally { output.close() }
        } finally { buffer.fill(0) }
    }

    /** Caller supplies independently verified init; RAM only, separately bounded. */
    @Synchronized fun installInitialization(bytes: ByteArray): Boolean {
        if (!active() || initialization != null || bytes.size !in 16..512 * 1024) return false
        initialization = bytes.copyOf()
        return true
    }

    @Synchronized fun initialization(): ByteArray? =
        if (active()) initialization?.copyOf() else null

    /** A previous response may still carry an older playlist; do not start
     * the removal grace clock until that response has closed. */
    @Synchronized fun openPlaylist(): Playlist? {
        if (!active() || playlist != null || initialization == null) return null
        val visible = planner.advertise() ?: return null
        val body = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:").append(targetUs / 1_000_000).append('\n')
            append("#EXT-X-MEDIA-SEQUENCE:").append(visible.first().sequence).append('\n')
            append("#EXT-X-MAP:URI=\"init.mp4\"\n")
            for (segment in visible) {
                append("#EXTINF:").append(String.format(Locale.ROOT, "%.6f", segment.durationUs / 1_000_000.0))
                    .append(",\nsegment/").append(segment.sequence).append(".m4s\n")
            }
        }
        if (!active()) return null
        return Playlist(body).also { playlist = it }
    }

    inner class Playlist internal constructor(private val body: String) : AutoCloseable {
        fun text(): String? = synchronized(this@RollingHlsLiveFileStore) {
            body.takeIf { playlist === this && active() }
        }
        override fun close() = synchronized(this@RollingHlsLiveFileStore) {
            if (playlist === this) playlist = null
        }
    }

    @Synchronized fun withdrawOldest(): Boolean = active() && playlist == null && planner.withdrawOldest()

    @Synchronized fun open(sequence: Long): Lease? {
        if (!active() || !planner.acquire(sequence)) return null
        try {
            val entry = checkNotNull(files[sequence])
            if (!entry.file.isFile || entry.file.length() != entry.size) throw IOException("Live segment size changed")
            val input = FileInputStream(entry.file)
            readers.add(sequence)
            return Lease(sequence, input, entry.size)
        } catch (error: Exception) {
            planner.release(sequence)
            revoked = true
            throw error
        }
    }

    inner class Lease internal constructor(val sequence: Long, private val input: FileInputStream,
                                           private var left: Long) : AutoCloseable {
        private var closed = false
        fun read(buffer: ByteArray): Int = synchronized(this@RollingHlsLiveFileStore) {
            require(buffer.size in 1..32 * 1024)
            try {
                if (closed || !active()) throw IOException("Live lease revoked")
                if (left == 0L) return@synchronized -1
                val n = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                if (n <= 0 || !active()) throw IOException("Live segment truncated or revoked")
                left -= n
                n
            } catch (error: Exception) {
                buffer.fill(0)
                revoked = true
                throw error
            }
        }
        override fun close() = synchronized(this@RollingHlsLiveFileStore) {
            if (closed) return@synchronized
            closed = true
            try { input.close() } finally {
                readers.remove(sequence)
                planner.release(sequence)
            }
        }
    }

    private fun remove(file: File): Boolean = try {
        if (file.exists()) delete(file)
        !file.exists()
    } catch (_: Exception) { false }

    /** Failed deletion never frees the planner's slot or byte reservation. */
    @Synchronized fun reclaim(): Boolean {
        if (!active()) return false
        var clean = true
        for (sequence in planner.eligible()) {
            val file = checkNotNull(files[sequence]).file
            if (!remove(file)) { clean = false; continue }
            if (!planner.confirmDeleted(sequence)) {
                revoked = true
                throw IOException("Live reclamation could not be reconciled")
            }
            files.remove(sequence)
        }
        return clean
    }

    @Synchronized fun retainedBytes(): Long = files.values.sumOf { it.size } + orphans.sumOf { it.length() }
    @Synchronized fun retainedCount(): Int = files.size + orphans.size

    /** Revoke before waiting for the IO monitor. Open leases pin their files;
     * caller closes them and retries close. No media access after revocation. */
    override fun close() {
        revoked = true
        synchronized(this) {
            playlist = null
            initialization?.fill(0)
            initialization = null
            val iterator = files.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key !in readers && remove(entry.value.file)) iterator.remove()
            }
            orphans.removeAll { remove(it) }
            if (files.isNotEmpty() || orphans.isNotEmpty()) throw IOException("Live scratch cleanup incomplete")
        }
    }
}
