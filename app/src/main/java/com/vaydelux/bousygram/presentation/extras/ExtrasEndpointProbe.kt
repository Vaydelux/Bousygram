package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope

/** These describe only a HEAD response, never the existence of playable video. */
internal enum class ExtrasEndpointObservation {
    RESPONDED, REDIRECTED, RESTRICTED, RATE_LIMITED, MISSING_ROUTE,
    HEAD_NOT_SUPPORTED, SERVER_ERROR, INCONCLUSIVE, CANNOT_CONNECT
}

internal fun classifyExtrasEndpointResponse(status: Int): ExtrasEndpointObservation = when {
    status in 200..299 -> ExtrasEndpointObservation.RESPONDED
    status in 300..399 -> ExtrasEndpointObservation.REDIRECTED
    status == 401 || status == 403 -> ExtrasEndpointObservation.RESTRICTED
    status == 429 -> ExtrasEndpointObservation.RATE_LIMITED
    status == 404 || status == 410 -> ExtrasEndpointObservation.MISSING_ROUTE
    status == 405 || status == 501 -> ExtrasEndpointObservation.HEAD_NOT_SUPPORTED
    status in 500..599 -> ExtrasEndpointObservation.SERVER_ERROR
    else -> ExtrasEndpointObservation.INCONCLUSIVE
}

internal fun describeExtrasEndpointObservation(result: ExtrasEndpointObservation): String = when (result) {
    ExtrasEndpointObservation.RESPONDED ->
        "Website responded. Video playback is not verified."
    ExtrasEndpointObservation.REDIRECTED ->
        "Website redirects. Destination and playback are not verified."
    ExtrasEndpointObservation.RESTRICTED ->
        "Access restricted or blocked. This does not prove the server is dead."
    ExtrasEndpointObservation.RATE_LIMITED ->
        "Rate limited. Try again later."
    ExtrasEndpointObservation.MISSING_ROUTE ->
        "This title URL was not found. Other titles may still work."
    ExtrasEndpointObservation.HEAD_NOT_SUPPORTED ->
        "Website does not support this lightweight check. Try the web player."
    ExtrasEndpointObservation.SERVER_ERROR ->
        "Website reports a temporary server error. Try another source."
    ExtrasEndpointObservation.INCONCLUSIVE ->
        "Connection response was inconclusive. Try the web player."
    ExtrasEndpointObservation.CANNOT_CONNECT ->
        "Could not connect from this device. Network or regional restrictions may apply."
}

/**
 * On-demand, privacy-minimized HEAD request against an explicitly selected
 * public HTTPS embed. Never read HTML/video bodies, send cookies/Telegram
 * credentials, follow redirects or persist remote-host health decisions.
 *
 * A timeout or bot challenge MUST NOT automatically remove a provider.
 */
internal class ExtrasEndpointProbe(
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    suspend fun check(rawUrl: String): ExtrasEndpointObservation = supervisorScope {
        val approved = MediaForgeUrlPolicy.safeHttpsUrl(rawUrl)
            ?: throw IllegalArgumentException("Selected source must be a safe public HTTPS URL")
        val connection = openConnection(approved.toURL())
        val request = async(Dispatchers.IO) {
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "HEAD"
                connection.setRequestProperty("Accept", "text/html")
                connection.setRequestProperty("User-Agent", "Bousygram/Extras server check")
                currentCoroutineContext().ensureActive()
                classifyExtrasEndpointResponse(connection.responseCode)
            } catch (_: IOException) {
                currentCoroutineContext().ensureActive()
                ExtrasEndpointObservation.CANNOT_CONNECT
            } finally {
                connection.disconnect()
            }
        }
        try {
            request.await()
        } finally {
            // Canceling a Composable's scope disconnects the pending socket;
            // a second disconnect by the IO worker is safe.
            connection.disconnect()
        }
    }
}
