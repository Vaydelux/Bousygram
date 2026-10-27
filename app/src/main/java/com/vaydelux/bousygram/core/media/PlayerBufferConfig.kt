package com.vaydelux.bousygram.core.media

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * Single source of truth for player buffer configurations across ExoPlayer and MPV engines.
 * Dynamically scales buffer depth based on device memory class and form-factor
 * (e.g. 32-bit low-RAM TV sticks vs standard 4K Android TVs vs phones).
 */
object PlayerBufferConfig {

    // --- ExoPlayer Constants (Standard / TV: ~30s min, ~120s max, 2.5s start, 5.0s rebuffer, 30s back buffer) ---
    const val EXO_DEFAULT_MIN_BUFFER_MS = 30_000
    const val EXO_DEFAULT_MAX_BUFFER_MS = 120_000
    const val EXO_DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2_500
    const val EXO_DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
    const val EXO_DEFAULT_BACK_BUFFER_MS = 30_000
    const val EXO_RETAIN_BACK_BUFFER_FROM_KEYFRAME = true

    // Direct aliases matching exact spec names
    const val EXO_MIN_BUFFER_MS = EXO_DEFAULT_MIN_BUFFER_MS
    const val EXO_MAX_BUFFER_MS = EXO_DEFAULT_MAX_BUFFER_MS
    const val EXO_BUFFER_FOR_PLAYBACK_MS = EXO_DEFAULT_BUFFER_FOR_PLAYBACK_MS
    const val EXO_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = EXO_DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
    const val EXO_BACK_BUFFER_MS = EXO_DEFAULT_BACK_BUFFER_MS

    // --- ExoPlayer Constants (Low-RAM / 32-bit Sticks: reduced runways to prevent OOM) ---
    const val EXO_LOW_RAM_MIN_BUFFER_MS = 15_000
    const val EXO_LOW_RAM_MAX_BUFFER_MS = 45_000
    const val EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_MS = 2_500
    const val EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5_000
    const val EXO_LOW_RAM_BACK_BUFFER_MS = 10_000

    // Higher-memory devices get a larger back buffer for cache-first rewinds without unbounded growth.
    const val EXO_HIGH_MEMORY_MIN_BUFFER_MS = 35_000
    const val EXO_HIGH_MEMORY_MAX_BUFFER_MS = 120_000
    const val EXO_HIGH_MEMORY_BACK_BUFFER_MS = 45_000
    const val HIGH_MEMORY_CLASS_MB = 512

    enum class ExoMemoryProfile { LowRam, Standard, HighMemory }

    // --- MPV Demuxer Constants ---
    const val MPV_CACHE_ENABLED = "yes"
    const val MPV_TV_DEMUXER_MAX_BYTES = 150L * 1024L * 1024L       // ~150 MB on TV
    const val MPV_PHONE_DEMUXER_MAX_BYTES = 64L * 1024L * 1024L     // ~64 MB on phones
    const val MPV_LOW_RAM_DEMUXER_MAX_BYTES = 32L * 1024L * 1024L   // ~32 MB on low-RAM sticks
    const val MPV_CELLULAR_DEMUXER_MAX_BYTES = 20L * 1024L * 1024L  // ~20 MB on cellular

    const val MPV_DEFAULT_READAHEAD_SECS = 60
    const val MPV_LOW_RAM_READAHEAD_SECS = 30

    const val MPV_TV_MAX_BACK_BYTES = 30L * 1024L * 1024L           // ~30 MB back buffer
    const val MPV_PHONE_MAX_BACK_BYTES = 15L * 1024L * 1024L        // ~15 MB back buffer
    const val MPV_LOW_RAM_MAX_BACK_BYTES = 5L * 1024L * 1024L       // ~5 MB back buffer

    const val ALLOCATOR_BLOCK_SIZE_BYTES = 128 * 1024

    data class ExoBufferConfig(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int,
        val backBufferMs: Int,
        val retainBackBufferFromKeyframe: Boolean,
        val isLowRam: Boolean,
        val profile: ExoMemoryProfile = if (isLowRam) ExoMemoryProfile.LowRam else ExoMemoryProfile.Standard,
        val description: String
    )

    data class MpvBufferConfig(
        val cacheEnabled: String,
        val demuxerMaxBytes: Long,
        val demuxerReadaheadSecs: Int,
        val demuxerMaxBackBytes: Long,
        val isLowRam: Boolean,
        val description: String
    )

    fun isLowRam(isLowRamDevice: Boolean, memoryClass: Int): Boolean =
        isLowRamDevice || memoryClass <= 192

    fun isLowRamDevice(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        isLowRam(
            isLowRamDevice = am?.isLowRamDevice == true,
            memoryClass = am?.memoryClass ?: 256
        )
    }.getOrDefault(false)

    fun isTv(context: Context): Boolean = runCatching {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
    }.getOrDefault(false)

