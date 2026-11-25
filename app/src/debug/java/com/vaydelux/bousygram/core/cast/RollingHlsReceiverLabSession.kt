package com.vaydelux.bousygram.core.cast

import android.graphics.Bitmap
import android.os.SystemClock
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.utils.QrCodeGenerator
import java.util.Locale

/** Synthetic receiver lab; all methods on a single IO owner. No real account,
 * remote sender, scanner, listener or user media. A simulated sender still
 * needs a separate explicit local approval. No persistence of invitations. */
internal class RollingHlsReceiverLabSession(
    private val current: () -> Boolean,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime
) : AutoCloseable {
    data class Invitation(val bitmap: Bitmap, val expiresAtMs: Long)
    private val owner = AccountSnapshot(1, 1)
    private val permitted: (AccountSnapshot) -> Boolean = { it == owner && current() }
    private val pairing = RollingHlsPeerPairing(permitted, nowMs)
    private var tv: RollingHlsKeystoreIdentity? = null
    private var sender: RollingHlsKeystoreIdentity? = null
    private var offer: RollingHlsPeerPairing.Offer? = null
    private var pending: RollingHlsPeerPairing.Pending? = null
    private var deadline = 0L
    private var issued = 0L
    private var closed = false

    private fun active() = !closed && current() && nowMs() in issued until deadline

    fun issue(): Invitation {
        check(!closed && tv == null && current())
        val identity = RollingHlsKeystoreIdentity.create(owner, permitted).also { tv = it }
        issued = nowMs()
        deadline = Math.addExact(issued, 120_000L)
        val invitation = checkNotNull(pairing.issue(owner, identity.fingerprint())).also { offer = it }
        val encoded = checkNotNull(RollingHlsPairingQr.encode(invitation))
        val bitmap = checkNotNull(QrCodeGenerator.generate(encoded, 480, 480))
        if (!active()) {
            bitmap.eraseColor(android.graphics.Color.BLACK)
            bitmap.recycle()
            error("Invitation expired")
        }
        return Invitation(bitmap, deadline)
    }

    fun simulateSender(): String {
        check(active() && pending == null && sender == null)
        val invitation = checkNotNull(offer)
        val identity = RollingHlsKeystoreIdentity.create(owner, permitted).also { sender = it }
        val pin = identity.fingerprint()
        val proof = checkNotNull(RollingHlsPeerPairing.proofFor(invitation, pin))
        try {
            pending = checkNotNull(pairing.receive(pin, proof))
            check(active())
            return pin.joinToString(":") { String.format(Locale.ROOT, "%02X", it.toInt() and 255) }
        } finally {
            proof.fill(0)
            invitation.close()
            offer = null
        }
    }

    /** Verify enrolled identities and TLS signing compatibility; this is not
     * a handshake and never starts any network listener. */
    fun confirm(accept: Boolean): Boolean {
        check(active())
        val ticket = checkNotNull(pending)
        pending = null
        val pins = pairing.confirm(ticket, accept)
        if (!accept) { close(); return false }
        checkNotNull(pins)
        val receiver = checkNotNull(tv)
        val remote = checkNotNull(sender)
        RollingHlsPairedTls.create(owner, pins, remote, receiver.certificate(), false, permitted)
        RollingHlsPairedTls.create(owner, pins, receiver, remote.certificate(), true, permitted)
        check(active())
        close()
        return true
    }

    override fun close() {
        closed = true
        pairing.close()
        offer?.close()
        offer = null
        pending = null
        var failure: Exception? = null
        for (key in listOfNotNull(sender, tv)) {
            try { key.close() }
            catch (error: Exception) {
                val previous = failure
                if (previous == null) failure = error else previous.addSuppressed(error)
            }
        }
        failure?.let { throw it }
        sender = null
        tv = null
    }
}
