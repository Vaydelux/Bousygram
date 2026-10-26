package com.vaydelux.bousygram.core.observability

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.app.BousygramApp
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Immutable playback telemetry snapshot recorded once per playback session.
 * Captures startup latency, rebuffering metrics, player engine, decoders, and exit/failure state.
 */
data class PlaybackTelemetryRecord(
    val sessionId: String,
    val timeToFirstByteMs: Long,
    val timeToFirstFrameMs: Long,
    val rebufferCount: Int,
    val totalRebufferMs: Long,
    val engine: String, // "ExoPlayer" or "mpv"
    val videoDecoderName: String,
    val audioDecoderName: String,
    val container: String,
    val fileSize: Long,
    val failureReason: String? = null,
    val fallbackReason: String? = null,
    val isFullyCachedAtStart: Boolean = false,
    val timeToHeaderChunkMs: Long? = null,
    val timeToTailChunkMs: Long? = null,
    val averageThroughputBytesPerSec: Long? = null,
    val bufferConfig: String? = null,
    val cacheHitCount: Int = 0,
    val cacheMissCount: Int = 0,
    val reusedCacheBytes: Long = 0L,
    val seekCount: Int = 0,
    val averageSeekLatencyMs: Long? = null,
    val droppedFrameCount: Int = 0,
    val memoryPressure: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class PlaybackCacheTelemetry(
    val hitCount: Int,
    val missCount: Int,
    val reusedBytes: Long
)

data class PlaybackSeekTelemetry(
    val count: Int,
    val averageLatencyMs: Long?
)

/**
 * Storage abstraction for playback telemetry records to allow testing in JVM unit tests
 * without an Android Context or SharedPreferences runtime.
 */
interface PlaybackTelemetryStorage {
    fun loadSessions(): List<PlaybackTelemetryRecord>
    fun saveSessions(records: List<PlaybackTelemetryRecord>)
    fun clear()
}

/**
 * Android SharedPreferences persistent storage implementation.
 * Stores records as a JSON array string in private app storage.
 */
class PreferencesPlaybackTelemetryStorage(
    private val contextProvider: () -> Context? = { runCatching { BousygramApp.instance }.getOrNull() }
) : PlaybackTelemetryStorage {

    companion object {
        private const val PREFS_NAME = "playback_telemetry"
        private const val KEY_SESSIONS_JSON = "sessions_json"
    }

    override fun loadSessions(): List<PlaybackTelemetryRecord> {
        val context = contextProvider() ?: return emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SESSIONS_JSON, null).orEmpty()
        if (json.isBlank()) return emptyList()
        return PlaybackTelemetrySerializer.parseRecords(json)
    }

    override fun saveSessions(records: List<PlaybackTelemetryRecord>) {
        val context = contextProvider() ?: return
        val json = PlaybackTelemetrySerializer.recordsToJson(records)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SESSIONS_JSON, json).apply()
    }

    override fun clear() {
        val context = contextProvider() ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_SESSIONS_JSON).apply()
    }
}

/**
 * In-memory storage for unit tests or environments without Android SharedPreferences.
 */
class InMemoryPlaybackTelemetryStorage : PlaybackTelemetryStorage {
    private val saved = mutableListOf<PlaybackTelemetryRecord>()

    override fun loadSessions(): List<PlaybackTelemetryRecord> = synchronized(saved) {
        saved.toList()
    }

    override fun saveSessions(records: List<PlaybackTelemetryRecord>) {
        synchronized(saved) {
            saved.clear()
            saved.addAll(records)
        }
    }

    override fun clear() {
        synchronized(saved) {
            saved.clear()
        }
    }
}

/**
 * Pure-Kotlin JSON serializer and deserializer for [PlaybackTelemetryRecord].
 * Zero Android SDK dependencies, avoiding Stub! exceptions in JVM unit tests.
 */
object PlaybackTelemetrySerializer {

    fun escapeJson(value: String): String = buildString {
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code in 0..0x1F) {
                        append(String.format("\\u%04x", char.code))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }

