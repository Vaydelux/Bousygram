package com.vaydelux.bousygram.core.media

/**
 * Android TV/HDMI-CEC media commands must enter the same user-intent and guarded-seek
 * path as the player's own controls. Native engine methods alone do not update the
 * explicit-pause flag or reject unsupported MPEG-TS seeks.
 *
 * Callbacks are supplied by the foreground playback owner and dispatched to its
 * coroutine scope before they mutate Compose state.
 */
internal class ExternalPlayerTransportCommands(
    private val requestPlay: () -> Unit,
    private val requestPause: () -> Unit,
    private val requestSeek: (Long) -> Unit
) {
    fun onPlay() = requestPlay()
    fun onPause() = requestPause()
    fun onSeekTo(positionMs: Long) = requestSeek(positionMs.coerceAtLeast(0L))
}
