package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.vaydelux.bousygram.presentation.media.PlaybackQueueItem
import com.vaydelux.bousygram.presentation.media.PlaybackRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import com.vaydelux.bousygram.core.tdlib.server.TdLibTelegramFileGateway
import com.vaydelux.bousygram.core.tdlib.server.TelegramChunkScheduler

/**
 * Lightweight Cast Connect payload model for cross-device media handoff.
 */
data class CastMediaPayload(
    val chatId: Long,
    val messageId: Long,
    val fileId: Int,
    val title: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val version: Int = CURRENT_VERSION
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/**
 * Manages Cast receiver session payloads, deep link resolution, and TV player handoff.
 */
object CastReceiverManager {
    private const val TAG = "CastReceiverManager"

    const val ACTION_CAST_LOAD = "com.vaydelux.bousygram.ACTION_CAST_LOAD"
    const val URI_SCHEME = "bousygram"
    const val URI_HOST = "cast"
    const val URI_PATH_LOAD = "/load"

    const val EXTRA_PAYLOAD_JSON = "payload_json"
    const val EXTRA_CHAT_ID = "chat_id"
    const val EXTRA_MESSAGE_ID = "message_id"
    const val EXTRA_FILE_ID = "file_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_POSITION_MS = "position_ms"
    const val EXTRA_DURATION_MS = "duration_ms"
    private const val MAX_PAYLOAD_JSON_CHARS = 8_192

    private val _pendingCastRequest = MutableStateFlow<CastMediaPayload?>(null)
    val pendingCastRequest: StateFlow<CastMediaPayload?> = _pendingCastRequest.asStateFlow()

    fun emitCastRequest(payload: CastMediaPayload) {
        Log.i(TAG, "Received a Cast playback request")
        _pendingCastRequest.value = payload
    }

    fun clearPendingRequest() {
        _pendingCastRequest.value = null
    }

    /** Validate externally supplied identifiers against a real message in the active TDLib session. */
    suspend fun isAuthorizedTelegramMedia(payload: CastMediaPayload): Boolean {
        if (payload.chatId == 0L || payload.messageId == 0L || payload.fileId <= 0) return false
        val file = runCatching {
            TdLibTelegramFileGateway.reResolveFile(payload.chatId, payload.messageId)
        }.getOrNull() ?: return false
        val effectiveId = TelegramChunkScheduler.resolveEffectiveFileId(payload.fileId)
        return file.fileId == payload.fileId || file.fileId == effectiveId
    }

    /**
     * Accept only the declared media handoff actions. Validate all payload formats
     * identically; malformed extras must not bypass URI/JSON input constraints.
     */
    fun parseIntent(intent: Intent?): CastMediaPayload? {
        if (intent == null || (intent.action != ACTION_CAST_LOAD && intent.action != Intent.ACTION_VIEW)) {
            return null
        }
        return runCatching { parseValidatedIntent(intent) }.getOrNull()
    }

    private fun parseValidatedIntent(intent: Intent): CastMediaPayload? {
        val jsonStr = intent.getStringExtra(EXTRA_PAYLOAD_JSON)
        if (!jsonStr.isNullOrBlank()) {
            // An invalid supplied JSON payload must not silently fall back to other extras.
            return fromJson(jsonStr)
        }

        val fileIdExtra = intent.getIntExtra(EXTRA_FILE_ID, 0)
        if (fileIdExtra > 0) {
            return validPayload(
                CastMediaPayload(
                    chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L),
                    messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0L),
                    fileId = fileIdExtra,
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Cast Media" },
                    positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L),
                    durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
                )
            )
        }

        val data = intent.data
        return if (data != null && data.scheme == URI_SCHEME && data.host == URI_HOST) {
            fromUri(data)
        } else {
            null
        }
    }

    fun fromUri(uri: Uri): CastMediaPayload? {
        return runCatching {
            if (uri.scheme != URI_SCHEME || uri.host != URI_HOST || uri.path != URI_PATH_LOAD) return null
            val fileId = uri.getQueryParameter("fileId")?.toIntOrNull() ?: return null
            val chatId = uri.getQueryParameter("chatId")?.toLongOrNull() ?: 0L
            val messageId = uri.getQueryParameter("messageId")?.toLongOrNull() ?: 0L
            val title = uri.getQueryParameter("title").orEmpty().ifBlank { "Cast Media" }
            val posMs = uri.getQueryParameter("positionMs")?.toLongOrNull() ?: 0L
            val durMs = uri.getQueryParameter("durationMs")?.toLongOrNull() ?: 0L
            val thumb = uri.getQueryParameter("thumb")
            val version = uri.getQueryParameter("v")?.toIntOrNull() ?: CastMediaPayload.CURRENT_VERSION

            validPayload(CastMediaPayload(
                chatId = chatId,
                messageId = messageId,
                fileId = fileId,
                title = title,
                positionMs = posMs,
                durationMs = durMs,
                thumbnailUrl = thumb,
                version = version
            ))
        }.getOrElse {
            fromUriString(uri.toString())
        }
    }

    fun toUriString(payload: CastMediaPayload): String {
        val encodedTitle = java.net.URLEncoder.encode(payload.title, "UTF-8")
        val base = "$URI_SCHEME://$URI_HOST$URI_PATH_LOAD?fileId=${payload.fileId}&chatId=${payload.chatId}&messageId=${payload.messageId}&title=$encodedTitle&positionMs=${payload.positionMs}&durationMs=${payload.durationMs}&v=${payload.version}"
        return if (!payload.thumbnailUrl.isNullOrBlank()) {
            "$base&thumb=${java.net.URLEncoder.encode(payload.thumbnailUrl, "UTF-8")}"
        } else {
            base
        }
    }

    fun fromUriString(uriStr: String): CastMediaPayload? {
        return runCatching {
            val uri = Uri.parse(uriStr)
            if (uri.scheme != URI_SCHEME || uri.host != URI_HOST || uri.path != URI_PATH_LOAD) return null
            val fileId = uri.getQueryParameter("fileId")?.toIntOrNull() ?: return null
            val payload = CastMediaPayload(
                chatId = uri.getQueryParameter("chatId")?.toLongOrNull() ?: 0L,
                messageId = uri.getQueryParameter("messageId")?.toLongOrNull() ?: 0L,
                fileId = fileId,
                title = uri.getQueryParameter("title").orEmpty().ifBlank { "Cast Media" },
                positionMs = uri.getQueryParameter("positionMs")?.toLongOrNull() ?: 0L,
                durationMs = uri.getQueryParameter("durationMs")?.toLongOrNull() ?: 0L,
                thumbnailUrl = uri.getQueryParameter("thumb")?.takeIf { it.isNotBlank() },
                version = uri.getQueryParameter("v")?.toIntOrNull() ?: CastMediaPayload.CURRENT_VERSION
            )
            validPayload(payload)
        }.getOrNull()
    }

    fun toUri(payload: CastMediaPayload): Uri {
        return Uri.parse(toUriString(payload))
    }

    fun toJson(payload: CastMediaPayload): String {
        return JSONObject().apply {
            put("chatId", payload.chatId)
            put("messageId", payload.messageId)
            put("fileId", payload.fileId)
            put("title", payload.title)
            put("positionMs", payload.positionMs)
            put("durationMs", payload.durationMs)
            put("thumbnailUrl", payload.thumbnailUrl)
            put("version", payload.version)
        }.toString()
    }

    fun fromJson(jsonStr: String): CastMediaPayload? {
        if (jsonStr.length > MAX_PAYLOAD_JSON_CHARS) return null
        return runCatching {
            val json = JSONObject(jsonStr)
            val fileId = json.optInt("fileId", 0)
            if (fileId <= 0) return null
            validPayload(CastMediaPayload(
                chatId = json.optLong("chatId", 0L),
                messageId = json.optLong("messageId", 0L),
                fileId = fileId,
                title = json.optString("title", "Cast Media"),
                positionMs = json.optLong("positionMs", 0L),
                durationMs = json.optLong("durationMs", 0L),
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                version = json.optInt("version", CastMediaPayload.CURRENT_VERSION)
            ))
        }.getOrNull()
    }

    fun createCastIntent(context: Context, payload: CastMediaPayload): Intent {
        return Intent(ACTION_CAST_LOAD).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_PAYLOAD_JSON, toJson(payload))
            putExtra(EXTRA_FILE_ID, payload.fileId)
            putExtra(EXTRA_CHAT_ID, payload.chatId)
            putExtra(EXTRA_MESSAGE_ID, payload.messageId)
            putExtra(EXTRA_TITLE, payload.title)
            putExtra(EXTRA_POSITION_MS, payload.positionMs)
            putExtra(EXTRA_DURATION_MS, payload.durationMs)
            data = toUri(payload)
        }
    }

    fun toPlaybackRequest(payload: CastMediaPayload, streamUrl: String?): PlaybackRequest {
        val queueItem = PlaybackQueueItem(
            url = null,
            chatId = payload.chatId,
            messageId = payload.messageId,
            title = payload.title,
            fileId = payload.fileId,
            fileName = null,
            mimeType = null,
            durationSeconds = (payload.durationMs / 1000L).toInt(),
            fileSizeBytes = 0L
        )
        return PlaybackRequest(
            initialStreamUrl = streamUrl?.takeIf { it.isNotBlank() },
            chatId = payload.chatId,
            messageId = payload.messageId,
            fileId = payload.fileId,
            title = payload.title,
            queue = listOf(queueItem),
            subtitleUrl = null,
            fileName = null,
            mimeType = null,
            durationSeconds = (payload.durationMs / 1000L).toInt(),
            fileSizeBytes = 0L
        )
    }

    private fun validPayload(payload: CastMediaPayload): CastMediaPayload? = payload.takeIf {
        it.fileId > 0 && it.chatId != 0L && it.messageId > 0L &&
            it.positionMs >= 0L && it.durationMs >= 0L &&
            it.durationMs <= Int.MAX_VALUE.toLong() * 1000L &&
            (it.durationMs == 0L || it.positionMs <= it.durationMs) &&
            it.title.length <= 256 && it.version == CastMediaPayload.CURRENT_VERSION
    }
}
