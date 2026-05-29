package com.vaydelux.bousygram.data.repository

import android.util.Log
import org.drinkless.tdlib.TdApi

fun SavedMessagesRepository.findSidecarSubtitle(
    chatId: Long,
    videoTitle: String,
    onResult: (Int?, String?) -> Unit
) {
    // Determine the name prefix by stripping common video extensions
    val lastDot = videoTitle.lastIndexOf('.')
    val prefix = if (lastDot > 0) videoTitle.substring(0, lastDot) else videoTitle
    if (prefix.isBlank() || prefix.length < 3) {
        onResult(null, null)
        return
    }

    Log.d("SubtitleSearch", "Searching for sidecar subtitles matching prefix: '$prefix' in chatId=$chatId")

    // Search chat messages for document filter with the video title prefix
    val query = TdApi.SearchChatMessages(
        chatId,
        null,
        prefix,
        null,
        0L,
        0,
        20,
        TdApi.SearchMessagesFilterDocument()
    )

    client.send(query) { result ->
        when (result) {
            is TdApi.FoundChatMessages -> {
                val messages = result.messages ?: emptyArray()
                for (message in messages) {
                    val content = message.content as? TdApi.MessageDocument ?: continue
                    val doc = content.document ?: continue
                    val fileName = doc.fileName?.lowercase() ?: continue
                    
                    // Check if it's an srt or ass file and matches our video prefix
                    if (fileName.endsWith(".srt") || fileName.endsWith(".ass")) {
                        val fileNameWithoutExt = if (fileName.contains('.')) {
                            fileName.substringBeforeLast('.')
                        } else {
                            fileName
                        }
                        val videoKey = prefix.lowercase()
                        val subKey = fileNameWithoutExt.lowercase()
                        val matches = subKey == videoKey ||
                            subKey.startsWith("$videoKey.") ||
                            subKey.startsWith("${videoKey}_") ||
                            subKey.startsWith("${videoKey}-")
                        if (matches) {
                            Log.i("SubtitleSearch", "Found matching sidecar subtitle: $fileName, fileId=${doc.document?.id}")
                            onResult(doc.document?.id, fileName)
                            return@send
                        }
                    }
                }
                onResult(null, null)
            }
            is TdApi.Error -> {
                Log.e("SubtitleSearch", "Error searching for subtitles: ${result.message}")
                onResult(null, result.message)
            }
            else -> {
                onResult(null, "Unknown response searching subtitles")
            }
        }
    }
}