    fun unescapeJson(value: String): String {
        val sb = java.lang.StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < value.length) {
                            val hex = value.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(c)
                        i++
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun recordToJson(record: PlaybackTelemetryRecord): String {
        val escapedReason = record.failureReason?.let { escapeJson(it) }
        val reasonField = if (escapedReason != null) "\"$escapedReason\"" else "null"
        val escapedFallback = record.fallbackReason?.let { escapeJson(it) }
        val fallbackField = if (escapedFallback != null) "\"$escapedFallback\"" else "null"
        val timeToHeaderField = record.timeToHeaderChunkMs?.toString() ?: "null"
        val timeToTailField = record.timeToTailChunkMs?.toString() ?: "null"
        val throughputField = record.averageThroughputBytesPerSec?.toString() ?: "null"
        val escapedBufferConfig = record.bufferConfig?.let { escapeJson(it) }
        val bufferConfigField = if (escapedBufferConfig != null) "\"$escapedBufferConfig\"" else "null"
        val escapedMemoryPressure = record.memoryPressure?.let { escapeJson(it) }
        val memoryPressureField = if (escapedMemoryPressure != null) "\"$escapedMemoryPressure\"" else "null"
        val seekLatencyField = record.averageSeekLatencyMs?.toString() ?: "null"
        return """{"sessionId":"${escapeJson(record.sessionId)}","timeToFirstByteMs":${record.timeToFirstByteMs},"timeToFirstFrameMs":${record.timeToFirstFrameMs},"rebufferCount":${record.rebufferCount},"totalRebufferMs":${record.totalRebufferMs},"engine":"${escapeJson(record.engine)}","videoDecoderName":"${escapeJson(record.videoDecoderName)}","audioDecoderName":"${escapeJson(record.audioDecoderName)}","container":"${escapeJson(record.container)}","fileSize":${record.fileSize},"failureReason":$reasonField,"fallbackReason":$fallbackField,"isFullyCachedAtStart":${record.isFullyCachedAtStart},"timeToHeaderChunkMs":$timeToHeaderField,"timeToTailChunkMs":$timeToTailField,"averageThroughputBytesPerSec":$throughputField,"bufferConfig":$bufferConfigField,"cacheHitCount":${record.cacheHitCount},"cacheMissCount":${record.cacheMissCount},"reusedCacheBytes":${record.reusedCacheBytes},"seekCount":${record.seekCount},"averageSeekLatencyMs":$seekLatencyField,"droppedFrameCount":${record.droppedFrameCount},"memoryPressure":$memoryPressureField,"timestampMs":${record.timestampMs}}"""
    }

    fun recordsToJson(records: List<PlaybackTelemetryRecord>): String =
        "[" + records.joinToString(",") { recordToJson(it) } + "]"

    fun parseRecord(json: String): PlaybackTelemetryRecord? {
        fun extractString(key: String): String? {
            val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"")
            return regex.find(json)?.groupValues?.get(1)?.let { unescapeJson(it) }
        }

        fun extractLong(key: String, default: Long = 0L): Long {
            val regex = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
            return regex.find(json)?.groupValues?.get(1)?.toLongOrNull() ?: default
        }

        fun extractInt(key: String, default: Int = 0): Int {
            val regex = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
            return regex.find(json)?.groupValues?.get(1)?.toIntOrNull() ?: default
        }

        fun extractNullableLong(key: String): Long? {
            if (Regex("\"$key\"\\s*:\\s*null").containsMatchIn(json)) return null
            val regex = Regex("\"$key\"\\s*:\\s*(-?\\d+)")
            return regex.find(json)?.groupValues?.get(1)?.toLongOrNull()
        }

        val sessionId = extractString("sessionId") ?: return null
        val engine = extractString("engine") ?: "Unknown"
        val videoDecoderName = extractString("videoDecoderName") ?: "Unknown"
        val audioDecoderName = extractString("audioDecoderName") ?: "Unknown"
        val container = extractString("container") ?: "unknown"
        val hasNullReason = Regex("\"failureReason\"\\s*:\\s*null").containsMatchIn(json)
        val failureReason = if (hasNullReason) null else extractString("failureReason")
        val hasNullFallback = Regex("\"fallbackReason\"\\s*:\\s*null").containsMatchIn(json)
        val fallbackReason = if (hasNullFallback) null else extractString("fallbackReason")
        val isFullyCachedAtStart = Regex("\"isFullyCachedAtStart\"\\s*:\\s*(true|false)")
            .find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false
        val timeToHeaderChunkMs = extractNullableLong("timeToHeaderChunkMs")
        val timeToTailChunkMs = extractNullableLong("timeToTailChunkMs")
        val averageThroughputBytesPerSec = extractNullableLong("averageThroughputBytesPerSec")
        val hasNullBufferConfig = Regex("\"bufferConfig\"\\s*:\\s*null").containsMatchIn(json)
        val bufferConfig = if (hasNullBufferConfig) null else extractString("bufferConfig")
        val hasNullMemoryPressure = Regex("\"memoryPressure\"\\s*:\\s*null").containsMatchIn(json)
        val memoryPressure = if (hasNullMemoryPressure) null else extractString("memoryPressure")

        return PlaybackTelemetryRecord(
            sessionId = sessionId,
            timeToFirstByteMs = extractLong("timeToFirstByteMs"),
            timeToFirstFrameMs = extractLong("timeToFirstFrameMs"),
            rebufferCount = extractInt("rebufferCount"),
            totalRebufferMs = extractLong("totalRebufferMs"),
            engine = engine,
            videoDecoderName = videoDecoderName,
            audioDecoderName = audioDecoderName,
            container = container,
            fileSize = extractLong("fileSize"),
            failureReason = failureReason,
            fallbackReason = fallbackReason,
            isFullyCachedAtStart = isFullyCachedAtStart,
            timeToHeaderChunkMs = timeToHeaderChunkMs,
            timeToTailChunkMs = timeToTailChunkMs,
            averageThroughputBytesPerSec = averageThroughputBytesPerSec,
            bufferConfig = bufferConfig,
            cacheHitCount = extractInt("cacheHitCount"),
            cacheMissCount = extractInt("cacheMissCount"),
            reusedCacheBytes = extractLong("reusedCacheBytes"),
            seekCount = extractInt("seekCount"),
            averageSeekLatencyMs = extractNullableLong("averageSeekLatencyMs"),
            droppedFrameCount = extractInt("droppedFrameCount"),
            memoryPressure = memoryPressure,
            timestampMs = extractLong("timestampMs", System.currentTimeMillis())
        )
    }

