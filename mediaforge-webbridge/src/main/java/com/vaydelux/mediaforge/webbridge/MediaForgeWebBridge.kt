package com.vaydelux.mediaforge.webbridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observes ordinary WebView resource URLs AFTER the user loads a provider page.
 * It does not inject scripts, bypass DRM, or guarantee capture of MSE/blob streams.
 * Callbacks are delivered on the main thread. Embeds are untrusted.
 */
class MediaForgeWebBridge(
    private val onMediaResource: (String) -> Unit,
    private val onNavigationBlocked: (() -> Unit)? = null
) {
    private val open = AtomicBoolean(true)
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, embedUrl: String): WebView {
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
        webView.webChromeClient = WebChromeClient()
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

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
            }
        }
        webView.loadUrl(embedUrl)
        return webView
    }

    private fun publish(url: String) {
        if (!open.get() || MediaForgeUrlPolicy.safeHttpsUrl(url) == null) return
        main.post { if (open.get()) onMediaResource(url) }
    }

    /** Dispatch a D-pad-confirmed real touch event to the WebView at view-local coordinates. */
    fun click(webView: WebView, x: Float, y: Float) {
        val time = android.os.SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 70 else 0,
                action, x, y, 0)
            try { webView.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    fun close() { open.set(false); main.removeCallbacksAndMessages(null) }
}
