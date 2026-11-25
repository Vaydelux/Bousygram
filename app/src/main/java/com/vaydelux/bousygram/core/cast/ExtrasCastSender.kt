package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.mediaforge.core.MediaForgeCastPlan
import com.vaydelux.mediaforge.core.MediaForgeCastPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class ExtrasCastPhase { IDLE, CHOOSING, CONNECTED, LOADING, ACTIVE, FAILED, DISCONNECTED }
internal data class ExtrasCastCaption(val id: Long, val label: String)
internal data class ExtrasCastState(
    val phase: ExtrasCastPhase = ExtrasCastPhase.IDLE,
    val message: String = "",
    val positionMs: Long? = null,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val live: Boolean = false,
    val captions: List<ExtrasCastCaption> = emptyList(),
    val activeCaptionIds: Set<Long> = emptySet()
)

/**
 * Screen-owned, debug-only sender. All calls and SDK callbacks are main-thread.
 * No URL is logged or persisted; no Telegram controller or LAN streamer is used.
 * An existing/resumed session is never implicitly adopted.
 */
internal class ExtrasCastSender {
    private val lease = ExtrasCastLease()
    private val policy = MediaForgeCastPolicy(BuildConfig.EXTRAS_CAST_QUALIFIED_HOSTS
        .split(',').filter { it.isNotBlank() }.toSet())
    private val mutableState = MutableStateFlow(ExtrasCastState())
    val state = mutableState.asStateFlow()
    private var manager: SessionManager? = null
    private var pending: CastSession? = null
    private var owned: CastSession? = null
    private var client: RemoteMediaClient? = null
    private var armed = false
    private var closed = false
    private var deadline = 0L
    private var loadAcknowledged = false

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = refresh()
        override fun onMetadataUpdated() = refresh()
    }

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            if (armed && !closed && owned == null) pending = session
        }
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            if (closed || !armed || pending !== session || manager?.currentCastSession !== session) return
            armed = false
            pending = null
            val remote = session.remoteMediaClient
            if (remote == null || !lease.claim(sessionId, true, remote.mediaInfo != null)) {
                fail("Receiver already has media or is unavailable. Disconnect it before trying again.")
                return
            }
            owned = session
            client = remote
            remote.registerCallback(mediaCallback)
            mutableState.value = ExtrasCastState(ExtrasCastPhase.CONNECTED, "TV connected. Select Cast video to start.")
        }
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            if (pending === session) fail("Could not connect to the TV.")
        }
        override fun onSessionEnding(session: CastSession) {
            if (owned === session) releaseOwnership(ExtrasCastPhase.DISCONNECTED, "TV disconnected. Local playback remains paused.")
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (owned === session || pending === session) releaseOwnership(ExtrasCastPhase.DISCONNECTED, "TV disconnected.")
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (owned === session) {
                // End our local route without assuming a disconnected receiver
                // can acknowledge a stop. Never adopt a subsequently resumed session.
                val current = manager
                releaseOwnership(ExtrasCastPhase.DISCONNECTED, "Connection interrupted. Check the TV before playing locally.")
                if (current != null && current.currentCastSession === session) runCatching { current.endCurrentSession(false) }
            }
        }
    }

    fun chooseReceiver(context: Context): Boolean {
        if (closed || !BuildConfig.DEBUG || BuildConfig.EXTRAS_CAST_QUALIFIED_HOSTS.isBlank()) return false
        if (owned != null || pending != null || armed) return false
        return try {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
                fail("Google Play services is unavailable.")
                return false
            }
            val sessions = CastContext.getSharedInstance(context.applicationContext).sessionManager
            // A route may belong to Telegram or another app screen. Do not adopt it.
            if (sessions.currentCastSession != null) {
                fail("Disconnect the existing Cast session first.")
                return false
            }
            if (manager == null) {
                manager = sessions
                sessions.addSessionManagerListener(listener, CastSession::class.java)
            }
            armed = true
            mutableState.value = ExtrasCastState(ExtrasCastPhase.CHOOSING, "Choose your TV using the Cast icon.")
            true
        } catch (_: Exception) {
            fail("Google Cast is unavailable.")
            false
        }
    }

    fun cancelChoice() {
        if (owned == null) {
            armed = false
            pending = null
            mutableState.value = ExtrasCastState()
        }
    }

    fun load(plan: MediaForgeCastPlan): Boolean {
        if (closed || !BuildConfig.DEBUG || mutableState.value.phase != ExtrasCastPhase.CONNECTED ||
            !policy.permits(plan)) return false
        val session = owned ?: return false
        val remote = client ?: return false
        if (manager?.currentCastSession !== session || !session.isConnected || remote.mediaInfo != null) {
            fail("Receiver is no longer available for this source.")
            return false
        }
        val id = session.sessionId ?: return false
        val token = lease.beginLoad(id, plan.url) ?: return false
        deadline = SystemClock.elapsedRealtime() + 20_000L
        loadAcknowledged = false
        mutableState.value = ExtrasCastState(ExtrasCastPhase.LOADING, "Waiting for TV playback.", live = plan.live)
        return try {
            remote.load(ExtrasCastRequestAdapter.build(plan)).setResultCallback { result ->
                if (closed || owned !== session || client !== remote ||
                    mutableState.value.phase != ExtrasCastPhase.LOADING) return@setResultCallback
                if (!result.status.isSuccess) {
                    failOwned("The receiver rejected this stream.")
                    return@setResultCallback
                }
                if (!lease.acknowledge(token, id, true)) return@setResultCallback
                loadAcknowledged = true
                refresh()
            }
            true
        } catch (_: Exception) {
            failOwned("Unable to send media to the receiver.")
            false
        }
    }

    fun refresh() {
        if (closed) return
        val session = owned ?: return
        val remote = client ?: return
        if (manager?.currentCastSession !== session || !session.isConnected) return
        val previous = mutableState.value
        if (previous.phase == ExtrasCastPhase.LOADING && SystemClock.elapsedRealtime() >= deadline) {
            failOwned("TV did not confirm playback. Check source compatibility.")
            return
        }
        if (previous.phase != ExtrasCastPhase.ACTIVE &&
            !(previous.phase == ExtrasCastPhase.LOADING && loadAcknowledged)) return
        val info = remote.mediaInfo
        if (!lease.controls(session.sessionId, info?.contentId)) {
            if (previous.phase == ExtrasCastPhase.ACTIVE) {
                releaseOwnership(ExtrasCastPhase.DISCONNECTED, "Receiver changed media. Local playback remains paused.")
            }
            return
        }
        val status = remote.mediaStatus ?: return
        if (status.playerState == MediaStatus.PLAYER_STATE_IDLE) {
            if (previous.phase == ExtrasCastPhase.LOADING) return
            failOwned("TV playback ended or stopped.")
            return
        }
        if (status.playerState !in setOf(MediaStatus.PLAYER_STATE_PLAYING,
                MediaStatus.PLAYER_STATE_PAUSED, MediaStatus.PLAYER_STATE_BUFFERING)) return
        val tracks = info?.mediaTracks.orEmpty().filter { it.type == MediaTrack.TYPE_TEXT }.take(30)
        mutableState.value = previous.copy(
            phase = ExtrasCastPhase.ACTIVE,
            message = if (status.playerState == MediaStatus.PLAYER_STATE_BUFFERING) "TV buffering" else "Casting to TV",
            positionMs = remote.approximateStreamPosition.coerceAtLeast(0L),
            durationMs = remote.streamDuration.coerceAtLeast(0L),
            playing = status.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            captions = tracks.map { ExtrasCastCaption(it.id, it.name.orEmpty().filterNot(Char::isISOControl).take(120).ifBlank { "Subtitles" }) },
            activeCaptionIds = status.activeTrackIds?.toSet().orEmpty().intersect(tracks.map { it.id }.toSet())
        )
    }

    private fun controlledClient(): RemoteMediaClient? {
        val session = owned ?: return null
        val remote = client ?: return null
        return remote.takeIf { !closed && mutableState.value.phase == ExtrasCastPhase.ACTIVE &&
            session.isConnected && manager?.currentCastSession === session &&
            lease.controls(session.sessionId, remote.mediaInfo?.contentId) }
    }

    private fun command(send: (RemoteMediaClient) -> com.google.android.gms.common.api.PendingResult<RemoteMediaClient.MediaChannelResult>): Boolean {
        val remote = controlledClient() ?: return false
        return try {
            send(remote).setResultCallback { result ->
                if (!closed && controlledClient() === remote && !result.status.isSuccess) {
                    mutableState.value = mutableState.value.copy(message = "TV did not accept the command.")
                }
            }
            true
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(message = "TV command failed.")
            false
        }
    }

    fun play() = command { it.play() }
    fun pause() = command { it.pause() }
    fun seekBy(deltaMs: Long): Boolean {
        val current = mutableState.value
        if (current.live || current.durationMs <= 0L || current.positionMs == null) return false
        val target = (current.positionMs + deltaMs.coerceIn(-60_000L, 60_000L)).coerceIn(0L, current.durationMs)
        return command { it.seek(target) }
    }

    fun changeVolume(delta: Double): Boolean {
        if (controlledClient() == null || !delta.isFinite()) return false
        val session = owned ?: return false
        return runCatching { session.setVolume((session.volume + delta).coerceIn(0.0, 1.0)); true }.getOrDefault(false)
    }

    fun selectSubtitle(trackId: Long?): Boolean {
        val remote = controlledClient() ?: return false
        val tracks = remote.mediaInfo?.mediaTracks.orEmpty()
        val textIds = tracks.filter { it.type == MediaTrack.TYPE_TEXT }.map { it.id }.toSet()
        if (trackId != null && trackId !in textIds) return false
        // setActiveMediaTracks replaces the entire selection; preserve audio/video IDs.
        val others = (remote.mediaStatus?.activeTrackIds ?: longArrayOf()).filter { it !in textIds }
        val next = (others + listOfNotNull(trackId)).distinct().toLongArray()
        return command { it.setActiveMediaTracks(next) }
    }

    fun disconnect() {
        endOwnedIfSafe()
        releaseOwnership(ExtrasCastPhase.DISCONNECTED, "TV disconnected. Local playback remains paused.")
    }

    private fun endOwnedIfSafe() {
        val session = owned ?: return
        val sessions = manager ?: return
        if (sessions.currentCastSession === session &&
            lease.mayEnd(session.sessionId, client?.mediaInfo?.contentId)) {
            runCatching { sessions.endCurrentSession(true) }
        }
    }

    private fun failOwned(message: String) {
        endOwnedIfSafe()
        releaseOwnership(ExtrasCastPhase.FAILED, message)
    }

    private fun fail(message: String) = releaseOwnership(ExtrasCastPhase.FAILED, message)

    private fun releaseOwnership(phase: ExtrasCastPhase, message: String) {
        client?.let { runCatching { it.unregisterCallback(mediaCallback) } }
        client = null
        owned = null
        pending = null
        armed = false
        lease.revoke()
        loadAcknowledged = false
        deadline = 0L
        mutableState.value = mutableState.value.copy(phase = phase, message = message, playing = false)
    }

    fun close() {
        if (closed) return
        disconnect()
        closed = true
        manager?.let { runCatching { it.removeSessionManagerListener(listener, CastSession::class.java) } }
        manager = null
    }
}
