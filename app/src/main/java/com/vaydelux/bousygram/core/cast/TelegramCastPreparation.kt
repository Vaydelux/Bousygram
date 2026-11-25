package com.vaydelux.bousygram.core.cast

import android.content.Context
import com.vaydelux.bousygram.BuildConfig
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.core.cache.TvStorageBudget
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import org.drinkless.tdlib.TdApi
import java.io.File

/** IO dispatcher only. No downloads or conversion of partially available files. */
internal object TelegramCastPreparation {
    sealed interface Result {
        class Ready(val url: String) : Result
        data class Unavailable(val reason: String) : Result
    }

    suspend fun prepare(context: Context, owner: AccountSnapshot?, chatId: Long, messageId: Long,
                        fileId: Int, mime: String, name: String, knownSize: Long): Result {
        fun unavailable(reason: String) = Result.Unavailable(reason)
        if (!BuildConfig.PRIVATE_MEDIA_CAST_ENABLED) return unavailable("Private Cast is disabled in this build.")
        if (owner == null || !AccountSession.isCurrent(owner) || chatId == 0L || messageId <= 0L || fileId <= 0) {
            return unavailable("Cast requires a current Telegram message and account.")
        }
        val remux = TelegramCastCompatibility.requiresRemux(mime, name)
        if (!remux) TelegramCastCompatibility.rejection(mime, name)?.let { return unavailable(it) }
        val jobContext = currentCoroutineContext()
        fun current() = jobContext.isActive && AccountSession.isCurrent(owner)
        var artifact: CastRemuxArtifact? = null
        var url: String? = null
        var handedOff = false
        try {
            val message = TdLibClient.awaitAccountResponse(TdApi.GetMessage(chatId, messageId),
                expectedOwner = owner, timeoutMs = 5_000L) as? TdApi.Message
                ?: return unavailable("Cannot verify Telegram media permission.")
            val chat = TdLibClient.awaitAccountResponse(TdApi.GetChat(chatId),
                expectedOwner = owner, timeoutMs = 5_000L) as? TdApi.Chat
                ?: return unavailable("Cannot verify Telegram chat permission.")
            if (!message.canBeSaved || chat.hasProtectedContent || chat.type is TdApi.ChatTypeSecret ||
                message.selfDestructType != null) return unavailable("This Telegram media is protected and cannot be cast.")
            val messageFile = when (val content = message.content) {
                is TdApi.MessageVideo -> content.video?.video?.id
                is TdApi.MessageDocument -> content.document?.document?.id
                is TdApi.MessageAnimation -> content.animation?.animation?.id
                is TdApi.MessageVideoNote -> content.videoNote?.video?.id
                else -> null
            }
            if (message.id != messageId || message.chatId != chatId || messageFile != fileId || !current()) {
                return unavailable("Telegram media changed. Reopen the video before casting.")
            }
            val file = TdLibClient.awaitAccountResponse(TdApi.GetFile(fileId),
                expectedOwner = owner, timeoutMs = 5_000L) as? TdApi.File
                ?: return unavailable("Telegram file is not available.")
            val size = file.size.takeIf { it > 0 } ?: knownSize
            if (file.id != fileId || size <= 0L || !current()) return unavailable("Telegram file size or account is not ready.")
            if (!remux) {
                url = CastLanStreamingServer.start(context, fileId, size, "video/mp4", owner)
            } else {
                if (size > BoundedTelegramRemuxer.MAX_INPUT_BYTES) return unavailable("MKV Cast currently supports completed clips up to 16 MiB and 60 seconds.")
                val local = file.local
                if (local?.isDownloadingCompleted != true || local.path.isBlank()) {
                    return unavailable("MKV Cast requires a fully downloaded clip. Local playback remains available.")
                }
                val root = File(context.filesDir, "tdlib_files").canonicalFile
                val source = File(local.path).canonicalFile
                if (!source.path.startsWith(root.path + File.separator) || !source.isFile || source.length() != size) {
                    return unavailable("Cannot verify the completed Telegram media file.")
                }
                val stats = CacheManager.storageStats(context, root)
                val availableForOutput = TvStorageBudget.TOTAL_MEDIA_CACHE_BYTES - BoundedTelegramRemuxer.MAX_OUTPUT_BYTES
                // Conservatively count all app cache and TDLib data, not just reclaimable
                // media; no eviction or speculative download is triggered by casting.
                if (stats.appCacheBytes !in 0L..availableForOutput ||
                    stats.tdLibCacheBytes !in 0L..(availableForOutput - stats.appCacheBytes)) {
                    return unavailable("Not enough space in the media cache budget for MKV conversion.")
                }
                val scratch = File(File(File(context.cacheDir, "accounts"), owner.accountId.toString()), "cast-remux")
                if (!current()) return unavailable("Telegram account changed.")
                when (val result = BoundedTelegramRemuxer.convert(source, scratch) { !current() }) {
                    is BoundedTelegramRemuxer.Result.Rejected -> return unavailable("MKV Cast unavailable: " + failureMessage(result.reason))
                    is BoundedTelegramRemuxer.Result.Ready -> {
                        artifact = CastRemuxArtifact.adopt(owner, fileId, result.output)
                        if (artifact == null) {
                            result.output.close()
                            return unavailable("Telegram account changed during conversion.")
                        }
                        url = CastLanStreamingServer.startRemuxed(context, artifact)
                        if (url != null) artifact = null // server owns it
                    }
                }
            }
            jobContext.ensureActive()
            if (!current()) return unavailable("Telegram account changed.")
            val ready = url ?: return unavailable("Cannot start the local Cast server on this network.")
            handedOff = true
            return Result.Ready(ready)
        } catch (error: CancellationException) {
            // A TDLib timeout is not necessarily cancellation of the caller.
            if (!jobContext.isActive) throw error
            return unavailable("Telegram media verification timed out or the account changed.")
        } catch (_: Exception) {
            return unavailable("Cast preparation failed. Local playback remains available.")
        } finally {
            if (!handedOff) url?.let(CastLanStreamingServer::stopIfCurrentUrl)
            artifact?.let { runCatching { it.close() } }
        }
    }

