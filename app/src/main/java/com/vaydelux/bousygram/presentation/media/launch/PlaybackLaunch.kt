package com.vaydelux.bousygram.presentation.media.launch

import com.vaydelux.bousygram.core.tdlib.cache.OfflineCacheManager
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.data.store.EnterprisePreferences
import com.vaydelux.bousygram.data.store.SelectedPlayerEngine
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production preflight.
 *
 * - ExoPlayer reads through `TdLibDataSource`, so no loopback URL/token is minted; only the first
 *   window is warmed without blocking.
 * - mpv needs a loopback URL; it is prepared within the launch deadline, otherwise the player
 *   prepares a fresh one itself.
 */
internal class TelegramStreamPreflight(
    private val engine: () -> SelectedPlayerEngine = EnterprisePreferences::selectedPlayerEngine,
    private val warmUp: (Int, Long) -> Unit = { id, size -> runCatching { LocalStreamingServer.warmUp(id, size) } }
) : StreamPreflight {
    override suspend fun prepare(item: MediaItem, deadlineAtMs: Long): String? {
        val fileId = item.fileId ?: return null
        return withContext(Dispatchers.IO) {
            if (engine() != SelectedPlayerEngine.MPV) {
                warmUp(fileId, item.fileSizeBytes)
                null
            } else {
                LocalStreamingServer.prepareStream(
                    fileId = fileId,
                    declaredSize = item.fileSizeBytes,
                    title = item.title,
                    mimeType = item.mimeType,
                    fileName = item.fileName,
                    chatId = item.chatId,
                    messageId = item.messageId,
                    deadlineAtMs = deadlineAtMs
                )?.url
            }
        }
    }
}

/** Process-wide coordinator so duplicate protection and focus anchors span every screen. */
object PlaybackLaunch {
    val coordinator: PlaybackLaunchCoordinator by lazy {
        PlaybackLaunchCoordinator(
            preflight = TelegramStreamPreflight(),
            onBeforeOpen = { item ->
                item.fileId?.let { fileId ->
                    EnterprisePreferences.setLastPlayed(item.chatId, item.messageId, fileId, item.title)
                    OfflineCacheManager.markFileAccessed(fileId)
                }
            }
        )
    }
}
