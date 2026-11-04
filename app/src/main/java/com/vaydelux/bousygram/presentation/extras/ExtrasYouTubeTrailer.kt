package com.vaydelux.bousygram.presentation.extras

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Single official YouTube embed on touch screens, native YouTube TV intent on TVs.
 * Trailer providers supply only an ID; never load arbitrary provider-supplied HTML.
 */
internal object ExtrasTrailerPolicy {
    const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"

    fun isYoutubeVideoId(id: String?): Boolean =
        id != null && id.matches(Regex("[A-Za-z0-9_-]{11}"))

    fun playbackMode(isAndroidTv: Boolean): TrailerMode =
        if (isAndroidTv) TrailerMode.NATIVE_YOUTUBE_TV else TrailerMode.INLINE_WEB

    fun watchUrl(id: String): String? =
        id.takeIf(::isYoutubeVideoId)?.let { "https://www.youtube.com/watch?v=$it" }

    fun tvVideoUri(id: String): String? =
        id.takeIf(::isYoutubeVideoId)?.let { "vnd.youtube:$it" }

    /**
     * Uses the installed Android applicationId for YouTube's WebView Referer policy,
     * not a third-party page or a file:// / about:blank origin.
     */
    fun embedBaseUrl(applicationId: String): String =
        "https://$applicationId/"

    fun embedHtml(videoId: String): String {
        require(isYoutubeVideoId(videoId)) { "Invalid YouTube video ID" }
        return """
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="referrer" content="strict-origin-when-cross-origin">
                <style>
                    html,body { margin:0; padding:0; width:100%; height:100%;
                                overflow:hidden; background:#000; }
                    iframe { position:absolute; inset:0; width:100%; height:100%; border:0; }
                </style>
            </head>
            <body>
              <iframe
                title="YouTube trailer"
                src="https://www.youtube.com/embed/$videoId?playsinline=1"
                referrerpolicy="strict-origin-when-cross-origin"
                allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share"
                allowfullscreen>
              </iframe>
            </body>
            </html>
        """.trimIndent()
    }
}

internal enum class TrailerMode { NATIVE_YOUTUBE_TV, INLINE_WEB }

/**
 * Prefer YouTube for Android TV. If it is unavailable, use the ordinary
 * HTTPS video URL so the user can open it in YouTube or their browser.
 * Never use the root https://youtube.com fallback (which loses the video).
 */
internal fun launchYouTubeTrailer(context: Context, videoId: String): Boolean {
    val target = ExtrasTrailerPolicy.tvVideoUri(videoId) ?: return false
    val tvIntent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
        .setPackage(ExtrasTrailerPolicy.YOUTUBE_TV_PACKAGE)
    try {
        context.startActivity(tvIntent)
        return true
    } catch (_: ActivityNotFoundException) {
        // YouTube TV not installed. Offer the exact same video to external handlers.
    } catch (_: SecurityException) {
        // Some devices disallow the explicit target. Use the standard HTTPS intent.
    }
    val watchUrl = ExtrasTrailerPolicy.watchUrl(videoId) ?: return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl))
            .addCategory(Intent.CATEGORY_BROWSABLE))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/**
 * JavaScript is required by the official YouTube iframe. The generated page has
 * only fixed HTML and a strictly validated video ID. There is no JavaScript
 * bridge, file/content access, or arbitrary top-level navigation.
 * Suppress lint narrowly on this trailer component only.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ExtrasYouTubeTrailer(
    videoId: String,
    isAndroidTv: Boolean,
    compact: Boolean
) {
    if (!ExtrasTrailerPolicy.isYoutubeVideoId(videoId)) return
    val context = LocalContext.current
    if (ExtrasTrailerPolicy.playbackMode(isAndroidTv) == TrailerMode.NATIVE_YOUTUBE_TV) {
        Button(onClick = {
            if (!launchYouTubeTrailer(context, videoId)) {
                android.widget.Toast.makeText(context,
                    "Unable to open YouTube. Install YouTube TV or a browser.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }) { Text("▶ Watch trailer in YouTube TV") }
        return
    }

    var showEmbed by remember(videoId) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Official YouTube trailer", style = MaterialTheme.typography.titleMedium)
        if (!showEmbed) {
            Button(onClick = { showEmbed = true }) {
                Text("▶ Play trailer here")
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(if (compact) 220.dp else 320.dp)
                    .background(androidx.compose.ui.graphics.Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(if (compact) 220.dp else 320.dp),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.apply {
                                javaScriptEnabled = true // Official YouTube iframe only; see scoped lint rationale.
                                domStorageEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
                                allowFileAccessFromFileURLs = false
                                allowUniversalAccessFromFileURLs = false
                                javaScriptCanOpenWindowsAutomatically = false
                                setSupportMultipleWindows(false)
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                mediaPlaybackRequiresUserGesture = true
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = true
                                }
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView, request: WebResourceRequest
                                ): Boolean {
                                    // No arbitrary main-frame navigation from the embedded page.
                                    // YouTube's normal iframe requests must remain intact.
                                    return request.isForMainFrame
                                }
                            }
                            loadDataWithBaseURL(
                                ExtrasTrailerPolicy.embedBaseUrl(ctx.packageName),
                                ExtrasTrailerPolicy.embedHtml(videoId),
                                "text/html", "UTF-8", null
                            )
                        }
                    },
                    onRelease = { view ->
                        // Free the Chromium renderer when changing titles or leaving Extras.
                        view.stopLoading()
                        view.loadUrl("about:blank")
                        view.webChromeClient = null
                        view.destroy()
                    },
                    update = {}
                )
            }
            OutlinedButton(onClick = { showEmbed = false }) { Text("Close embedded player") }
        }
        OutlinedButton(onClick = {
            val url = ExtrasTrailerPolicy.watchUrl(videoId)
            if (url != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    android.widget.Toast.makeText(context, "No browser or YouTube app available",
                        android.widget.Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    android.widget.Toast.makeText(context, "Unable to open YouTube",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }) { Text("Open in YouTube instead") }
    }
}
