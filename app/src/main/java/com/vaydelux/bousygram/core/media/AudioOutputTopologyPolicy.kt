package com.vaydelux.bousygram.core.media

/** Device identity is used transiently to detect topology changes; never logged. */
internal data class AudioOutputDeviceKey(val id: Int, val type: Int)

internal fun didAudioOutputTopologyChange(
    before: Set<AudioOutputDeviceKey>,
    after: Set<AudioOutputDeviceKey>
): Boolean = before != after

/** No change to playback state; only decide what to do with local sound effects. */
internal enum class AudioTopologyRecoveryAction { IGNORE, WAIT_FOR_OUTPUT, REAPPLY }

internal fun audioTopologyRecoveryAction(
    profile: AudioProcessingProfile,
    hasMedia: Boolean,
    isReady: Boolean,
    sessionId: Int
): AudioTopologyRecoveryAction {
    if (!hasMedia || (!profile.preset.isEnabled && profile.smartVolume == SmartVolumeMode.OFF)) {
        return AudioTopologyRecoveryAction.IGNORE
    }
    return if (isReady && sessionId > 0) AudioTopologyRecoveryAction.REAPPLY
    else AudioTopologyRecoveryAction.WAIT_FOR_OUTPUT
}
