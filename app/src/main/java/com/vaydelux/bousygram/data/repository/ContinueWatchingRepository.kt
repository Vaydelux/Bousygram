package com.vaydelux.bousygram.data.repository

import android.util.AtomicFile
import android.util.Log
import android.util.AtomicFile
import com.vaydelux.bousygram.app.BousygramApp
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
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
import java.io.FileNotFoundException

data class ContinueWatchingEntry(
    val chatId: Long, val messageId: Long, val fileId: Int, val title: String,
    val thumbnailPath: String? = null, val positionMs: Long, val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(), val completed: Boolean = false
) {
    val progress: Float get() = if (durationMs > 0L)
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

internal interface ProgressPersistence {
    suspend fun read(accountId: Long): List<ContinueWatchingEntry>
    suspend fun write(accountId: Long, entries: List<ContinueWatchingEntry>)
}

/** One mutex owns hydration and all mutations; readers receive immutable published snapshots. */
internal class AccountProgressLedger(
    private val persistence: ProgressPersistence,
    private val isCurrent: (AccountSnapshot) -> Boolean,
    private val publish: (AccountSnapshot, List<ContinueWatchingEntry>) -> Unit
) {
    private val mutex = Mutex()
    @Volatile private var loaded: Pair<AccountSnapshot, List<ContinueWatchingEntry>>? = null

    suspend fun load(owner: AccountSnapshot) = mutex.withLock { loadLocked(owner) }

    // Initialize before any queued mutation, so an early progress save cannot be
    // overwritten by a delayed disk load. All writers also acquire [mutex].
    private val initializationJob = scope.launch {
        mutex.withLock {
            synchronized(entries) {
                loadFromDisk()
                evictExpiredLocked()
                updateRailFlowLocked()
            }
        }
    private suspend fun loadLocked(owner: AccountSnapshot) {
        if (!isCurrent(owner) || loaded?.first == owner) return
        val rows = persistence.read(owner.accountId)
        if (!isCurrent(owner)) return
        val immutable = rows.toList()
        loaded = owner to immutable
        publish(owner, immutable)
    }

    suspend fun mutate(owner: AccountSnapshot, change: (MutableMap<String, ContinueWatchingEntry>) -> Unit) =
        mutex.withLock {
            loadLocked(owner)
            val current = loaded ?: return@withLock
            if (!isCurrent(owner) || current.first != owner) return@withLock
            val next = current.second.associateBy { key(it.chatId, it.messageId) }.toMutableMap()
            change(next)
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            val rows = next.values.filterNot { it.completed && it.updatedAt < cutoff }.toList()
            persistence.write(owner.accountId, rows)
            // Failed writes never change the in-memory snapshot; old-account completions cannot publish.
            if (!isCurrent(owner)) return@withLock
            loaded = owner to rows
            publish(owner, rows)
        }

    fun snapshot(owner: AccountSnapshot?): List<ContinueWatchingEntry> {
        if (owner == null || !isCurrent(owner)) return emptyList()
        return loaded?.takeIf { it.first == owner }?.second.orEmpty()
    }

    fun invalidate() { loaded = null }
    private fun key(chatId: Long, messageId: Long) = "$chatId:$messageId"
}

        scope.launch {
            initializationJob.join()
            mutex.withLock {
                synchronized(entries) {
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
                    updateRailFlowLocked()
                }
                saveToDiskLocked()
            }
        }
    }

    fun markCompleted(chatId: Long, messageId: Long) {
        val entryKey = key(chatId, messageId)
        scope.launch {
            initializationJob.join()
            mutex.withLock {
                val changed = synchronized(entries) {
                    val existing = entries[entryKey] ?: return@synchronized false
                    entries[entryKey] = existing.copy(
                        completed = true,
                        updatedAt = System.currentTimeMillis()
                    )
                    updateRailFlowLocked()
                    true
                }
                if (changed) saveToDiskLocked()
            }
        }
    }

    fun removeEntry(chatId: Long, messageId: Long) {
        val entryKey = key(chatId, messageId)
        scope.launch {
            initializationJob.join()
            mutex.withLock {
                val removed = synchronized(entries) {
                    val changed = entries.remove(entryKey) != null
                    if (changed) updateRailFlowLocked()
                    changed
private class AccountProgressFiles : ProgressPersistence {
    private fun file(accountId: Long): File {
        val dir = File(BousygramApp.instance.filesDir, "accounts/$accountId")
        check(dir.exists() || dir.mkdirs()) { "Account storage is unavailable" }
        return File(dir, "continue_watching.json")
    }

    override suspend fun read(accountId: Long): List<ContinueWatchingEntry> {
        val target = file(accountId)
        val atomic = AtomicFile(target)
        if (!target.exists() && !File(target.path + ".bak").exists()) return emptyList()
        return try {
            val bytes = atomic.openRead().use { stream ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    check(out.size() + read <= MAX_FILE_BYTES) { "Progress document is too large" }
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            check(root.getLong("accountId") == accountId && root.getInt("version") == 1)
            val array = root.getJSONArray("entries")
            check(array.length() <= MAX_ENTRIES)
            buildList {
                for (i in 0 until array.length()) {
                    val row = array.getJSONObject(i)
                    val entry = ContinueWatchingEntry(
                        row.getLong("chatId"), row.getLong("messageId"), row.optInt("fileId"),
                        row.optString("title", "Video").take(1024),
                        row.optString("thumbnailPath").takeIf { it.isNotBlank() }?.take(4096),
                        row.getLong("positionMs").coerceAtLeast(0L),
                        row.getLong("durationMs").coerceAtLeast(0L),
                        row.optLong("updatedAt"), row.optBoolean("completed")
                    )
                    if (entry.chatId != 0L && entry.messageId != 0L) add(entry)
                }
                if (removed) saveToDiskLocked()
            }
        } catch (error: Exception) {
            // Preserve the entire unreadable original before allowing a clean ledger to be written.
            val retained = File(target.parentFile, "continue_watching.unreadable." + System.currentTimeMillis() + ".json")
            target.copyTo(retained, overwrite = false)
            Log.w("ContinueWatchingRepo", "Unreadable progress retained for explicit recovery")
            emptyList()
        }
    }

    override suspend fun write(accountId: Long, entries: List<ContinueWatchingEntry>) {
        check(entries.size <= MAX_ENTRIES) { "Progress entry limit reached; existing data was preserved" }
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("chatId", entry.chatId); put("messageId", entry.messageId); put("fileId", entry.fileId)
                put("title", entry.title.take(1024)); put("thumbnailPath", entry.thumbnailPath.orEmpty())
                put("positionMs", entry.positionMs); put("durationMs", entry.durationMs)
                put("updatedAt", entry.updatedAt); put("completed", entry.completed)
            })
        }
        val bytes = JSONObject().put("version", 1).put("accountId", accountId).put("entries", array)
            .toString().toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_FILE_BYTES) { "Progress document limit reached; existing data was preserved" }
        val atomic = AtomicFile(file(accountId))
        var stream: java.io.FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw error
        }
    }

    private companion object { const val MAX_FILE_BYTES = 2 * 1024 * 1024; const val MAX_ENTRIES = 5_000 }
}

object ContinueWatchingRepository {
    private const val MIN_RESUME_MS = 5_000L
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _railItems = MutableStateFlow<List<ContinueWatchingEntry>>(emptyList())
    val railItems: StateFlow<List<ContinueWatchingEntry>> = _railItems.asStateFlow()
    private val ledger = AccountProgressLedger(AccountProgressFiles(), AccountSession::isCurrent) { owner, rows ->
        AccountSession.runIfCurrent(owner) {
            _railItems.value = rows.filter { !it.completed && it.positionMs >= MIN_RESUME_MS &&
                (it.durationMs <= 0 || it.positionMs < (it.durationMs * 0.90).toLong()) }
                .sortedByDescending { it.updatedAt }.take(12)
        }
    }

    suspend fun activateAccount(owner: AccountSnapshot) { ledger.load(owner) }
    fun invalidateAccountResources() { ledger.invalidate(); _railItems.value = emptyList() }

    fun saveProgress(chatId: Long, messageId: Long, fileId: Int, title: String, thumbnailPath: String? = null,
        positionMs: Long, durationMs: Long, owner: AccountSnapshot? = AccountSession.current()) {
        if (chatId == 0L || messageId == 0L || owner == null) return
        mutate(owner) { rows ->
            val key = "$chatId:$messageId"
            val old = rows[key]
            val duration = durationMs.takeIf { it > 0 } ?: old?.durationMs ?: 0L
            val position = positionMs.coerceAtLeast(0)
            rows[key] = ContinueWatchingEntry(chatId, messageId, fileId.takeIf { it > 0 } ?: old?.fileId ?: 0,
                title.ifBlank { old?.title ?: "Video" }.take(1024), thumbnailPath ?: old?.thumbnailPath,
                position, duration, completed = duration > 0 && position >= (duration * 0.90).toLong())
        }
    }

    fun markCompleted(chatId: Long, messageId: Long, owner: AccountSnapshot? = AccountSession.current()) {
        if (owner == null) return
        mutate(owner) { rows -> val key = "$chatId:$messageId"
            rows[key]?.let { rows[key] = it.copy(completed = true, updatedAt = System.currentTimeMillis()) } }
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
            // AtomicFile preserves the last good version if a write or process dies midway.
            // Avoid deleting the original before a replacement is durable.
            val atomicFile = AtomicFile(file)
            val stream = atomicFile.startWrite()
            try {
                stream.write(jsonArray.toString().toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
            } catch (error: Exception) {
                atomicFile.failWrite(stream)
                throw error
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist continue watching data", e)
        }
    }

    private fun loadFromDisk() {
        val file = storageFile() ?: return
        try {
            val content = AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (content.isBlank()) return
            val jsonArray = JSONArray(content)
            // Parse completely before replacing in-memory state. A malformed JSON record
            // must not leave a half-populated map behind.
            val loaded = LinkedHashMap<String, ContinueWatchingEntry>()
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
                    loaded[key(entry.chatId, entry.messageId)] = entry
                }
            }
            entries.clear()
            entries.putAll(loaded)
            Log.i(TAG, "Loaded ${entries.size} continue watching entries from disk")
        } catch (_: FileNotFoundException) {
            // Fresh installation: nothing to restore.
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read continue watching data from disk", e)
    fun removeEntry(chatId: Long, messageId: Long) {
        val owner = AccountSession.current() ?: return
        mutate(owner) { it.remove("$chatId:$messageId") }
    }
    private fun mutate(owner: AccountSnapshot, change: (MutableMap<String, ContinueWatchingEntry>) -> Unit) {
        scope.launch {
            try { ledger.mutate(owner, change) }
            catch (error: Exception) { Log.e("ContinueWatchingRepo", "Progress was not saved; previous data was preserved", error) }
        }
    }
    private fun rows() = ledger.snapshot(AccountSession.current())
    fun getEntry(chatId: Long, messageId: Long) = rows().firstOrNull { it.chatId == chatId && it.messageId == messageId }
    fun isWatched(chatId: Long, messageId: Long, durationMs: Long): Boolean =
        getEntry(chatId, messageId)?.let { it.completed || durationMs > 0 && it.positionMs >= (durationMs * 0.90).toLong() } ?: false
    fun isWatched(fileId: Int): Boolean = rows().any { it.fileId == fileId && it.completed }
    fun isContinueWatching(fileId: Int): Boolean = rows().any {
        it.fileId == fileId && !it.completed && it.positionMs >= MIN_RESUME_MS }
    fun getContinueWatchingFileIds(): Set<Int> = rows().filter {
        !it.completed && it.positionMs >= MIN_RESUME_MS }.map { it.fileId }.filter { it > 0 }.toSet()
}
