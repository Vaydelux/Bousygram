package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

enum class CastPlaybackStatus { Idle, Buffering, Playing, Paused, Ended, Error }

data class CastSessionState(
    val supported: Boolean = false,
    val connected: Boolean = false,
    val deviceName: String? = null,
    val playbackStatus: CastPlaybackStatus = CastPlaybackStatus.Idle,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null
)

/** Owns Cast listeners and remote media commands for the process lifetime. */
object CastSessionController {
    private const val TAG = "CastSessionController"
    private val mutableState = MutableStateFlow(CastSessionState())
    val state: StateFlow<CastSessionState> = mutableState.asStateFlow()

    @Volatile private var applicationContextRef: WeakReference<Context>? = null
    @Volatile private var initialized = false

    private val mediaCallback = object : com.google.android.gms.cast.framework.media.RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = refreshRemoteState()
        override fun onMetadataUpdated() = refreshRemoteState()
    }

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            mutableState.value = mutableState.value.copy(errorMessage = null)
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) = fail("Cast session could not start ($error)")
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionEnded(session: CastSession, error: Int) = detach(session)
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionResumeFailed(session: CastSession, error: Int) = fail("Cast session could not resume ($error)")
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            mutableState.value = mutableState.value.copy(errorMessage = "Cast connection interrupted")
        }
    }

    /** Call on the main thread before presenting a Cast control. */
    fun initialize(context: Context): Boolean {
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
                (it.currentCastSession)?.let(::attach)
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

    fun loadMedia(url: String, title: String, contentType: String, durationMs: Long, positionMs: Long = 0L): Boolean {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Cast media URL must be network reachable" }
        val client = remoteClient() ?: return false
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
                    .setAutoplay(true)
                    .build()
            )
            true
        }.onFailure { Log.w(TAG, "Cast media load request failed", it) }.getOrDefault(false)
    }

    fun play(): Boolean = remoteClient()?.let { runCatching { it.play(); true }.getOrDefault(false) } ?: false
    fun pause(): Boolean = remoteClient()?.let { runCatching { it.pause(); true }.getOrDefault(false) } ?: false
    fun seekTo(positionMs: Long): Boolean = remoteClient()?.let {
        runCatching { it.seek(positionMs.coerceAtLeast(0L)); true }.getOrDefault(false)
    } ?: false
    fun stopPlayback(): Boolean = remoteClient()?.let { runCatching { it.stop(); true }.getOrDefault(false) } ?: false

    fun endSession(stopReceiver: Boolean = true) {
        val manager = castSessionManager() ?: return
        if (stopReceiver) remoteClient()?.stop()
        CastLanStreamingServer.stop()
        manager.endCurrentSession(stopReceiver)
    }

    private fun castSessionManager() = applicationContextRef?.get()?.let { context ->
        runCatching { CastContext.getSharedInstance(context).sessionManager }.getOrNull()
    }

    private fun remoteClient() = castSessionManager()?.currentCastSession?.remoteMediaClient

    private fun attach(session: CastSession) {
        session.remoteMediaClient?.unregisterCallback(mediaCallback)
        session.remoteMediaClient?.registerCallback(mediaCallback)
        mutableState.value = mutableState.value.copy(connected = true, deviceName = session.castDevice?.friendlyName, errorMessage = null)
        refreshRemoteState()
    }

    private fun detach(session: CastSession) {
        session.remoteMediaClient?.unregisterCallback(mediaCallback)
        CastLanStreamingServer.stop()
        mutableState.value = mutableState.value.copy(connected = false, deviceName = null, playbackStatus = CastPlaybackStatus.Idle, positionMs = 0L, durationMs = 0L)
    }

    private fun refreshRemoteState() {
        val client = remoteClient() ?: return
        val mediaStatus = client.mediaStatus
        val playbackStatus = when (mediaStatus?.playerState) {
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_BUFFERING -> CastPlaybackStatus.Buffering
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PLAYING -> CastPlaybackStatus.Playing
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_PAUSED -> CastPlaybackStatus.Paused
            com.google.android.gms.cast.MediaStatus.PLAYER_STATE_IDLE -> CastPlaybackStatus.Idle
            else -> CastPlaybackStatus.Idle
        }
        mutableState.value = mutableState.value.copy(
            playbackStatus = playbackStatus,
            positionMs = client.approximateStreamPosition.coerceAtLeast(0L),
            durationMs = client.streamDuration.coerceAtLeast(0L)
        )
    }

    private fun fail(message: String) {
        mutableState.value = mutableState.value.copy(errorMessage = message, playbackStatus = CastPlaybackStatus.Error)
    }
}
