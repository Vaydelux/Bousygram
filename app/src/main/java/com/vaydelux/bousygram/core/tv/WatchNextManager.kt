package com.vaydelux.bousygram.core.tv

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build
import android.util.Log
import com.vaydelux.bousygram.app.MainActivity
import com.vaydelux.bousygram.domain.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the Android TV Home Screen "Continue Watching" (Watch Next) channel.
 *
 * Implements Google TV / Android TV Leanback recommendation standards using
 * [TvContract.WatchNextPrograms] (API 26+).
 * Safe on non-TV devices; silently skips when provider is unavailable.
 */
object WatchNextManager {
    private const val TAG = "WatchNextManager"
    private const val MIN_ENGAGEMENT_MILLIS = 10_000L // 10s minimum before adding to Continue Watching
    private const val COMPLETION_RATIO_THRESHOLD = 0.95 // 95% watched removes from Continue Watching

    suspend fun updateProgress(
        context: Context,
        chatId: Long,
        messageId: Long,
        fileId: Int?,
        title: String,
        currentPositionMs: Long,
        durationMs: Long,
        thumbnailPath: String? = null
    ) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext
        if (durationMs <= 0 || currentPositionMs < MIN_ENGAGEMENT_MILLIS) return@withContext

        val internalId = "bousy_${chatId}_${messageId}_$fileId"
        val ratio = currentPositionMs.toDouble() / durationMs.toDouble()

        if (ratio >= COMPLETION_RATIO_THRESHOLD) {
            // Video is practically finished, remove from Watch Next
            removeFromWatchNext(context, internalId)
            return@withContext
        }

        runCatching {
            val contentResolver = context.contentResolver
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("chat_id", chatId)
                putExtra("message_id", messageId)
                if (fileId != null) putExtra("file_id", fileId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val values = ContentValues().apply {
                put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                put(TvContract.WatchNextPrograms.COLUMN_TYPE, TvContract.WatchNextPrograms.TYPE_CLIP)
                put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, System.currentTimeMillis())
                put(TvContract.WatchNextPrograms.COLUMN_TITLE, title.ifBlank { "Telegram Video" })
                put(TvContract.WatchNextPrograms.COLUMN_SHORT_DESCRIPTION, "Continue watching from Telegram")
                put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, currentPositionMs.toInt())
                put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, durationMs.toInt())
                put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, internalId)
                put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, intent.toUri(Intent.URI_INTENT_SCHEME))

                thumbnailPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, Uri.fromFile(file).toString())
                    }
                }
            }

            // Check if entry already exists
            val cursor = contentResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI,
                arrayOf(TvContract.WatchNextPrograms._ID),
                "${TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                arrayOf(internalId),
                null
            )

            val existingId = cursor?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            }

            if (existingId != null) {
                val updateUri = TvContract.buildWatchNextProgramUri(existingId)
                contentResolver.update(updateUri, values, null, null)
                Log.d(TAG, "Updated WatchNext program id=$existingId internalId=$internalId pos=$currentPositionMs/$durationMs")
            } else {
                val insertedUri = contentResolver.insert(TvContract.WatchNextPrograms.CONTENT_URI, values)
                Log.d(TAG, "Inserted WatchNext program uri=$insertedUri internalId=$internalId")
            }
        }.onFailure { e ->
            Log.d(TAG, "WatchNext update skipped or unavailable on this device: ${e.message}")
        }
    }

    suspend fun removeFromWatchNext(context: Context, internalId: String) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return@withContext
        runCatching {
            val rows = context.contentResolver.delete(
                TvContract.WatchNextPrograms.CONTENT_URI,
                "${TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID} = ?",
                arrayOf(internalId)
            )
            Log.d(TAG, "Removed $rows rows from WatchNext for internalId=$internalId")
        }.onFailure { e ->
            Log.d(TAG, "WatchNext remove skipped: ${e.message}")
        }
    }
}
