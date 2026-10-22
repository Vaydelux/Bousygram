package com.vaydelux.bousygram.presentation.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.vaydelux.bousygram.core.media.StreamingSessionManager
import com.vaydelux.bousygram.core.tdlib.server.VideoResolutionOption
import org.drinkless.tdlib.Client

@Composable
fun VideoPlayerScreen(
    fileId: Int,
    tdLibClient: Client,
    modifier: Modifier = Modifier,
    resolutionOptions: List<VideoResolutionOption> = emptyList(),
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val streamingSessionManager = remember(context) {
        StreamingSessionManager(context)
    }
    val playbackOptions = remember(fileId, resolutionOptions) {
        resolutionOptions
            .filter { it.fileId > 0 }
            .takeIf { it.isNotEmpty() }
            ?: listOf(VideoResolutionOption(quality = 0, fileId = fileId))
    }

    LaunchedEffect(viewModel, tdLibClient, playbackOptions) {
        viewModel.bindTdLibClient(tdLibClient)
        viewModel.playVideo(playbackOptions)
    }

    LaunchedEffect(uiState) {
        val error = uiState as? VideoPlayerUiState.Error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error.message)
    }

    DisposableEffect(lifecycleOwner, streamingSessionManager, viewModel) {
        streamingSessionManager.acquireLocks()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    streamingSessionManager.acquireLocks()
                    viewModel.resumePlayback()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.stopPlayback()
                    streamingSessionManager.releaseLocks()
                }
                Lifecycle.Event.ON_DESTROY -> streamingSessionManager.releaseLocks()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPlayback()
            streamingSessionManager.releaseLocks()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(contentPadding)
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        player = viewModel.player
                    }
                },
                update = { playerView ->
                    playerView.player = viewModel.player
                },
                modifier = Modifier.fillMaxSize()
            )

            if (uiState is VideoPlayerUiState.Loading || uiState is VideoPlayerUiState.Buffering) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
