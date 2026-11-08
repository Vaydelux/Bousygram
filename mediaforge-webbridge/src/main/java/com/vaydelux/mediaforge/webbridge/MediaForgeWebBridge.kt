package com.vaydelux.mediaforge.webbridge

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import com.vaydelux.mediaforge.core.MediaForgeClassifier
import com.vaydelux.mediaforge.core.StreamFormat
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Observes ordinary WebView resource URLs AFTER the user loads a provider page.
 * It does not inject scripts, bypass DRM, or guarantee capture of MSE/blob streams.
 * Callbacks are delivered on the main thread. Embeds are untrusted.
 */
class MediaForgeWebBridge(
    private val onMediaResource: (String) -> Unit,
    private val onNavigationBlocked: (() -> Unit)? = null,
    private val onFullscreenRequested: ((View) -> Unit)? = null,
    private val onFullscreenEnded: (() -> Unit)? = null,
    private val onRendererGone: (() -> Unit)? = null
) {
    private val open = AtomicBoolean(true)
    private val main = Handler(Looper.getMainLooper())
    // Resource callbacks may come from WebView IO threads in bursts.
    private val pendingObservations = AtomicInteger(0)
    private val maxPendingObservations = 32
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    // Identity of WebViews destroyed after renderer loss, so Compose cleanup
    // does not call WebView APIs on an already-destroyed instance.
    private val rendererGoneViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    fun rendererGone(view: WebView): Boolean = rendererGoneViews.contains(view)

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, embedUrl: String): WebView {
        check(open.get()) { "A closed MediaForge WebBridge cannot create a new WebView" }
        val approved = MediaForgeUrlPolicy.safeHttpsUrl(embedUrl)
            ?: throw IllegalArgumentException("Invalid external embed URL")
        val embedHost = approved.host
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.isFocusableInTouchMode = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (!open.get() || fullscreenView != null || onFullscreenRequested == null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                onFullscreenRequested.invoke(view)
            }

            override fun onHideCustomView() {
                dismissFullscreen()
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val target = MediaForgeUrlPolicy.safeHttpsUrl(request.url.toString())
                val allowed = target != null && target.host.equals(embedHost, true) &&
                    target.path.orEmpty().startsWith("/embed/")
                if (!allowed) onNavigationBlocked?.invoke()
                return !allowed
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.method.equals("GET", true)) publish(request.url.toString())
                return null // Do not modify responses, headers, or remote media requests.
            }

            override fun onLoadResource(view: WebView, url: String) {
                publish(url)
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                // API 26+: this WebView is permanently unusable. Remove/destroy
                // the specific view before returning true; never retry invisibly.
                rendererGoneViews.add(view)
                runCatching { (view.parent as? ViewGroup)?.removeView(view) }
                runCatching { view.destroy() }
                close()
                runCatching { onRendererGone?.invoke() }
                return true
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }
        }
        webView.loadUrl(embedUrl)
        return webView
    }

    private fun publish(url: String) {
        if (!open.get()) return
        val stream = MediaForgeClassifier.stream(url)
        val caption = MediaForgeClassifier.caption(url)
        // MPEG-TS segments inside HLS are not stand-alone video candidates.
        // Filter before posting callbacks to prevent source-picker floods.
        if ((stream == null || stream.format == StreamFormat.MPEG_TS) && caption == null) return
        if (pendingObservations.incrementAndGet() > maxPendingObservations) {
            pendingObservations.decrementAndGet()
            return
        }
        val posted = main.post {
            try {
                if (open.get()) onMediaResource(url)
            } finally {
                pendingObservations.decrementAndGet()
            }
        }
        if (!posted) pendingObservations.decrementAndGet()
    }

    /** Dispatch a D-pad-confirmed real touch event to the WebView at view-local coordinates. */
    fun click(target: View, x: Float, y: Float) {
        if (!open.get()) return // Ignore late TV remote input after native handoff or renderer death.
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 70 else 0,
                action, x, y, 0)
            try { target.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    /**
     * HTML5 video fullscreen is a view owned by the embedded browser. Dismissing
     * it must notify Chromium and release the host overlay before destroying
     * the WebView. These methods are used on Android's main thread.
     */
    fun dismissFullscreen() {
        val callback = fullscreenCallback
        if (fullscreenView == null && callback == null) return
        fullscreenView = null
        fullscreenCallback = null
        runCatching { onFullscreenEnded?.invoke() }
        runCatching { callback?.onCustomViewHidden() }
    }

    fun close() {
        if (!open.compareAndSet(true, false)) return
        dismissFullscreen()
        main.removeCallbacksAndMessages(null)
    }
}