    fun parseRecords(json: String): List<PlaybackTelemetryRecord> {
        val list = mutableListOf<PlaybackTelemetryRecord>()
        var depth = 0
        var startIndex = -1
        var inQuotes = false
        var isEscaped = false

        for (i in json.indices) {
            val c = json[i]
            if (c == '\\' && !isEscaped) {
                isEscaped = true
                continue
            }
            if (c == '"' && !isEscaped) {
                inQuotes = !inQuotes
            }
            isEscaped = false

            if (!inQuotes) {
                if (c == '{') {
                    if (depth == 0) startIndex = i
                    depth++
                } else if (c == '}') {
                    depth--
                    if (depth == 0 && startIndex != -1) {
                        val objStr = json.substring(startIndex, i + 1)
                        val record = parseRecord(objStr)
                        if (record != null) list.add(record)
                        startIndex = -1
                    }
                }
            }
        }
        return list
    }
}

/**
 * Local playback telemetry recorder.
 * - Stores the last 50 playback sessions locally using a persistent FIFO ring buffer.
 * - Records TTFB, TTFF, rebuffer count, total rebuffer ms, engine, video/audio decoders,
 *   container, file size, and failure reason.
 * - Strictly local: no network upload.
 * - Integrated into diagnostics report and JSON export.
 */
object PlaybackTelemetryRecorder {
    private const val TAG = "PlaybackTelemetryRecorder"
    const val MAX_SESSIONS = 50

