package com.vaydelux.bousygram.core.cast

import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Per resource, not per socket. Retry only a transport interruption, never
 * a refusal, a certificate failure, an invalid range or a revoked session.
 * Mutable playlists may be reopened before delivery, never spliced mid-body. */
internal class RollingLanReadRecovery(private val permitted: () -> Boolean) {
    private var attempts = 0
    fun retry(error: IOException, path: String, delivered: Long): Boolean {
        if (!permitted() || attempts >= 2 || delivered < 0 || error is SSLException) return false
        if (error !is EOFException && error !is SocketException && error !is SocketTimeoutException) return false
        val immutable = path == "/init.mp4" || path.startsWith("/segment/")
        if (delivered > 0 && !immutable) return false
        attempts++
        return true
    }
}
