package com.vaydelux.bousygram.core.cast

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/** Fixed-width versioned payload inside an already authenticated TLS channel.
 * Never serialize a TDLib file ID, title, URL, session secret or credential. */
internal object TelegramHandoffWire {
    private const val REQUEST = 0x42475431
    private const val REPLY = 0x42475432

    fun writeRequest(out: DataOutputStream, request: TelegramTvHandoff) {
        if (!request.isValid()) throw IOException("Invalid Telegram handoff")
        out.writeInt(REQUEST)
        out.writeLong(request.chatId)
        out.writeLong(request.messageId)
        out.writeLong(request.positionMs)
        out.writeByte(if (request.playRequested) 1 else 0)
        out.flush()
    }

    fun readRequest(input: DataInputStream): TelegramTvHandoff {
        if (input.readInt() != REQUEST) throw IOException("Incorrect Telegram handoff mode")
        val chat = input.readLong()
        val message = input.readLong()
        val position = input.readLong()
        val play = input.readUnsignedByte()
        if (play !in 0..1) throw IOException("Invalid handoff playback flag")
        val request = TelegramTvHandoff(chat, message, position, play == 1)
        if (!request.isValid()) throw IOException("Invalid Telegram handoff")
        return request
    }

    fun writeReply(out: DataOutputStream, accepted: Boolean) {
        out.writeInt(REPLY)
        out.writeByte(if (accepted) 1 else 0)
        out.flush()
    }

    fun readReply(input: DataInputStream): Boolean {
        if (input.readInt() != REPLY) throw IOException("Invalid handoff response")
        return when (input.readUnsignedByte()) {
            0 -> false
            1 -> true
            else -> throw IOException("Invalid handoff response flag")
        }
    }
}