    fun resolveExoConfig(isLowRam: Boolean): ExoBufferConfig {
        return if (isLowRam) {
            ExoBufferConfig(
                minBufferMs = EXO_LOW_RAM_MIN_BUFFER_MS,
                maxBufferMs = EXO_LOW_RAM_MAX_BUFFER_MS,
                bufferForPlaybackMs = EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_MS,
                bufferForPlaybackAfterRebufferMs = EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                backBufferMs = EXO_LOW_RAM_BACK_BUFFER_MS,
                retainBackBufferFromKeyframe = EXO_RETAIN_BACK_BUFFER_FROM_KEYFRAME,
                isLowRam = true,
                profile = ExoMemoryProfile.LowRam,
                description = "exo:low_ram(min=${EXO_LOW_RAM_MIN_BUFFER_MS}ms,max=${EXO_LOW_RAM_MAX_BUFFER_MS}ms,play=${EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_MS}ms,rebuf=${EXO_LOW_RAM_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS}ms,back=${EXO_LOW_RAM_BACK_BUFFER_MS}ms)"
            )
        } else {
            ExoBufferConfig(
                minBufferMs = EXO_DEFAULT_MIN_BUFFER_MS,
                maxBufferMs = EXO_DEFAULT_MAX_BUFFER_MS,
                bufferForPlaybackMs = EXO_DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                bufferForPlaybackAfterRebufferMs = EXO_DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                backBufferMs = EXO_DEFAULT_BACK_BUFFER_MS,
                retainBackBufferFromKeyframe = EXO_RETAIN_BACK_BUFFER_FROM_KEYFRAME,
                isLowRam = false,
                profile = ExoMemoryProfile.Standard,
                description = "exo:normal(min=${EXO_DEFAULT_MIN_BUFFER_MS}ms,max=${EXO_DEFAULT_MAX_BUFFER_MS}ms,play=${EXO_DEFAULT_BUFFER_FOR_PLAYBACK_MS}ms,rebuf=${EXO_DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS}ms,back=${EXO_DEFAULT_BACK_BUFFER_MS}ms)"
            )
        }
    }

    fun resolveExoConfig(isLowRamDevice: Boolean, memoryClass: Int): ExoBufferConfig {
        if (isLowRam(isLowRamDevice, memoryClass)) return resolveExoConfig(isLowRam = true)
        if (memoryClass < HIGH_MEMORY_CLASS_MB) return resolveExoConfig(isLowRam = false)
        return ExoBufferConfig(
            minBufferMs = EXO_HIGH_MEMORY_MIN_BUFFER_MS,
            maxBufferMs = EXO_HIGH_MEMORY_MAX_BUFFER_MS,
            bufferForPlaybackMs = EXO_DEFAULT_BUFFER_FOR_PLAYBACK_MS,
            bufferForPlaybackAfterRebufferMs = EXO_DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            backBufferMs = EXO_HIGH_MEMORY_BACK_BUFFER_MS,
            retainBackBufferFromKeyframe = EXO_RETAIN_BACK_BUFFER_FROM_KEYFRAME,
            isLowRam = false,
            profile = ExoMemoryProfile.HighMemory,
            description = "exo:high_memory(min=${EXO_HIGH_MEMORY_MIN_BUFFER_MS}ms,max=${EXO_HIGH_MEMORY_MAX_BUFFER_MS}ms,play=${EXO_DEFAULT_BUFFER_FOR_PLAYBACK_MS}ms,rebuf=${EXO_DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS}ms,back=${EXO_HIGH_MEMORY_BACK_BUFFER_MS}ms)"
        )
    }

    fun resolveExoConfig(context: Context): ExoBufferConfig = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        resolveExoConfig(
            isLowRamDevice = am?.isLowRamDevice == true,
            memoryClass = am?.memoryClass ?: 256
        )
    }.getOrElse { resolveExoConfig(isLowRam = false) }

    @OptIn(UnstableApi::class)
    fun buildLoadControl(config: ExoBufferConfig): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(true, ALLOCATOR_BLOCK_SIZE_BYTES))
            .setBufferDurationsMs(
                config.minBufferMs,
                config.maxBufferMs,
                config.bufferForPlaybackMs,
                config.bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(
                config.backBufferMs,
                config.retainBackBufferFromKeyframe
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    fun resolveMpvConfig(isLowRam: Boolean, isTv: Boolean, isCellular: Boolean = false): MpvBufferConfig {
        val (maxBytes, backBytes, readaheadSecs) = when {
            isCellular -> Triple(MPV_CELLULAR_DEMUXER_MAX_BYTES, MPV_LOW_RAM_MAX_BACK_BYTES, MPV_LOW_RAM_READAHEAD_SECS)
            isLowRam -> Triple(MPV_LOW_RAM_DEMUXER_MAX_BYTES, MPV_LOW_RAM_MAX_BACK_BYTES, MPV_LOW_RAM_READAHEAD_SECS)
            isTv -> Triple(MPV_TV_DEMUXER_MAX_BYTES, MPV_TV_MAX_BACK_BYTES, MPV_DEFAULT_READAHEAD_SECS)
            else -> Triple(MPV_PHONE_DEMUXER_MAX_BYTES, MPV_PHONE_MAX_BACK_BYTES, MPV_DEFAULT_READAHEAD_SECS)
        }

        val maxMb = maxBytes / (1024 * 1024)
        val backMb = backBytes / (1024 * 1024)
        val typeLabel = when {
            isCellular -> "cellular"
            isLowRam -> "low_ram"
            isTv -> "tv"
            else -> "phone"
        }

        return MpvBufferConfig(
            cacheEnabled = MPV_CACHE_ENABLED,
            demuxerMaxBytes = maxBytes,
            demuxerReadaheadSecs = readaheadSecs,
            demuxerMaxBackBytes = backBytes,
            isLowRam = isLowRam,
            description = "mpv:$typeLabel(cache=yes,max=${maxMb}MB,readahead=${readaheadSecs}s,back=${backMb}MB)"
        )
    }

    fun resolveMpvConfig(context: Context, isCellular: Boolean = false): MpvBufferConfig =
        resolveMpvConfig(
            isLowRam = isLowRamDevice(context),
            isTv = isTv(context),
            isCellular = isCellular
        )
}
