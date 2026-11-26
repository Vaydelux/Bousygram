package com.vaydelux.bousygram.core.cast

/** A single mode eliminates conflicting checkbox combinations. Diagnostics
 * are explicitly selected, never the default user media route. */
internal enum class RollingLanMediaMode(val label: String, val needsFile: Boolean) {
    DIRECT_VIDEO("Local video (MP4, MKV, TS)", true),
    TELEGRAM("Telegram player handoff", false),
    FRAGMENTED_MP4("Fragmented MP4 diagnostic", true),
    SYNTHETIC("Synthetic playback diagnostic", true),
    CONNECTIVITY("Connectivity diagnostic", false);

    val live: Boolean get() = this == DIRECT_VIDEO || this == FRAGMENTED_MP4 || this == SYNTHETIC
    val diagnostic: Boolean get() = this == FRAGMENTED_MP4 || this == SYNTHETIC || this == CONNECTIVITY
    fun canSend(invitation: Boolean, selectedFile: Boolean, selectedReference: Boolean): Boolean =
        invitation && (!needsFile || selectedFile) && (this != TELEGRAM || selectedReference)
    fun canReceive(selectedReference: Boolean): Boolean = !selectedReference
}
