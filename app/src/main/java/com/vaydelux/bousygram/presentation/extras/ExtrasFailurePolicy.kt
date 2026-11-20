package com.vaydelux.bousygram.presentation.extras

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Known, actionable UI failures only. Never surface raw provider responses,
 * URLs, API credentials, stack traces or private identifiers in a user-facing
 * Compose error panel. Retry remains user-driven; no infinite automatic loop.
 */
internal object ExtrasFailurePolicy {
    fun message(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.take(6).last()
        val value = listOfNotNull(error.message, root.message).joinToString(" ").lowercase()
        return when {
            error is SocketTimeoutException || root is SocketTimeoutException ||
                "timed out" in value || "timeout" in value ->
                "The request took too long. Check your connection and try again."
            error is UnknownHostException || root is UnknownHostException ||
                error is ConnectException || root is ConnectException ||
                "unable to resolve host" in value || "network is unreachable" in value ->
                "Can't reach the catalog service. Check your connection, then retry."
            error is SSLException || root is SSLException ->
                "A secure connection could not be established. Check your network and device clock."
            "429" in value || "rate limit" in value ||
                "request limit reached" in value || "cooldown" in value ->
                "The catalog is temporarily limiting requests. Please retry after a moment."
            "401" in value || "403" in value || "access denied" in value ||
                "not configured" in value ->
                "This catalog's access is not configured or has been rejected. Anime may still work independently."
            "500" in value || "502" in value || "503" in value || "504" in value ->
                "The catalog provider is temporarily unavailable. Your saved titles are safe."
            "invalid" in value || "malformed" in value || "incomplete" in value ||
                "no catalog data" in value || "response is empty" in value ->
                "The catalog returned incomplete information. Try again or explore another title."
            error is IOException || root is IOException ->
                "The catalog request could not be completed. Check your connection and retry."
            else ->
                "Something went wrong while loading this content. Try again."
        }
    }
}