    private val firstByteTimestamps = ConcurrentHashMap<Int, Long>()
    private val fallbackReasons = ConcurrentHashMap<String, String>()
    private val fullyCachedAtStart = ConcurrentHashMap<Int, Boolean>()
    private val headerChunkTimes = ConcurrentHashMap<Int, Long>()
    private val tailChunkTimes = ConcurrentHashMap<Int, Long>()
    private val throughputSamples = ConcurrentHashMap<Int, Long>()
    private val cacheHits = ConcurrentHashMap<Int, AtomicLong>()
    private val cacheMisses = ConcurrentHashMap<Int, AtomicLong>()
    private val reusedCacheBytes = ConcurrentHashMap<Int, AtomicLong>()
    private val seekCounts = ConcurrentHashMap<Int, AtomicLong>()
    private val totalSeekLatencyMs = ConcurrentHashMap<Int, AtomicLong>()
    private val sessions = mutableListOf<PlaybackTelemetryRecord>()
    private var isInitialized = false

    @Volatile
    private var storage: PlaybackTelemetryStorage = PreferencesPlaybackTelemetryStorage()

    @Synchronized
    fun setStorageForTesting(testStorage: PlaybackTelemetryStorage) {
        storage = testStorage
        sessions.clear()
        sessions.addAll(testStorage.loadSessions())
        isInitialized = true
    }

    @Synchronized
    private fun ensureLoaded() {
        if (!isInitialized) {
            runCatching {
                val loaded = storage.loadSessions()
                sessions.clear()
                sessions.addAll(loaded)
            }.onFailure { e ->
                Log.e(TAG, "Failed to load telemetry sessions from storage", e)
            }
            isInitialized = true
        }
    }

    /**
     * Records a completed or failed playback session.
     * Enforces the 50-session FIFO cap and persists changes locally.
     */
    @Synchronized
    fun record(record: PlaybackTelemetryRecord) {
        ensureLoaded()
        sessions.add(record)
        while (sessions.size > MAX_SESSIONS) {
            sessions.removeAt(0)
        }
        runCatching {
            storage.saveSessions(sessions)
        }.onFailure { e ->
            Log.e(TAG, "Failed to persist playback telemetry session", e)
        }
        Log.i(TAG, "Recorded telemetry: session=${record.sessionId} engine=${record.engine} buf=${record.bufferConfig} container=${record.container} TTFB=${record.timeToFirstByteMs}ms TTFF=${record.timeToFirstFrameMs}ms rebuffers=${record.rebufferCount} (${record.totalRebufferMs}ms) cachedAtStart=${record.isFullyCachedAtStart} header=${record.timeToHeaderChunkMs}ms tail=${record.timeToTailChunkMs}ms throughput=${record.averageThroughputBytesPerSec}Bps failure=${record.failureReason != null}")
    }

    /**
     * Returns a snapshot list of stored playback sessions (up to 50), in chronological order.
     */
    @Synchronized
    fun getRecords(): List<PlaybackTelemetryRecord> {
        ensureLoaded()
        return sessions.toList()
    }

    /**
     * Clears all in-memory and persisted playback sessions.
     */
    @Synchronized
    fun clear() {
        sessions.clear()
        firstByteTimestamps.clear()
        fallbackReasons.clear()
        fullyCachedAtStart.clear()
        headerChunkTimes.clear()
        tailChunkTimes.clear()
        throughputSamples.clear()
        cacheHits.clear()
        cacheMisses.clear()
        reusedCacheBytes.clear()
        seekCounts.clear()
        totalSeekLatencyMs.clear()
        runCatching { storage.clear() }
        isInitialized = true
    }

    /**
     * Records the engine fallback reason for a given playback session.
     */
    fun recordFallback(sessionId: String, reason: String) {
        fallbackReasons[sessionId] = reason
        Log.i(TAG, "Recorded engine fallback for session=$sessionId: $reason")
    }

    fun getFallbackReason(sessionId: String): String? = fallbackReasons[sessionId]

    fun clearFallback(sessionId: String) {
        fallbackReasons.remove(sessionId)
    }

    fun recordFullyCachedAtStart(fileId: Int, isCached: Boolean) {
        if (fileId > 0) fullyCachedAtStart[fileId] = isCached
    }

    fun consumeFullyCachedAtStart(fileId: Int): Boolean =
        if (fileId > 0) fullyCachedAtStart.remove(fileId) ?: false else false

