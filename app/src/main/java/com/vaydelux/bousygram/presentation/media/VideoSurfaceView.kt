package com.vaydelux.bousygram.presentation.media

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.vaydelux.bousygram.core.media.ExoPlayerEngine
import com.vaydelux.bousygram.core.media.PlayerEngine

data class SurfaceDiagnosticState(
    val surfaceType: String = "None",
    val isValid: Boolean = false,
    val width: Int = 0,
    val height: Int = 0
)

/**
 * Universal video presentation surface that supports both native MPV hardware surface
 * rendering and Media3 PlayerView compositing.
 * Supports dynamic SurfaceView <-> TextureView fallback for surface compositor recovery.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurfaceView(
    engine: PlayerEngine,
    resizeMode: Int,
    useTextureView: Boolean = false,
    modifier: Modifier = Modifier,
    onSurfaceDiagnostic: ((SurfaceDiagnosticState) -> Unit)? = null
) {
    if (useTextureView) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                TextureView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    var activeSurface: Surface? = null
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            val surface = Surface(surfaceTexture)
                            activeSurface?.release()
                            activeSurface = surface
                            if (engine is ExoPlayerEngine) {
                                engine.player.setVideoTextureView(this@apply)
                            } else {
                                engine.attachSurface(surface)
                            }
                            engine.setResizeMode(resizeMode)
                            onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("TextureView", surface.isValid, width, height))
                        }

                        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            engine.setResizeMode(resizeMode)
                            val valid = activeSurface?.isValid == true
                            onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("TextureView", valid, width, height))
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            if (engine is ExoPlayerEngine) {
                                engine.player.clearVideoTextureView(this@apply)
                            } else {
                                engine.detachSurface()
                                activeSurface?.release()
                                activeSurface = null
                            }
                            onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("TextureView", false, 0, 0))
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            },
            update = { view ->
                engine.setResizeMode(resizeMode)
                onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("TextureView", view.isAvailable, view.width, view.height))
            },
            onRelease = {
                if (engine is ExoPlayerEngine) {
                    engine.player.clearVideoTextureView(null)
                } else {
                    engine.detachSurface()
                }
                onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("None", false, 0, 0))
            }
        )
    } else if (engine is ExoPlayerEngine) {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    keepScreenOn = true
                    // Prevent default opaque black shutter from occluding video if onRenderedFirstFrame is delayed
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.resizeMode = resizeMode
                    this.player = engine.player
                    post {
                        onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("PlayerView (SurfaceView)", true, width, height))
                    }
                }
            },
            update = { view ->
                if (view.player != engine.player) {
                    view.player = engine.player
                }
                view.resizeMode = resizeMode
                onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("PlayerView (SurfaceView)", true, view.width, view.height))
            },
            onRelease = { view ->
                view.player = null
                engine.detachSurface()
                onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("None", false, 0, 0))
            }
        )
    } else {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    keepScreenOn = true
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            engine.attachSurface(holder.surface)
                            engine.setResizeMode(resizeMode)
                            onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("SurfaceView", holder.surface.isValid, width, height))
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            engine.attachSurface(holder.surface)
                            engine.setResizeMode(resizeMode)
                            onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("SurfaceView", holder.surface.isValid, width, height))
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            engine.detachSurface()
                            onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("SurfaceView", false, 0, 0))
                        }
                    })
                }
            },
            update = { view ->
                engine.setResizeMode(resizeMode)
                onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("SurfaceView", true, view.width, view.height))
            },
            onRelease = {
                engine.detachSurface()
                onSurfaceDiagnostic?.invoke(SurfaceDiagnosticState("None", false, 0, 0))
            }
        )
    }
}
