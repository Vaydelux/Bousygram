package com.vaydelux.bousygram.presentation.debug

import android.graphics.Bitmap
import com.vaydelux.bousygram.core.account.AccountSession
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.core.cast.TelegramTvHandoff
import com.vaydelux.bousygram.core.cast.TelegramTvAccountHandoff
import com.vaydelux.bousygram.core.cast.TelegramHandoffAuthorization
import com.vaydelux.bousygram.core.cast.TelegramHandoffNavigation
import com.vaydelux.bousygram.core.cast.RollingLanTelegramHandoff
import kotlinx.coroutines.runBlocking
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.vaydelux.bousygram.core.cast.RollingLanMediaMode
import androidx.compose.foundation.layout.Row
import com.vaydelux.bousygram.core.cast.RollingHlsLanLiveBridge
import com.vaydelux.bousygram.core.cast.RollingHlsLanPlayback
import java.security.MessageDigest
import java.io.File
import android.os.StatFs
import com.vaydelux.bousygram.core.cache.CacheManager
import com.vaydelux.bousygram.core.cast.RollingHlsBudget
import java.io.ByteArrayInputStream
import android.os.Bundle
import android.os.Build
import com.vaydelux.bousygram.core.cast.RollingLanDiscovery
import com.vaydelux.bousygram.core.cast.RollingLanDirectMedia
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.vaydelux.bousygram.core.cast.RollingHlsLanInvitation
import com.vaydelux.bousygram.core.cast.RollingHlsLanRoute
import com.vaydelux.bousygram.core.cast.RollingHlsLanSession
import com.vaydelux.bousygram.core.tdlib.utils.QrCodeGenerator
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/** Internal debug casting preview; explicit selection and foreground consent.
 * Synthetic diagnostics remain separate from the ordinary media routes. */
@androidx.annotation.OptIn(UnstableApi::class)
class RollingLanLabActivity : ComponentActivity() {
    private val generation = AtomicLong()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var session: RollingHlsLanSession? = null
    private var discovery: RollingLanDiscovery? = null
    private var discoveryRoute: RollingHlsLanRoute? = null
    private var foundDevices by mutableStateOf<List<String>>(emptyList())
    private var browsing by mutableStateOf(false)
    private var browseDeadline = 0L
    private var consent: CompletableFuture<Boolean>? = null
    private var visible = false
    private var busy by mutableStateOf(false)
    private var pending by mutableStateOf(false)
    private var message by mutableStateOf("Connect both devices to the same private Wi-Fi or Ethernet network.")
    private var input by mutableStateOf("")
    private var invitation by mutableStateOf("")
    private var showText by mutableStateOf(false)
    private var qr by mutableStateOf<Bitmap?>(null)
    private var fingerprint by mutableStateOf("")
    private var expires by mutableLongStateOf(0)
    private var seconds by mutableLongStateOf(0)
    private var permissionRole: Boolean? = null
    private var externalPicker = false
    private var mediaMode by mutableStateOf(RollingLanMediaMode.DIRECT_VIDEO)
    private var diagnosticsOpen by mutableStateOf(false)
    private val liveMode get() = mediaMode.live
    private val telegramMode get() = mediaMode == RollingLanMediaMode.TELEGRAM
    private val localClip get() = mediaMode == RollingLanMediaMode.FRAGMENTED_MP4
    private val directMedia get() = mediaMode == RollingLanMediaMode.DIRECT_VIDEO
    private var outgoing: TelegramTvHandoff? = null
    private var outgoingOwner: AccountSnapshot? = null

