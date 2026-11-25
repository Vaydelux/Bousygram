package com.vaydelux.bousygram.core.cast

import java.io.IOException

/**
 * Transport-independent qualification dispatcher. A future trusted transport
 * must reject duplicate headers/query parameters, parse the raw path exactly
 * once, avoid access logs containing tokens, and supply a successful flushed
 * send callback. No listener or Ktor registration here. No range support.
 */
internal class RollingHlsDeliveryDispatcher(private val session: RollingHlsDeliverySession) {
    data class Request(
        val method: String,
        val resource: String?,
        val path: String,
        val readToken: String? = null,
        val controlToken: String? = null,
        val hasRange: Boolean = false,
        val hasBody: Boolean = false
    )

    class Response internal constructor(
        val status: Int,
        val contentType: String?,
        val length: Long?,
        private val body: Body? = null
    ) : AutoCloseable {
        val headers: Map<String, String> get() = buildMap {
            put("Cache-Control", "private, no-store")
            put("X-Content-Type-Options", "nosniff")
            put("Referrer-Policy", "no-referrer")
            if (contentType != null) put("Content-Type", contentType)
            if (length != null) put("Content-Length", length.toString())
        }

        /** Sender must return only after successful transmission/flush. */
        fun transmit(send: (ByteArray, Int) -> Unit) { body?.transmit(send) }
        override fun close() { body?.close() }
    }

    internal interface Body : AutoCloseable {
        fun transmit(send: (ByteArray, Int) -> Unit)
    }

    private fun unavailable() = Response(404, null, 0)
    private fun rejected(status: Int) = Response(status, null, 0)

    fun dispatch(request: Request): Response {
        val control = request.method == "POST"
        val authorized = if (control) session.canControl(request.resource, request.controlToken)
            else session.canRead(request.resource, request.readToken)
        if (!authorized) return unavailable()
        if (request.hasRange) return rejected(416)
        if (request.hasBody) return rejected(400)
        if (request.path.length !in 1..64 ||
            request.path.any { it !in 'a'..'z' && it !in '0'..'9' && it != '.' && it != '/' })
            return unavailable()
        if (control) {
            val sequence = sequence(request.path, "ack/", "") ?: return unavailable()
            return if (session.acknowledge(request.resource, request.controlToken, sequence))
                Response(204, null, null) else rejected(409)
        }
        if (request.method != "GET" && request.method != "HEAD") return rejected(405)
        val head = request.method == "HEAD"
        return when (request.path) {
            "index.m3u8" -> {
                val playlist = session.playlist(request.resource, request.readToken) ?: return unavailable()
                bytes(playlist.toByteArray(Charsets.UTF_8), "application/vnd.apple.mpegurl", head)
            }
            "init.mp4" -> {
                val init = session.initialization(request.resource, request.readToken) ?: return unavailable()
                bytes(init, "video/mp4", head)
            }
            else -> {
                val number = sequence(request.path, "segment/", ".m4s") ?: return unavailable()
                val info = session.metadata(request.resource, request.readToken, number) ?: return unavailable()
                if (head) Response(200, "video/mp4", info.size)
                else {
                    val transfer = session.open(request.resource, request.readToken, number)
                        ?: return if (session.canRead(request.resource, request.readToken)) rejected(503) else unavailable()
                    Response(200, "video/mp4", transfer.segment.size, SegmentBody(transfer))
                }
            }
        }
    }

    private fun sequence(path: String, prefix: String, suffix: String): String? {
        if (!path.startsWith(prefix) || !path.endsWith(suffix) ||
            path.length <= prefix.length + suffix.length) return null
        val part = path.substring(prefix.length, path.length - suffix.length)
        if (part.length !in 1..19 || part.first() == '0' || part.any { it !in '0'..'9' }) return null
        return part.takeIf { it.toLongOrNull()?.let { n -> n in 1L until Long.MAX_VALUE } == true }
    }

    private fun bytes(data: ByteArray, type: String, head: Boolean): Response {
        val length = data.size.toLong()
        if (!session.active()) { data.fill(0); return unavailable() }
        if (head) { data.fill(0); return Response(200, type, length) }
        return Response(200, type, length, MemoryBody(data, session::active))
    }

    private class MemoryBody(private val data: ByteArray, private val active: () -> Boolean) : Body {
        private var closed = false
        @Synchronized override fun transmit(send: (ByteArray, Int) -> Unit) {
            check(!closed)
            val buffer = ByteArray(minOf(32 * 1024, data.size))
            try {
                var offset = 0
                while (offset < data.size) {
                    if (!active()) throw IOException("Delivery revoked")
                    val count = minOf(buffer.size, data.size - offset)
                    data.copyInto(buffer, 0, offset, offset + count)
                    if (!active()) throw IOException("Delivery revoked")
                    send(buffer, count)
                    if (!active()) throw IOException("Delivery revoked")
                    offset += count
                }
            } finally {
                buffer.fill(0)
                close()
            }
        }
        @Synchronized override fun close() { closed = true; data.fill(0) }
    }

    private class SegmentBody(private val transfer: RollingHlsDeliverySession.Transfer) : Body {
        private var closed = false
        @Synchronized override fun transmit(send: (ByteArray, Int) -> Unit) {
            check(!closed)
            val buffer = ByteArray(32 * 1024)
            try {
                while (true) {
                    val count = transfer.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    send(buffer, count)
                }
                transfer.complete(true)
            } finally {
                buffer.fill(0)
                close()
            }
        }
        @Synchronized override fun close() {
            if (closed) return
            closed = true
            transfer.close()
        }
    }
}
