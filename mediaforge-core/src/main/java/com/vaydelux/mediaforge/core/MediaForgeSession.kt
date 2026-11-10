package com.vaydelux.mediaforge.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import java.util.UUID

/**
 * Host-level request for one user-selected external embed.
 *
 * No credentials, Telegram state, WebView cookie jar, or native player is
 * accepted here. Signed media URLs are observed later and remain in memory.
 */
data class MediaForgeSessionRequest(
    val embedUrl: String,
    val maxStreams: Int = 20,
    val maxCaptions: Int = 30
) {
    init {
        require(MediaForgeUrlPolicy.safeHttpsUrl(embedUrl) != null) {
            "MediaForge requires an external HTTPS embed"
        }
        require(maxStreams in 1..100 && maxCaptions in 1..100) {
            "MediaForge discovery budgets must be between 1 and 100"
        }
    }
}

/** Opaque, session-scoped ID: no signed media URL is exposed to picker UI. */
data class MediaForgeStreamOption(
    val id: String,
    val format: StreamFormat,
    val host: String
)

data class MediaForgeSessionSnapshot(
    val streams: List<MediaForgeStreamOption>,
    val captions: List<CaptionCandidate>
)

sealed interface MediaForgeSessionState {
    data class Ready(
        val generation: Long,
        val media: MediaForgeSessionSnapshot
    ) : MediaForgeSessionState

    data object Closed : MediaForgeSessionState
}

/** Fixed failure codes; never surface arbitrary network or provider errors. */
enum class MediaForgeInspectionFailure {
    NOT_OBSERVED,
    SUPERSEDED,
    SESSION_CLOSED,
    SOURCE_UNAVAILABLE,
    UNSAFE_RESULT
}

sealed interface MediaForgeSessionInspection {
    /** A bounded probe succeeded, not a guarantee that every frame decodes. */
    data class Verified(val media: InspectedMedia) : MediaForgeSessionInspection
    data class Rejected(val reason: MediaForgeInspectionFailure) : MediaForgeSessionInspection
}

/** Substitute an inspector in tests or in a host's authorized media transport. */
fun interface MediaForgeInspector {
    suspend fun inspect(
        stream: StreamCandidate,
        captions: List<CaptionCandidate>
    ): InspectionResult
}

/**
 * Public SDK entry point. One instance may open independent, disposable
 * sessions. All network work stays in the injected guarded inspector.
 */
class MediaForgeSdk(
    private val inspector: MediaForgeInspector = MediaForgeInspector { stream, captions ->
        MediaForgeResolver().inspect(stream, captions)
    }
) {
    fun openSession(request: MediaForgeSessionRequest): MediaForgeSession =
        DefaultMediaForgeSession(request, inspector)
}

/** The host owns a session and must close it when its selected embed exits. */
interface MediaForgeSession : AutoCloseable {
    val state: StateFlow<MediaForgeSessionState>

    /** Observe public HTTPS media resources supplied by a user-visible embed. */
    fun observe(url: String): Boolean

    /** Never includes raw stream bearer URLs; captions remain in-memory only. */
    fun snapshot(): MediaForgeSessionSnapshot

    /** Invalidates prior candidate IDs and cancels any active inspection. */
    fun clear()

    /** Only an ID emitted by this session can be inspected. Latest request wins. */
    suspend fun inspect(candidateId: String): MediaForgeSessionInspection

    /** Idempotent: clears sources and cancels in-flight probes. */
    override fun close()
}

