package com.vaydelux.bousygram.presentation.extras

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ExtrasHttpResult(
    val status: Int,
    val retryAfter: String?,
    val json: String?
)

/**
 * Cancellation-aware, bounded HTTP transport for public catalog metadata.
 *
 * HttpURLConnection blocks on its own socket, so canceling the UI coroutine
 * alone is insufficient. The awaiting coroutine disconnects the connection
 * immediately in finally; the IO worker then exits without caching a stale
 * response. No request carries Telegram credentials or private media.
 */
internal class ExtrasHttpTransport(
    private val openConnection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    }
) {
    suspend fun execute(url: String, body: String?, maxBytes: Int): ExtrasHttpResult =
        coroutineScope {
            require(maxBytes in 1..4_000_000)
            val connection = openConnection(URL(url))
            val request = async(Dispatchers.IO) {
                try {
                    connection.connectTimeout = 6_000
                    connection.readTimeout = 10_000
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty(
                        "User-Agent", "Bousygram/Extras (catalog metadata)"
                    )
                    if (body != null) {
                        connection.requestMethod = "POST"
                        connection.doOutput = true
                        connection.setRequestProperty(
                            "Content-Type", "application/json; charset=utf-8"
                        )
                        connection.outputStream.use {
                            currentCoroutineContext().ensureActive()
                            it.write(body.toByteArray(StandardCharsets.UTF_8))
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    val status = connection.responseCode
                    val retryAfter = connection.getHeaderField("Retry-After")
                    if (status !in 200..299) {
                        ExtrasHttpResult(status, retryAfter, null)
                    } else {
                        val response = connection.inputStream.use { input ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                if (read == 0) continue
                                if (output.size().toLong() + read > maxBytes) {
                                    throw IOException("Catalog response exceeded size limit")
                                }
                                output.write(buffer, 0, read)
                            }
                            output.toString(StandardCharsets.UTF_8.name())
                        }
                        currentCoroutineContext().ensureActive()
                        ExtrasHttpResult(status, retryAfter, response)
                    }
                } finally {
                    connection.disconnect()
                }
            }
            try {
                request.await()
            } finally {
                // Runs on caller cancellation, independently of a blocked IO
                // worker. A second disconnect from its own finally is harmless.
                connection.disconnect()
            }
        }
}
