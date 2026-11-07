package com.vaydelux.bousygram.presentation.extras

/** Only for native playback launched from the Extras WebBridge. */
internal enum class ExtrasNativeEngine(val label: String) {
    MEDIA3("Media3"),
    LIBVLC("LibVLC")
}
