package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.vaydelux.bousygram.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

enum class CastPlaybackStatus { Idle, Buffering, Playing, Paused, Ended, Error }

data class CastSessionState(
    val supported: Boolean = false,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val deviceName: String? = null,
    val playbackStatus: CastPlaybackStatus = CastPlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/** Owns Telegram Cast listeners only while its player is mounted. */
object CastSessionController {
    private const val TAG = "CastSessionController"
    private val mutableState = MutableStateFlow(CastSessionState())
    val state: StateFlow<CastSessionState> = mutableState.asStateFlow()

    @Volatile private var applicationContextRef: WeakReference<Context>? = null
    @Volatile private var initialized = false
    private var ownedSessionRef: WeakReference<CastSession>? = null
    private val ownedSession: CastSession? get() = ownedSessionRef?.get()
    private val handler = Handler(Looper.getMainLooper())
    private var loadGeneration = 0L
    private var expectedUrl: String? = null
    private var loadAccepted = false
    private var loadTimeout: Runnable? = null

    private val mediaCallback = object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = refreshRemoteState()
        override fun onMetadataUpdated() = refreshRemoteState()
    }

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            mutableState.value = mutableState.value.copy(errorMessage = null)
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            if (!initialized) return
            if (session.remoteMediaClient != null && session.remoteMediaClient?.mediaInfo == null) attach(session)
            else fail("TV already has media or cannot accept playback. Disconnect it first.")
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) = fail("Cast session could not start ($error)")
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = detach(session)
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            if (initialized && ownedSession === session) attach(session)
        }
        override fun onSessionResumeFailed(session: CastSession, error: Int) = fail("Cast session could not resume ($error)")
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (ownedSession === session) {
                detach(session)
                mutableState.value = mutableState.value.copy(errorMessage = "Cast connection interrupted")
            }
        }
    }

    /** Call on the main thread before presenting a Cast control. */
    fun initialize(context: Context): Boolean {
        if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) {
            mutableState.value = CastSessionState(errorMessage = "Private-media Cast is disabled in this build")
            return false
        }
        if (initialized) return mutableState.value.supported
        val appContext = context.applicationContext
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) != ConnectionResult.SUCCESS) {
            mutableState.value = CastSessionState(errorMessage = "Google Play services is unavailable")
            return false
        }
        return try {
            val castContext = CastContext.getSharedInstance(appContext)
            applicationContextRef = WeakReference(appContext)
            castContext.sessionManager.also {
                it.addSessionManagerListener(listener, CastSession::class.java)
                // Never adopt an existing Extras or another sender's route.
            }
            initialized = true
            mutableState.value = mutableState.value.copy(supported = true)
            true
        } catch (error: Exception) {
            Log.w(TAG, "Google Cast initialization failed", error)
            mutableState.value = CastSessionState(errorMessage = "Google Cast is unavailable on this device")
            false
        }
    }

    fun loadMedia(url: String, title: String, contentType: String, durationMs: Long, positionMs: Long = 0L,
                  playRequested: Boolean = true): Boolean {
        if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) return false
        // Only the short-lived Ktor capability for the active private media
        // session is valid. Never hand a WebView embed, public URL or stale
        // token to a receiver by mistake.
        if (!CastLanStreamingServer.canServeCurrentUrl(url)) return false
        val client = remoteClient() ?: return false
        val owner = ownedSession ?: return false
        // Never replace media that another sender loaded on the receiver.
        if (client.mediaInfo != null && client.mediaInfo?.contentId != expectedUrl) {
            rejectLoad("TV is already playing another source. Disconnect before casting.", stopReceiver = false)
            return false
        }
        clearLoad()
        val generation = loadGeneration
        expectedUrl = url
        mutableState.value = mutableState.value.copy(loading = true, playbackStatus = CastPlaybackStatus.Idle,
            positionMs = positionMs.coerceAtLeast(0L), errorMessage = null)
        val timeout = Runnable {
            if (generation == loadGeneration && ownedSession === owner && mutableState.value.loading) {
                rejectLoad("TV did not confirm playback. Check Wi-Fi, container and codec compatibility.")
            }
        }
        loadTimeout = timeout
        handler.postDelayed(timeout, 25_000L)
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title.take(256))
        }
        val media = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .apply { if (durationMs > 0L) setStreamDuration(durationMs) }
            .build()
        return runCatching {
            client.load(
                MediaLoadRequestData.Builder()
                    .setMediaInfo(media)
                    .setCurrentTime(positionMs.coerceAtLeast(0L))
                    .setAutoplay(playRequested)
                    .build()
            ).setResultCallback { result ->
                if (generation != loadGeneration || ownedSession !== owner) return@setResultCallback
                if (!result.status.isSuccess) {
                    rejectLoad("TV rejected playback (Cast ${result.status.statusCode}). Check format and network.")
                } else {
                    loadAccepted = true
                    refreshRemoteState()
                }
            }
            true
        }.onFailure { rejectLoad("Could not send the stream to the TV.") }.getOrDefault(false)
    }

    private fun command(action: (RemoteMediaClient) -> com.google.android.gms.common.api.PendingResult<RemoteMediaClient.MediaChannelResult>): Boolean {
        val client = controlledClient() ?: return false
        val generation = loadGeneration
        return runCatching {
            action(client).setResultCallback { result ->
                if (generation == loadGeneration && controlledClient() === client && !result.status.isSuccess) {
                    mutableState.value = mutableState.value.copy(errorMessage = "TV rejected the command (Cast ${result.status.statusCode}).")
                }
            }
            true
        }.onFailure {
            mutableState.value = mutableState.value.copy(errorMessage = "Could not send the TV command.")
        }.getOrDefault(false)
    }

    fun play(): Boolean = command { it.play() }
    fun pause(): Boolean = command { it.pause() }
    fun seekTo(positionMs: Long): Boolean = command { it.seek(positionMs.coerceAtLeast(0L)) }
    fun stopPlayback(): Boolean = command { it.stop() }

    private fun controlledClient(): RemoteMediaClient? = remoteClient()?.takeIf {
        loadAccepted && !mutableState.value.loading && expectedUrl != null && it.mediaInfo?.contentId == expectedUrl
    }

    fun endSession(stopReceiver: Boolean = true) {
        val manager = castSessionManager()
        val owned = ownedSession
        if (owned != null && manager != null && manager.currentCastSession === owned) {
            val remote = remoteClient()
            val currentUrl = remote?.mediaInfo?.contentId
            val sameMedia = currentUrl == null || currentUrl == expectedUrl
            if (stopReceiver && sameMedia) runCatching { remote?.stop() }
            runCatching { manager.endCurrentSession(stopReceiver && sameMedia) }
        }
        if (owned != null) detach(owned) else {
            clearLoad()
            CastLanStreamingServer.stop()
        }
    }

    private fun castSessionManager() = applicationContextRef?.get()?.let { context ->
        runCatching { CastContext.getSharedInstance(context).sessionManager }.getOrNull()
    }

    private fun remoteClient() = ownedSession?.takeIf { castSessionManager()?.currentCastSession === it }
        ?.remoteMediaClient

    /** Player composition owns the Telegram listener, not the process lifetime. */
    fun shutdown() {
        if (!initialized) return
        endSession()
        runCatching { castSessionManager()?.removeSessionManagerListener(listener, CastSession::class.java) }
        initialized = false
        applicationContextRef = null
        mutableState.value = CastSessionState()
    }

    private fun attach(session: CastSession) {
        if (ownedSession != null && ownedSession !== session) return
        ownedSessionRef = WeakReference(session)
        session.remoteMediaClient?.unregisterCallback(mediaCallback)
        session.remoteMediaClient?.registerCallback(mediaCallback)
        mutableState.value = mutableState.value.copy(connected = true, deviceName = session.castDevice?.friendlyName, errorMessage = null)
        refreshRemoteState()
    }

    private fun detach(session: CastSession) {
        if (ownedSession !== session) return
        ownedSessionRef = null
        clearLoad()
        runCatching { session.remoteMediaClient?.unregisterCallback(mediaCallback) }
        CastLanStreamingServer.stop()
        mutableState.value = mutableState.value.copy(connected = false, loading = false, deviceName = null, playbackStatus = CastPlaybackStatus.Idle, positionMs = 0L, durationMs = 0L)
    }

    private fun refreshRemoteState() {
        val client = remoteClient() ?: return
        val expected = expectedUrl ?: return
        if (!loadAccepted) return
        val actual = client.mediaInfo?.contentId
        if (actual != expected) {
            if (!mutableState.value.loading) rejectLoad("TV changed media. Casting stopped.", stopReceiver = false)
            return
        }
        val mediaStatus = client.mediaStatus ?: return
        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_IDLE) {
            if (mediaStatus.idleReason == MediaStatus.IDLE_REASON_ERROR) {
                rejectLoad("TV could not decode or retrieve this media. Try a compatible MP4.")
            } else if (!mutableState.value.loading) {
                endSession()
            }
            return
        }
        val playbackStatus = when (mediaStatus?.playerState) {
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_BUFFERING -> CastPlaybackStatus.Buffering
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING -> CastPlaybackStatus.Playing
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PAUSED -> CastPlaybackStatus.Paused
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE -> CastPlaybackStatus.Idle
            else -> CastPlaybackStatus.Idle
        }
        if (playbackStatus == CastPlaybackStatus.Idle) return
        // BUFFERING alone does not confirm successful startup.
        val confirmed = playbackStatus == CastPlaybackStatus.Playing || playbackStatus == CastPlaybackStatus.Paused
        if (confirmed) {
            loadTimeout?.let(handler::removeCallbacks)
            loadTimeout = null
        }
        mutableState.value = mutableState.value.copy(
            loading = mutableState.value.loading && !confirmed,
            playbackStatus = playbackStatus,
            positionMs = client.approximateStreamPosition.coerceAtLeast(0L),
            durationMs = client.streamDuration.coerceAtLeast(0L)
        )
    }

    private fun clearLoad() {
        loadGeneration++
        loadTimeout?.let(handler::removeCallbacks)
        loadTimeout = null
        expectedUrl = null
        loadAccepted = false
    }

    fun reportRouteFailure() {
        if (initialized && ownedSession == null) fail("Cast device selection is unavailable on this device.")
    }

    private fun rejectLoad(message: String, stopReceiver: Boolean = true) {
        endSession(stopReceiver)
        fail(message)
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message, playbackStatus = CastPlaybackStatus.Error)
    }
}