    private fun failureMessage(reason: BoundedTelegramRemuxer.Failure): String = when (reason) {
        BoundedTelegramRemuxer.Failure.BUSY -> "another conversion is active."
        BoundedTelegramRemuxer.Failure.PLATFORM_UNAVAILABLE -> "Android 9 or newer is required."
        BoundedTelegramRemuxer.Failure.INPUT_UNAVAILABLE -> "the completed file changed or is missing."
        BoundedTelegramRemuxer.Failure.INPUT_TOO_LARGE, BoundedTelegramRemuxer.Failure.DURATION_LIMIT -> "clip exceeds the current size or duration limit."
        BoundedTelegramRemuxer.Failure.STORAGE_PRESSURE, BoundedTelegramRemuxer.Failure.OUTPUT_LIMIT -> "insufficient storage or output exceeds the conversion budget."
        BoundedTelegramRemuxer.Failure.UNSUPPORTED_CONTAINER, BoundedTelegramRemuxer.Failure.UNSUPPORTED_TRACKS,
        BoundedTelegramRemuxer.Failure.UNSUPPORTED_CODEC -> "this container or track layout is unsupported; H.264 and AAC only."
        BoundedTelegramRemuxer.Failure.UNSUPPORTED_SAMPLE, BoundedTelegramRemuxer.Failure.UNSUPPORTED_TIMELINE,
        BoundedTelegramRemuxer.Failure.OUTPUT_VERIFICATION_FAILED -> "the converted media could not be verified without sample or timing loss."
        BoundedTelegramRemuxer.Failure.TIME_LIMIT -> "conversion timed out."
        BoundedTelegramRemuxer.Failure.CANCELLED -> "conversion cancelled."
        BoundedTelegramRemuxer.Failure.CONVERSION_FAILED -> "the Android converter rejected the media."
        BoundedTelegramRemuxer.Failure.CLEANUP_FAILED -> "temporary-file cleanup failed; clear the app cache before retrying."
    }
}
