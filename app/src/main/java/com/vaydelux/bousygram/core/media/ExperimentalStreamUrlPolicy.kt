package com.vaydelux.bousygram.core.media

import android.net.Uri

/**
 * Native experimental decoders may access only the private loopback server.
 * Never leak per-file Telegram capabilities to Internet hosts or redirects.
 */
internal fun isTrustedExperimentalLocalStream(uri: Uri): Boolean =
    uri.scheme == "http" &&
    uri.host == "127.0.0.1" &&
    uri.port in 1..65535 &&
    !uri.userInfoPartPresent()

private fun Uri.userInfoPartPresent(): Boolean =
    encodedAuthority.orEmpty().substringBefore('@', "").isNotEmpty()
