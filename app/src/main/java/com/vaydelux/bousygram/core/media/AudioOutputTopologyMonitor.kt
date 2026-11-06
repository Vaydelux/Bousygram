package com.vaydelux.bousygram.core.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Only watches connected output-device topology. This is NOT a routed-device
 * observer and must never be described as proof of audible output or DSP.
 * The callback is dispatched on the owning Media3 application Looper.
 */
internal class AudioOutputTopologyMonitor(
    context: Context,
    applicationLooper: Looper,
    private val onChange: () -> Unit
) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val handler = Handler(applicationLooper)
    private var registered = false
    private var lastOutputs: Set<AudioOutputDeviceKey>? = null

    private fun snapshot(): Set<AudioOutputDeviceKey>? = runCatching {
        manager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.mapTo(mutableSetOf()) {
            AudioOutputDeviceKey(it.id, it.type)
        }
    }.getOrNull()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    fun start(): Boolean {
        if (registered) return true
        val audio = manager ?: return false
        lastOutputs = snapshot() ?: return false
        return runCatching {
            audio.registerAudioDeviceCallback(callback, handler)
            registered = true
            true
        }.getOrElse { error ->
            Log.w("BousyAudioEq", "Audio output topology watcher unavailable", error)
            lastOutputs = null
            false
        }
    }

    private fun refresh() {
        if (!registered) return
        val next = snapshot() ?: return
        val previous = lastOutputs ?: return
        if (!didAudioOutputTopologyChange(previous, next)) return
        lastOutputs = next
        onChange()
    }

    fun close() {
        if (!registered) return
        registered = false
        runCatching { manager?.unregisterAudioDeviceCallback(callback) }
            .onFailure { Log.w("BousyAudioEq", "Unable to unregister audio topology watcher", it) }
        lastOutputs = null
    }
}
