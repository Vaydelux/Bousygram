package com.vaydelux.bousygram.presentation.extras

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
): Boolean = isAnime && !loadFailed && !immersive && !detectedVideo && longLoad
