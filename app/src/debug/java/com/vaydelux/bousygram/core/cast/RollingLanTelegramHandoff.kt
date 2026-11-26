package com.vaydelux.bousygram.core.cast

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import javax.net.ssl.SSLSocket

/** Explicitly approved one-shot reference transport. No streaming, caching,
 * automatic player launch or playback acknowledgement. The receiving caller
 * must independently authorize and publish the reference against its account. */
internal object RollingLanTelegramHandoff {
    private fun handshake(socket: SSLSocket, peer: RollingHlsPairedTls.Peer,
                          client: Boolean, permitted: () -> Boolean) {
        if (!permitted()) throw IOException("Telegram handoff revoked")
        socket.useClientMode = client
        socket.enabledProtocols = arrayOf("TLSv1.3")
        if (!client) socket.needClientAuth = true
        socket.soTimeout = 5_000
        socket.startHandshake()
        RollingHlsPeerIdentity.requirePeer(socket.session, peer.pin())
        if (!permitted()) throw IOException("Telegram handoff revoked")
    }

    fun send(socket: SSLSocket, peer: RollingHlsPairedTls.Peer, request: TelegramTvHandoff,
             permitted: () -> Boolean): Boolean {
        handshake(socket, peer, true, permitted)
        TelegramHandoffWire.writeRequest(DataOutputStream(socket.outputStream), request)
        socket.soTimeout = 25_000
        val accepted = TelegramHandoffWire.readReply(DataInputStream(socket.inputStream))
        if (!permitted()) throw IOException("Telegram handoff revoked")
        return accepted
    }

    fun receive(socket: SSLSocket, peer: RollingHlsPairedTls.Peer,
                permitted: () -> Boolean, authorizeAndAccept: (TelegramTvHandoff) -> Boolean): Boolean {
        handshake(socket, peer, false, permitted)
        val request = TelegramHandoffWire.readRequest(DataInputStream(socket.inputStream))
        if (!permitted()) throw IOException("Telegram handoff revoked")
        val accepted = authorizeAndAccept(request)
        if (!permitted()) throw IOException("Telegram handoff revoked")
        TelegramHandoffWire.writeReply(DataOutputStream(socket.outputStream), accepted)
        return accepted
    }
}