    fun recordHeaderChunkTime(fileId: Int, durationMs: Long) {
        if (fileId > 0) headerChunkTimes[fileId] = durationMs
    }

    fun consumeHeaderChunkTime(fileId: Int): Long? =
        if (fileId > 0) headerChunkTimes.remove(fileId) else null

    fun peekHeaderChunkTime(fileId: Int): Long? =
        if (fileId > 0) headerChunkTimes[fileId] else null

    fun recordTailChunkTime(fileId: Int, durationMs: Long) {
        if (fileId > 0) tailChunkTimes[fileId] = durationMs
    }

    fun consumeTailChunkTime(fileId: Int): Long? =
        if (fileId > 0) tailChunkTimes.remove(fileId) else null

    fun recordThroughput(fileId: Int, throughputBytesPerSec: Long) {
        if (fileId > 0 && throughputBytesPerSec > 0L) {
            throughputSamples[fileId] = throughputBytesPerSec
        }
    }

    fun consumeThroughput(fileId: Int): Long? =
        if (fileId > 0) throughputSamples.remove(fileId) else null

    fun recordCacheHit(fileId: Int, reusedBytes: Int) {
        if (fileId <= 0) return
        cacheHits.computeIfAbsent(fileId) { AtomicLong() }.incrementAndGet()
        if (reusedBytes > 0) {
            this.reusedCacheBytes.computeIfAbsent(fileId) { AtomicLong() }.addAndGet(reusedBytes.toLong())
        }
    }

    fun recordCacheMiss(fileId: Int) {
        if (fileId > 0) cacheMisses.computeIfAbsent(fileId) { AtomicLong() }.incrementAndGet()
    }

    fun consumeCacheTelemetry(fileId: Int): PlaybackCacheTelemetry {
        if (fileId <= 0) return PlaybackCacheTelemetry(0, 0, 0L)
        return PlaybackCacheTelemetry(
            hitCount = (cacheHits.remove(fileId)?.get() ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            missCount = (cacheMisses.remove(fileId)?.get() ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            reusedBytes = reusedCacheBytes.remove(fileId)?.get() ?: 0L
        )
    }

    fun recordSeekLatency(fileId: Int, latencyMs: Long) {
        if (fileId <= 0 || latencyMs < 0L) return
        seekCounts.computeIfAbsent(fileId) { AtomicLong() }.incrementAndGet()
        totalSeekLatencyMs.computeIfAbsent(fileId) { AtomicLong() }.addAndGet(latencyMs)
    }

    fun consumeSeekTelemetry(fileId: Int): PlaybackSeekTelemetry {
        if (fileId <= 0) return PlaybackSeekTelemetry(0, null)
        val count = seekCounts.remove(fileId)?.get() ?: 0L
        val total = totalSeekLatencyMs.remove(fileId)?.get() ?: 0L
        return PlaybackSeekTelemetry(
            count = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            averageLatencyMs = if (count > 0L) total / count else null
        )
    }

    /**
     * Called by LocalStreamingServer or TelegramChunkScheduler when the first byte of [fileId] is delivered.
     */
    fun notifyFirstByte(fileId: Int) {
        if (fileId > 0 && !firstByteTimestamps.containsKey(fileId)) {
            firstByteTimestamps[fileId] = System.currentTimeMillis()
        }
    }

    fun hasFirstByte(fileId: Int): Boolean = fileId > 0 && firstByteTimestamps.containsKey(fileId)

    fun clearFirstByte(fileId: Int) {
        if (fileId > 0) {
            firstByteTimestamps.remove(fileId)
            fullyCachedAtStart.remove(fileId)
            headerChunkTimes.remove(fileId)
            tailChunkTimes.remove(fileId)
            throughputSamples.remove(fileId)
            cacheHits.remove(fileId)
            cacheMisses.remove(fileId)
            reusedCacheBytes.remove(fileId)
            seekCounts.remove(fileId)
            totalSeekLatencyMs.remove(fileId)
        }
    }

    /**
     * Calculates TTFB for [fileId] relative to [startMs], or 0 if no byte was registered yet.
     */
    fun consumeFirstByteElapsed(fileId: Int, startMs: Long): Long {
        val firstByteTime = firstByteTimestamps.remove(fileId) ?: return 0L
        return if (startMs > 0L) (firstByteTime - startMs).coerceAtLeast(1L) else 0L
    }

    /**
     * One-line summary suitable for inline diagnostic strings.
     */
    @Synchronized
    fun summary(): String {
        ensureLoaded()
        if (sessions.isEmpty()) return "no playback sessions"
        val count = sessions.size
        val avgTtfb = sessions.map { it.timeToFirstByteMs }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average().toLong() else 0L }
        val avgTtff = sessions.map { it.timeToFirstFrameMs }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average().toLong() else 0L }
        val rebuffers = sessions.sumOf { it.rebufferCount }
        val errors = sessions.count { it.failureReason != null }
        return "count=$count, avgTTFB=${avgTtfb}ms, avgTTFF=${avgTtff}ms, rebuffers=$rebuffers, errors=$errors"
    }

