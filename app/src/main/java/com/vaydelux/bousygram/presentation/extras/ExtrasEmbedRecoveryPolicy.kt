package com.vaydelux.bousygram.presentation.extras

import com.vaydelux.mediaforge.webbridge.MediaForgeWebIssue

/** High-signal player failures outrank noisy third-party ad/CDN errors. */
internal fun preferredAnimeWebIssue(
    current: MediaForgeWebIssue?,
    next: MediaForgeWebIssue
): MediaForgeWebIssue {
    fun rank(issue: MediaForgeWebIssue): Int = when (issue) {
        MediaForgeWebIssue.IFRAME_POLICY -> 6
        MediaForgeWebIssue.VIDEO_UNSUPPORTED -> 5
        MediaForgeWebIssue.AUTOPLAY_DENIED -> 4
        MediaForgeWebIssue.SUBRESOURCE_TLS -> 3
        MediaForgeWebIssue.NAVIGATION_BLOCKED -> 2
        MediaForgeWebIssue.SUBRESOURCE_HTTP -> 2
        MediaForgeWebIssue.SUBRESOURCE_NETWORK -> 1
    }
    return if (current == null || rank(next) > rank(current)) next else current
}

/**
 * A WebView page reaching HTTP 200 is not proof that its iframe/media works.
 * Provide actionable, non-fatal messages without exposing external URLs,
 * scripts, tracking parameters or browser console details.
 */
internal fun animeWebIssueRecoveryMessage(issue: MediaForgeWebIssue?): String = when (issue) {
    MediaForgeWebIssue.IFRAME_POLICY ->
        "An embedded frame was refused by a provider policy (possibly an ad or player frame). If playback stays blank, try the browser or another server."
    MediaForgeWebIssue.AUTOPLAY_DENIED ->
        "The embedded video requested autoplay without a permitted gesture. Tap Play, or try compatibility mode."
    MediaForgeWebIssue.VIDEO_UNSUPPORTED ->
        "The embedded video uses a format this WebView cannot decode. Try an updated Android System WebView or another server."
    MediaForgeWebIssue.SUBRESOURCE_HTTP ->
        "A provider resource returned an HTTP error. It could be an ad or player resource; try another server if playback stays blank."
    MediaForgeWebIssue.SUBRESOURCE_NETWORK ->
        "A background provider request failed. Check the connection and try reloading or switching server."
    MediaForgeWebIssue.SUBRESOURCE_TLS ->
        "A provider resource failed TLS verification. For safety the connection was blocked; choose another server."
    MediaForgeWebIssue.NAVIGATION_BLOCKED ->
        "An unwanted window or off-page navigation was blocked. If the picture is blank, retry the selected page or choose another server. Compatibility options are available in Playback tools."
    null ->
        "This player may require a user tap or cross-site cookies. Try compatibility mode or another server."
}

/**
 * A blank provider response is not proof that playback failed:
 * DRM/MSE and WebView-managed playback may hide direct stream URLs.
 * Offer a non-blocking manual server switch rather than silently reloading
 * or switching external providers, which could disturb valid playback.
 */
internal fun shouldOfferAnimeBlankPageHelp(
    isAnime: Boolean,
    loadFailed: Boolean,
    immersive: Boolean,
    detectedVideo: Boolean,
    longLoad: Boolean
): Boolean = isAnime && shouldOfferExtrasBlankPageHelp(
    loadFailed, immersive, detectedVideo, longLoad
)

/** Every Extras embed (TMDB movie, episode or AniList) shares the same WebView. */
internal fun shouldOfferExtrasBlankPageHelp(
    loadFailed: Boolean,
    immersive: Boolean,
    detectedVideo: Boolean,
    longLoad: Boolean
): Boolean = !loadFailed && !immersive && !detectedVideo && longLoad