private class DefaultMediaForgeSession(
    request: MediaForgeSessionRequest,
    private val inspector: MediaForgeInspector
) : MediaForgeSession {
    private val lock = Any()
    private val discovery = MediaForgeDiscovery(request.maxStreams, request.maxCaptions)
    private val idsByUrl = LinkedHashMap<String, String>()
    private val sessionPrefix = UUID.randomUUID().toString()
    private var nextId = 0L
    private var generation = 0L
    private var inspectionEpoch = 0L
    private var closed = false
    private var active: Deferred<InspectionResult>? = null

    private val changes = MutableStateFlow<MediaForgeSessionState>(
        MediaForgeSessionState.Ready(0L, MediaForgeSessionSnapshot(emptyList(), emptyList()))
    )
    override val state: StateFlow<MediaForgeSessionState> = changes.asStateFlow()

    private fun snapshotLocked(): MediaForgeSessionSnapshot {
        val observed = discovery.snapshot()
        val activeUrls = observed.streams.mapTo(HashSet()) { it.url }
        idsByUrl.keys.retainAll(activeUrls)
        val streams = observed.streams.map { stream ->
            // Never reuse an ID after eviction, reload, or provider switch.
            val id = idsByUrl.getOrPut(stream.url) { sessionPrefix + "-mf-" + (++nextId) }
            MediaForgeStreamOption(id, stream.format, stream.host)
        }
        return MediaForgeSessionSnapshot(streams, observed.captions)
    }

    override fun observe(url: String): Boolean = synchronized(lock) {
        if (closed || !discovery.observe(url)) return@synchronized false
        changes.value = MediaForgeSessionState.Ready(generation, snapshotLocked())
        true
    }

    override fun snapshot(): MediaForgeSessionSnapshot = synchronized(lock) {
        if (closed) MediaForgeSessionSnapshot(emptyList(), emptyList())
        else snapshotLocked()
    }

    override fun clear() {
        val previous = synchronized(lock) {
            if (closed) return
            generation++
            inspectionEpoch++
            val pending = active
            active = null
            discovery.clear()
            idsByUrl.clear()
            changes.value = MediaForgeSessionState.Ready(generation, snapshotLocked())
            pending
        }
        previous?.cancel()
    }

    override fun close() {
        val previous = synchronized(lock) {
            if (closed) return
            closed = true
            generation++
            inspectionEpoch++
            val pending = active
            active = null
            discovery.clear()
            idsByUrl.clear()
            changes.value = MediaForgeSessionState.Closed
            pending
        }
        previous?.cancel()
    }

    private data class Selection(
        val generation: Long,
        val stream: StreamCandidate,
        val captions: List<CaptionCandidate>
    )

    override suspend fun inspect(candidateId: String): MediaForgeSessionInspection = coroutineScope {
        val selection = synchronized(lock) {
            if (closed) null else {
                val raw = discovery.snapshot()
                val candidate = raw.streams.firstOrNull { idsByUrl[it.url] == candidateId }
                if (candidate != null) Selection(generation, candidate, raw.captions) else null
            }
        }
        if (selection == null) {
            return@coroutineScope MediaForgeSessionInspection.Rejected(
                synchronized(lock) {
                    if (closed) MediaForgeInspectionFailure.SESSION_CLOSED
                    else MediaForgeInspectionFailure.NOT_OBSERVED
                }
            )
        }

        val task = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                inspector.inspect(selection.stream, selection.captions)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Provider exceptions may contain signed URLs or credentials.
                InspectionResult.Unavailable("Source unavailable")
            }
        }
        var prior: Deferred<InspectionResult>? = null
        val epoch = synchronized(lock) {
            if (closed || generation != selection.generation) {
                -1L
            } else {
                inspectionEpoch++
                prior = active
                active = task
                inspectionEpoch
            }
        }
        if (epoch < 0L) {
            task.cancel()
            return@coroutineScope MediaForgeSessionInspection.Rejected(
                synchronized(lock) {
                    if (closed) MediaForgeInspectionFailure.SESSION_CLOSED
                    else MediaForgeInspectionFailure.SUPERSEDED
                }
            )
        }
        prior?.cancel()

        try {
            val result = task.await()
            synchronized(lock) {
                when {
                    closed -> MediaForgeSessionInspection.Rejected(
                        MediaForgeInspectionFailure.SESSION_CLOSED
                    )
                    generation != selection.generation || inspectionEpoch != epoch ->
                        MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.SUPERSEDED)
                    result is InspectionResult.Unavailable ->
                        MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.SOURCE_UNAVAILABLE)
                    result is InspectionResult.Playable -> {
                        val finalUri = MediaForgeUrlPolicy.safeHttpsUrl(result.media.stream.url)
                        if (finalUri == null ||
                            !finalUri.host.equals(result.media.stream.host, ignoreCase = true)
                        ) {
                            MediaForgeSessionInspection.Rejected(MediaForgeInspectionFailure.UNSAFE_RESULT)
                        } else {
                            MediaForgeSessionInspection.Verified(result.media)
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            // Respect cancellation originating in the host; only convert a
            // cancellation initiated by this session into a typed response.
            if (!currentCoroutineContext().isActive) throw cancelled
            synchronized(lock) {
                MediaForgeSessionInspection.Rejected(
                    if (closed) MediaForgeInspectionFailure.SESSION_CLOSED
                    else MediaForgeInspectionFailure.SUPERSEDED
                )
            }
        } finally {
            synchronized(lock) {
                if (active === task) active = null
            }
            task.cancel()
        }
    }
}
