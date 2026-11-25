package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/**
 * Private controlled-receiver delivery contract, not a registered HTTP route.
 * A read capability authorizes playlist/init/media only. A separate control
 * capability acknowledges fully transmitted segments in strict sequence.
 * GET, HEAD, retries and partial transfers never advance retention.
 *
 * Acknowledgements require a receiver that can independently confirm playback
 * no longer needs the segment. Ordinary HLS clients do not provide this
 * contract. RFC live playlist availability/retention and TLS remain gates.
 * Store must be exclusively owned; callbacks must not acquire this monitor.
 */
internal class RollingHlsDeliverySession(
    private val owner: AccountSnapshot,
    private val fileId: Int,
    private val store: RollingHlsSegmentStore,
    private val current: (AccountSnapshot) -> Boolean,
    private val routeAllowed: () -> Boolean,
    private val nowMs: () -> Long,
    firstSequence: Long = 1L,
    ttlMs: Long = 2L * 60 * 60 * 1000
) : AutoCloseable {
    private val random = SecureRandom()
    val resourceId = randomToken(18)
    val readToken = randomToken(32)
    val controlToken = randomToken(32)
    private val created = nowMs()
    private val expires: Long
    @Volatile private var revoked = false
    private var init: ByteArray? = null
    private var nextAck = firstSequence
    private var finalSequence: Long? = null
    private var inFlight: Transfer? = null
    private val delivered = mutableSetOf<Long>()

    init {
        require(fileId > 0 && firstSequence in 1L until Long.MAX_VALUE)
        require(ttlMs in 1L..2L * 60 * 60 * 1000 && created >= 0L && created <= Long.MAX_VALUE - ttlMs)
        expires = created + ttlMs
        check(active()) { "Delivery owner unavailable" }
    }

    private fun randomToken(size: Int) = ByteArray(size).also(random::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    /** Lock-free predicate usable by producer/store callbacks. */
    fun active(): Boolean = !revoked && current(owner) && routeAllowed() && nowMs() in created until expires

    private fun equal(expected: String, offered: String?): Boolean =
        offered != null && offered.length == expected.length &&
            offered.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' } &&
            MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), offered.toByteArray(Charsets.US_ASCII))

    private fun authorize(resource: String?, token: String?, control: Boolean = false): Boolean =
        active() && equal(resourceId, resource) && equal(if (control) controlToken else readToken, token)

    /** Trusted producer supplies independently verified initialization exactly once. */
    @Synchronized fun installInitialization(bytes: ByteArray): Boolean {
        if (!active() || init != null || bytes.size !in 16..512 * 1024) return false
        val copy = bytes.copyOf()
        if (!active()) { copy.fill(0); return false }
        init = copy
        return true
    }

    /** No caller-supplied URI or path is emitted. Relative capability-bearing
     * URLs are generated from this session and canonical numeric sequence IDs.
     * This draft playlist is for the controlled receiver qualification only. */
    @Synchronized fun playlist(resource: String?, token: String?): String? {
        if (!authorize(resource, token) || init == null) return null
        val segments = store.snapshot()
        if (segments.isEmpty() || segments.first().sequence != nextAck) return null
        val final = finalSequence
        if (final != null && segments.last().sequence != final) return null
        val result = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:7\n")
            append("#EXT-X-TARGETDURATION:12\n")
            append("#EXT-X-MEDIA-SEQUENCE:").append(segments.first().sequence).append('\n')
            append("#EXT-X-MAP:URI=\"init.mp4?token=").append(readToken).append("\"\n")
            for (segment in segments) {
                append("#EXTINF:").append(String.format(Locale.ROOT, "%.6f", segment.durationUs / 1_000_000.0))
                    .append(",\n")
                append("segment/").append(segment.sequence).append(".m4s?token=").append(readToken).append('\n')
            }
            if (final != null) append("#EXT-X-ENDLIST\n")
        }
        return result.takeIf { authorize(resource, token) }
    }

    @Synchronized fun initialization(resource: String?, token: String?): ByteArray? {
        if (!authorize(resource, token)) return null
        val bytes = init?.copyOf() ?: return null
        if (!authorize(resource, token)) { bytes.fill(0); return null }
        return bytes
    }

    /** Producer completion, never inferred from empty buffer or receiver GET. */
    @Synchronized fun markComplete(lastSequence: Long): Boolean {
        if (!active() || init == null || finalSequence != null || lastSequence < nextAck ||
            store.snapshot().lastOrNull()?.sequence != lastSequence) return false
        finalSequence = lastSequence
        return true
    }

    /** One active body transfer globally for this session. HEAD must use
     * metadata, not open a lease. Reject noncanonical/overflow sequence IDs. */
    @Synchronized fun open(resource: String?, token: String?, sequence: String): Transfer? {
        if (!authorize(resource, token) || init == null || inFlight != null) return null
        val number = parseSequence(sequence) ?: return null
        if (number < nextAck) return null
        val lease = store.open(number) ?: return null
        if (!authorize(resource, token)) { lease.close(); return null }
        return Transfer(lease, ::active, ::completed).also { inFlight = it }
    }

    @Synchronized fun metadata(resource: String?, token: String?, sequence: String): RollingHlsSegmentStore.Segment? {
        if (!authorize(resource, token) || init == null) return null
        val number = parseSequence(sequence) ?: return null
        return store.snapshot().firstOrNull { it.sequence == number }.takeIf { authorize(resource, token) }
    }

    private fun parseSequence(value: String): Long? {
        if (value.length !in 1..19 || value[0] == '0' || value.any { it !in '0'..'9' }) return null
        return value.toLongOrNull()?.takeIf { it in 1L until Long.MAX_VALUE }
    }

    @Synchronized private fun completed(transfer: Transfer, sequence: Long, transmitted: Boolean) {
        if (inFlight !== transfer) return
        inFlight = null
        if (transmitted && active() && sequence >= nextAck &&
            store.snapshot().any { it.sequence == sequence }) delivered.add(sequence)
    }

    /** Separate authenticated acknowledgement of playback consumption. Only
     * the next contiguous fully transmitted sequence can retire. Keep final
     * segment available until explicit close so ENDLIST remains retrievable. */
    @Synchronized fun acknowledge(resource: String?, token: String?, sequence: String): Boolean {
        if (!authorize(resource, token, control = true)) return false
        val number = parseSequence(sequence) ?: return false
        if (number != nextAck || number !in delivered || number == finalSequence ||
            store.snapshot().firstOrNull()?.sequence != number) return false
        if (!authorize(resource, token, control = true)) return false
        try {
            store.retireBefore(number + 1)
        } catch (error: IOException) {
            revoked = true
            throw error
        }
        delivered.remove(number)
        nextAck++
        return true
    }

    /** Revoke before waiting on store IO. In-flight lease stays pinned until
     * its owner closes; a second close retries failed scratch reclamation. */
    override fun close() {
        revoked = true
        synchronized(this) {
            init?.fill(0)
            init = null
            delivered.clear()
        }
        store.close()
    }

    class Transfer internal constructor(
        private val lease: RollingHlsSegmentStore.Lease,
        private val permitted: () -> Boolean,
        private val finish: (Transfer, Long, Boolean) -> Unit
    ) : AutoCloseable {
        val segment get() = lease.segment
        private var remaining = segment.size
        private var closed = false
        private var failed = false

        @Synchronized fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (closed || failed || !permitted()) throw IOException("Delivery transfer revoked")
            try {
                val count = lease.read(buffer, offset, length)
                if (!permitted()) {
                    if (count > 0) buffer.fill(0, offset, offset + count)
                    throw IOException("Delivery transfer revoked")
                }
                if (count < 0 && remaining != 0L) throw IOException("Incomplete delivery transfer")
                if (count > 0) remaining -= count
                return count
            } catch (error: Exception) {
                failed = true
                // The store may have filled the buffer before rejecting access.
                if (offset >= 0 && length >= 0 && offset <= buffer.size - length)
                    buffer.fill(0, offset, offset + length)
                throw error
            }
        }

        /** Transport calls only after all body bytes were successfully sent.
         * Merely reading a file is not proof of successful transmission. */
        fun complete(transmitted: Boolean) {
            val eligible = synchronized(this) {
                if (closed) return
                closed = true
                transmitted && !failed && remaining == 0L && permitted()
            }
            var clean = false
            try { lease.close(); clean = true }
            finally { finish(this, segment.sequence, eligible && clean) }
        }

        override fun close() = complete(false)
    }
}
