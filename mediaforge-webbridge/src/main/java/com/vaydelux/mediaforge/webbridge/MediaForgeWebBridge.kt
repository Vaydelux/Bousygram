package com.vaydelux.mediaforge.webbridge

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vaydelux.mediaforge.core.MediaForgeUrlPolicy
import com.vaydelux.mediaforge.core.MediaForgeEmbedNavigationPolicy
import com.vaydelux.mediaforge.core.MediaForgeClassifier
import com.vaydelux.mediaforge.core.StreamFormat
import java.util.Collections
import java.util.WeakHashMap
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Non-sensitive signals only. Never expose provider console text, iframe URLs,
 * signed stream URLs, cookies or JavaScript to the UI or app logs.
 * A resource failure can be an advertisement, not necessarily the video.
 */
enum class MediaForgeWebIssue {
    IFRAME_POLICY,
    AUTOPLAY_DENIED,
    VIDEO_UNSUPPORTED,
    SUBRESOURCE_HTTP,
    SUBRESOURCE_NETWORK,
    SUBRESOURCE_TLS
}

/** Classify only recognisable browser failures; unknown messages are discarded. */
object MediaForgeWebIssuePolicy {
    fun classifyConsoleError(raw: String): MediaForgeWebIssue? {
        val message = raw.lowercase(Locale.ROOT)
        return when {
            ("frame-ancestors" in message ||
                "x-frame-options" in message ||
                "refused to frame" in message ||
                "refused to display" in message) -> MediaForgeWebIssue.IFRAME_POLICY
            ("notallowederror" in message ||
                ("play()" in message && ("user gesture" in message ||
                    "user didn't interact" in message || "user interaction" in message))) ->
                MediaForgeWebIssue.AUTOPLAY_DENIED
            ("notsupportederror" in message ||
                "media_err_src_not_supported" in message ||
                "no supported source" in message ||
                "no supported video format" in message) -> MediaForgeWebIssue.VIDEO_UNSUPPORTED
            else -> null
        }
    }
}

/**
 * This is a URL-level defense for network subresources, not DNS pinning.
 * Android WebView may handle non-network schemes (blob/data/javascript) that
 * should remain owned by the browser rather than intercepted here.
 */
