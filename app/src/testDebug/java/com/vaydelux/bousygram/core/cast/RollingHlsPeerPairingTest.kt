package com.vaydelux.bousygram.core.cast

import com.vaydelux.bousygram.core.account.AccountSnapshot
import org.junit.Assert.*
import org.junit.Test

class RollingHlsPeerPairingTest {
    private val owner = AccountSnapshot(1, 1)
    private var current = owner
    private var clock = 10L
    private val receiver = ByteArray(32) { 1 }
    private val sender = ByteArray(32) { 2 }

    @Test fun proofBindsBothPinsAndRequiresSeparateOneTimeConsent() {
        RollingHlsPeerPairing({ it == current }, { clock }).use { pairing ->
            pairing.issue(owner, receiver)!!.use { offer ->
                val proof = RollingHlsPeerPairing.proofFor(offer, sender)!!
                assertNull(pairing.receive(ByteArray(32) { 3 }, proof))
                val pending = pairing.receive(sender, proof)!!
                assertNull(pairing.receive(sender, proof))
                val pins = pairing.confirm(pending, true)!!
                assertArrayEquals(sender, pins.senderPin())
                assertArrayEquals(receiver, pins.receiverPin())
                pins.senderPin().fill(0)
                assertArrayEquals(sender, pins.senderPin())
                assertNull(pairing.confirm(pending, true))
            }
        }
    }

    @Test fun denialExpiryAndAccountChangeConsumeOrRejectPending() {
        RollingHlsPeerPairing({ it == current }, { clock }).use { pairing ->
            repeat(3) { i ->
                current = owner
                clock = 10
                pairing.issue(owner, receiver)!!.use { offer ->
                    val pending = pairing.receive(sender, RollingHlsPeerPairing.proofFor(offer, sender)!!)!!
                    if (i == 1) clock += 120_000
                    if (i == 2) current = AccountSnapshot(2, 2)
                    assertNull(pairing.confirm(pending, i != 0))
                    assertNull(pairing.confirm(pending, true))
                }
            }
        }
    }

    @Test fun attemptsBoundAndReissuedOfferRejectsOldProof() {
        RollingHlsPeerPairing({ it == current }, { clock }).use { pairing ->
            pairing.issue(owner, receiver)!!.use { offer ->
                val old = RollingHlsPeerPairing.proofFor(offer, sender)!!
                pairing.issue(owner, receiver)!!.use { next ->
                    assertNull(pairing.receive(sender, old))
                    repeat(7) { assertNull(pairing.receive(sender, ByteArray(32))) }
                    assertNull(pairing.receive(sender, RollingHlsPeerPairing.proofFor(next, sender)!!))
                }
            }
        }
    }
}
