package com.vaydelux.bousygram.presentation.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.media.PlaybackCacheStopReason
import com.vaydelux.bousygram.core.media.PlaybackRetryDecision
import com.vaydelux.bousygram.core.media.PlaybackSession
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.media.StreamingSessionManager
import com.vaydelux.bousygram.core.media.TelegramProxyPlayerFactory
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.core.observability.PlaybackErrorMapper
import com.vaydelux.bousygram.core.observability.PlaybackLogContext
import com.vaydelux.bousygram.core.observability.StructuredPlaybackLogger
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.PlaybackMode
import com.vaydelux.bousygram.data.store.StreamMetrics
import com.vaydelux.bousygram.core.tdlib.fastlink.FastLinkManager
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.PlaybackCacheState
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SEEK_INCREMENT_MS = 10_000L
private const val BUFFERING_STALL_MS = 22_000L
private const val MAX_AUTO_RETRIES = 5
private const val CACHE_AHEAD_REFRESH_MS = 10_000L

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adaptive = rememberAdaptiveMetrics()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val compactHud = adaptive.isCompact
    val queue = remember(request) {
        request.queue.ifEmpty {
            listOf(
                PlaybackQueueItem(
                    request.url,
                    request.chatId,
                    request.messageId,
                    request.title,
                    request.fileId,
                    request.fileName,
                    request.mimeType,
                    request.durationSeconds,
                    request.fileSizeBytes
                )
            )
        }
    }
    val initialIndex = remember(request) {
        queueIndexForFileId(queue, request.fileId).takeIf { it >= 0 } ?: 0
    }
    if (queueIndexForFileId(queue, request.fileId) < 0) {
        LaunchedEffect(Unit) { onExit() }
        return
    }

    var queueIndex by remember(request) { mutableIntStateOf(initialIndex) }
    var hudVisible by remember { mutableStateOf(true) }
    var tracksPanelVisible by remember { mutableStateOf(false) }
    var title by remember(request, queueIndex) {
        mutableStateOf(queue.getOrElse(queueIndex) { queue.first() }.title)
    }
    var fileName by remember(request, queueIndex) {
        mutableStateOf(queue.getOrElse(queueIndex) { queue.first() }.fileName)
    }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(EnterprisePreferences.preferredSpeed()) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var isOpening by remember { mutableStateOf(true) }
    var isReconnecting by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var retryCount by remember { mutableIntStateOf(0) }
    var rebufferCount by remember { mutableIntStateOf(0) }
    var lastSavedPosition by remember { mutableLongStateOf(0L) }
    var lastGoodPosition by remember { mutableLongStateOf(0L) }
    var lastAheadPrefetchPosition by remember { mutableLongStateOf(0L) }
    var fileSizeBytes by remember { mutableLongStateOf(0L) }
    var playerStartedAtMs by remember { mutableLongStateOf(0L) }
    var firstFrameMs by remember { mutableLongStateOf(0L) }
    var decoderName by remember { mutableStateOf("Unknown") }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var cacheState by remember { mutableStateOf(PlaybackCacheState.Preparing) }
    var playbackSession by remember { mutableStateOf<PlaybackSession?>(null) }
    var activeFastLinkUrl by remember(request, queueIndex) {
        mutableStateOf(request.url.takeIf { request.startsWithFastLink && queueIndex == initialIndex })
    }
    var fastLinkFallbackAttempted by remember(request, queueIndex) { mutableStateOf(false) }

    fun recordMetrics() {
        PlaybackDiagnostics.recordPlayerMetrics(
            StreamMetrics(
                firstFrameMs = firstFrameMs,
                retryCount = retryCount,
                rebufferCount = rebufferCount,
                decoderName = decoderName,
                droppedFrames = droppedFrames,
                bufferMode = EnterprisePreferences.bufferMode()
            )
        )
    }

    fun applyTrustedDurationMs(candidateMs: Long) {
        if (candidateMs > duration) duration = candidateMs
    }

    fun applyPlayerDurationMs(candidateMs: Long) {
        val item = queue.getOrNull(queueIndex) ?: return
        val trusted = MediaDurationResolver.playerDurationIfTrusted(
            fileId = item.fileId,
            durationMs = candidateMs,
            totalSize = fileSizeBytes,
            hintSeconds = item.durationSeconds
        )
        if (trusted > duration) duration = trusted
    }

    val player = remember {
        TelegramProxyPlayerFactory.build(
            context = context,
            loadErrorHandlingPolicy = TelegramStreamLoadErrorPolicy(),
            preferredSpeed = speed
        )
    }
    val mediaSession = remember(player) { MediaSession.Builder(context, player).build() }
    val audioFocusController = remember(context) { PlayerAudioFocusController(context) }
    val streamingSessionManager = remember(context) { StreamingSessionManager(context) }

    fun stopCurrentCache(reason: PlaybackCacheStopReason) {
        FastLinkManager.revokeFastLink(activeFastLinkUrl)
        val session = playbackSession
        if (session != null) {
            PlaybackSessionController.stop(session, reason)
            return
        }
        queue.getOrNull(queueIndex)?.fileId?.let { fileId -> TelegramPreCacheEngine.stopCaching(fileId) }
    }

    fun exitPlayback() {
        stopCurrentCache(PlaybackCacheStopReason.Back)
        onExit()
    }

    fun goToTrack(newIndex: Int) {
        if (newIndex !in queue.indices || newIndex == queueIndex) return
        val oldItem = queue[queueIndex]
        if (oldItem.fileId > 0 && player.currentPosition > 0) {
            PlaybackProgressTracker.savePosition(oldItem.fileId, player.currentPosition.toInt(), duration)
        }
        stopCurrentCache(PlaybackCacheStopReason.TrackSwitch)
        playbackSession = null
        reloadNonce = 0
        retryCount = 0
        fastLinkFallbackAttempted = false
        activeFastLinkUrl = null
        isReconnecting = false
        player.stop()
        player.clearMediaItems()
        queueIndex = newIndex
    }

    DisposableEffect(player) {
        var previousPlaybackState = player.playbackState
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) audioFocusController.request(player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING &&
                    previousPlaybackState != Player.STATE_BUFFERING &&
                    firstFrameMs > 0L
                ) {
                    rebufferCount++
                    recordMetrics()
                }
                previousPlaybackState = playbackState
                isBuffering = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_BUFFERING -> Unit
                    Player.STATE_IDLE -> Unit
                    Player.STATE_READY -> {
                        applyPlayerDurationMs(player.duration)
                        isPlaying = player.isPlaying
                        isOpening = false
                        isReconnecting = false
                        playbackError = null
                    }
                    Player.STATE_ENDED -> {
                        val current = queue[queueIndex]
                        PlaybackProgressTracker.clearPosition(current.fileId)
                        if (EnterprisePreferences.autoPlayNext() && queueIndex < queue.lastIndex) {
                            goToTrack(queueIndex + 1)
                        } else {
                            exitPlayback()
                        }
                    }
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                mediaMetadata.durationMs?.takeIf { it > 0L }?.let { applyPlayerDurationMs(it) }
            }

            override fun onRenderedFirstFrame() {
                if (firstFrameMs <= 0L && playerStartedAtMs > 0L) {
                    firstFrameMs = System.currentTimeMillis() - playerStartedAtMs
                    recordMetrics()
                    val current = queue.getOrNull(queueIndex)
                    if (current != null && fileSizeBytes > 0L) {
                        TelegramPreCacheEngine.cacheFullVideoOnWifi(context, current.fileId, fileSizeBytes)
                    }
                    playbackSession?.let { session ->
                        PlaybackSessionController.recordFirstFrame(session, firstFrameMs)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val resolution = PlaybackErrorMapper.fromPlaybackException(error, retryCount)
                val message = "${resolution.title}: ${resolution.message}"
                EnterprisePreferences.setLastError(message)
                PlaybackDiagnostics.recordStreamError(message)
                val item = queue.getOrNull(queueIndex)
                val retryPosition = maxOf(player.currentPosition, lastGoodPosition).coerceAtLeast(0L)
                lastGoodPosition = retryPosition
                hudVisible = true
                val session = playbackSession
                val retryDecision = if (session != null) {
                    PlaybackSessionController.recordError(session, error, retryCount)
                } else {
                    PlaybackRetryDecision.RetrySameQuality
                }
                if (item != null) {
                    StructuredPlaybackLogger.error(
                        event = "player_error",
                        error = error,
                        context = PlaybackLogContext(
                            fileId = item.fileId,
                            offset = retryPosition,
                            decoderName = decoderName,
                            cacheState = cacheState.name,
                            activeChunks = com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler.activeChunkCount(item.fileId)
                        )
                    )
                }
                if (item != null &&
                    EnterprisePreferences.playbackMode() == PlaybackMode.Auto &&
                    activeFastLinkUrl == null &&
                    !fastLinkFallbackAttempted &&
                    isRecoverablePlaybackError(error)
                ) {
                    fastLinkFallbackAttempted = true
                    retryCount++
                    recordMetrics()
                    isReconnecting = true
                    isBuffering = true
                    playbackError = null
                    hudVisible = true
                    stopCurrentCache(PlaybackCacheStopReason.ErrorRecovery)
                    scope.launch(Dispatchers.IO) {
                        val result = FastLinkManager.requestFastLink(item.chatId, item.messageId)
                        withContext(Dispatchers.Main) {
                            result.fold(
                                onSuccess = { url ->
                                    EnterprisePreferences.setLastError("Using fast-link fallback")
                                    activeFastLinkUrl = url
                                },
                                onFailure = { fallbackError ->
                                    playbackError = "Fast-link fallback failed: ${fallbackError.message.orEmpty()}"
                                    isReconnecting = false
                                    isBuffering = false
                                }
                            )
                        }
                    }
                    return
                }
                if (item != null && resolution.recoverable && isRecoverablePlaybackError(error) && retryCount < MAX_AUTO_RETRIES) {
                    retryCount++
                    recordMetrics()
                    isReconnecting = true
                    isBuffering = true
                    playbackError = null
                    if (retryDecision == PlaybackRetryDecision.CacheFirstRetry && session != null) {
                        PlaybackSessionController.cacheAhead(
                            session = session,
                            totalSize = fileSizeBytes,
                            positionMs = retryPosition,
                            durationMs = duration,
                            startup = true
                        )
                    } else {
                        LocalStreamingServer.prefetchAtPosition(item.fileId, fileSizeBytes, retryPosition, duration)
                    }
                    scope.launch {
                        delay(if (retryDecision == PlaybackRetryDecision.CacheFirstRetry) 2_500L else autoRetryDelayMs(retryCount))
                        if (queue.getOrNull(queueIndex)?.fileId == item.fileId && playbackError == null) {
                            reloadNonce++
                        }
                    }
                } else {
                    isReconnecting = false
                    playbackError = message
                }
            }
        }
        val analyticsListener = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                videoDecoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                decoderName = videoDecoderName
                recordMetrics()
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrameCount: Int,
                elapsedMs: Long
            ) {
                droppedFrames += droppedFrameCount
                recordMetrics()
            }
        }
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        onDispose {
            val current = queue.getOrElse(queueIndex) { queue.first() }
            if (current.fileId > 0 && player.currentPosition > 0) {
                PlaybackProgressTracker.savePosition(
                    current.fileId,
                    player.currentPosition.toInt(),
                    duration
                )
            }
            player.removeListener(listener)
            player.removeAnalyticsListener(analyticsListener)
            stopCurrentCache(PlaybackCacheStopReason.Dispose)
            audioFocusController.abandon()
            mediaSession.release()
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner, player, streamingSessionManager) {
        streamingSessionManager.acquireLocks()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> streamingSessionManager.acquireLocks()
                Lifecycle.Event.ON_PAUSE -> {
                    if (player.isPlaying) player.pause()
                    queue.getOrNull(queueIndex)?.fileId?.let {
                        PlaybackProgressTracker.savePosition(it, player.currentPosition.toInt(), duration)
                    }
                }
                Lifecycle.Event.ON_RESUME -> if (player.playWhenReady) player.play()
                Lifecycle.Event.ON_STOP -> {
                    stopCurrentCache(PlaybackCacheStopReason.LifecycleStop)
                    streamingSessionManager.releaseLocks()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopCurrentCache(PlaybackCacheStopReason.Dispose)
            streamingSessionManager.releaseLocks()
        }
    }

    LaunchedEffect(request, queueIndex) {
        retryCount = 0
        rebufferCount = 0
        firstFrameMs = 0L
        droppedFrames = 0
        decoderName = "Unknown"
        lastGoodPosition = 0L
        lastAheadPrefetchPosition = 0L
        playbackSession = null
        cacheState = PlaybackCacheState.Preparing
        isReconnecting = false
        playbackError = null
        recordMetrics()
    }

    LaunchedEffect(request, queueIndex, reloadNonce, activeFastLinkUrl) {
        val item = queue[queueIndex]
        title = displayTitle(item.title)
        fileName = item.fileName
        playbackError = null
        isOpening = true
        isReconnecting = retryCount > 0
        isBuffering = true
        duration = 0L
        MediaDurationResolver.cachedDurationMs(item.fileId)
            .takeIf { it > 0L }
            ?.let { duration = it }
            ?: item.durationSeconds.takeIf { it > 0 }?.let { duration = it * 1000L }
        lastSavedPosition = 0L
        val fastUrl = activeFastLinkUrl
        val streamUrl = if (fastUrl != null) {
            fileSizeBytes = item.fileSizeBytes
            cacheState = PlaybackCacheState.Streaming
            fastUrl
        } else {
            val preparation = withContext(Dispatchers.IO) {
                LocalStreamingServer.prepareStream(
                    fileId = item.fileId,
                    declaredSize = item.fileSizeBytes,
                    title = item.title,
                    mimeType = item.mimeType,
                    fileName = item.fileName
                )
            }
            if (preparation == null) {
                val message = "Stream not ready. The file may still be downloading in Telegram."
                playbackError = message
                PlaybackDiagnostics.recordStreamError(message)
                isOpening = false
                isReconnecting = false
                isBuffering = false
                hudVisible = true
                return@LaunchedEffect
            }
            fileSizeBytes = preparation.resolvedSize
            cacheState = TelegramPreCacheEngine.cacheState(item.fileId)
            preparation.url
        }
        val videoMime = VideoFormatSupport.exoMimeType(item.mimeType, item.fileName, item.title)
        launch {
            val resolvedDuration = withContext(Dispatchers.IO) {
                MediaDurationResolver.resolveDurationEstimate(
                    item.fileId,
                    fileSizeBytes,
                    streamUrl,
                    item.durationSeconds
                )
            }
            if (resolvedDuration.trusted) {
                applyTrustedDurationMs(resolvedDuration.durationMs)
            }
        }
        val resumePosition = lastGoodPosition
            .takeIf { it > 0L }
            ?: PlaybackProgressTracker.getPosition(item.fileId, duration).toLong()
        val session = PlaybackSessionController.start(item.fileId, resumePosition)
        playbackSession = session
        player.stop()
        player.clearMediaItems()

        val subtitleConfigs = request.subtitleUrl
            ?.takeIf { queueIndex == initialIndex && EnterprisePreferences.subtitlesDefault() }
            ?.let { subUrl ->
                val mime = when {
                    subUrl.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                listOf(
                    ExoMediaItem.SubtitleConfiguration.Builder(subUrl.toUri())
                        .setMimeType(mime)
                        .setLanguage("und")
                        .build()
                )
            }
        val mediaItemBuilder = ExoMediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(videoMime)
        if (subtitleConfigs != null) {
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }
        val mediaItem = mediaItemBuilder.build()
        player.setMediaItem(mediaItem)
        firstFrameMs = 0L
        playerStartedAtMs = System.currentTimeMillis()
        player.prepare()
        if (resumePosition > 0L) player.seekTo(resumePosition)
        player.setPlaybackSpeed(speed)
        player.play()
        isPlaying = player.isPlaying
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(queueIndex, reloadNonce, isBuffering) {
        if (!isBuffering) return@LaunchedEffect
        delay(BUFFERING_STALL_MS)
        if (!isBuffering) return@LaunchedEffect
        if (player.playbackState == Player.STATE_BUFFERING &&
            !isOpening &&
            !isReconnecting &&
            duration <= 0L &&
            player.currentPosition <= 0L
        ) {
            playbackError = "Playback stalled. Try Retry or Skip to the next item."
            hudVisible = true
        }
    }

    LaunchedEffect(player, queueIndex) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            applyPlayerDurationMs(player.duration)
            isPlaying = player.isPlaying
            val current = queue[queueIndex]
            cacheState = TelegramPreCacheEngine.cacheState(current.fileId)
            if (position > lastGoodPosition && player.playbackState == Player.STATE_READY) {
                lastGoodPosition = position
            }
            playbackSession?.let { session ->
                PlaybackSessionController.recordProgress(session, position, fileSizeBytes, duration)
            }
            if (position > 0L &&
                activeFastLinkUrl == null &&
                duration > 0L &&
                fileSizeBytes > 0L &&
                position - lastAheadPrefetchPosition >= CACHE_AHEAD_REFRESH_MS
            ) {
                playbackSession?.let { session ->
                    PlaybackSessionController.cacheAhead(session, fileSizeBytes, position, duration)
                }
                lastAheadPrefetchPosition = position
            }
            if (current.fileId > 0 && position > 0 && position - lastSavedPosition >= 5_000L) {
                PlaybackProgressTracker.savePosition(current.fileId, position.toInt(), duration)
                lastSavedPosition = position
            }
            delay(500)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { hudVisible = !hudVisible }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (tracksPanelVisible) {
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.key == Key.Back
                    ) {
                        tracksPanelVisible = false
                        return@onKeyEvent true
                    }
                    return@onKeyEvent false
                }
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        val newPos = (player.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        LocalStreamingServer.prefetchAtPosition(
                            queue[queueIndex].fileId,
                            fileSizeBytes,
                            newPos,
                            duration
                        )
                        true
                    }
                    Key.DirectionRight -> {
                        val max = duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        val newPos = (player.currentPosition + SEEK_INCREMENT_MS).coerceAtMost(max)
                        player.seekTo(newPos)
                        LocalStreamingServer.prefetchAtPosition(
                            queue[queueIndex].fileId,
                            fileSizeBytes,
                            newPos,
                            duration
                        )
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (player.isPlaying) player.pause() else player.play()
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        hudVisible = !hudVisible
                        true
                    }
                    Key.Back -> {
                        exitPlayback()
                        true
                    }
                    else -> when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            if (queueIndex > 0) goToTrack(queueIndex - 1) else true
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            if (queueIndex < queue.lastIndex) goToTrack(queueIndex + 1) else true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (player.isPlaying) player.pause() else player.play()
                            true
                        }
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_INFO -> {
                            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                                tracksPanelVisible = true
                                hudVisible = true
                            } else {
                                speed = nextSpeed(speed)
                                player.setPlaybackSpeed(speed)
                                EnterprisePreferences.setPreferredSpeed(speed)
                                hudVisible = true
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    useController = false
                    keepScreenOn = true
                    subtitleView?.visibility = View.GONE
                    this.player = player
                }
            },
            update = { it.player = player }
        )
        if (hudVisible) {
            PlayerHud(
                state = PlayerHudState(
                    title = title,
                    fileName = fileName,
                    positionMs = position,
                    durationMs = duration,
                    durationTrusted = duration > 0L,
                    speed = speed,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isOpening = isOpening,
                    isReconnecting = isReconnecting,
                    retryCount = retryCount,
                    error = playbackError,
                    trackLabel = "${queueIndex + 1} / ${queue.size}",
                    cacheStateLabel = cacheState.playerLabel()
                ),
                compact = compactHud,
                onRetry = {
                    retryCount = 0
                    isReconnecting = true
                    lastGoodPosition = maxOf(lastGoodPosition, player.currentPosition).coerceAtLeast(0L)
                    playbackError = null
                    reloadNonce++
                },
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeekBy = { delta ->
                    val max = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val newPos = (player.currentPosition + delta).coerceIn(0L, max)
                    player.seekTo(newPos)
                    LocalStreamingServer.prefetchAtPosition(queue[queueIndex].fileId, fileSizeBytes, newPos, duration)
                },
                onSkipPrevious = {
                    if (queueIndex > 0) goToTrack(queueIndex - 1)
                },
                onSkipNext = {
                    if (queueIndex < queue.lastIndex) goToTrack(queueIndex + 1) else exitPlayback()
                },
                onTracks = {
                    tracksPanelVisible = true
                    hudVisible = true
                },
                onSpeed = {
                    speed = nextSpeed(speed)
                    player.setPlaybackSpeed(speed)
                    EnterprisePreferences.setPreferredSpeed(speed)
                    hudVisible = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        if (tracksPanelVisible) {
            TrackPickerOverlay(
                player = player,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackPickerOverlay(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    val tracks = player.currentTracks
    Surface(
        modifier = modifier
            .padding(32.dp)
            .width(320.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tracks (Menu to close)", style = MaterialTheme.typography.titleMedium)
            tracks.groups.forEach { group ->
                val typeLabel = when (group.type) {
                    C.TRACK_TYPE_TEXT -> "Subtitles"
                    C.TRACK_TYPE_AUDIO -> "Audio"
                    else -> return@forEach
                }
                Text(typeLabel, style = MaterialTheme.typography.labelLarge)
                group.mediaTrackGroup.let { trackGroup ->
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getFormat(i)
                        val label = format.label ?: format.language ?: "Track $i"
                        Text(
                            text = "  $label",
                            modifier = Modifier
                                .focusable()
                                .padding(vertical = 4.dp)
                                .onKeyEvent { event ->
                                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                        (event.key == Key.Enter ||
                                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
                                    ) {
                                        selectTrack(player, tracks, group, trackGroup, i)
                                        true
                                    } else false
                                }
                        )
                    }
                }
            }
            Text(
                "Disable subtitles",
                modifier = Modifier
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            (event.key == Key.Enter ||
                                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
                        ) {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            true
                        } else false
                    }
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun selectTrack(
    player: ExoPlayer,
    tracks: Tracks,
    group: Tracks.Group,
    trackGroup: TrackGroup,
    trackIndex: Int
) {
    val override = TrackSelectionOverride(trackGroup, listOf(trackIndex))
    val builder = player.trackSelectionParameters.buildUpon()
    if (group.type == C.TRACK_TYPE_TEXT) {
        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    }
    builder.setOverrideForType(override)
    player.trackSelectionParameters = builder.build()
}

@Composable
private fun PlayerHud(
    state: PlayerHudState,
    compact: Boolean,
    onRetry: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onTracks: () -> Unit,
    onSpeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val edgePadding = if (compact) 12.dp else 32.dp
    val innerPadding = if (compact) 14.dp else 24.dp
    val statusText = when {
        state.error != null -> "Playback error"
        state.isReconnecting -> "Reconnecting"
        state.isOpening -> "Opening"
        state.isBuffering -> "Buffering"
        state.isPlaying -> "Playing"
        else -> "Paused"
    }
    val statusLine = listOfNotNull(
        statusText,
        state.retryCount.takeIf { it > 0 }?.let { "retry $it/$MAX_AUTO_RETRIES" },
        state.trackLabel,
        state.cacheStateLabel
    ).joinToString(" · ")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(edgePadding),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 6.dp
    ) {
        Column(Modifier.padding(innerPadding)) {
            Text(
                text = state.title,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!state.fileName.isNullOrBlank() && state.fileName != state.title) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            if (compact) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isBuffering && state.error == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        statusLine,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${formatMs(state.positionMs)} / ${durationText(state)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${speedLabel(state.speed)}x", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isBuffering && state.error == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        statusLine,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("${formatMs(state.positionMs)} / ${durationText(state)}")
                    Spacer(Modifier.width(24.dp))
                    Text("${speedLabel(state.speed)}x")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                state.error
                    ?: if (state.isReconnecting) "Reconnecting to Telegram stream automatically"
                    else "D-pad left/right seek · center play/pause · CH± queue · Menu tracks · Info speed · up/down HUD",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = {
                    if (state.durationTrusted && state.durationMs > 0) {
                        (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Button(onClick = { onSeekBy(-SEEK_INCREMENT_MS) }) { Text("-10") }
                Button(onClick = onPlayPause) { Text(if (state.isPlaying) "Pause" else "Play") }
                Button(onClick = { onSeekBy(SEEK_INCREMENT_MS) }) { Text("+10") }
                Button(onClick = onSkipPrevious) { Text("Prev") }
                Button(onClick = onSkipNext) { Text("Next") }
                Button(onClick = onTracks) { Text("Tracks") }
                Button(onClick = onSpeed) { Text("${speedLabel(state.speed)}x") }
            }
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                    Button(onClick = onSkipNext) {
                        Text("Skip")
                    }
                }
            }
        }
    }
}

private fun durationText(state: PlayerHudState): String =
    if (state.durationTrusted && state.durationMs > 0L) formatMs(state.durationMs) else "Unknown"

private fun PlaybackCacheState.playerLabel(): String =
    when (this) {
        PlaybackCacheState.Preparing -> "Preparing cache"
        PlaybackCacheState.Priming -> "Priming stream"
        PlaybackCacheState.Streaming -> "Streaming"
        PlaybackCacheState.Reconnecting -> "Reconnecting cache"
        PlaybackCacheState.CachingAhead -> "Caching ahead"
        PlaybackCacheState.Cached -> "Cached"
        PlaybackCacheState.Failed -> "Cache waiting"
    }

private fun displayTitle(raw: String): String {
    val line = raw.lineSequence().firstOrNull().orEmpty().trim()
    return if (line.length <= 96) line else line.take(93) + "…"
}

private fun playbackErrorMessage(error: PlaybackException): String {
    val detail = error.message?.takeIf { it.isNotBlank() }
    val isSourceDetail = detail.equals("Source error", ignoreCase = true)
    val normalizedDetail = when {
        isSourceDetail ->
            "Telegram is still caching this range. Bousygram will reconnect automatically."
        detail != null -> detail
        else -> null
    }
    val summary = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
            "Telegram stream read failed"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "Stream unavailable (server rejected the request)"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Network error while reading the stream"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "Stream file not found on device"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            if (isSourceDetail) "Telegram stream data is not ready yet" else "Unsupported or corrupted video format"
        else -> "Playback failed"
    }
    return if (normalizedDetail != null) "$summary: $normalizedDetail" else summary
}

private fun isRecoverablePlaybackError(error: PlaybackException): Boolean =
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
        else -> false
    }

private fun autoRetryDelayMs(retryCount: Int): Long = when (retryCount) {
    1 -> 500L
    2 -> 1_500L
    3 -> 3_000L
    4 -> 5_000L
    else -> 8_000L
}

@OptIn(UnstableApi::class)
private class TelegramStreamLoadErrorPolicy : DefaultLoadErrorHandlingPolicy(MAX_AUTO_RETRIES + 1) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        return when (loadErrorInfo.errorCount) {
            1 -> 500L
            2 -> 1_500L
            3 -> 3_000L
            4 -> 5_000L
            5, 6 -> 8_000L
            else -> C.TIME_UNSET
        }
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = MAX_AUTO_RETRIES + 1
}

private fun nextSpeed(current: Float): Float {
    val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val index = speeds.indexOfFirst { it == current }.takeIf { it >= 0 } ?: 1
    return speeds[(index + 1) % speeds.size]
}

private fun speedLabel(value: Float): String =
    if (value == 1.0f) "1.0" else "%.2f".format(value).trimEnd('0').trimEnd('.')

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private class PlayerAudioFocusController(context: Context) {
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var currentPlayer: ExoPlayer? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val player = currentPlayer ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> player.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> player.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.volume = 0.35f
            AudioManager.AUDIOFOCUS_GAIN -> player.volume = 1f
        }
    }

    fun request(player: ExoPlayer) {
        val audioManager = audioManager ?: return
        currentPlayer = player
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    fun abandon() {
        val audioManager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(listener)
        }
        focusRequest = null
        currentPlayer = null
    }
}
