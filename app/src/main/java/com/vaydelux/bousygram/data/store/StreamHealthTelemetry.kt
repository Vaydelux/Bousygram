package com.vaydelux.bousygram.data.store

import android.content.Context
import android.os.Build
import android.util.Log
import com.vaydelux.bousygram.app.BousygramApp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Health telemetry for a single playback streaming session.
 */
data class SessionHealthRecord(
    val sessionId: String,
    val fileId: Int,
    val ttfbMs: Long,
    val stallCount: Int,
    val totalPlayTimeMs: Long,
    val totalRebufferTimeMs: Long,
    val rebufferRatio: Float,
    val networkType: String,
    val decoderName: String,
    val engine: String,
    /** Buffer configuration the engine used for this session (see PlayerBufferConfig descriptions). */
    val bufferConfig: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Ring-buffer telemetry recorder for streaming health and automated engine fallback.
 * - Stores up to 50 recent streaming sessions
 * - Exports formatted JSON for diagnostics
 * - Triggers auto-selection of ExoPlayer on two consecutive MPV init failures
 */
object StreamHealthTelemetry {
    private const val TAG = "StreamHealthTelemetry"
    private const val MAX_RING_BUFFER_SIZE = 50
    private const val PREFS_NAME = "stream_health_telemetry"
    private const val KEY_CONSECUTIVE_MPV_FAILURES = "consecutive_mpv_failures"

    private val ringBuffer = ConcurrentLinkedQueue<SessionHealthRecord>()

    private val prefs by lazy {
        BousygramApp.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun recordSession(record: SessionHealthRecord) {
        ringBuffer.add(record)
        while (ringBuffer.size > MAX_RING_BUFFER_SIZE) {
            ringBuffer.poll()
        }
        Log.i(TAG, "Recorded session telemetry: ${record.sessionId}, ttfb=${record.ttfbMs}ms, stalls=${record.stallCount}, rebufferRatio=${"%.2f".format(record.rebufferRatio)}, buf=${record.bufferConfig}")
    }

    fun getRecords(): List<SessionHealthRecord> = ringBuffer.toList()

    fun exportJson(): String {
        val root = JSONObject()
        val deviceObj = JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("device", Build.DEVICE)
            put("sdkInt", Build.VERSION.SDK_INT)
            put("fingerprint", Build.FINGERPRINT)
        }
        root.put("device", deviceObj)

        val records = getRecords()
        root.put("sessionCount", records.size)

        val avgTtfb = if (records.isNotEmpty()) records.map { it.ttfbMs }.filter { it > 0 }.average() else 0.0
        val avgRebuffer = if (records.isNotEmpty()) records.map { it.rebufferRatio.toDouble() }.average() else 0.0
        val totalStalls = records.sumOf { it.stallCount }

        val summaryObj = JSONObject().apply {
            put("avgTtfbMs", avgTtfb)
            put("avgRebufferRatio", avgRebuffer)
            put("totalStalls", totalStalls)
            put("activeEngine", EnterprisePreferences.selectedPlayerEngine().name)
        }
        root.put("summary", summaryObj)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sessionsArray = JSONArray()
        for (item in records) {
            val sObj = JSONObject().apply {
                put("sessionId", item.sessionId)
                put("fileId", item.fileId)
                put("ttfbMs", item.ttfbMs)
                put("stallCount", item.stallCount)
                put("totalPlayTimeMs", item.totalPlayTimeMs)
                put("totalRebufferTimeMs", item.totalRebufferTimeMs)
                put("rebufferRatio", item.rebufferRatio)
                put("networkType", item.networkType)
                put("decoderName", item.decoderName)
                put("engine", item.engine)
                put("bufferConfig", item.bufferConfig ?: JSONObject.NULL)
                put("time", dateFormat.format(Date(item.timestamp)))
            }
            sessionsArray.put(sObj)
        }
        root.put("sessions", sessionsArray)

        return root.toString(2)
    }

    /**
     * Records an MPV initialization failure. If two consecutive failures occur on this device,
     * automatically switches preferred engine to ExoPlayer.
     */
    fun recordMpvInitFailure(): Boolean {
        val current = prefs.getInt(KEY_CONSECUTIVE_MPV_FAILURES, 0) + 1
        prefs.edit().putInt(KEY_CONSECUTIVE_MPV_FAILURES, current).apply()
        Log.w(TAG, "Recorded consecutive MPV init failure ($current/2) on ${Build.MANUFACTURER} ${Build.MODEL}")

        if (current >= 2) {
            Log.e(TAG, "Triggering automatic fallback to ExoPlayer after $current consecutive MPV init failures")
            EnterprisePreferences.setSelectedPlayerEngine(SelectedPlayerEngine.ExoPlayer)
            PlaybackDiagnostics.recordStreamError("Auto-selected ExoPlayer due to $current consecutive libmpv init failures on ${Build.MODEL}")
            return true
        }
        return false
    }

    /**
     * Resets consecutive failure counter on successful first frame rendering.
     */
    fun recordMpvInitSuccess() {
        val current = prefs.getInt(KEY_CONSECUTIVE_MPV_FAILURES, 0)
        if (current > 0) {
            prefs.edit().putInt(KEY_CONSECUTIVE_MPV_FAILURES, 0).apply()
            Log.i(TAG, "Reset consecutive MPV init failures to 0")
        }
    }
}
