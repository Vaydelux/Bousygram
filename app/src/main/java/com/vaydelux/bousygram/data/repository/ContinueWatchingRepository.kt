package com.vaydelux.bousygram.data.repository

import android.content.Context
import android.util.Log
import com.vaydelux.bousygram.app.BousygramApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Data model for an item in the Continue Watching rail.
 */
data class ContinueWatchingEntry(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val thumbnailPath: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Persists and provides access to the Continue Watching rail.
 * - Stores (chatId, messageId, fileId, title, thumbnailPath, positionMs, durationMs, updatedAt, completed)
 * - Exposes top 12 unfinished items ordered by updatedAt DESC
 * - Automatically evicts completed entries older than 30 days
 */
object ContinueWatchingRepository {
    private const val TAG = "ContinueWatchingRepo"
    private const val FILE_NAME = "continue_watching.json"
    private const val MAX_RAIL_ITEMS = 12
    private const val COMPLETED_THRESHOLD_RATIO = 0.90f
    private const val MIN_RESUME_MS = 5_000L
    private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, ContinueWatchingEntry>()

    private val _railItems = MutableStateFlow<List<ContinueWatchingEntry>>(emptyList())
    val railItems: StateFlow<List<ContinueWatchingEntry>> = _railItems.asStateFlow()

    init {
        scope.launch {
            loadFromDisk()
            evictExpiredLocked()
            updateRailFlowLocked()
        }
    }

    private fun key(chatId: Long, messageId: Long): String = "$chatId:$messageId"

    fun saveProgress(
        chatId: Long,
        messageId: Long,
        fileId: Int,
        title: String,
        thumbnailPath: String? = null,
        positionMs: Long,
        durationMs: Long
    ) {
        if (chatId == 0L || messageId == 0L) return

        val isCompleted = durationMs > 0L && positionMs >= (durationMs * COMPLETED_THRESHOLD_RATIO).toLong()
        val entryKey = key(chatId, messageId)

        scope.launch {
            mutex.withLock {
                val existing = entries[entryKey]
                val updated = ContinueWatchingEntry(
                    chatId = chatId,
                    messageId = messageId,
                    fileId = if (fileId > 0) fileId else (existing?.fileId ?: 0),
                    title = title.ifBlank { existing?.title ?: "Video" },
                    thumbnailPath = thumbnailPath ?: existing?.thumbnailPath,
                    positionMs = positionMs,
                    durationMs = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L),
                    updatedAt = System.currentTimeMillis(),
                    completed = isCompleted
                )
                entries[entryKey] = updated
                evictExpiredLocked()
                saveToDiskLocked()
                updateRailFlowLocked()
            }
        }
    }

    fun markCompleted(chatId: Long, messageId: Long) {
        val entryKey = key(chatId, messageId)
        scope.launch {
            mutex.withLock {
                val existing = entries[entryKey] ?: return@withLock
                entries[entryKey] = existing.copy(
                    completed = true,
                    updatedAt = System.currentTimeMillis()
                )
                saveToDiskLocked()
                updateRailFlowLocked()
            }
        }
    }

    fun removeEntry(chatId: Long, messageId: Long) {
        val entryKey = key(chatId, messageId)
        scope.launch {
            mutex.withLock {
                if (entries.remove(entryKey) != null) {
                    saveToDiskLocked()
                    updateRailFlowLocked()
                }
            }
        }
    }

    fun getEntry(chatId: Long, messageId: Long): ContinueWatchingEntry? {
        val entryKey = key(chatId, messageId)
        return synchronized(entries) { entries[entryKey] }
    }

    fun isWatched(chatId: Long, messageId: Long, durationMs: Long): Boolean {
        val entry = getEntry(chatId, messageId) ?: return false
        if (entry.completed) return true
        if (durationMs > 0L && entry.positionMs >= (durationMs * COMPLETED_THRESHOLD_RATIO).toLong()) {
            return true
        }
        return false
    }

    fun isWatched(fileId: Int): Boolean {
        if (fileId <= 0) return false
        val entry = synchronized(entries) { entries.values.firstOrNull { it.fileId == fileId } } ?: return false
        if (entry.completed) return true
        if (entry.durationMs > 0L && entry.positionMs >= (entry.durationMs * COMPLETED_THRESHOLD_RATIO).toLong()) {
            return true
        }
        return false
    }

    /** Returns true if this fileId has an active, unfinished resume point in Continue Watching. */
    fun isContinueWatching(fileId: Int): Boolean {
        if (fileId <= 0) return false
        return synchronized(entries) {
            entries.values.any { entry ->
                entry.fileId == fileId &&
                    !entry.completed &&
                    entry.positionMs >= MIN_RESUME_MS &&
                    (entry.durationMs <= 0L || entry.positionMs < (entry.durationMs * COMPLETED_THRESHOLD_RATIO).toLong())
            }
        }
    }

    /** Returns set of fileIds currently active in Continue Watching. */
    fun getContinueWatchingFileIds(): Set<Int> {
        return synchronized(entries) {
            entries.values
                .filter { entry ->
                    !entry.completed &&
                        entry.positionMs >= MIN_RESUME_MS &&
                        (entry.durationMs <= 0L || entry.positionMs < (entry.durationMs * COMPLETED_THRESHOLD_RATIO).toLong())
                }
                .map { it.fileId }
                .filter { it > 0 }
                .toSet()
        }
    }

    private fun evictExpiredLocked() {
        val now = System.currentTimeMillis()
        val iterator = entries.entries.iterator()
        var evicted = 0
        while (iterator.hasNext()) {
            val (_, item) = iterator.next()
            if (item.completed && (now - item.updatedAt) > THIRTY_DAYS_MS) {
                iterator.remove()
                evicted++
            }
        }
        if (evicted > 0) {
            Log.i(TAG, "Evicted $evicted completed continue-watching items older than 30 days")
        }
    }

    private fun updateRailFlowLocked() {
        val unfinished = entries.values
            .filter { entry ->
                !entry.completed &&
                    entry.positionMs >= MIN_RESUME_MS &&
                    (entry.durationMs <= 0L || entry.positionMs < (entry.durationMs * COMPLETED_THRESHOLD_RATIO).toLong())
            }
            .sortedByDescending { it.updatedAt }
            .take(MAX_RAIL_ITEMS)

        _railItems.value = unfinished
    }

    private fun storageFile(): File? {
        val dir = runCatching { BousygramApp.instance.filesDir }.getOrNull() ?: return null
        return File(dir, FILE_NAME)
    }

    private fun saveToDiskLocked() {
        try {
            val jsonArray = JSONArray()
            for (entry in entries.values) {
                val obj = JSONObject().apply {
                    put("chatId", entry.chatId)
                    put("messageId", entry.messageId)
                    put("fileId", entry.fileId)
                    put("title", entry.title)
                    put("thumbnailPath", entry.thumbnailPath ?: "")
                    put("positionMs", entry.positionMs)
                    put("durationMs", entry.durationMs)
                    put("updatedAt", entry.updatedAt)
                    put("completed", entry.completed)
                }
                jsonArray.put(obj)
            }
            val file = storageFile() ?: return
            val temp = File(file.parentFile, "$FILE_NAME.tmp")
            temp.writeText(jsonArray.toString())
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist continue watching data", e)
        }
    }

    private fun loadFromDisk() {
        val file = storageFile() ?: return
        if (!file.exists()) return
        try {
            val content = file.readText()
            if (content.isBlank()) return
            val jsonArray = JSONArray(content)
            entries.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val entry = ContinueWatchingEntry(
                    chatId = obj.optLong("chatId"),
                    messageId = obj.optLong("messageId"),
                    fileId = obj.optInt("fileId"),
                    title = obj.optString("title", "Video"),
                    thumbnailPath = obj.optString("thumbnailPath").takeIf { it.isNotBlank() },
                    positionMs = obj.optLong("positionMs"),
                    durationMs = obj.optLong("durationMs"),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                    completed = obj.optBoolean("completed", false)
                )
                if (entry.chatId != 0L && entry.messageId != 0L) {
                    entries[key(entry.chatId, entry.messageId)] = entry
                }
            }
            Log.i(TAG, "Loaded ${entries.size} continue watching entries from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read continue watching data from disk", e)
        }
    }
}
