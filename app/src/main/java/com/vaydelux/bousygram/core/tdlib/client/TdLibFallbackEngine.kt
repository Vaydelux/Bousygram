package com.vaydelux.bousygram.core.tdlib.client

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import com.vaydelux.bousygram.app.BousygramApp
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * In-memory TDLib protocol engine used automatically when the native `libtdjni.so` binary
 * is absent for the current device/emulator ABI (e.g. x86_64 cloud emulators or stripped builds).
 *
 * Materializes a bundled H.264/AAC `.mp4` stream asset and generated `.jpg` poster thumbnails
 * on local storage so Bousygram's real `TdLibDataSource`, `TelegramChunkScheduler`,
 * `LocalStreamingServer`, `TelegramProxyManager`, and `SeekThumbnailManager` operate 100% locally
 * with zero external HTTP dependencies.
 *
 * When `libtdjni.so` is present, [Client.isNativeAvailable] is true and this fallback engine is
 * completely bypassed in favor of real JNI execution.
 */
object TdLibFallbackEngine {
    private const val TAG = "TdLibFallbackEngine"
    const val SAVED_MESSAGES_CHAT_ID = 777000L
    const val FORUM_CHAT_ID = -1001001L
    const val CHANNEL_CHAT_ID = -1001002L
    private const val ASSET_STREAM_FILE_NAME = "fallback_stream.mp4"
    private const val DEFAULT_FALLBACK_SIZE_BYTES = 201_204L

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Bousygram-TdLibFallback").apply { isDaemon = true }
    }

    private data class DemoVideoSpec(
        val chatId: Long,
        val messageId: Long,
        val topicId: Int?,
        val fileId: Int,
        val thumbFileId: Int,
        val title: String,
        val subtitle: String,
        val fileName: String,
        val durationSeconds: Int,
        val dateSeconds: Int,
        val gradientStartColor: Int,
        val gradientEndColor: Int,
        val accentColor: Int
    )

    private val demoVideos = listOf(
        DemoVideoSpec(
            chatId = SAVED_MESSAGES_CHAT_ID,
            messageId = 1001L,
            topicId = null,
            fileId = 900001,
            thumbFileId = 910001,
            title = "Big Buck Bunny (2008) - 1080p Blender Open Movie",
            subtitle = "BLENDER FOUNDATION • OPEN MOVIE",
            fileName = "Big.Buck.Bunny.2008.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_100,
            gradientStartColor = 0xFF0F2027.toInt(),
            gradientEndColor = 0xFF2C5364.toInt(),
            accentColor = 0xFF5EE5BE.toInt()
        ),
        DemoVideoSpec(
            chatId = SAVED_MESSAGES_CHAT_ID,
            messageId = 1002L,
            topicId = null,
            fileId = 900002,
            thumbFileId = 910002,
            title = "Sintel (2010) - 4K Remastered Fantasy Short",
            subtitle = "DURIAN OPEN MOVIE PROJECT • 4K HDR",
            fileName = "Sintel.2010.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_200,
            gradientStartColor = 0xFF1F1C2C.toInt(),
            gradientEndColor = 0xFF928DAB.toInt(),
            accentColor = 0xFFFFB74D.toInt()
        ),
        DemoVideoSpec(
            chatId = SAVED_MESSAGES_CHAT_ID,
            messageId = 1003L,
            topicId = null,
            fileId = 900003,
            thumbFileId = 910003,
            title = "Tears of Steel (2012) - Sci-Fi Live Action VFX",
            subtitle = "MANGO OPEN MOVIE • CYBERPUNK SCI-FI",
            fileName = "Tears.of.Steel.2012.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_300,
            gradientStartColor = 0xFF0B132B.toInt(),
            gradientEndColor = 0xFF1C2541.toInt(),
            accentColor = 0xFF6FFFE9.toInt()
        ),
        DemoVideoSpec(
            chatId = SAVED_MESSAGES_CHAT_ID,
            messageId = 1004L,
            topicId = null,
            fileId = 900004,
            thumbFileId = 910004,
            title = "Elephants Dream (2006) - Experimental Cinema",
            subtitle = "ORANGE OPEN MOVIE • SURREAL 3D",
            fileName = "Elephants.Dream.2006.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_400,
            gradientStartColor = 0xFF23074D.toInt(),
            gradientEndColor = 0xFFCC5333.toInt(),
            accentColor = 0xFFFFD166.toInt()
        ),
        DemoVideoSpec(
            chatId = FORUM_CHAT_ID,
            messageId = 2001L,
            topicId = 1,
            fileId = 900005,
            thumbFileId = 910005,
            title = "For Bigger Blazes - Dolby Atmos Showcase",
            subtitle = "CINEMA VAULT HD • GENERAL TOPIC",
            fileName = "For.Bigger.Blazes.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_500,
            gradientStartColor = 0xFF1A2A6C.toInt(),
            gradientEndColor = 0xFFB21F1F.toInt(),
            accentColor = 0xFFFDBB2D.toInt()
        ),
        DemoVideoSpec(
            chatId = FORUM_CHAT_ID,
            messageId = 2002L,
            topicId = 10,
            fileId = 900006,
            thumbFileId = 910006,
            title = "Tears of Steel S01E01 - Amsterdam Cyber Dome",
            subtitle = "SCI-FI & CYBERPUNK • EPISODE 1",
            fileName = "Tears.of.Steel.S01E01.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_600,
            gradientStartColor = 0xFF0F0C29.toInt(),
            gradientEndColor = 0xFF302B63.toInt(),
            accentColor = 0xFF00F5D4.toInt()
        ),
        DemoVideoSpec(
            chatId = FORUM_CHAT_ID,
            messageId = 2003L,
            topicId = 20,
            fileId = 900007,
            thumbFileId = 910007,
            title = "Sintel S01E01 - The Dragon Scales",
            subtitle = "ANIMATION & SHORTS • EPISODE 1",
            fileName = "Sintel.S01E01.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_700,
            gradientStartColor = 0xFF200122.toInt(),
            gradientEndColor = 0xFF6F0000.toInt(),
            accentColor = 0xFFFF9F1C.toInt()
        ),
        DemoVideoSpec(
            chatId = CHANNEL_CHAT_ID,
            messageId = 3001L,
            topicId = null,
            fileId = 900008,
            thumbFileId = 910008,
            title = "For Bigger Escapes - Alpine Horizon 4K",
            subtitle = "OPEN MOVIE CHANNEL • DIRECT STREAM",
            fileName = "For.Bigger.Escapes.1080p.mp4",
            durationSeconds = 15,
            dateSeconds = 1_729_700_800,
            gradientStartColor = 0xFF134E5E.toInt(),
            gradientEndColor = 0xFF71B280.toInt(),
            accentColor = 0xFFA8E6CF.toInt()
        )
    )

    private val deletedMessageIds = CopyOnWriteArraySet<String>()
    private val assetLock = Any()

    @Volatile
    private var cachedLocalVideoFile: File? = null

    @Volatile
    private var authState: TdApi.AuthorizationState = TdApi.AuthorizationStateWaitTdlibParameters()

    private fun ensureFallbackDir(): File? {
        val app = runCatching { BousygramApp.instance }.getOrNull() ?: return null
        val dir = File(app.filesDir, "tdlib_fallback")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun ensureLocalVideoFile(): File? {
        cachedLocalVideoFile?.takeIf { it.isFile && it.length() > 0L }?.let { return it }
        synchronized(assetLock) {
            cachedLocalVideoFile?.takeIf { it.isFile && it.length() > 0L }?.let { return it }
            val app = runCatching { BousygramApp.instance }.getOrNull() ?: return null
            val dir = ensureFallbackDir() ?: return null
            val target = File(dir, ASSET_STREAM_FILE_NAME)
            if (!target.isFile || target.length() <= 0L) {
                runCatching {
                    app.assets.open(ASSET_STREAM_FILE_NAME).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Failed to extract $ASSET_STREAM_FILE_NAME from assets", err)
                    return null
                }
            }
            cachedLocalVideoFile = target
            return target
        }
    }

    private fun ensureLocalPosterFile(spec: DemoVideoSpec): File? {
        val dir = ensureFallbackDir() ?: return null
        val target = File(dir, "poster_${spec.thumbFileId}.jpg")
        if (target.isFile && target.length() > 0L) return target
        synchronized(assetLock) {
            if (target.isFile && target.length() > 0L) return target
            runCatching {
                val width = 640
                val height = 360
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        width.toFloat(),
                        height.toFloat(),
                        spec.gradientStartColor,
                        spec.gradientEndColor,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = spec.accentColor
                }
                canvas.drawRoundRect(RectF(36f, 36f, 140f, 70f), 10f, 10f, badgePaint)

                val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF091418.toInt()
                    textSize = 20f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                canvas.drawText("1080p HD", 48f, 60f, badgeTextPaint)

                val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = spec.accentColor
                    textSize = 18f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                canvas.drawText(spec.subtitle, 36f, 248f, subtitlePaint)

                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = 28f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val shortTitle = spec.title.substringBefore(" - ")
                canvas.drawText(shortTitle.take(32), 36f, 290f, titlePaint)

                val subline = spec.title.substringAfter(" - ", "Direct Loopback Stream")
                val sublinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFD0DCE5.toInt()
                    textSize = 21f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
                canvas.drawText(subline.take(40), 36f, 324f, sublinePaint)

                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    out.fd.sync()
                }
                bitmap.recycle()
            }.onFailure { err ->
                Log.w(TAG, "Failed to render fallback poster for ${spec.thumbFileId}", err)
                return null
            }
            return target.takeIf { it.isFile && it.length() > 0L }
        }
    }

    @JvmStatic
    fun sampleFileSizeForFile(fileId: Int): Long {
        if (demoVideos.none { it.fileId == fileId }) return 0L
        return ensureLocalVideoFile()?.length()?.takeIf { it > 0L } ?: DEFAULT_FALLBACK_SIZE_BYTES
    }

    @JvmStatic
    fun onClientCreated(updateHandler: Client.ResultHandler?) {
        if (updateHandler == null) return
        executor.execute {
            try {
                updateHandler.onResult(TdApi.UpdateConnectionState(TdApi.ConnectionStateReady()))
                updateHandler.onResult(TdApi.UpdateAuthorizationState(authState))
            } catch (t: Throwable) {
                Log.w(TAG, "Error dispatching initial fallback update", t)
            }
        }
    }

    @JvmStatic
    fun execute(query: TdApi.Function<*>): TdApi.Object {
        return when (query) {
            is TdApi.SetLogVerbosityLevel -> TdApi.Ok()
            is TdApi.GetOption -> TdApi.OptionValueString("1.8.35-fallback")
            else -> TdApi.Ok()
        }
    }

    @JvmStatic
    fun send(
        query: TdApi.Function<*>,
        resultHandler: Client.ResultHandler?,
        exceptionHandler: Client.ExceptionHandler?,
        updateHandler: Client.ResultHandler?
    ) {
        executor.execute {
            try {
                val response = handleFunction(query, updateHandler)
                resultHandler?.onResult(response)
            } catch (t: Throwable) {
                Log.w(TAG, "Error in TdLibFallbackEngine for ${query.javaClass.simpleName}", t)
                try {
                    exceptionHandler?.onException(t)
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun handleFunction(
        query: TdApi.Function<*>,
        updateHandler: Client.ResultHandler?
    ): TdApi.Object {
        return when (query) {
            is TdApi.GetOption -> TdApi.OptionValueString("1.8.35-fallback")
            is TdApi.SetOption -> TdApi.Ok()
            is TdApi.SetLogVerbosityLevel -> TdApi.Ok()
            is TdApi.GetAuthorizationState -> authState
            is TdApi.SetTdlibParameters -> {
                authState = TdApi.AuthorizationStateReady()
                updateHandler?.onResult(TdApi.UpdateConnectionState(TdApi.ConnectionStateReady()))
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.RequestQrCodeAuthentication -> {
                authState = TdApi.AuthorizationStateWaitOtherDeviceConfirmation("tg://login?token=bousygram-preview")
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.SetAuthenticationPhoneNumber -> {
                authState = TdApi.AuthorizationStateReady()
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.CheckAuthenticationCode,
            is TdApi.CheckAuthenticationPassword -> {
                authState = TdApi.AuthorizationStateReady()
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.LogOut -> {
                authState = TdApi.AuthorizationStateWaitPhoneNumber()
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(authState))
                TdApi.Ok()
            }
            is TdApi.Destroy -> {
                authState = TdApi.AuthorizationStateWaitTdlibParameters()
                updateHandler?.onResult(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
                TdApi.Ok()
            }
            is TdApi.GetMe -> TdApi.User().apply {
                id = SAVED_MESSAGES_CHAT_ID
                firstName = "Bousygram"
                lastName = "Preview"
                phoneNumber = "15550199"
            }
            is TdApi.CreatePrivateChat -> buildChat(SAVED_MESSAGES_CHAT_ID)
            is TdApi.OpenChat,
            is TdApi.CloseChat -> TdApi.Ok()
            is TdApi.GetChats -> {
                if (query.chatList is TdApi.ChatListArchive) {
                    TdApi.Chats(0, longArrayOf())
                } else {
                    TdApi.Chats(3, longArrayOf(SAVED_MESSAGES_CHAT_ID, FORUM_CHAT_ID, CHANNEL_CHAT_ID))
                }
            }
            is TdApi.LoadChats -> TdApi.Error(404, "No more chats")
            is TdApi.GetChat -> buildChat(query.chatId)
            is TdApi.GetSupergroup -> TdApi.Supergroup().apply {
                id = query.supergroupId
                isForum = (query.supergroupId == 1001L)
                isChannel = (query.supergroupId == 1002L)
                memberCount = 1280
            }
            is TdApi.GetForumTopics -> buildForumTopics(query.chatId)
            is TdApi.SearchChatMessages -> handleSearchChatMessages(query)
            is TdApi.GetChatHistory -> handleGetChatHistory(query.chatId, query.fromMessageId, null)
            is TdApi.GetForumTopicHistory -> handleGetChatHistory(query.chatId, query.fromMessageId, query.forumTopicId)
            is TdApi.SearchMessages -> handleGlobalSearch(query)
            is TdApi.GetMessage -> {
                val spec = activeVideos().firstOrNull { it.chatId == query.chatId && it.messageId == query.messageId }
                if (spec != null) toTdMessage(spec) else TdApi.Error(404, "Message not found")
            }
            is TdApi.DeleteMessages -> {
                query.messageIds?.forEach { msgId ->
                    deletedMessageIds.add("${query.chatId}:$msgId")
                }
                TdApi.Ok()
            }
            is TdApi.GetFile -> handleGetFile(query.fileId)
            is TdApi.DownloadFile -> {
                val fileObj = handleGetFile(query.fileId)
                if (fileObj is TdApi.File) {
                    updateHandler?.onResult(TdApi.UpdateFile(fileObj))
                }
                fileObj
            }
            is TdApi.ReadFilePart -> handleReadFilePart(query)
            is TdApi.GetFileDownloadedPrefixSize -> {
                val total = sampleFileSizeForFile(query.fileId)
                val remaining = (total - query.offset.coerceAtLeast(0L)).coerceAtLeast(0L)
                TdApi.FileDownloadedPrefixSize(remaining)
            }
            is TdApi.CancelDownloadFile -> TdApi.Ok()
            is TdApi.OptimizeStorage -> TdApi.StorageStatistics().apply {
                size = 0L
                count = 0
                byChat = emptyArray()
            }
            is TdApi.GetStorageStatisticsFast -> TdApi.StorageStatisticsFast()
            else -> TdApi.Ok()
        }
    }

    private fun handleReadFilePart(query: TdApi.ReadFilePart): TdApi.Object {
        val videoSpec = demoVideos.firstOrNull { it.fileId == query.fileId }
        if (videoSpec != null) {
            val file = ensureLocalVideoFile() ?: return TdApi.Error(404, "Local stream asset unavailable")
            val totalLength = file.length()
            val offset = query.offset.coerceAtLeast(0L)
            if (offset >= totalLength) {
                return TdApi.Data(ByteArray(0))
            }
            val available = (totalLength - offset).coerceAtLeast(0L)
            val requested = if (query.count > 0L) min(query.count, available).toInt() else available.toInt()
            if (requested <= 0) {
                return TdApi.Data(ByteArray(0))
            }
            val buffer = ByteArray(requested)
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(offset)
                var totalRead = 0
                while (totalRead < requested) {
                    val read = raf.read(buffer, totalRead, requested - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                return if (totalRead == requested) {
                    TdApi.Data(buffer)
                } else {
                    TdApi.Data(buffer.copyOf(totalRead))
                }
            }
        }
        val thumbSpec = demoVideos.firstOrNull { it.thumbFileId == query.fileId }
        if (thumbSpec != null) {
            val file = ensureLocalPosterFile(thumbSpec) ?: return TdApi.Error(404, "Poster unavailable")
            return TdApi.Data(file.readBytes())
        }
        return TdApi.Error(404, "File ${query.fileId} not found")
    }

    private fun activeVideos(): List<DemoVideoSpec> =
        demoVideos.filterNot { deletedMessageIds.contains("${it.chatId}:${it.messageId}") }

    private fun buildChat(chatId: Long): TdApi.Chat = TdApi.Chat().apply {
        id = chatId
        when (chatId) {
            SAVED_MESSAGES_CHAT_ID -> {
                title = "Saved Messages"
                type = TdApi.ChatTypePrivate(SAVED_MESSAGES_CHAT_ID)
            }
            FORUM_CHAT_ID -> {
                title = "Cinema Vault HD"
                type = TdApi.ChatTypeSupergroup(1001L, false)
            }
            CHANNEL_CHAT_ID -> {
                title = "Open Movie Channel"
                type = TdApi.ChatTypeSupergroup(1002L, true)
            }
            else -> {
                title = "Telegram Media"
                type = TdApi.ChatTypePrivate(SAVED_MESSAGES_CHAT_ID)
            }
        }
    }

    private fun buildForumTopics(chatId: Long): TdApi.ForumTopics {
        if (chatId != FORUM_CHAT_ID) {
            return TdApi.ForumTopics(0, emptyArray(), 0, 0L, 0)
        }
        val topics = arrayOf(
            makeForumTopic(1, "General", isGeneral = true, isPinned = true, color = 0x6FB9F0),
            makeForumTopic(10, "Sci-Fi & Cyberpunk", isGeneral = false, isPinned = true, color = 0xFFD67E),
            makeForumTopic(20, "Animation & Shorts", isGeneral = false, isPinned = false, color = 0xCB86DB)
        )
        return TdApi.ForumTopics(topics.size, topics, 0, 0L, 0)
    }

    private fun makeForumTopic(
        topicId: Int,
        topicName: String,
        isGeneral: Boolean,
        isPinned: Boolean,
        color: Int
    ): TdApi.ForumTopic = TdApi.ForumTopic().apply {
        info = TdApi.ForumTopicInfo().apply {
            chatId = FORUM_CHAT_ID
            forumTopicId = topicId
            name = topicName
            icon = TdApi.ForumTopicIcon(color, 0L)
            this.isGeneral = isGeneral
            isClosed = false
            isHidden = false
        }
        this.isPinned = isPinned
        unreadCount = 0
    }

    private fun handleSearchChatMessages(query: TdApi.SearchChatMessages): TdApi.FoundChatMessages {
        if (query.fromMessageId != 0L || query.filter !is TdApi.SearchMessagesFilterVideo) {
            return TdApi.FoundChatMessages(0, emptyArray(), 0L)
        }
        val requestedTopicId = (query.topicId as? TdApi.MessageTopicForum)?.forumTopicId
        val textQuery = query.query?.trim().orEmpty()
        val matched = activeVideos()
            .filter { spec ->
                spec.chatId == query.chatId &&
                    (requestedTopicId == null || requestedTopicId == 0 || spec.topicId == requestedTopicId) &&
                    (textQuery.isEmpty() || spec.title.contains(textQuery, ignoreCase = true) || spec.fileName.contains(textQuery, ignoreCase = true))
            }
            .map(::toTdMessage)
            .toTypedArray()
        return TdApi.FoundChatMessages(matched.size, matched, 0L)
    }

    private fun handleGetChatHistory(
        chatId: Long,
        fromMessageId: Long,
        topicId: Int?
    ): TdApi.Messages {
        if (fromMessageId != 0L) {
            return TdApi.Messages(0, emptyArray())
        }
        val matched = activeVideos()
            .filter { spec ->
                spec.chatId == chatId &&
                    (topicId == null || topicId == 0 || spec.topicId == topicId)
            }
            .map(::toTdMessage)
            .toTypedArray()
        return TdApi.Messages(matched.size, matched)
    }

    private fun handleGlobalSearch(query: TdApi.SearchMessages): TdApi.FoundMessages {
        if (query.offset.isNotBlank()) {
            return TdApi.FoundMessages(0, emptyArray(), "")
        }
        val text = query.query?.trim().orEmpty()
        val matched = activeVideos()
            .filter { spec ->
                text.isEmpty() ||
                    spec.title.contains(text, ignoreCase = true) ||
                    spec.fileName.contains(text, ignoreCase = true)
            }
            .map(::toTdMessage)
            .toTypedArray()
        return TdApi.FoundMessages(matched.size, matched, "")
    }

    private fun handleGetFile(fileId: Int): TdApi.Object {
        val videoSpec = demoVideos.firstOrNull { it.fileId == fileId }
        if (videoSpec != null) {
            val localFile = ensureLocalVideoFile()
            val fileSize = localFile?.length()?.takeIf { it > 0L } ?: DEFAULT_FALLBACK_SIZE_BYTES
            return buildTdFile(
                fileId = videoSpec.fileId,
                sizeBytes = fileSize,
                localPath = localFile?.absolutePath.orEmpty(),
                completed = localFile != null
            )
        }
        val thumbSpec = demoVideos.firstOrNull { it.thumbFileId == fileId }
        if (thumbSpec != null) {
            val posterFile = ensureLocalPosterFile(thumbSpec)
            val posterSize = posterFile?.length()?.takeIf { it > 0L } ?: 32_000L
            return buildTdFile(
                fileId = thumbSpec.thumbFileId,
                sizeBytes = posterSize,
                localPath = posterFile?.absolutePath.orEmpty(),
                completed = posterFile != null
            )
        }
        return TdApi.Error(404, "File $fileId not found")
    }

    private fun toTdMessage(spec: DemoVideoSpec): TdApi.Message = TdApi.Message().apply {
        val localVideo = ensureLocalVideoFile()
        val videoSize = localVideo?.length()?.takeIf { it > 0L } ?: DEFAULT_FALLBACK_SIZE_BYTES
        val localPoster = ensureLocalPosterFile(spec)
        val posterSize = localPoster?.length()?.takeIf { it > 0L } ?: 32_000L

        id = spec.messageId
        chatId = spec.chatId
        date = spec.dateSeconds
        if (spec.topicId != null) {
            topicId = TdApi.MessageTopicForum(spec.topicId)
        }
        content = TdApi.MessageVideo().apply {
            caption = TdApi.FormattedText(spec.title, emptyArray())
            video = TdApi.Video().apply {
                duration = spec.durationSeconds
                width = 1280
                height = 720
                fileName = spec.fileName
                mimeType = "video/mp4"
                supportsStreaming = true
                thumbnail = TdApi.Thumbnail().apply {
                    format = TdApi.ThumbnailFormatJpeg()
                    width = 640
                    height = 360
                    file = buildTdFile(
                        fileId = spec.thumbFileId,
                        sizeBytes = posterSize,
                        localPath = localPoster?.absolutePath.orEmpty(),
                        completed = localPoster != null
                    )
                }
                video = buildTdFile(
                    fileId = spec.fileId,
                    sizeBytes = videoSize,
                    localPath = localVideo?.absolutePath.orEmpty(),
                    completed = localVideo != null
                )
            }
        }
    }

    private fun buildTdFile(
        fileId: Int,
        sizeBytes: Long,
        localPath: String,
        completed: Boolean
    ): TdApi.File = TdApi.File().apply {
        id = fileId
        size = sizeBytes
        expectedSize = sizeBytes
        local = TdApi.LocalFile().apply {
            path = localPath
            canBeDownloaded = true
            canBeDeleted = false
            isDownloadingActive = false
            isDownloadingCompleted = completed
            downloadOffset = 0L
            downloadedPrefixSize = if (completed) sizeBytes else 0L
            downloadedSize = if (completed) sizeBytes else 0L
        }
        remote = TdApi.RemoteFile().apply {
            id = "remote_$fileId"
            uniqueId = "unique_$fileId"
            isUploadingActive = false
            isUploadingCompleted = true
            uploadedSize = sizeBytes
        }
    }
}