    private fun selectMode(mode: RollingLanMediaMode) {
        if (busy || outgoing != null || mediaMode == mode) return
        stopDiscovery()
        fixture = null
        input = ""
        mediaMode = mode
        message = "Select the same media mode on both devices before pairing."
    }
    private var fixture by mutableStateOf<Uri?>(null)
    private var player by mutableStateOf<ExoPlayer?>(null)
    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            if (discovery != null && (discoveryRoute?.current() != true ||
                    (browsing && SystemClock.elapsedRealtime() >= browseDeadline))) stopDiscovery()
            if (expires > 0L) {
                seconds = ((expires - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0)
                if (seconds == 0L) reset("Invitation or consent expired.")
            }
            main.postDelayed(this, 250)
        }
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val role = permissionRole
        permissionRole = null
        externalPicker = false
        main.post {
            if (granted && visible && role != null) start(role)
            else if (!granted && visible) message = "Local-network permission is required for this optional test."
        }
    }
    private val video = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        externalPicker = false
        if (!busy && uri != null) { fixture = uri; message = if (localClip || directMedia) "Local video selected. Compatibility will be checked during playback." else "Test clip selected. Its SHA-256 will be checked before playback." }
    }
    private val photo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        externalPicker = false
        if (uri != null && !busy) decodePhoto(uri)
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        externalPicker = false
        if (bitmap != null && !busy) {
            decodeBitmapAsync { bitmap }
        } else bitmap?.recycle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val owner = AccountSession.current()
        if (owner != null && owner.accountId == intent.getLongExtra("handoff_account", 0L) &&
            owner.generation == intent.getLongExtra("handoff_generation", -1L)) {
            val request = TelegramTvHandoff(intent.getLongExtra("handoff_chat", 0L),
                intent.getLongExtra("handoff_message", 0L), intent.getLongExtra("handoff_position", -1L),
                intent.getBooleanExtra("handoff_play", false))
            if (request.isValid()) {
                outgoing = request
                outgoingOwner = owner
                mediaMode = RollingLanMediaMode.TELEGRAM
            }
        }
        intent.replaceExtras(android.os.Bundle())
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            BousygramTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Cast devices", style = MaterialTheme.typography.headlineMedium)
                            Text("Preview · both devices must be on the same private network.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(message, style = MaterialTheme.typography.bodyLarge)
                            if (expires > 0) Text("Time remaining: ${seconds}s")
                            qr?.let { Image(it.asImageBitmap(), "Private LAN invitation QR", Modifier.align(Alignment.CenterHorizontally).size(256.dp)) }
                            if (invitation.isNotEmpty()) {
                                OutlinedButton(onClick = { showText = !showText }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (showText) "Hide manual invitation" else "Show manual invitation")
                                }
                                if (showText) Text(invitation)
                            }
                            player?.let { engine ->
                                AndroidView(factory = { context -> PlayerView(context).apply { useController = false; this.player = engine } },
                                    update = { it.player = engine }, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                Button(onClick = { engine.play() }, modifier = Modifier.fillMaxWidth()) { Text("Play") }
                                OutlinedButton(onClick = { engine.pause() }, modifier = Modifier.fillMaxWidth()) { Text("Pause") }
                                if (directMedia) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = {
                                            if (engine.isCurrentMediaItemSeekable)
                                                engine.seekTo((engine.currentPosition - 10_000L).coerceAtLeast(0L))
                                        }, modifier = Modifier.weight(1f)) { Text("−10s") }
                                        OutlinedButton(onClick = {
                                            if (engine.isCurrentMediaItemSeekable && engine.duration > 0L)
                                                engine.seekTo((engine.currentPosition + 10_000L).coerceAtMost(engine.duration))
                                        }, modifier = Modifier.weight(1f)) { Text("+10s") }
                                    }
                                }
                            }
                            if (pending) {
                                Text("Sender certificate SHA-256\n$fingerprint")
                                Button(onClick = { decide(true) }, modifier = Modifier.fillMaxWidth()) { Text("Approve sender") }
                                OutlinedButton(onClick = { decide(false) }, modifier = Modifier.fillMaxWidth()) { Text("Reject sender") }
                            }
                            if (!busy) {
                                Text("Media source", style = MaterialTheme.typography.titleMedium)
                                val availableModes = if (outgoing != null) listOf(RollingLanMediaMode.TELEGRAM)
                                    else RollingLanMediaMode.entries.filter { !it.diagnostic || diagnosticsOpen || it == mediaMode }
                                availableModes.forEach { mode ->
                                    Row(Modifier.fillMaxWidth().testTag("cast-mode-" + mode.name)
                                        .selectable(selected = mediaMode == mode, enabled = outgoing == null,
                                            role = Role.RadioButton, onClick = { selectMode(mode) }).padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = mediaMode == mode, onClick = null, enabled = outgoing == null)
                                        Text(mode.label, modifier = Modifier.padding(start = 10.dp))
                                    }
                                }
                                if (telegramMode) Text(if (outgoing != null) "Selected video from the player. Scan the TV invitation."
                                    else "Receive an existing Telegram video using your authorized Telegram account.")
                                if (mediaMode.needsFile) {
                                    OutlinedButton(onClick = { launchPicker { video.launch(if (directMedia) "video/*" else "video/mp4") } }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (fixture != null) "Video selected" else if (directMedia) "Select local video to send"
                                            else if (localClip) "Select fragmented MP4" else "Select official test clip")
                                    }
                                    Text(if (directMedia) "Seekable local files only. Compatible TV decoder required. Up to four hours; no copied file."
                                        else if (localClip) "H.264/AAC fragmented MP4 only; 16 MiB, up to 12 fragments."
                                        else "Only the official synthetic clip is accepted.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Connect a device", style = MaterialTheme.typography.titleMedium)
                                Button(onClick = { requestStart(true) }, enabled = mediaMode.canReceive(outgoing != null),
                                    modifier = Modifier.fillMaxWidth().testTag("cast-receive")) { Text("Receive on this device") }
                                if (Build.VERSION.SDK_INT >= 33) {
                                    OutlinedButton(onClick = { if (browsing) stopDiscovery() else browseReceivers() },
                                        modifier = Modifier.fillMaxWidth()) { Text(if (browsing) "Stop finding receivers" else "Find nearby receivers") }
                                    if (browsing) {
                                        Text("Nearby receiver hints (unverified). Scan the TV invitation before connecting.")
                                        if (foundDevices.isEmpty()) Text("Searching…")
                                        foundDevices.forEach { Text(it) }
                                    }
                                }
                                OutlinedTextField(value = input, onValueChange = {
                                    if (it.length <= RollingHlsLanInvitation.MAX_TEXT) input = it
                                }, label = { Text("Sender: paste LAN invitation") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                                OutlinedButton(onClick = {
                                    launchPicker { camera.launch(null) }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Capture QR photo") }
                                OutlinedButton(onClick = { launchPicker { photo.launch("image/*") } }, modifier = Modifier.fillMaxWidth()) { Text("Import QR photo") }
                                Button(onClick = { requestStart(false) }, enabled = mediaMode.canSend(input.isNotBlank(), fixture != null, outgoing != null),
                                    modifier = Modifier.fillMaxWidth().testTag("cast-send")) {
                                    Text(if (telegramMode) "Send Telegram video" else if (directMedia) "Cast selected video"
                                        else if (liveMode) "Send diagnostic clip" else "Send connectivity test")
                                }
                                if (outgoing == null) {
                                    OutlinedButton(onClick = {
                                        diagnosticsOpen = !diagnosticsOpen
                                        if (!diagnosticsOpen && mediaMode.diagnostic) selectMode(RollingLanMediaMode.DIRECT_VIDEO)
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (diagnosticsOpen) "Hide diagnostics" else "Advanced diagnostics")
                                    }
                                    if (diagnosticsOpen) OutlinedButton(onClick = {
                                        startActivity(android.content.Intent(this@RollingLanLabActivity, RollingReceiverLabActivity::class.java))
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Open synthetic pairing lab") }
                                }
                            } else OutlinedButton(onClick = { reset("Test cancelled.") }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
                            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Back to Bousygram") }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visible = true
        main.post(tick)
    }
    override fun onStop() {
        visible = false
        stopDiscovery()
        main.removeCallbacks(tick)
        // Only an explicitly launched picker/permission prompt may preserve idle
        // selections. No active network session survives any onStop.
        if (busy || session != null || !externalPicker) reset("Start a new foreground test.")
        super.onStop()
    }
    override fun onDestroy() {
        reset("Closed.")
        io.shutdown()
        super.onDestroy()
    }

    private fun reset(text: String) {
        generation.incrementAndGet()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        stopDiscovery()
        externalPicker = false
        permissionRole = null
        consent?.complete(false)
        consent = null
        session?.close()
        session = null
        busy = false
        pending = false
        qr = null
        invitation = ""
        input = ""
        fixture = null
        player = null
        fingerprint = ""
        showText = false
        expires = 0L
        seconds = 0L
        message = text
    }

    private fun stopDiscovery() {
        if (Build.VERSION.SDK_INT >= 33) discovery?.close()
        discovery = null
        discoveryRoute = null
        browsing = false
        browseDeadline = 0L
        foundDevices = emptyList()
    }

    @androidx.annotation.RequiresApi(33)
    private fun browseReceivers() {
        if (!visible || busy) return
        val route = runCatching { RollingHlsLanRoute.capture(this) }.getOrNull()
        if (route == null) {
            message = "Discovery needs local-network permission and private Wi-Fi/Ethernet. QR pairing remains available."
            return
        }
        stopDiscovery()
        val result = runCatching {
            RollingLanDiscovery(this, route) { names ->
                if (visible && browsing && discoveryRoute === route) foundDevices = names
            }.also { owner ->
                discovery = owner
                discoveryRoute = route
                browsing = true
                browseDeadline = SystemClock.elapsedRealtime() + 30_000
                owner.browse()
            }
        }
        if (result.isFailure) { stopDiscovery(); message = "Discovery unavailable. Use QR or manual pairing." }
    }

    private fun launchPicker(action: () -> Unit) {
        if (busy || session != null) return
        stopDiscovery()
        externalPicker = true
        try { action() } catch (_: Exception) {
            externalPicker = false
            permissionRole = null
            message = "Picker unavailable. Use another input method."
        }
    }

    private fun requestStart(receiver: Boolean) {
        if (!RollingHlsLanRoute.hasPermission(this)) {
            permissionRole = receiver
            launchPicker { permission.launch(RollingHlsLanRoute.PERMISSION) }
        } else start(receiver)
    }
    private fun post(token: Long, action: () -> Unit) {
        main.post { if (visible && generation.get() == token) action() }
    }
    private fun decide(value: Boolean) {
        pending = false
        fingerprint = ""
        expires = 0L
        consent?.complete(value)
        consent = null
        message = if (value) "Authenticating both devices…" else "Rejecting sender…"
    }

    private fun start(receiver: Boolean) {
        if (busy || !visible) return
        stopDiscovery()
        val route = runCatching { RollingHlsLanRoute.capture(this) }.getOrNull()
        if (route == null) { message = "No eligible private Wi-Fi/Ethernet connection."; return }
        val token = generation.incrementAndGet()
        val raw = input
        val selected = fixture
        val telegram = telegramMode
        val account = if (telegram) AccountSession.current() else null
        if (telegram && (account == null || (!receiver && (outgoing == null || outgoingOwner != account)))) {
            message = "Sign in to Telegram first or reopen the selected video."
            return
        }
        val live = !telegram && liveMode
        val local = live && localClip
        val direct = live && directMedia
        val reference = outgoing
        val handoff = TelegramTvAccountHandoff()
        var prepared: TelegramTvAccountHandoff.Prepared? = null
        var referenceAccepted = false
        input = ""
        busy = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        message = "Preparing temporary identity…"
        val active = RollingHlsLanSession(route.address, route.prefix,
            { token == generation.get() && route.current() && (account == null || AccountSession.isCurrent(account)) },
            lifetime = if (direct) com.vaydelux.bousygram.core.cast.RollingLanSessionLifetime.DIRECT_VIDEO
                else com.vaydelux.bousygram.core.cast.RollingLanSessionLifetime.QUALIFICATION)
        session = active
        io.execute {
            val result = runCatching {
                active.use {
                    if (telegram && !receiver) {
                        checkNotNull(runBlocking { TelegramHandoffAuthorization.resolve(checkNotNull(account), checkNotNull(reference)) })
                        check(active.active())
                    }
                    if (receiver) active.receive(
                        onInvitation = { text, deadline ->
                            val bitmap = checkNotNull(QrCodeGenerator.generate(text, 640, 640))
                            main.post {
                                if (visible && token == generation.get()) {
                                    invitation = text
                                    qr = bitmap
                                    expires = deadline
                                    message = "Scan this invitation on the sender. Waiting for enrollment…"
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        runCatching {
                                            val port = checkNotNull(RollingHlsLanInvitation.decode(text)).use { it.endpoint.port }
                                            RollingLanDiscovery(this@RollingLanLabActivity, route) {}.also { owner ->
                                                discovery = owner
                                                discoveryRoute = route
                                                owner.advertise(port)
                                            }
                                        }.onFailure { stopDiscovery() }
                                    }
                                } else bitmap.recycle()
                            }
                        },
                        onPending = { pin, deadline ->
                            val answer = CompletableFuture<Boolean>()
                            post(token) {
                                stopDiscovery()
                                invitation = ""
                                qr = null
                                showText = false
                                fingerprint = pin
                                pending = true
                                consent = answer
                                expires = deadline
                                message = "Review the sender certificate and explicitly approve."
                            }
                            var approved = false
                            while (active.active() && SystemClock.elapsedRealtime() < deadline) {
                                try { approved = answer.get(250, TimeUnit.MILLISECONDS); break }
                                catch (_: TimeoutException) { }
                            }
                            approved
                        },
                        onApproved = { post(token) { expires = 0L; message = if (live) "Waiting for live stream…" else "Receiving authenticated synthetic bytes…" } },
                        live = if (telegram) ({ socket, peer, _ ->
                            referenceAccepted = RollingLanTelegramHandoff.receive(socket, peer, active::active) { request ->
                                prepared = runBlocking { handoff.prepare(request, true) }
                                prepared?.owner == account && active.active()
                            }
                        }) else if (direct) ({ socket, peer, _ ->
                            RollingLanDirectMedia.receive(socket, peer, active::active) { uri, factory, connected ->
                                RollingHlsLanPlayback.playDirect(this@RollingLanLabActivity, uri, factory, connected,
                                    { engine -> if (token == generation.get()) player = engine },
                                    { text -> post(token) { message = text } })
                            }
                        }) else if (!live) null else { socket, peer, remote ->
                            RollingHlsLanLiveBridge.receive(socket, peer, route.address, remote, active::active, localClip = local) { client ->
                                RollingHlsLanPlayback.play(this@RollingLanLabActivity, client, active::active,
                                    { engine -> if (token == generation.get()) player = engine },
                                    { text -> post(token) { message = text } }, localClip = local)
                            }
                        })
                    else checkNotNull(RollingHlsLanInvitation.decode(raw.trim())).use { decoded ->
                        active.send(decoded,
                            { post(token) { message = "Waiting for approval on the receiver." } },
                            { post(token) { message = if (live) "Preparing verified live stream…" else "Sending authenticated synthetic bytes…" } },
                            live = if (telegram) ({ socket, peer, _ ->
                                referenceAccepted = RollingLanTelegramHandoff.send(socket, peer, checkNotNull(reference), active::active)
                            }) else if (direct) ({ socket, peer, _ ->
                                RollingLanDirectMedia.sendSelected(this@RollingLanLabActivity,
                                    checkNotNull(selected), socket, peer, active::active)
                            }) else if (!live) null else { socket, peer, remote ->
                                if (local) {
                                    val uri = checkNotNull(selected)
                                    check(uri.scheme == "content")
                                    val source = checkNotNull(contentResolver.openInputStream(uri))
                                    source.use {
                                        RollingHlsLanLiveBridge.send(socket, peer, route.address, remote, cacheDir,
                                            source, active::active, localClip = true, sharedAdmission = { additional ->
                                                runCatching {
                                                    val stats = CacheManager.storageStats(this@RollingLanLabActivity,
                                                        File(filesDir, "tdlib_files"))
                                                    RollingHlsBudget.admits(stats.appCacheBytes, stats.tdLibCacheBytes,
                                                        StatFs(cacheDir.absolutePath).availableBytes, additional)
                                                }.getOrDefault(false)
                                            }) { seq -> post(token) { message = "Published verified local fragment $seq" } }
                                    }
                                } else {
                                    val bytes = readOfficialFixture(checkNotNull(selected))
                                    try {
                                        RollingHlsLanLiveBridge.send(socket, peer, route.address, remote, cacheDir,
                                            ByteArrayInputStream(bytes), active::active) { seq ->
                                            post(token) { message = "Published test fragment $seq / 12" }
                                        }
                                    } finally { bytes.fill(0) }
                                }
                            })
                    }
                }
            }
            post(token) {
                if (telegram && receiver && referenceAccepted && result.getOrNull() == RollingHlsLanSession.Result.VERIFIED) {
                    val accepted = prepared
                    var entry: com.vaydelux.bousygram.core.cast.TelegramHandoffInbox.Entry? = null
                    if (accepted != null) handoff.publish(accepted) { media ->
                        entry = TelegramHandoffNavigation.inbox.offer(accepted.owner, media)
                    }
                    val launch = entry
                    if (launch != null) {
                        try {
                            startActivity(android.content.Intent(this@RollingLanLabActivity,
                                com.vaydelux.bousygram.app.MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP))
                            finish()
                            return@post
                        } catch (_: Exception) { TelegramHandoffNavigation.inbox.discard(launch) }
                    }
                    reset("Telegram handoff could not open the player.")
                    return@post
                }
                if (telegram) {
                    reset(if (referenceAccepted && result.getOrNull() == RollingHlsLanSession.Result.VERIFIED)
                        "TV authorized the reference. Playback is not yet confirmed."
                        else "Telegram handoff declined or unavailable.")
                    return@post
                }
                reset(result.fold(
                    { if (it == RollingHlsLanSession.Result.VERIFIED)
                        if (live) "Verified: encrypted live playback completed. Temporary credentials closed." else "Verified: 1 MiB transferred over mutual TLS. Temporary credentials closed."
                      else "Sender rejected. Temporary credentials closed." },
                    { "LAN test failed or disconnected. Check the network and create a new invitation." }))
            }
        }
    }

    private fun readOfficialFixture(uri: Uri): ByteArray {
        val bytes = ByteArray(2_231_652)
        try {
            checkNotNull(contentResolver.openInputStream(uri)).use { input ->
                java.io.DataInputStream(input).readFully(bytes)
                check(input.read() == -1)
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            check(hash == "cfbb996048865964a1e4238f0cf91a916c480ab9012446abca971d355bafc62f")
            return bytes
        } catch (error: Exception) { bytes.fill(0); throw error }
    }

    private fun decodePhoto(uri: Uri) = decodeBitmapAsync {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth in 1..12000 && bounds.outHeight in 1..12000)
        val options = BitmapFactory.Options()
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize.coerceAtLeast(1) > 2048)
            options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
        checkNotNull(contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) })
    }

    private fun decodeBitmapAsync(load: () -> Bitmap) {
        if (busy) return
        val token = generation.incrementAndGet()
        busy = true
        message = "Reading invitation QR…"
        io.execute {
            val result = runCatching {
                val bitmap = load()
                try {
                    check(bitmap.width in 1..2048 && bitmap.height in 1..2048)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    try {
                        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                        val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))
                        val text = QRCodeReader().decode(binary).text
                        checkNotNull(RollingHlsLanInvitation.decode(text)).close()
                        text
                    } finally { pixels.fill(0) }
                } finally { bitmap.recycle() }
            }
            post(token) {
                busy = false
                input = result.getOrDefault("")
                message = if (result.isSuccess) "Invitation decoded. Ready to send the selected media."
                    else "Invalid QR photo. Try a clearer image or paste the invitation."
            }
        }
    }
}
