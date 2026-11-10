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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    val maxCaptions: Int = 30,
    /** Time limit includes waiting for the session's network permit. */
    val inspectionTimeoutMillis: Long = 40_000L
) {
    init {
        require(MediaForgeUrlPolicy.safeHttpsUrl(embedUrl) != null) {
            "MediaForge requires an external HTTPS embed"
        }
        require(maxStreams in 1..100 && maxCaptions in 1..100) {
            "MediaForge discovery budgets must be between 1 and 100"
        }
        require(inspectionTimeoutMillis in 1_000L..120_000L) {
            "MediaForge inspection timeout must be between 1 and 120 seconds"
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

/**
 * Fixed-size, in-memory diagnostics. No URLs, query tokens, headers,
 * subtitle labels, account IDs or provider errors are retained here.
 * The SDK never uploads or logs these counters.
 */
data class MediaForgeSessionDiagnostics(
    val observedStreams: Int,
    val observedCaptions: Int,
    val inspectionsStarted: Long,
    val inspectionsVerified: Long,
    val inspectionsRejected: Long,
    val inspectionsCancelled: Long,
    val inspectionsTimedOut: Long,
    val inspectionActive: Boolean,
    val lastRejection: MediaForgeInspectionFailure?
)

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

    /** Fixed-size metrics with no stream or subtitle URLs. */
    fun diagnostics(): MediaForgeSessionDiagnostics

    /** Only an ID emitted by this session can be inspected. Latest request wins. */
    suspend fun inspect(candidateId: String): MediaForgeSessionInspection

    /** Idempotent: clears sources and cancels in-flight probes. */
    override fun close()
}

private class DefaultMediaForgeSession(
    private val request: MediaForgeSessionRequest,
    private val inspector: MediaForgeInspector
) : MediaForgeSession {
    private val lock = Any()
    // Cancellation plus an explicit permit keeps one active inspector per
    // session even if the user rapidly switches source candidates.
    private val inspectionPermit = Semaphore(1)
    private val discovery = MediaForgeDiscovery(request.maxStreams, request.maxCaptions)
    private val idsByUrl = LinkedHashMap<String, String>()
    private val sessionPrefix = UUID.randomUUID().toString()
    private var nextId = 0L
    private var generation = 0L
    private var inspectionEpoch = 0L
    private var closed = false
    private var active: Deferred<InspectionResult>? = null
    private var inspectionsStarted = 0L
    private var inspectionsVerified = 0L
    private var inspectionsRejected = 0L
    private var inspectionsCancelled = 0L
    private var inspectionsTimedOut = 0L
    private var lastRejection: MediaForgeInspectionFailure? = null

    private fun recordRejected(reason: MediaForgeInspectionFailure) {
        inspectionsRejected++
        lastRejection = reason
    }

    override fun diagnostics(): MediaForgeSessionDiagnostics = synchronized(lock) {
        val counts = discovery.snapshot()
        MediaForgeSessionDiagnostics(
            observedStreams = counts.streams.size,
            observedCaptions = counts.captions.size,
            inspectionsStarted = inspectionsStarted,
            inspectionsVerified = inspectionsVerified,
            inspectionsRejected = inspectionsRejected,
            inspectionsCancelled = inspectionsCancelled,
            inspectionsTimedOut = inspectionsTimedOut,
            inspectionActive = active != null,
            lastRejection = lastRejection
        )
    }

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
            return@coroutineScope synchronized(lock) {
                val reason = if (closed) MediaForgeInspectionFailure.SESSION_CLOSED
                    else MediaForgeInspectionFailure.NOT_OBSERVED
                recordRejected(reason)
                MediaForgeSessionInspection.Rejected(reason)
            }
        }

        val task = async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val checked = withTimeoutOrNull(request.inspectionTimeoutMillis) {
                    inspectionPermit.withPermit {
                        inspector.inspect(selection.stream, selection.captions)
                    }
                }
                if (checked == null) {
                    synchronized(lock) { inspectionsTimedOut++ }
                    InspectionResult.Unavailable("Inspection time budget exceeded")
                } else checked
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
                inspectionsStarted++
                inspectionEpoch
            }
        }
        if (epoch < 0L) {
            task.cancel()
            return@coroutineScope synchronized(lock) {
                val reason = if (closed) MediaForgeInspectionFailure.SESSION_CLOSED
                    else MediaForgeInspectionFailure.SUPERSEDED
                recordRejected(reason)
                MediaForgeSessionInspection.Rejected(reason)
            }
        }
        prior?.cancel()

        try {
            val result = task.await()
            synchronized(lock) {
                val outcome = when {
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
                    else -> MediaForgeSessionInspection.Rejected(
                        MediaForgeInspectionFailure.SOURCE_UNAVAILABLE
                    )
                }
                when (outcome) {
                    is MediaForgeSessionInspection.Verified -> inspectionsVerified++
                    is MediaForgeSessionInspection.Rejected -> recordRejected(outcome.reason)
                }
                outcome
            }
        } catch (cancelled: CancellationException) {
            // Respect cancellation originating in the host; only convert a
            // cancellation initiated by this session into a typed response.
            if (!currentCoroutineContext().isActive) {
                synchronized(lock) { inspectionsCancelled++ }
                throw cancelled
            }
            synchronized(lock) {
                inspectionsCancelled++
                val reason = if (closed) MediaForgeInspectionFailure.SESSION_CLOSED
                    else MediaForgeInspectionFailure.SUPERSEDED
                recordRejected(reason)
                MediaForgeSessionInspection.Rejected(reason)
            }
        } finally {
            synchronized(lock) {
                if (active === task) active = null
            }
            task.cancel()
        }
    }
}
