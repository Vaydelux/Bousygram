package com.vaydelux.bousygram.core.media

/**
 * Small, predictable libavfilter graphs. Returning null for AUTO_LEVEL on
 * ARM32 avoids the history-heavy dynaudnorm filter in constrained processes.
 * All graphs use a conservative limiter and do not modify source media bytes.
 */
internal fun smartVolumeMpvGraph(mode: SmartVolumeMode, is32BitProcess: Boolean): String? =
    when (mode) {
        SmartVolumeMode.OFF -> null
        SmartVolumeMode.NIGHT ->
            "acompressor=threshold=0.18:ratio=2.5:attack=15:release=240:makeup=1.0," +
                "alimiter=limit=0.92:level=false"
        SmartVolumeMode.DIALOGUE_BOOST ->
            "highpass=f=80,acompressor=threshold=0.14:ratio=3:attack=10:release=230:makeup=1.2," +
                "alimiter=limit=0.92:level=false"
        SmartVolumeMode.AUTO_LEVEL ->
            if (is32BitProcess) null else
                "dynaudnorm=f=150:g=5:p=0.88:m=2:s=4," +
                    "alimiter=limit=0.92:level=false"
    }
