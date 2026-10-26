package com.vaydelux.bousygram.core.media.diagnostics

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import com.vaydelux.bousygram.core.media.VideoFormatSupport
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine

data class ProbingResult(
    val recommendedEngine: SelectedPlayerEngine,
    val useTextureView: Boolean,
    val isHardwareAccelerated: Boolean,
    val reason: String
)

/**
 * Pre-playback capability prober.
 * Inspects device hardware MediaCodec capabilities and past verdicts
 * to predict and avert black-screen failures BEFORE starting playback.
 */
object CapabilityProber {
    private const val TAG = "CapabilityProber"

    // Cache hardware codec capabilities for process lifetime
    private val codecList by lazy {
        runCatching { MediaCodecList(MediaCodecList.ALL_CODECS) }.getOrNull()
    }

    fun probe(
        context: Context,
        mimeType: String?,
        fileName: String?,
        title: String?,
        is10Bit: Boolean = false,
        profileHint: Int = 0
    ): ProbingResult {
        val container = VideoFormatSupport.resolveContainer(fileName, mimeType, title.orEmpty())
        val resolvedMime = VideoFormatSupport.exoMimeType(mimeType, fileName, title.orEmpty())

        // 1. Check persistent verdict store first
        val verdictKey = EngineVerdictStore.buildKey(resolvedMime, container, profileHint, is10Bit)
        val savedVerdict = EngineVerdictStore.getVerdict(context, verdictKey)
        if (savedVerdict != null) {
            Log.i(TAG, "Using cached verdict for $verdictKey: ${savedVerdict.recommendedEngine} (reason: ${savedVerdict.reason})")
            return ProbingResult(
                recommendedEngine = savedVerdict.recommendedEngine,
                useTextureView = savedVerdict.useTextureView,
                isHardwareAccelerated = savedVerdict.recommendedEngine == SelectedPlayerEngine.ExoPlayer,
                reason = "Cached verdict: ${savedVerdict.reason}"
            )
        }

        // 2. Format-specific heuristic probing
        val normalizedTitle = "${title ?: ""} ${fileName ?: ""}".lowercase()

        // Case A: H.264 10-bit (Hi10P / anime encodes) - hardware decoders fail or produce black/corrupted frames
        val isHi10P = is10Bit && (resolvedMime?.contains("avc", ignoreCase = true) == true || resolvedMime?.contains("h264", ignoreCase = true) == true)
                || normalizedTitle.contains("hi10p") || normalizedTitle.contains("hi10") || (normalizedTitle.contains("10bit") && normalizedTitle.contains("x264"))
        if (isHi10P) {
            return ProbingResult(
                recommendedEngine = SelectedPlayerEngine.MPV,
                useTextureView = false,
                isHardwareAccelerated = false,
                reason = "H.264 10-bit (Hi10P) requires software decoding; hardware decoders reject profile"
            )
        }

        // Case B: Dolby Vision Profile 5 without fallback layer on non-DV certified TV
        val isDolbyVision = resolvedMime?.contains("dolby-vision", ignoreCase = true) == true || normalizedTitle.contains("dovi") || normalizedTitle.contains("dv")
        if (isDolbyVision && !hasHardwareDolbyVisionSupport()) {
            return ProbingResult(
                recommendedEngine = SelectedPlayerEngine.MPV,
                useTextureView = false,
                isHardwareAccelerated = true,
                reason = "Dolby Vision on non-DV panel requires MPV tone-mapping to BT.709"
            )
        }

        // Case C: HEVC 10-bit on budget device lacking Main10 hardware decoder
        val isHevc = resolvedMime?.contains("hevc", ignoreCase = true) == true || resolvedMime?.contains("h265", ignoreCase = true) == true
        if (isHevc && is10Bit && !hasHardwareHevcMain10Support()) {
            return ProbingResult(
                recommendedEngine = SelectedPlayerEngine.MPV,
                useTextureView = false,
                isHardwareAccelerated = false,
                reason = "Hardware HEVC decoder lacks Main10 profile support"
            )
        }

        // Case D: AV1 without hardware decoder on budget chipset
        val isAv1 = resolvedMime?.contains("av01", ignoreCase = true) == true || resolvedMime?.contains("av1", ignoreCase = true) == true
        if (isAv1 && !hasHardwareAv1Support()) {
            return ProbingResult(
                recommendedEngine = SelectedPlayerEngine.MPV,
                useTextureView = false,
                isHardwareAccelerated = false,
                reason = "AV1 hardware decoder absent; routing to MPV software decoder"
            )
        }

        // Default: ExoPlayer with zero-copy SurfaceView
        return ProbingResult(
            recommendedEngine = SelectedPlayerEngine.ExoPlayer,
            useTextureView = false,
            isHardwareAccelerated = true,
            reason = "Hardware decoder available for $resolvedMime"
        )
    }

    private fun hasHardwareDolbyVisionSupport(): Boolean {
        val list = codecList ?: return false
        return list.codecInfos.any { info ->
            !info.isEncoder && info.supportedTypes.any { it.contains("dolby-vision", ignoreCase = true) }
        }
    }

    private fun hasHardwareHevcMain10Support(): Boolean {
        val list = codecList ?: return true
        val hevcDecoders = list.codecInfos.filter { !infoIsSoftware(it) && it.supportedTypes.any { t -> t.contains("hevc", ignoreCase = true) } }
        if (hevcDecoders.isEmpty()) return false
        return hevcDecoders.any { info ->
            val caps = runCatching { info.getCapabilitiesForType("video/hevc") }.getOrNull() ?: return@any false
            caps.profileLevels?.any { it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 } == true
        }
    }

    private fun hasHardwareAv1Support(): Boolean {
        val list = codecList ?: return false
        return list.codecInfos.any { info ->
            !infoIsSoftware(info) && info.supportedTypes.any { it.contains("av01", ignoreCase = true) || it.contains("av1", ignoreCase = true) }
        }
    }

    private fun infoIsSoftware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isSoftwareOnly
        }
        val name = info.name.lowercase()
        return name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.startsWith("omx.ffmpeg.")
    }
}