    /**
     * Formatted multi-line diagnostic report of the last 50 sessions for clipboard or details view.
     */
    @Synchronized
    fun exportSummary(): String {
        ensureLoaded()
        if (sessions.isEmpty()) return "No playback telemetry recorded yet."
        val sb = StringBuilder()
        sb.append("Local Playback Telemetry (${sessions.size} sessions recorded, max $MAX_SESSIONS):\n")
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        sessions.reversed().forEachIndexed { index, r ->
            val dateStr = runCatching { dateFmt.format(java.util.Date(r.timestampMs)) }.getOrDefault("")
            val sizeMb = if (r.fileSize > 0) "${r.fileSize / (1024 * 1024)}MB" else "unknown"
            val throughputStr = r.averageThroughputBytesPerSec?.let { "${it / 1024}KB/s" } ?: "unknown"
            sb.append("#${index + 1} [$dateStr] [${r.engine}] ${r.container.uppercase()} ($sizeMb) ")
            sb.append("TTFB=${r.timeToFirstByteMs}ms TTFF=${r.timeToFirstFrameMs}ms ")
            sb.append("rebuffers=${r.rebufferCount} (${r.totalRebufferMs}ms) ")
            sb.append("cached=${r.isFullyCachedAtStart} hits=${r.cacheHitCount} misses=${r.cacheMissCount} reused=${r.reusedCacheBytes}B ")
            sb.append("seeks=${r.seekCount} seekAvg=${r.averageSeekLatencyMs ?: -1}ms dropped=${r.droppedFrameCount} memory=${r.memoryPressure ?: "unknown"} ")
            sb.append("header=${r.timeToHeaderChunkMs ?: -1}ms tail=${r.timeToTailChunkMs ?: -1}ms tp=$throughputStr ")
            sb.append("vDec=${r.videoDecoderName} aDec=${r.audioDecoderName}")
            if (r.bufferConfig != null) {
                sb.append(" buf=[${r.bufferConfig}]")
            }
            if (r.failureReason != null) {
                sb.append(" [FAILURE: ${r.failureReason}]")
            }
            if (r.fallbackReason != null) {
                sb.append(" [FALLBACK: ${r.fallbackReason}]")
            }
            sb.append("\n")
        }
        return sb.toString().trimEnd()
    }

