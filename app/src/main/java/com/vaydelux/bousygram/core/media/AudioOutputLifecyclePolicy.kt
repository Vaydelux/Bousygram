package com.vaydelux.bousygram.core.media

/**
 * Requested audio settings survive player.stop(), but native effect instances do
 * not. The UI must not advertise Applied when there is no audible media output.
 */
internal fun audioEffectStatusWithoutOutput(enabled: Boolean): AudioEffectStatus =
    if (enabled) AudioEffectStatus.PENDING else AudioEffectStatus.BYPASSED

/**
 * Retry only pending effects on the next ready audio output. Unsupported effects
 * are not repeatedly recreated on each READY event (which could cause glitches).
 */
internal fun shouldRetryPendingAudioEffectsOnReady(
    profile: AudioProcessingProfile,
    eqStatus: AudioEffectStatus,
    volumeStatus: AudioEffectStatus,
    sessionId: Int
): Boolean =
    sessionId > 0 && (
        (profile.preset.isEnabled && eqStatus == AudioEffectStatus.PENDING) ||
            (profile.smartVolume != SmartVolumeMode.OFF &&
                volumeStatus == AudioEffectStatus.PENDING)
    )
