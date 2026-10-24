package com.vaydelux.bousygram.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.vaydelux.bousygram.core.tdlib.server.LocalStreamingServer
import com.vaydelux.bousygram.domain.model.MediaItem

object ExternalPlayerLauncher {

    fun launch(context: Context, item: MediaItem) {
        val fileId = item.fileId
        if (fileId == null) {
            Toast.makeText(context, "No streamable file found", Toast.LENGTH_SHORT).show()
            return
        }

        val prep = LocalStreamingServer.prepareStream(
            fileId = fileId,
            declaredSize = item.fileSizeBytes,
            title = item.title,
            mimeType = item.mimeType,
            fileName = item.fileName,
            chatId = item.chatId,
            messageId = item.messageId
        )

        val streamUrl = prep?.url
        if (streamUrl.isNullOrBlank()) {
            Toast.makeText(context, "Unable to prepare media stream", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.parse(streamUrl)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra("title", item.title)
        }

        val pm = context.packageManager
        // Priority list of well-known external players for Android TV & mobile
        val knownPackages = listOf(
            "is.xyz.mpv",             // MPV-Android (Best for Android TV)
            "org.videolan.vlc",        // VLC for Android
            "com.brouken.player",      // Just Player
            "com.mxtech.videoplayer.ad", // MX Player Free
            "com.mxtech.videoplayer.pro" // MX Player Pro
        )

        for (pkg in knownPackages) {
            val targetedIntent = Intent(intent).apply { setPackage(pkg) }
            if (targetedIntent.resolveActivity(pm) != null) {
                runCatching {
                    context.startActivity(targetedIntent)
                    return
                }
            }
        }

        // Fallback: show system intent chooser
        val chooser = Intent.createChooser(intent, "Play with...").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "No external video player found", Toast.LENGTH_SHORT).show()
        }
    }
}
