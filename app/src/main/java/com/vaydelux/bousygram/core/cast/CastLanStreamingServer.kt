package com.vaydelux.bousygram.core.cast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.tdlib.server.HttpRangeParser
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler
import com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager
import com.vaydelux.bousygram.core.tdlib.server.HttpByteRange
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import java.net.Inet4Address
import java.lang.ref.WeakReference
import java.util.concurrent.Semaphore

/** Session-only Ktor endpoint bound to the active private LAN IPv4 address. */
object CastLanStreamingServer {
    private const val TAG = "CastLanStreamingServer"
    private val clientPermit = Semaphore(1)
    @Volatile private var server: NettyApplicationEngine? = null
    @Volatile private var capability: CastAccessRegistry.Capability? = null
    @Volatile private var publicUrl: String? = null
    private var remuxArtifact: CastRemuxArtifact? = null
    @Volatile private var serverContextRef: WeakReference<Context>? = null

    val isRunning: Boolean get() = server != null

    /** Prevent a sender from loading any URL except the currently authorized
     * session-specific LAN stream. This is not a substitute for LAN TLS. */
    @Synchronized
    internal fun canServeCurrentUrl(url: String): Boolean {
        if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED || server == null) return false
        if (!CastHandoffUrlPolicy.permits(url, publicUrl)) return false
        val current = capability ?: return false
        return CastAccessRegistry.authorize(current.resourceId, current.token) == current.fileId
    }

    @Synchronized
    fun start(context: Context, fileId: Int, size: Long, mimeType: String,
              expectedOwner: AccountSnapshot? = AccountSession.current()): String? =
        startInternal(context, fileId, size, mimeType, null, expectedOwner)

    /** Debug-only verified clip delivery. */
    @Synchronized
    internal fun startRemuxed(context: Context, artifact: CastRemuxArtifact): String? {
        if (!artifact.isAvailable()) return null
        // Caller retains ownership on failure; server owns the output on success.
        return startInternal(context, artifact.fileId, artifact.size, "video/mp4", artifact, artifact.owner)
    }

    private fun startInternal(context: Context, fileId: Int, size: Long, mimeType: String,
                              artifact: CastRemuxArtifact?, expectedOwner: AccountSnapshot?): String? {
        if (expectedOwner == null || !AccountSession.isCurrent(expectedOwner)) return null
        stop()
        if (remuxArtifact != null || (artifact != null && !artifact.isAvailable())) return null
        // The current receiver workflow uses HTTP. A bearer capability is authorization,
        // not encryption, so private media is disabled in production until trusted TLS works
        // with supported physical receivers.
        if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) return null
        if (fileId <= 0 || size <= 0L || !hasLanPermission(context)) return null
        val address = activeLanAddress(context) ?: return null
        val hostAddress = address.hostAddress ?: return null
        val grant = runCatching {
            CastAccessRegistry.issue(fileId, expectedOwner)
        }.getOrElse {
            Log.w(TAG, "Could not create Cast media capability", it)
            return null
        }
        try {
            CastStreamingService.start(context.applicationContext)
        } catch (error: Exception) {
            CastAccessRegistry.revoke(grant.resourceId)
            Log.w(TAG, "Could not start visible Cast streaming service", error)
            CastStreamingService.stop(context.applicationContext)
            return null
        }
        val engine = try {
            embeddedServer(Netty, host = hostAddress, port = 0) {
                routing {
                    get("/cast/media/{resourceId}") { serveMedia(call, grant, size, mimeType, artifact) }
                    head("/cast/media/{resourceId}") { serveHead(call, grant, size, mimeType, artifact) }
                }
            }.start(wait = false)
        } catch (error: Exception) {
            CastAccessRegistry.revoke(grant.resourceId)
            CastStreamingService.stop(context.applicationContext)
            Log.w(TAG, "Could not bind Cast server to the active LAN interface", error)
            return null
        }
        return try {
            val port = runBlocking {
                var resolved: Int? = null
                for (attempt in 0 until 50) {
                    resolved = runCatching { engine.resolvedConnectors().firstOrNull()?.port }.getOrNull()
                    if (resolved != null && resolved!! > 0) break
                    delay(20L)
                }
                resolved
            }
            val resolvedPort = port?.takeIf { it > 0 } ?: error("Cast server did not bind")
            check(AccountSession.isCurrent(expectedOwner) && CastAccessRegistry.isActive(grant)) { "Cast account changed" }
            server = engine
            capability = grant
            serverContextRef = WeakReference(context.applicationContext)
            if (artifact == null) TelegramChunkScheduler.noteFileSize(fileId, size)
            remuxArtifact = artifact
            publicUrl = "http://$hostAddress:$resolvedPort/cast/media/${grant.resourceId}?token=${grant.token}"
            Log.i(TAG, "Cast LAN stream started on selected interface for resource ${grant.resourceId}")
            publicUrl
        } catch (error: Exception) {
            runCatching { engine.stop(0, 500) }
            CastAccessRegistry.revoke(grant.resourceId)
            CastStreamingService.stop(context.applicationContext)
            Log.w(TAG, "Cast server startup failed", error)
            null
        }
    }

    @Synchronized
    internal fun stopIfCurrentUrl(url: String) {
        if (publicUrl == url) stop()
    }

    @Synchronized
    fun stop() {
        // Revoke before draining connections so in-flight writers fail closed.
        capability?.let { CastAccessRegistry.revoke(it.resourceId) }
        capability = null
        CastAccessRegistry.revokeAll()
        publicUrl = null
        serverContextRef = null
        server?.let { runCatching { it.stop(0, 1_000) } }
        server = null
        remuxArtifact?.let { artifact ->
            // Keep a failed cleanup reference so subsequent starts fail closed.
            if (runCatching { artifact.close() }.isSuccess) remuxArtifact = null
            else Log.w(TAG, "Cast remux artifact cleanup failed")
        }
        runCatching { CastStreamingService.stop(com.vaydelux.bousygram.app.BousygramApp.instance) }
    }

    private suspend fun serveHead(
        call: io.ktor.server.application.ApplicationCall,
        grant: CastAccessRegistry.Capability,
        size: Long,
        mimeType: String,
        artifact: CastRemuxArtifact?
    ) {
        authorize(call, grant) ?: return
        if (artifact != null && !artifact.isAvailable()) {
            call.respond(HttpStatusCode.Gone, "Remux output unavailable")
            return
        }
        respondHeaders(call, size, mimeType)
    }

    private suspend fun serveMedia(
        call: io.ktor.server.application.ApplicationCall,
        grant: CastAccessRegistry.Capability,
        size: Long,
        mimeType: String,
        artifact: CastRemuxArtifact?
    ) {
        val fileId = authorize(call, grant) ?: return
        if (artifact != null && !artifact.isAvailable()) {
            call.respond(HttpStatusCode.Gone, "Remux output unavailable")
            return
        }
        val range = parseRange(call, size) ?: return
        // A busy response must not advertise the media body's length.
        if (!clientPermit.tryAcquire()) {
            call.response.header(HttpHeaders.RetryAfter, "1")
            call.response.header("Cache-Control", "no-store")
            call.respond(HttpStatusCode.ServiceUnavailable, "Cast stream is busy")
            return
        }
        try {
            val isPartial = range.isPartial
            val length = range.endInclusive - range.start + 1L
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.response.header(HttpHeaders.ContentLength, length.toString())
            call.response.header(HttpHeaders.ContentType, safeContentType(mimeType).toString())
            call.response.header("Cache-Control", "private, no-store")
            call.response.header("X-Content-Type-Options", "nosniff")
            if (isPartial) call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.endInclusive}/$size")
            val status = if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK
            call.respondBytesWriter(contentType = safeContentType(mimeType), status = status) {
                coroutineScope {
                    val permitted = { CastAccessRegistry.isActive(grant) &&
                        serverContextRef?.get()?.let(::hasLanPermission) == true }
                    if (artifact != null) {
                        withContext(Dispatchers.IO) {
                            artifact.stream(range.start, range.endInclusive, permitted) { bytes, count ->
                                coroutineContext.ensureActive()
                                this@respondBytesWriter.writeFully(bytes, 0, count)
                                this@respondBytesWriter.flush()
                            }
                        }
                    } else TelegramProxyManager.streamTelegramFileSequentially(
                        fileId,
                        HttpByteRange(range.start, range.endInclusive),
                        this@respondBytesWriter,
                        mayContinue = permitted
                    )
                }
            }
        } catch (error: Exception) {
            if (artifact == null) TelegramChunkScheduler.onClientDisconnected(fileId, range.start)
            Log.d(TAG, "Cast media client disconnected for resource ${grant.resourceId}")
            throw error
        } finally {
            clientPermit.release()
        }
    }

    private suspend fun respondHeaders(
        call: io.ktor.server.application.ApplicationCall,
        size: Long,
        mimeType: String
    ) {
        val range = parseRange(call, size) ?: return
        val length = range.endInclusive - range.start + 1L
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentLength, length.toString())
        call.response.header(HttpHeaders.ContentType, safeContentType(mimeType).toString())
        call.response.header("Cache-Control", "private, no-store")
        call.response.header("X-Content-Type-Options", "nosniff")
        if (range.isPartial) call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.endInclusive}/$size")
        call.respond(if (range.isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK)
    }

    private suspend fun parseRange(
        call: io.ktor.server.application.ApplicationCall,
        size: Long
    ): HttpRangeParser.Range? = when (val parsed = HttpRangeParser.parse(call.request.header(HttpHeaders.Range), size)) {
        is HttpRangeParser.Result.Valid -> parsed.range
        HttpRangeParser.Result.Unsatisfiable -> {
            call.response.header(HttpHeaders.ContentRange, "bytes */$size")
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Unsatisfiable byte range")
            null
        }
    }

    private suspend fun authorize(
        call: io.ktor.server.application.ApplicationCall,
        expected: CastAccessRegistry.Capability
    ): Int? {
        if (serverContextRef?.get()?.let(::hasLanPermission) != true) {
            call.respond(HttpStatusCode.Forbidden, "Local network permission is unavailable")
            return null
        }
        val resourceId = call.parameters["resourceId"]
        val token = call.request.queryParameters["token"]
        val fileId = CastAccessRegistry.authorize(resourceId, token)
        if (fileId != expected.fileId || resourceId != expected.resourceId) {
            call.respond(HttpStatusCode.Forbidden, "Invalid or expired Cast capability")
            return null
        }
        return fileId
    }

    private fun safeContentType(value: String): ContentType =
        runCatching { ContentType.parse(value) }.getOrDefault(ContentType.Application.OctetStream)

    private fun hasLanPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun activeLanAddress(context: Context): Inet4Address? {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val networks = buildList {
            connectivity.activeNetwork?.let(::add)
            addAll(connectivity.allNetworks.asList())
        }.distinct()
        for (network in networks) {
            val capabilities = connectivity.getNetworkCapabilities(network) ?: continue
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            ) continue
            val addresses = connectivity.getLinkProperties(network)?.linkAddresses.orEmpty()
            val address = addresses.asSequence().mapNotNull { it.address as? Inet4Address }
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress && it.isSiteLocalAddress }
            if (address != null) return address
        }
        return null
    }
}
