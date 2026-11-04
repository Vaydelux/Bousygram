package com.vaydelux.bousygram.core.media

/** Native buffering notifications cannot override user Pause or active playback. */
internal fun libVlcBufferState(userWantsPlay: Boolean, nativePlaying: Boolean): EnginePlaybackState =
    if (nativePlaying || !userWantsPlay) EnginePlaybackState.Ready
    else EnginePlaybackState.Buffering
