package com.vaydelux.bousygram.core.cast

import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * Debug-only, finite JNI callback adapter. Construct and execute on a dedicated
 * IO thread. The caller owns a private, unpublished staging sink and MUST
 * discard it on failure or failed independent media verification.
 *
 * No library is loaded here, no receiver is exposed, and the source remains
 * observational (missing bytes abort, rather than repositioning a player).
 * Authorization and admission callbacks must be non-blocking where possible.
 */
internal class RollingHlsNativeCallbacks(
    private val source: RollingHlsSource,
    private val sink: OutputStream,
    private val permitted: () -> Boolean,
    private val admit: (Long) -> Boolean
) : NativeCastRemuxBridge.Callbacks, AutoCloseable {
    enum class Failure { REVOKED, PENDING_INPUT, INVALID_READ, INVALID_WRITE, STORAGE_PRESSURE, IO, CANCELLED }
    data class Outcome(val status: Int, val failure: Failure?, val inputBytes: Long, val outputBytes: Long) {
        // This is only a successful native transfer, never proof of media integrity.
        val transferComplete: Boolean get() = status == 0 && failure == null
    }

    private val worker = Thread.currentThread()
    @Volatile private var closed = false
    @Volatile private var problem: Failure? = null
    private var cursor = 0L
    private var written = 0L
    private var started = false

    init { require(source.size in 1L..MAX_INPUT_BYTES) }

    private fun fail(reason: Failure) {
        if (problem == null) problem = reason
    }

    override fun active(): Boolean {
        if (Thread.currentThread() !== worker || closed || problem != null) return false
        if (!permitted()) { fail(Failure.REVOKED); return false }
        return true
    }

    override fun read(offset: Long, target: ByteArray, count: Int): Int {
        if (!active()) return -1
        if (offset != cursor || count !in 1..IO_BYTES || count > target.size ||
            count.toLong() > source.size - cursor) {
            fail(Failure.INVALID_READ)
            return -1
        }
        val result = try {
            runBlocking { source.readAt(offset, count) }
        } catch (_: CancellationException) {
            fail(Failure.CANCELLED)
            return -1
        } catch (_: IOException) {
            fail(Failure.IO)
            return -1
        }
        if (!active()) return -1
        val bytes = when (result) {
            is RollingHlsSource.Read.Ready -> result.bytes
            RollingHlsSource.Read.Pending -> { fail(Failure.PENDING_INPUT); return -1 }
            RollingHlsSource.Read.End -> { fail(Failure.INVALID_READ); return -1 }
        }
        if (bytes.size !in 1..count) { fail(Failure.INVALID_READ); return -1 }
        bytes.copyInto(target)
        if (!active()) {
            target.fill(0, 0, bytes.size)
            return -1
        }
        cursor += bytes.size
        return bytes.size
    }

    override fun write(bytes: ByteArray, count: Int): Boolean {
        if (!active()) return false
        if (count !in 1..IO_BYTES || count > bytes.size || count.toLong() > MAX_OUTPUT_BYTES - written) {
            fail(Failure.INVALID_WRITE)
            return false
        }
        if (!admit(count.toLong())) { fail(Failure.STORAGE_PRESSURE); return false }
        if (!active()) return false
        try {
            sink.write(bytes, 0, count)
        } catch (_: IOException) {
            fail(Failure.IO)
            return false
        }
        written += count
        if (!active()) return false
        // Fresh accounting must include the bytes just staged, exactly once.
        if (!admit(0)) { fail(Failure.STORAGE_PRESSURE); return false }
        return active()
    }

    /** One-shot invocation. Native library must have been loaded by an explicit
     * qualification caller. Never called by the stable player. */
    fun execute(kind: Int, invoke: (Int, Long, NativeCastRemuxBridge.Callbacks) -> Int =
        NativeCastRemuxBridge::run): Outcome {
        check(Thread.currentThread() === worker && !started && !closed)
        require(kind in 1..2)
        started = true
        try {
            if (!active()) return Outcome(5, problem ?: Failure.REVOKED, cursor, written)
            val status = invoke(kind, source.size, this)
            if (status == 0) {
                if (!active()) fail(Failure.REVOKED)
                else if (cursor != source.size || written <= 0) fail(Failure.INVALID_READ)
                else if (!admit(0)) fail(Failure.STORAGE_PRESSURE)
                else try { sink.flush() } catch (_: IOException) { fail(Failure.IO) }
                if (!active() && problem == null) fail(Failure.REVOKED)
            }
            return Outcome(status, problem, cursor, written)
        } finally {
            close()
        }
    }

    /** Cooperative source revocation from any thread. Sink is caller-owned. */
    override fun close() {
        closed = true
        source.close()
    }

    companion object {
        private const val IO_BYTES = 32 * 1024
        private const val MAX_INPUT_BYTES = 16L * 1024 * 1024
        private const val MAX_OUTPUT_BYTES = 20L * 1024 * 1024
    }
}
