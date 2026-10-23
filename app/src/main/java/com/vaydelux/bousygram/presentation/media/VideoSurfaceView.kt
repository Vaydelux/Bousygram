package com.vaydelux.bousygram.presentation.media

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.vaydelux.bousygram.core.media.ExoPlayerEngine
import com.vaydelux.bousygram.core.media.PlayerEngine

/**
 * Universal video presentation surface that supports both native MPV hardware surface
 * rendering and Media3 PlayerView compositing.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurfaceView(
    engine: PlayerEngine,
    resizeMode: Int,
    modifier: Modifier = Modifier
) {
    if (engine is ExoPlayerEngine) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode
                    this.player = engine.player
                }
            },
            update = { view ->
                view.player = engine.player
                view.resizeMode = resizeMode
            }
        )
    } else {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    keepScreenOn = true
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            engine.attachSurface(holder.surface)
                            engine.setResizeMode(resizeMode)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            engine.setResizeMode(resizeMode)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            engine.detachSurface()
                        }
                    })
                }
            },
            update = {
                engine.setResizeMode(resizeMode)
            }
        )
    }
}