object MediaForgeWebRequestPolicy {
    fun blockNetworkSubresource(url: String): Boolean {
        val scheme = url.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return when (scheme) {
            "https" -> MediaForgeUrlPolicy.safeHttpsUrl(url) == null
            // These are browser-local document/media representations.
            // Chromium does not call this hook for every blob/javascript
            // request, so this is not a complete browser network firewall.
            "about", "data", "blob" -> false
            // Fail closed for file:, content:, intent:, FTP and unknown
            // external protocols if Chromium does surface them here.
            else -> true
        }
    }
}

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
    private val onRendererGone: (() -> Unit)? = null,
    private val onPageLoadFailed: ((String) -> Unit)? = null,
    private val onPageLoadSucceeded: (() -> Unit)? = null,
    private val onWebIssue: ((MediaForgeWebIssue) -> Unit)? = null
) {
    private val open = AtomicBoolean(true)
    private val main = Handler(Looper.getMainLooper())
    // Resource callbacks may come from WebView IO threads in bursts.
    private val pendingObservations = AtomicInteger(0)
    private val maxPendingObservations = 32
    // Cap duplicate diagnostics from ad-heavy pages. A newly loaded page gets
    // a fresh set; strict and compatibility modes create separate bridge instances.
    private val publishedIssues = mutableSetOf<MediaForgeWebIssue>()
    private fun reportIssue(issue: MediaForgeWebIssue) {
        if (!open.get()) return
        main.post {
            if (open.get() && publishedIssues.add(issue)) onWebIssue?.invoke(issue)
        }
    }
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    // An earlier fatal error may have put a recovery panel over WebView.
    // Only a fresh, successful navigation to the selected title can clear it.
    private var mainFrameNavigating = false
    private var mainFrameFailed = false
    // Identity of WebViews destroyed after renderer loss, so Compose cleanup
    // does not call WebView APIs on an already-destroyed instance.
    private val rendererGoneViews = Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())

    fun rendererGone(view: WebView): Boolean = rendererGoneViews.contains(view)

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, embedUrl: String, compatibilityMode: Boolean = false): WebView {
        check(open.get()) { "A closed MediaForge WebBridge cannot create a new WebView" }
        val approved = MediaForgeUrlPolicy.safeHttpsUrl(embedUrl)
            ?: throw IllegalArgumentException("Invalid external embed URL")
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Third-party anime embeds may require autoplay after the user opted in.
            // Keep gesture gating by default; the Extras UI must disclose the tradeoff.
            mediaPlaybackRequiresUserGesture = !compatibilityMode
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            // Critical: with false, window.open/target=_blank can replace the
            // player in the *main* frame, where our episode-identity policy
            // correctly rejects it but may leave the embed blank. With true
            // and no onCreateWindow handler, Chromium rejects the popup
            // without navigating away from the selected episode.
            setSupportMultipleWindows(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        webView.isFocusableInTouchMode = true
        // Explicit per-WebView compatibility opt-in only. Never relax URL, TLS,
        // local-network, file or content restrictions to make a provider play.
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, compatibilityMode)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                // Third-party scripts can print credentials/URLs in console output.
                // Classify on-device; do not store or log the raw string.
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    MediaForgeWebIssuePolicy.classifyConsoleError(message.message())
                        ?.let(::reportIssue)
                }
                return true
            }

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
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (open.get() && url != null &&
                    MediaForgeEmbedNavigationPolicy.allows(approved, url)
                ) {
                    mainFrameNavigating = true
                    mainFrameFailed = false
                    publishedIssues.clear()
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (!open.get() || !mainFrameNavigating || url == null) return
                val validPage = MediaForgeEmbedNavigationPolicy.allows(approved, url)
                val loaded = validPage && !mainFrameFailed
                mainFrameNavigating = false
                if (loaded) onPageLoadSucceeded?.invoke()
                // onPageFinished also runs after HTTP 4xx/5xx and Chromium
                // network errors. Those must NEVER dismiss recovery UI.
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                // One shared SDK policy owns title/episode/provider identity.
                val allowed = MediaForgeEmbedNavigationPolicy.allows(
                    approved, request.url.toString()
                )
                if (!allowed) onNavigationBlocked?.invoke()
                return !allowed
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (MediaForgeWebRequestPolicy.blockNetworkSubresource(url)) {
                    // Do not let a third-party embed access common local aliases,
                    // loopback addresses, or insecure HTTP via WebView resources.
                    // Never fetch the URL ourselves or follow provider redirects.
                    return WebResourceResponse(
                        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                    ).apply { setStatusCodeAndReasonPhrase(403, "Blocked") }
                }
                if (request.method.equals("GET", true)) publish(url)
                return null // Allow permitted subresources to load normally.
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

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (open.get() && !request.isForMainFrame) {
                    reportIssue(MediaForgeWebIssue.SUBRESOURCE_NETWORK)
                }
                if (open.get() && request.isForMainFrame) {
                    mainFrameFailed = true
                    // Do not display provider URLs, tokens, or raw Chromium error descriptions.
                    onPageLoadFailed?.invoke("The selected web server could not load. Choose another server or retry.")
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                if (open.get() && !request.isForMainFrame && errorResponse.statusCode >= 400) {
                    reportIssue(MediaForgeWebIssue.SUBRESOURCE_HTTP)
                }
                if (open.get() && request.isForMainFrame && errorResponse.statusCode >= 400) {
                    mainFrameFailed = true
                    onPageLoadFailed?.invoke("The selected web server is unavailable (HTTP ${errorResponse.statusCode}). Choose another server.")
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel() // Never bypass certificate errors.
                if (open.get() && error.url?.let {
                    !MediaForgeEmbedNavigationPolicy.allows(approved, it)
                } == true) reportIssue(MediaForgeWebIssue.SUBRESOURCE_TLS)
                // A failed unrelated iframe/subresource must not place a fatal
                // black/opaque error overlay over a successfully loaded top page.
                // Only errors for the selected top-level document are fatal UI.
                if (open.get() && error.url?.let { MediaForgeEmbedNavigationPolicy.allows(approved, it) } == true) {
                    mainFrameFailed = true
                    onPageLoadFailed?.invoke("Cannot securely load this server. Choose another server.")
                }
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