    /**
     * JSON formatted export containing summary metrics and array of the last 50 sessions.
     */
    @Synchronized
    fun exportJson(): String {
        ensureLoaded()
        val records = sessions.toList()
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"sessionCount\": ${records.size},\n")
        sb.append("  \"maxRingBufferSize\": $MAX_SESSIONS,\n")
        val avgTtfb = if (records.isNotEmpty()) records.map { it.timeToFirstByteMs }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average() else 0.0 } else 0.0
        val avgTtff = if (records.isNotEmpty()) records.map { it.timeToFirstFrameMs }.filter { it > 0 }.let { if (it.isNotEmpty()) it.average() else 0.0 } else 0.0
        val totalRebuffers = records.sumOf { it.rebufferCount }
        val totalRebufferMs = records.sumOf { it.totalRebufferMs }
        val failureCount = records.count { it.failureReason != null }

        sb.append("  \"summary\": {\n")
        sb.append("    \"avgTimeToFirstByteMs\": ${String.format(java.util.Locale.US, "%.1f", avgTtfb)},\n")
        sb.append("    \"avgTimeToFirstFrameMs\": ${String.format(java.util.Locale.US, "%.1f", avgTtff)},\n")
        sb.append("    \"totalRebuffers\": $totalRebuffers,\n")
        sb.append("    \"totalRebufferMs\": $totalRebufferMs,\n")
        sb.append("    \"failureCount\": $failureCount\n")
        sb.append("  },\n")

        sb.append("  \"sessions\": [\n")
        for (i in records.indices) {
            val r = records[i]
            val escReason = r.failureReason?.let { PlaybackTelemetrySerializer.escapeJson(it) }
            val reasonStr = if (escReason != null) "\"$escReason\"" else "null"
            val escFallback = r.fallbackReason?.let { PlaybackTelemetrySerializer.escapeJson(it) }
            val fallbackStr = if (escFallback != null) "\"$escFallback\"" else "null"
            val escBufferConfig = r.bufferConfig?.let { PlaybackTelemetrySerializer.escapeJson(it) }
            val bufferConfigStr = if (escBufferConfig != null) "\"$escBufferConfig\"" else "null"
            val headerField = r.timeToHeaderChunkMs?.toString() ?: "null"
            val tailField = r.timeToTailChunkMs?.toString() ?: "null"
            val throughputField = r.averageThroughputBytesPerSec?.toString() ?: "null"
            val seekLatencyField = r.averageSeekLatencyMs?.toString() ?: "null"
            val memoryPressureField = r.memoryPressure?.let { "\"${PlaybackTelemetrySerializer.escapeJson(it)}\"" } ?: "null"
            sb.append("    {\n")
            sb.append("      \"sessionId\": \"${PlaybackTelemetrySerializer.escapeJson(r.sessionId)}\",\n")
            sb.append("      \"timeToFirstByteMs\": ${r.timeToFirstByteMs},\n")
            sb.append("      \"timeToFirstFrameMs\": ${r.timeToFirstFrameMs},\n")
            sb.append("      \"rebufferCount\": ${r.rebufferCount},\n")
            sb.append("      \"totalRebufferMs\": ${r.totalRebufferMs},\n")
            sb.append("      \"engine\": \"${PlaybackTelemetrySerializer.escapeJson(r.engine)}\",\n")
            sb.append("      \"videoDecoderName\": \"${PlaybackTelemetrySerializer.escapeJson(r.videoDecoderName)}\",\n")
            sb.append("      \"audioDecoderName\": \"${PlaybackTelemetrySerializer.escapeJson(r.audioDecoderName)}\",\n")
            sb.append("      \"container\": \"${PlaybackTelemetrySerializer.escapeJson(r.container)}\",\n")
            sb.append("      \"fileSize\": ${r.fileSize},\n")
            sb.append("      \"failureReason\": $reasonStr,\n")
            sb.append("      \"fallbackReason\": $fallbackStr,\n")
            sb.append("      \"isFullyCachedAtStart\": ${r.isFullyCachedAtStart},\n")
            sb.append("      \"timeToHeaderChunkMs\": $headerField,\n")
            sb.append("      \"timeToTailChunkMs\": $tailField,\n")
            sb.append("      \"averageThroughputBytesPerSec\": $throughputField,\n")
            sb.append("      \"bufferConfig\": $bufferConfigStr,\n")
            sb.append("      \"cacheHitCount\": ${r.cacheHitCount},\n")
            sb.append("      \"cacheMissCount\": ${r.cacheMissCount},\n")
            sb.append("      \"reusedCacheBytes\": ${r.reusedCacheBytes},\n")
            sb.append("      \"seekCount\": ${r.seekCount},\n")
            sb.append("      \"averageSeekLatencyMs\": $seekLatencyField,\n")
            sb.append("      \"droppedFrameCount\": ${r.droppedFrameCount},\n")
            sb.append("      \"memoryPressure\": $memoryPressureField,\n")
            sb.append("      \"timestampMs\": ${r.timestampMs}\n")
            sb.append("    }${if (i < records.lastIndex) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }
}
