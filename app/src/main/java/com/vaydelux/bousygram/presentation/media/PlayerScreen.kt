package com.vaydelux.bousygram.presentation.media

import android.app.Activity
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import com.vaydelux.bousygram.app.MainActivity
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.vaydelux.bousygram.core.media.SeekThumbnailManager
import com.vaydelux.bousygram.data.store.SessionHealthRecord
import com.vaydelux.bousygram.data.store.StreamHealthTelemetry
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.activity.compose.BackHandler
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import com.vaydelux.bousygram.core.media.EnginePlaybackState
import com.vaydelux.bousygram.core.media.ExoPlayerEngine
import com.vaydelux.bousygram.core.media.MediaDurationResolver
import com.vaydelux.bousygram.core.media.MediaTrackInfo
import com.vaydelux.bousygram.core.media.MpvPlayerEngine
import com.vaydelux.bousygram.core.media.PlaybackCacheStopReason
import com.vaydelux.bousygram.core.media.PlaybackSession
import com.vaydelux.bousygram.core.media.PlaybackSessionController
import com.vaydelux.bousygram.core.media.PlayerEngine
import com.vaydelux.bousygram.core.media.PlayerEngineFactory
import com.vaydelux.bousygram.core.media.StreamingSessionManager
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.core.network.AppNetworkState
import com.vaydelux.bousygram.core.network.NetworkConnectivityObserver
import com.vaydelux.bousygram.core.observability.PlaybackErrorMapper
import com.vaydelux.bousygram.core.observability.PlaybackLogContext
import com.vaydelux.bousygram.core.observability.StructuredPlaybackLogger
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.PlaybackDiagnostics
import com.vaydelux.bousygram.data.store.StreamMetrics
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.core.tdlib.server.StreamAccessRegistry
import com.vaydelux.bousygram.core.tdlib.server.PlaybackCacheState
import com.vaydelux.bousygram.core.tdlib.server.TelegramPreCacheEngine
import com.vaydelux.bousygram.core.tdlib.utils.PlaybackProgressTracker
import com.vaydelux.bousygram.presentation.adaptive.rememberAdaptiveMetrics
import com.vaydelux.bousygram.presentation.components.BousygramButton
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
    modifier: Modifier = Modifier,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {}
) {
    val context = LocalContext.current
    val adaptive = rememberAdaptiveMetrics()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val compactHud = adaptive.isCompact
    val queue = remember(request) {
        val seeded = request.queue.ifEmpty { listOf(queueItemFromRequest(request)) }
        if (queueIndexForRequest(seeded, request.chatId, request.messageId, request.fileId) >= 0) {
            seeded
        } else {
            listOf(queueItemFromRequest(request)) + seeded
        }
    }
    val initialIndex = remember(request, queue) {
        queueIndexForRequest(queue, request.chatId, request.messageId, request.fileId).coerceAtLeast(0)
    }

    var queueIndex by remember(request) { mutableIntStateOf(initialIndex) }
    var hudVisible by remember { mutableStateOf(true) }
    var hudInteractionTick by remember { mutableIntStateOf(0) }
    var tracksPanelVisible by remember { mutableStateOf(false) }
    var episodesDrawerVisible by remember { mutableStateOf(false) }
    var showBrightnessHud by remember { mutableStateOf(false) }
    var showVolumeHud by remember { mutableStateOf(false) }
    val currentActivity = context as? Activity
    var gestureBrightness by remember {
        mutableFloatStateOf(currentActivity?.window?.attributes?.screenBrightness?.takeIf { it in 0.01f..1f } ?: 0.5f)
    }
    val currentAudioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var gestureVolume by remember {
        mutableIntStateOf(currentAudioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 5)
    }
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
    var activeLocalStreamUrl by remember { mutableStateOf<String?>(null) }
    var pendingResumeMs by remember { mutableLongStateOf(0L) }
    val playerSessionStartMs = remember { System.currentTimeMillis() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var userSelectedResizeMode by remember { mutableStateOf<Int?>(null) }

    val effectiveResizeMode = when {
        isInPipMode -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        isLandscape && userSelectedResizeMode == null -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> userSelectedResizeMode ?: AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    var videoBoundsOnScreen by remember { mutableStateOf<Rect?>(null) }

    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackSide by remember { mutableIntStateOf(0) }
    var toastFeedbackText by remember { mutableStateOf<String?>(null) }
    var showResumeRestartOption by remember { mutableStateOf(false) }

    fun formatResizeMode(mode: Int): String = when (mode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Fill (Zoom)"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch (Fill)"
        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit (Letterbox)"
        else -> "Fit"
    }

    LaunchedEffect(seekFeedbackText) {
        if (seekFeedbackText != null) {
            kotlinx.coroutines.delay(850)
            seekFeedbackText = null
        }
    }

    LaunchedEffect(toastFeedbackText) {
        if (toastFeedbackText != null) {
            kotlinx.coroutines.delay(1200)
            toastFeedbackText = null
        }
    }

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

    var playerEngine by remember {
        mutableStateOf(
            PlayerEngineFactory.create(
                context = context,
                preferredSpeed = speed
            )
        )
    }
    val mediaSession = remember(playerEngine) {
        val engine = playerEngine
        if (engine is ExoPlayerEngine) {
            MediaSession.Builder(context, engine.player).build()
        } else {
            null
        }
    }
    val audioFocusController = remember(context) { PlayerAudioFocusController(context) }
    val streamingSessionManager = remember(context) { StreamingSessionManager(context) }

    val videoDimensions by playerEngine.videoDimensions.collectAsState()
    val currentItem = queue.getOrNull(queueIndex)
    val videoAspectRatio = remember(videoDimensions, currentItem) {
        val firstAlt = currentItem?.alternativeVideos?.firstOrNull()
        when {
            videoDimensions.first > 0 && videoDimensions.second > 0 ->
                Rational(videoDimensions.first, videoDimensions.second)
            firstAlt != null && firstAlt.width > 0 && firstAlt.height > 0 ->
                Rational(firstAlt.width, firstAlt.height)
            else -> Rational(16, 9)
        }
    }

    LaunchedEffect(videoAspectRatio, videoBoundsOnScreen) {
        val activity = context as? MainActivity
        activity?.updatePipAutoEnter(
            enabled = true,
            aspectRatio = videoAspectRatio,
            sourceRect = videoBoundsOnScreen
        )
    }

    DisposableEffect(isInPipMode) {
        val window = (context as? Activity)?.window
        if (window != null && !isInPipMode) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            val activity = context as? MainActivity
            activity?.updatePipAutoEnter(enabled = false)
            val win = (context as? Activity)?.window
            if (win != null) {
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Crash-Safe Proxy Supervision: hot-swap stream URL if Ktor proxy rebinds to a fresh ephemeral port
    LaunchedEffect(playerEngine) {
        com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager.proxyBaseUrlFlow.collect { newBaseUrl ->
            val currentUrl = activeLocalStreamUrl ?: return@collect
            val oldPrefix = currentUrl.substringBefore('?')
            val newPrefix = newBaseUrl
            if (oldPrefix != newPrefix && oldPrefix.startsWith("http://127.0.0.1:") && newPrefix.startsWith("http://127.0.0.1:")) {
                val query = currentUrl.substringAfter('?', "")
                val hotSwappedUrl = "$newPrefix?$query"
                Log.i("PlayerScreen", "Hot-swapping stream URL on proxy rebind: $hotSwappedUrl")
                val pos = playerEngine.currentPositionMs
                activeLocalStreamUrl = hotSwappedUrl
                playerEngine.prepare(hotSwappedUrl)
                if (pos > 0L) playerEngine.seekTo(pos)
                if (isPlaying) playerEngine.play()
            }
        }
    }

    // Token Rotation on Network Change: refresh stream token on Wi-Fi -> Cellular handoff
    LaunchedEffect(Unit) {
        var previousWasCellular = com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular
        com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.networkState.collect { netState ->
            val isCell = netState.isCellular
            if (!previousWasCellular && isCell) {
                val current = queue.getOrNull(queueIndex)
                if (current != null && activeLocalStreamUrl != null) {
                    val newToken = com.vaydelux.bousygram.core.tdlib.server.StreamAccessRegistry.issueFile(current.fileId)
                    val newUrl = com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager.getStreamUrl(
                        fileId = current.fileId,
                        size = fileSizeBytes,
                        title = current.title,
                        mimeType = current.mimeType,
                        fileName = current.fileName,
                        token = newToken
                    )
                    Log.i("PlayerScreen", "Refreshed stream token on Wi-Fi->Cellular transition: $newUrl")
                    activeLocalStreamUrl = newUrl
                    val pos = playerEngine.currentPositionMs
                    playerEngine.prepare(newUrl)
                    if (pos > 0L) playerEngine.seekTo(pos)
                    if (isPlaying) playerEngine.play()
                }
            }
            previousWasCellular = isCell
        }
    }

    // Seek preview snapshot generation
    LaunchedEffect(queueIndex, duration, activeLocalStreamUrl) {
        val item = queue.getOrNull(queueIndex)
        val url = activeLocalStreamUrl
        if (item != null && duration > 0L && !url.isNullOrBlank()) {
            val key = "${item.chatId}:${item.messageId}"
            SeekThumbnailManager.generateSnapshots(key, url, duration)
        }
    }

    fun stopCurrentCache(reason: PlaybackCacheStopReason) {
        val session = playbackSession
        if (session != null) {
            PlaybackSessionController.stop(session, reason)
            return
        }
        queue.getOrNull(queueIndex)?.fileId?.let { fileId -> TelegramPreCacheEngine.stopCaching(fileId) }
    }

    fun revokeLocalPlaybackCapabilities() {
        StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)
        activeLocalStreamUrl = null
        StreamAccessRegistry.revokeFromUrl(request.subtitleUrl)
    }

    fun exitPlayback() {
        revokeLocalPlaybackCapabilities()
        stopCurrentCache(PlaybackCacheStopReason.Back)
        onExit()
    }

    fun goToTrack(newIndex: Int) {
        if (newIndex !in queue.indices || newIndex == queueIndex) return
        StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)
        activeLocalStreamUrl = null
        val oldItem = queue[queueIndex]
        if (playerEngine.currentPositionMs > 0) {
            PlaybackProgressTracker.saveMessagePosition(
                oldItem.chatId,
                oldItem.messageId,
                playerEngine.currentPositionMs.toInt(),
                duration,
                oldItem.fileId,
                oldItem.title
            )
        }
        stopCurrentCache(PlaybackCacheStopReason.TrackSwitch)
        playbackSession = null
        reloadNonce = 0
        retryCount = 0
        pendingResumeMs = 0L
        isReconnecting = false
        playerEngine.stop()
        queueIndex = newIndex
    }

    LaunchedEffect(playerEngine) {
        var hadFirstFrame = false
        playerEngine.playbackState.collect { state ->
            when (state) {
                is EnginePlaybackState.Buffering -> {
                    isBuffering = true
                    if (firstFrameMs > 0L) {
                        rebufferCount++
                        recordMetrics()
                    }
                }
                is EnginePlaybackState.Ready -> {
                    isBuffering = false
                    isOpening = false
                    isReconnecting = false
                    playbackError = null
                    if (playerEngine.durationMs > 0L) {
                        applyPlayerDurationMs(playerEngine.durationMs)
                    }
                    isPlaying = playerEngine.isPlaying
                    if (isPlaying) audioFocusController.request(playerEngine)

                    if (!hadFirstFrame && playerStartedAtMs > 0L) {
                        hadFirstFrame = true
                        firstFrameMs = System.currentTimeMillis() - playerStartedAtMs
                        decoderName = playerEngine.activeDecoder
                        recordMetrics()
                        val current = queue.getOrNull(queueIndex)
                        if (current != null && fileSizeBytes > 0L) {
                            TelegramPreCacheEngine.cacheFullVideoOnWifi(context, current.fileId, fileSizeBytes)
                        }
                        playbackSession?.let { session ->
                            PlaybackSessionController.recordFirstFrame(session, firstFrameMs)
                        }
                    }

                    val resume = pendingResumeMs
                    if (resume > 1_000L) {
                        pendingResumeMs = 0L
                        playerEngine.seekTo(resume)
                        if (resume > 15_000L) {
                            showResumeRestartOption = true
                        }
                    }
                }
                is EnginePlaybackState.Ended -> {
                    isBuffering = false
                    isPlaying = false
                    val current = queue.getOrNull(queueIndex) ?: return@collect
                    // Auto-Next Guard: Ensure video reached actual conclusion before advancing
                    val reachedEnd = duration > 5_000L && playerEngine.currentPositionMs >= (duration - 5_000L)
                    if (reachedEnd) {
                        PlaybackProgressTracker.clearMessagePosition(current.chatId, current.messageId)
                        if (EnterprisePreferences.autoPlayNext() && queueIndex < queue.lastIndex) {
                            goToTrack(queueIndex + 1)
                        } else {
                            exitPlayback()
                        }
                    } else {
                        Log.w("PlayerScreen", "Premature EOF detected at ${playerEngine.currentPositionMs}ms of ${duration}ms; guarding against premature track skip")
                        isReconnecting = true
                        isBuffering = true
                        reloadNonce++
                    }
                }
                is EnginePlaybackState.Error -> {
                    isBuffering = false
                    val message = state.message
                    EnterprisePreferences.setLastError(message)
                    PlaybackDiagnostics.recordStreamError(message)
                    val item = queue.getOrNull(queueIndex)
                    val retryPosition = maxOf(playerEngine.currentPositionMs, lastGoodPosition).coerceAtLeast(0L)
                    lastGoodPosition = retryPosition
                    hudVisible = true
                    val session = playbackSession
                    if (session != null) {
                        val playbackEx = (state.cause as? PlaybackException)
                            ?: PlaybackException(state.message, state.cause, PlaybackException.ERROR_CODE_UNSPECIFIED)
                        PlaybackSessionController.recordError(session, playbackEx, retryCount)
                    }

                    // Engine Failover: If MpvPlayerEngine fails, seamlessly failover to ExoPlayerEngine at current position
                    if (playerEngine is MpvPlayerEngine && retryCount == 0) {
                        Log.w("PlayerScreen", "MPV engine error: $message. Executing seamless hot failover to ExoPlayerEngine at $retryPosition ms")
                        val (fallback, pos) = PlayerEngineFactory.createFallback(context, playerEngine, speed)
                        playerEngine = fallback
                        pendingResumeMs = pos
                        retryCount = 1
                        isReconnecting = true
                        isBuffering = true
                        playbackError = null
                        reloadNonce++
                    } else if (item != null && retryCount < MAX_AUTO_RETRIES) {
                        retryCount++
                        recordMetrics()
                        isReconnecting = true
                        isBuffering = true
                        playbackError = null
                        scope.launch {
                            delay(maxOf(autoRetryDelayMs(retryCount), 2_000L))
                            if (queue.getOrNull(queueIndex)?.fileId == item.fileId && playbackError == null) {
                                reloadNonce++
                            }
                        }
                    } else {
                        isReconnecting = false
                        playbackError = message
                    }
                }
                is EnginePlaybackState.Idle -> Unit
            }
        }
    }

    LaunchedEffect(playerEngine) {
        NetworkConnectivityObserver.networkState.collect { net ->
            playerEngine.updateNetworkState(net.isCellular)
        }
    }

    DisposableEffect(lifecycleOwner, playerEngine, streamingSessionManager) {
        streamingSessionManager.acquireLocks()
        val activity = context as? androidx.activity.ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> streamingSessionManager.acquireLocks()
                Lifecycle.Event.ON_PAUSE -> {
                    val inPip = isInPipMode || activity?.isInPictureInPictureMode == true
                    if (!inPip && playerEngine.isPlaying) {
                        playerEngine.pause()
                    }
                    queue.getOrNull(queueIndex)?.let { current ->
                        if (playerEngine.currentPositionMs > 0) {
                            PlaybackProgressTracker.saveMessagePosition(
                                current.chatId,
                                current.messageId,
                                playerEngine.currentPositionMs.toInt(),
                                duration,
                                current.fileId,
                                current.title
                            )
                        }
                    }
                }
                Lifecycle.Event.ON_RESUME -> if (isPlaying) playerEngine.play()
                Lifecycle.Event.ON_STOP -> {
                    val inPip = isInPipMode || activity?.isInPictureInPictureMode == true
                    if (!inPip) {
                        stopCurrentCache(PlaybackCacheStopReason.LifecycleStop)
                        streamingSessionManager.releaseLocks()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val current = queue.getOrNull(queueIndex)
            val totalPlayTime = System.currentTimeMillis() - playerSessionStartMs
            if (current != null && totalPlayTime > 1_000L) {
                val rebufferTime = rebufferCount * 1_500L
                val rebufferRatio = if (totalPlayTime > 0) (rebufferTime.toFloat() / totalPlayTime.toFloat()).coerceIn(0f, 1f) else 0f
                StreamHealthTelemetry.recordSession(
                    SessionHealthRecord(
                        sessionId = "sess_${System.currentTimeMillis()}",
                        fileId = current.fileId,
                        ttfbMs = firstFrameMs.takeIf { it > 0 } ?: 0L,
                        stallCount = rebufferCount,
                        totalPlayTimeMs = totalPlayTime,
                        totalRebufferTimeMs = rebufferTime,
                        rebufferRatio = rebufferRatio,
                        networkType = if (com.vaydelux.bousygram.core.network.NetworkConnectivityObserver.isCellular) "Cellular" else "WiFi",
                        decoderName = decoderName,
                        engine = if (playerEngine is ExoPlayerEngine) "ExoPlayer" else "libmpv"
                    )
                )
            }
            if (current != null && playerEngine.currentPositionMs > 0) {
                PlaybackProgressTracker.saveMessagePosition(
                    current.chatId,
                    current.messageId,
                    playerEngine.currentPositionMs.toInt(),
                    duration,
                    current.fileId,
                    current.title
                )
            }
            revokeLocalPlaybackCapabilities()
            stopCurrentCache(PlaybackCacheStopReason.Dispose)
            streamingSessionManager.releaseLocks()
            audioFocusController.abandon()
            mediaSession?.release()
            playerEngine.release()
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

    LaunchedEffect(request, queueIndex, reloadNonce) {
        // A retry or track change replaces the old URL; revoke its capability first.
        // Lifecycle backgrounding does not run this effect, so a paused player can resume.
        StreamAccessRegistry.revokeFromUrl(activeLocalStreamUrl)
        activeLocalStreamUrl = null
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
        val streamUrl = run {
            // The initial local URL was already prepared by the repository. Reuse it once
            // instead of issuing a duplicate token; later queue items and retries get a fresh URL.
            val alreadyPreparedUrl = request.url.takeIf {
                queueIndex == initialIndex && reloadNonce == 0 &&
                    it.startsWith("http://127.0.0.1")
            }
            val preparation = if (alreadyPreparedUrl != null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    if (item.alternativeVideos.isNotEmpty()) {
                        val options = item.alternativeVideos.mapNotNull { alt ->
                            val fid = alt.fileId ?: return@mapNotNull null
                            val q = alt.height.takeIf { it > 0 } ?: 720
                            val tok = StreamAccessRegistry.issueFile(fid)
                            com.vaydelux.bousygram.core.tdlib.server.VideoResolutionOption(q, fid, tok)
                        }
                        if (options.isNotEmpty()) {
                            val hlsUrl = com.vaydelux.bousygram.core.tdlib.server.TelegramProxyManager.hlsMasterStreamUrl(options)
                            com.vaydelux.bousygram.core.tdlib.server.StreamPreparation(
                                fileId = item.fileId,
                                resolvedSize = item.fileSizeBytes,
                                url = hlsUrl,
                                warmupStarted = true
                            )
                        } else {
                            LocalStreamingServer.prepareStream(
                                fileId = item.fileId,
                                declaredSize = item.fileSizeBytes,
                                title = item.title,
                                mimeType = item.mimeType,
                                fileName = item.fileName
                            )
                        }
                    } else {
                        LocalStreamingServer.prepareStream(
                            fileId = item.fileId,
                            declaredSize = item.fileSizeBytes,
                            title = item.title,
                            mimeType = item.mimeType,
                            fileName = item.fileName
                        )
                    }
                }
            }
            if (alreadyPreparedUrl == null && preparation == null) {
                val message = "Stream not ready. The file may still be downloading in Telegram."
                playbackError = message
                PlaybackDiagnostics.recordStreamError(message)
                isOpening = false
                isReconnecting = false
                isBuffering = false
                hudVisible = true
                return@LaunchedEffect
            }
            if (preparation != null) {
                fileSizeBytes = preparation.resolvedSize
                cacheState = TelegramPreCacheEngine.cacheState(item.fileId)
            } else {
                val sizeFromPreparedUrl = alreadyPreparedUrl
                    ?.substringAfter("size=", "")
                    ?.substringBefore('&')
                    ?.toLongOrNull()
                fileSizeBytes = item.fileSizeBytes.takeIf { it > 0L }
                    ?: sizeFromPreparedUrl?.takeIf { it > 0L }
                    ?: 0L
                cacheState = TelegramPreCacheEngine.cacheState(item.fileId)
            }
            alreadyPreparedUrl ?: preparation!!.url
        }
        activeLocalStreamUrl = streamUrl.takeIf { it.startsWith("http://127.0.0.1") }
        val videoMime = VideoFormatSupport.exoMimeType(item.mimeType, item.fileName, item.title)
        val resumePosition = lastGoodPosition
            .takeIf { it > 0L }
            ?: PlaybackProgressTracker.getMessagePosition(item.chatId, item.messageId, duration).toLong()
        playbackSession?.let {
            PlaybackSessionController.stop(it, PlaybackCacheStopReason.ErrorRecovery, cancelTdLibDownload = false)
        }
        val session = PlaybackSessionController.start(item.fileId, resumePosition)
        playbackSession = session
        playerEngine.stop()

        firstFrameMs = 0L
        playerStartedAtMs = System.currentTimeMillis()
        pendingResumeMs = resumePosition
        playerEngine.prepare(streamUrl)
        playerEngine.setPlaybackSpeed(speed)
        playerEngine.play()
        isPlaying = true
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(queueIndex, reloadNonce, isBuffering) {
        if (!isBuffering) return@LaunchedEffect
        delay(BUFFERING_STALL_MS)
        if (!isBuffering) return@LaunchedEffect
        if (playerEngine.playbackState.value is EnginePlaybackState.Buffering &&
            !isOpening &&
            !isReconnecting &&
            duration <= 0L &&
            playerEngine.currentPositionMs <= 0L
        ) {
            playbackError = "Playback stalled. Try Retry or Skip to the next item."
            hudVisible = true
        }
    }

    LaunchedEffect(hudVisible, isPlaying, playbackError, tracksPanelVisible, queueIndex, hudInteractionTick) {
        if (hudVisible && isPlaying && playbackError == null && !tracksPanelVisible) {
            delay(5_500)
            hudVisible = false
        }
    }

    LaunchedEffect(playerEngine, queueIndex) {
        while (true) {
            position = playerEngine.currentPositionMs.coerceAtLeast(0L)
            applyPlayerDurationMs(playerEngine.durationMs)
            isPlaying = playerEngine.isPlaying
            val current = queue[queueIndex]
            cacheState = TelegramPreCacheEngine.cacheState(current.fileId)
            if (position > lastGoodPosition && playerEngine.playbackState.value is EnginePlaybackState.Ready) {
                lastGoodPosition = position
            }
            playbackSession?.let { session ->
                PlaybackSessionController.recordProgress(session, position, fileSizeBytes, duration)
            }
            if (position > 0L &&
                duration > 0L &&
                fileSizeBytes > 0L &&
                position - lastAheadPrefetchPosition >= CACHE_AHEAD_REFRESH_MS
            ) {
                playbackSession?.let { session ->
                    PlaybackSessionController.cacheAhead(session, fileSizeBytes, position, duration)
                }
                lastAheadPrefetchPosition = position
            }
            if (position > 0 && position - lastSavedPosition >= 5_000L) {
                PlaybackProgressTracker.saveMessagePosition(
                    current.chatId,
                    current.messageId,
                    position.toInt(),
                    duration,
                    current.fileId,
                    current.title
                )
                lastSavedPosition = position
            }
            delay(500)
        }
    }

    LaunchedEffect(showBrightnessHud, gestureBrightness) {
        if (showBrightnessHud) {
            delay(1500)
            showBrightnessHud = false
        }
    }

    LaunchedEffect(showVolumeHud, gestureVolume) {
        if (showVolumeHud) {
            delay(1500)
            showVolumeHud = false
        }
    }

    BackHandler(enabled = true) {
        when {
            episodesDrawerVisible -> episodesDrawerVisible = false
            tracksPanelVisible -> tracksPanelVisible = false
            showResumeRestartOption -> showResumeRestartOption = false
            hudVisible -> hudVisible = false
            else -> exitPlayback()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(queueIndex, duration) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val delta = if (offset.x < size.width / 2f) -SEEK_INCREMENT_MS else SEEK_INCREMENT_MS
                        val max = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        val newPos = (playerEngine.currentPositionMs + delta).coerceIn(0L, max)
                        playerEngine.seekTo(newPos)
                        LocalStreamingServer.prefetchAtPosition(queue[queueIndex].fileId, fileSizeBytes, newPos, duration)
                        seekFeedbackText = if (delta < 0) "-10s" else "+10s"
                        seekFeedbackSide = if (delta < 0) -1 else 1
                        hudVisible = true
                        hudInteractionTick++
                    },
                    onTap = {
                        hudVisible = !hudVisible
                        hudInteractionTick++
                    }
                )
            }
            .pointerInput(Unit) {
                var isLeft = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isLeft = offset.x < size.width / 2f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (isLeft) {
                            if (currentActivity != null) {
                                val current = currentActivity.window.attributes.screenBrightness.let { if (it < 0f) 0.5f else it }
                                val next = (current - (dragAmount / size.height.toFloat())).coerceIn(0.01f, 1.0f)
                                val lp = currentActivity.window.attributes
                                lp.screenBrightness = next
                                currentActivity.window.attributes = lp
                                gestureBrightness = next
                                showBrightnessHud = true
                                hudInteractionTick++
                            }
                        } else {
                            if (currentAudioManager != null) {
                                val maxVol = currentAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val current = currentAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val delta = (-dragAmount / (size.height / (maxVol * 1.5f))).toInt()
                                if (delta != 0) {
                                    val next = (current + delta).coerceIn(0, maxVol)
                                    currentAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    gestureVolume = next
                                    showVolumeHud = true
                                    hudInteractionTick++
                                }
                            }
                        }
                    }
                )
            }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (tracksPanelVisible) {
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        (event.key == Key.Back || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU)
                    ) {
                        tracksPanelVisible = false
                        return@onKeyEvent true
                    }
                    // Let the focus system move between the controls in the track picker.
                    return@onKeyEvent false
                }
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                hudInteractionTick++
                // When the HUD is visible, arrows should move focus between buttons, not seek
                // behind the user's back. Video-only seeking remains available with the HUD hidden.
                if (hudVisible && event.key in setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                    return@onKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        val newPos = (playerEngine.currentPositionMs - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                        playerEngine.seekTo(newPos)
                        LocalStreamingServer.prefetchAtPosition(
                            queue[queueIndex].fileId,
                            fileSizeBytes,
                            newPos,
                            duration
                        )
                        seekFeedbackText = "-10s"
                        seekFeedbackSide = -1
                        true
                    }
                    Key.DirectionRight -> {
                        val max = duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        val newPos = (playerEngine.currentPositionMs + SEEK_INCREMENT_MS).coerceAtMost(max)
                        playerEngine.seekTo(newPos)
                        LocalStreamingServer.prefetchAtPosition(
                            queue[queueIndex].fileId,
                            fileSizeBytes,
                            newPos,
                            duration
                        )
                        seekFeedbackText = "+10s"
                        seekFeedbackSide = 1
                        true
                    }
                    Key.Enter, Key.DirectionCenter -> {
                        if (playerEngine.isPlaying) playerEngine.pause() else playerEngine.play()
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        hudVisible = !hudVisible
                        true
                    }
                    Key.Back -> {
                        when {
                            tracksPanelVisible -> tracksPanelVisible = false
                            showResumeRestartOption -> showResumeRestartOption = false
                            hudVisible -> hudVisible = false
                            else -> exitPlayback()
                        }
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
                            if (playerEngine.isPlaying) playerEngine.pause() else playerEngine.play()
                            true
                        }
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_INFO -> {
                            if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                                tracksPanelVisible = true
                                hudVisible = true
                            } else {
                                speed = nextSpeed(speed)
                                playerEngine.setPlaybackSpeed(speed)
                                EnterprisePreferences.setPreferredSpeed(speed)
                                toastFeedbackText = "Speed: ${speed}x"
                                hudVisible = true
                            }
                            true
                        }
                        KeyEvent.KEYCODE_PROG_GREEN -> {
                            val nextMode = when (effectiveResizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                            userSelectedResizeMode = nextMode
                            toastFeedbackText = "Aspect: " + formatResizeMode(nextMode)
                            hudVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_CAPTIONS -> {
                            tracksPanelVisible = true
                            hudVisible = true
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        VideoSurfaceView(
            engine = playerEngine,
            resizeMode = effectiveResizeMode,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    if (coordinates.isAttached) {
                        val bounds = coordinates.boundsInWindow()
                        if (bounds.width > 0 && bounds.height > 0) {
                            videoBoundsOnScreen = Rect(
                                bounds.left.toInt(),
                                bounds.top.toInt(),
                                bounds.right.toInt(),
                                bounds.bottom.toInt()
                            )
                        }
                    }
                }
        )
        if (hudVisible && !isInPipMode) {
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
                    cacheStateLabel = cacheState.playerLabel(),
                    bufferedPositionMs = playerEngine.bufferedPositionMs,
                    resizeMode = effectiveResizeMode,
                    mediaKey = "${queue.getOrNull(queueIndex)?.chatId}:${queue.getOrNull(queueIndex)?.messageId}"
                ),
                compact = compactHud,
                onExit = ::exitPlayback,
                onRetry = {
                    hudInteractionTick++
                    retryCount = 0
                    isReconnecting = true
                    lastGoodPosition = maxOf(lastGoodPosition, playerEngine.currentPositionMs).coerceAtLeast(0L)
                    playbackError = null
                    reloadNonce++
                },
                onPlayPause = {
                    hudInteractionTick++
                    if (playerEngine.isPlaying) playerEngine.pause() else playerEngine.play()
                },
                onSeekBy = { delta ->
                    hudInteractionTick++
                    val max = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val newPos = (playerEngine.currentPositionMs + delta).coerceIn(0L, max)
                    playerEngine.seekTo(newPos)
                    LocalStreamingServer.prefetchAtPosition(queue[queueIndex].fileId, fileSizeBytes, newPos, duration)
                },
                onSeekTo = { target ->
                    hudInteractionTick++
                    val max = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                    val newPos = target.coerceIn(0L, max)
                    playerEngine.seekTo(newPos)
                    LocalStreamingServer.prefetchAtPosition(queue[queueIndex].fileId, fileSizeBytes, newPos, duration)
                },
                onSkipPrevious = {
                    hudInteractionTick++
                    if (queueIndex > 0) goToTrack(queueIndex - 1)
                },
                onSkipNext = {
                    hudInteractionTick++
                    if (queueIndex < queue.lastIndex) goToTrack(queueIndex + 1) else exitPlayback()
                },
                onTracks = {
                    hudInteractionTick++
                    tracksPanelVisible = true
                    hudVisible = true
                },
                onToggleResizeMode = {
                    hudInteractionTick++
                    val nextMode = when (effectiveResizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    userSelectedResizeMode = nextMode
                    toastFeedbackText = "Aspect: " + formatResizeMode(nextMode)
                    hudVisible = true
                },
                onSpeed = {
                    hudInteractionTick++
                    speed = nextSpeed(speed)
                    playerEngine.setPlaybackSpeed(speed)
                    EnterprisePreferences.setPreferredSpeed(speed)
                    toastFeedbackText = "Speed: ${speed}x"
                    hudVisible = true
                },
                onEpisodes = if (queue.size > 1) { { episodesDrawerVisible = !episodesDrawerVisible } } else null,
                onPip = {
                    val activity = context as? MainActivity
                    if (activity != null) {
                        activity.enterPipMode(videoAspectRatio, videoBoundsOnScreen)
                    } else {
                        onEnterPip()
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        if (tracksPanelVisible && !isInPipMode) {
            TrackPickerOverlay(
                playerEngine = playerEngine,
                compact = compactHud,
                onClose = { tracksPanelVisible = false },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // In-Player Episodes Drawer Overlay
        AnimatedVisibility(
            visible = episodesDrawerVisible && !isInPipMode,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (compactHud) 130.dp else 170.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF20A101D),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E405D)),
                modifier = Modifier
                    .fillMaxWidth(if (compactHud) 0.96f else 0.85f)
                    .padding(horizontal = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Episodes in Queue (${queue.size})",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        BousygramButton("Close", { episodesDrawerVisible = false })
                    }
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(queue) { idx, item ->
                            val isCurrent = idx == queueIndex
                            val epShape = RoundedCornerShape(12.dp)
                            Surface(
                                shape = epShape,
                                color = if (isCurrent) Color(0xFF162E3B) else Color(0xFF131D2D),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrent) Color(0xFF5EE5BE) else Color(0xFF223249)
                                ),
                                modifier = Modifier
                                    .width(if (compactHud) 160.dp else 200.dp)
                                    .clip(epShape)
                                    .clickable {
                                        episodesDrawerVisible = false
                                        goToTrack(idx)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${idx + 1}. ${item.title}",
                                        color = if (isCurrent) Color(0xFF5EE5BE) else Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = if (isCurrent) "▶ Playing" else formatMs((item.durationSeconds * 1000L)),
                                        color = if (isCurrent) Color(0xFF5EE5BE) else Color(0xFF94A3B8),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gesture Brightness HUD Pill (Left)
        AnimatedVisibility(
            visible = showBrightnessHud && !isInPipMode,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E405D)),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("☀️", fontSize = 22.sp)
                    Text(
                        "${(gestureBrightness * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gesture Volume HUD Pill (Right)
        AnimatedVisibility(
            visible = showVolumeHud && !isInPipMode,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 28.dp)
        ) {
            val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
            val maxVol = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.82f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E405D)),
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🔊", fontSize = 22.sp)
                    Text(
                        "${(gestureVolume * 100 / maxVol.coerceAtLeast(1))}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Animated Seek Feedback Overlay
        AnimatedVisibility(
            visible = seekFeedbackText != null && !isInPipMode,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(if (seekFeedbackSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = seekFeedbackText.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Mode Toast Overlay (Aspect ratio / Speed change)
        AnimatedVisibility(
            visible = toastFeedbackText != null && !isInPipMode,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.70f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Text(
                    text = toastFeedbackText.orEmpty(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Smart Resume Restart Prompt
        AnimatedVisibility(
            visible = showResumeRestartOption && hudVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier
                    .padding(top = 28.dp, end = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        playerEngine.seekTo(0L)
                        showResumeRestartOption = false
                        toastFeedbackText = "Restarted from beginning"
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "↺ Start Over",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TrackPickerOverlay(
    playerEngine: PlayerEngine,
    compact: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks by playerEngine.tracks.collectAsStateWithLifecycle()
    var selectedType by remember { mutableStateOf(MediaTrackInfo.TrackType.SUBTITLE) }
    val firstChoiceFocus = remember { FocusRequester() }
    val visibleTracks = tracks.filter { it.type == selectedType }
    val hasSelectedTrack = visibleTracks.any { it.isSelected }

    LaunchedEffect(selectedType) {
        runCatching { firstChoiceFocus.requestFocus() }
    }

    Surface(
        modifier = modifier
            .padding(if (compact) 12.dp else 28.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth(if (compact) 0.94f else 0.42f)
            .focusGroup(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 20.dp
    ) {
        Column(
            Modifier
                .heightIn(max = if (compact) 420.dp else 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (selectedType) {
                        MediaTrackInfo.TrackType.SUBTITLE -> "Subtitles"
                        MediaTrackInfo.TrackType.AUDIO -> "Audio"
                        MediaTrackInfo.TrackType.VIDEO -> "Video Streams"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                BousygramButton("Close", onClose)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BousygramButton(
                    "CC · Subtitles",
                    { selectedType = MediaTrackInfo.TrackType.SUBTITLE },
                    selected = selectedType == MediaTrackInfo.TrackType.SUBTITLE
                )
                BousygramButton(
                    "♫ Audio",
                    { selectedType = MediaTrackInfo.TrackType.AUDIO },
                    selected = selectedType == MediaTrackInfo.TrackType.AUDIO
                )
                val videoTracks = tracks.filter { it.type == MediaTrackInfo.TrackType.VIDEO }
                if (videoTracks.size > 1) {
                    BousygramButton(
                        "⚙ Streams",
                        { selectedType = MediaTrackInfo.TrackType.VIDEO },
                        selected = selectedType == MediaTrackInfo.TrackType.VIDEO
                    )
                }
            }

            if (selectedType == MediaTrackInfo.TrackType.SUBTITLE) {
                TrackChoice(
                    label = "Off",
                    selected = !hasSelectedTrack,
                    modifier = Modifier.focusRequester(firstChoiceFocus),
                    onSelect = {
                        playerEngine.deselectTrack(MediaTrackInfo.TrackType.SUBTITLE)
                    }
                )
            } else if (visibleTracks.isEmpty()) {
                Text(
                    if (selectedType == MediaTrackInfo.TrackType.AUDIO)
                        "No alternate audio tracks are available."
                    else
                        "No subtitle tracks are available for this video.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            visibleTracks.forEachIndexed { index, track ->
                TrackChoice(
                    label = track.name,
                    selected = track.isSelected,
                    modifier = if (index == 0 && selectedType == MediaTrackInfo.TrackType.AUDIO) {
                        Modifier.focusRequester(firstChoiceFocus)
                    } else Modifier,
                    onSelect = { playerEngine.selectTrack(track) }
                )
            }
        }
    }
}

@Composable
private fun TrackChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onSelect)
            .focusable(),
        color = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            focused -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surface
        },
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (selected) Text("✓", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PlayerHud(
    state: PlayerHudState,
    compact: Boolean,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onTracks: () -> Unit,
    onToggleResizeMode: () -> Unit,
    onSpeed: () -> Unit,
    onEpisodes: (() -> Unit)? = null,
    onPip: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val edgePadding = if (compact) 12.dp else 32.dp
    val innerPadding = if (compact) 14.dp else 22.dp
    val statusText = when {
        state.error != null -> "Playback error"
        state.isReconnecting -> "Retrying download"
        state.isOpening -> "Downloading"
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
    val hudShape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(edgePadding)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, hudShape),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        shape = hudShape,
        tonalElevation = 8.dp
    ) {
        Column(Modifier.padding(innerPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!state.fileName.isNullOrBlank() && state.fileName != state.title) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                BousygramButton("Back", onExit)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.isBuffering && state.error == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    statusLine,
                    modifier = Modifier.weight(1f),
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${formatMs(state.positionMs)} / ${durationText(state)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${speedLabel(state.speed)}x",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                state.error
                    ?: if (state.isOpening || state.isReconnecting) "Fetching this video from Telegram. Playback starts as soon as the first bytes arrive."
                    else if (compact) "Double-tap left/right to seek ±10s · drag timeline to scrub"
                    else "D-pad left/right seek · center play/pause · CH± queue · Menu tracks · Info speed · up/down HUD",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            PlayerSeekBar(
                mediaKey = state.mediaKey,
                positionMs = state.positionMs,
                bufferedPositionMs = state.bufferedPositionMs,
                durationMs = state.durationMs,
                enabled = state.durationTrusted && state.durationMs > 0L,
                onSeekTo = onSeekTo
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                BousygramButton("-30s", { onSeekBy(-30_000L) })
                BousygramButton("-10s", { onSeekBy(-SEEK_INCREMENT_MS) })
                BousygramButton(if (state.isPlaying) "Pause" else "Play", onPlayPause, selected = true)
                BousygramButton("+10s", { onSeekBy(SEEK_INCREMENT_MS) })
                BousygramButton("+30s", { onSeekBy(30_000L) })
                BousygramButton("Prev", onSkipPrevious)
                BousygramButton("Next", onSkipNext)
                BousygramButton("Tracks", onTracks)
                val aspectLabel = when (state.resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                    else -> "Fit"
                }
                BousygramButton(aspectLabel, onToggleResizeMode)
                BousygramButton("${speedLabel(state.speed)}x", onSpeed)
                if (onEpisodes != null) {
                    BousygramButton("Episodes ☰", onEpisodes)
                }
                BousygramButton("PiP ⧉", onPip)
            }
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BousygramButton("Retry", onRetry, selected = true)
                    BousygramButton("Skip", onSkipNext)
                }
            }
        }
    }
}

private fun durationText(state: PlayerHudState): String =
    if (state.durationTrusted && state.durationMs > 0L) formatMs(state.durationMs) else "Unknown"

@Composable
private fun PlayerSeekBar(
    mediaKey: String = "",
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekTo: (Long) -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(positionMs) }
    var barFocused by remember { mutableStateOf(false) }
    val shown = if (dragging) dragPosition else positionMs
    val fraction = if (enabled && durationMs > 0L) (shown.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val buffered = if (enabled && durationMs > 0L) {
        (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val previewBitmap = remember(mediaKey, shown, durationMs) {
        if (mediaKey.isNotBlank() && durationMs > 0L) {
            SeekThumbnailManager.getSnapshotAt(mediaKey, shown, durationMs)
        } else {
            null
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if ((dragging || barFocused) && enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${formatMs(shown)} / ${formatMs(durationMs)}",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                if (previewBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.90f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Seek frame preview",
                            modifier = Modifier.size(width = 84.dp, height = 48.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (barFocused) 1.5.dp else 0.dp,
                    color = if (barFocused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 6.dp)
                .onFocusChanged { barFocused = it.isFocused }
                .focusable()
                .onKeyEvent { event ->
                    if (!enabled || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    val repeat = event.nativeKeyEvent.repeatCount
                    val step = when {
                        repeat >= 8 -> 60_000L
                        repeat >= 3 -> 30_000L
                        else -> SEEK_INCREMENT_MS
                    }
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeekTo((shown - step).coerceAtLeast(0L))
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekTo((shown + step).coerceAtMost(durationMs))
                            true
                        }
                        else -> false
                    }
                }
        ) {
            LinearProgressIndicator(
                progress = { buffered },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center),
                color = MaterialTheme.colorScheme.outline,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Slider(
                value = fraction,
                onValueChange = { value ->
                    if (!enabled) return@Slider
                    dragging = true
                    dragPosition = (value * durationMs).toLong().coerceIn(0L, durationMs)
                },
                onValueChangeFinished = {
                    if (enabled) onSeekTo(dragPosition)
                    dragging = false
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.Transparent
                )
            )
        }
        if (dragging && enabled && previewBitmap == null) {
            Text(
                "${formatMs(dragPosition)} / ${formatMs(durationMs)}",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

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
            "Telegram is still sending this part"
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
    private var currentEngine: PlayerEngine? = null
    private val listener = AudioManager.OnAudioFocusChangeListener { change ->
        val engine = currentEngine ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> engine.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> engine.pause()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
            AudioManager.AUDIOFOCUS_GAIN -> Unit
        }
    }

    fun request(engine: PlayerEngine) {
        val audioManager = audioManager ?: return
        currentEngine = engine
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
        currentEngine = null
    }
}
