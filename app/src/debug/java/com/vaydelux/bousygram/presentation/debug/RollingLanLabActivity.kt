package com.vaydelux.bousygram.presentation.debug

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import com.vaydelux.bousygram.core.cast.RollingHlsLanLiveBridge
import com.vaydelux.bousygram.core.cast.RollingHlsLanPlayback
import java.security.MessageDigest
import java.io.ByteArrayInputStream
import android.os.Bundle
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

/** Debug only, foreground-only two-device probe. No user media, storage
 * permission, discovery broadcast or exported intent inputs. */
@UnstableApi
class RollingLanLabActivity : ComponentActivity() {
    private val generation = AtomicLong()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var session: RollingHlsLanSession? = null
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
    private var liveMode by mutableStateOf(false)
    private var fixture by mutableStateOf<Uri?>(null)
    private var player by mutableStateOf<ExoPlayer?>(null)
    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
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
        if (granted && visible && role != null) start(role)
        else if (!granted) message = "Local-network permission is required for this optional test."
    }
    private val video = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (!busy && uri != null) { fixture = uri; message = "Test clip selected. Its SHA-256 will be checked before playback." }
    }
    private val photo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !busy) decodePhoto(uri)
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null && !busy) {
            decodeBitmapAsync { bitmap }
        } else bitmap?.recycle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            BousygramTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Two-device LAN test", style = MaterialTheme.typography.headlineMedium)
                            Text("Debug only · synthetic connectivity or live playback · no Telegram media")
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
                            }
                            if (pending) {
                                Text("Sender certificate SHA-256\n$fingerprint")
                                Button(onClick = { decide(true) }, modifier = Modifier.fillMaxWidth()) { Text("Approve sender") }
                                OutlinedButton(onClick = { decide(false) }, modifier = Modifier.fillMaxWidth()) { Text("Reject sender") }
                            }
                            if (!busy) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = liveMode, onCheckedChange = { liveMode = it })
                                    Text("72-second encrypted live playback test")
                                }
                                if (liveMode) {
                                    OutlinedButton(onClick = { video.launch("video/mp4") }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (fixture == null) "Select official synthetic test clip (sender)" else "Test clip selected")
                                    }
                                    Text("Both devices must choose the same test mode. Only the official clip is accepted.")
                                }
                                Button(onClick = { requestStart(true) }, modifier = Modifier.fillMaxWidth()) { Text("Receive on this device") }
                                OutlinedTextField(value = input, onValueChange = {
                                    if (it.length <= RollingHlsLanInvitation.MAX_TEXT) input = it
                                }, label = { Text("Sender: paste LAN invitation") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                                OutlinedButton(onClick = {
                                    runCatching { camera.launch(null) }.onFailure { message = "Camera unavailable. Import a QR photo instead." }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Capture QR photo") }
                                OutlinedButton(onClick = { photo.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Import QR photo") }
                                Button(onClick = { requestStart(false) }, enabled = input.isNotBlank() && (!liveMode || fixture != null),
                                    modifier = Modifier.fillMaxWidth()) { Text(if (liveMode) "Send live test clip" else "Send connectivity test") }
                            } else OutlinedButton(onClick = { reset("Test cancelled.") }, modifier = Modifier.fillMaxWidth()) { Text("Cancel test") }
                            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Close LAN test") }
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
        main.removeCallbacks(tick)
        reset("Start a new foreground test.")
        super.onStop()
    }
    override fun onDestroy() {
        reset("Closed.")
        io.shutdown()
        super.onDestroy()
    }

    private fun reset(text: String) {
        generation.incrementAndGet()
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

    private fun requestStart(receiver: Boolean) {
        if (!RollingHlsLanRoute.hasPermission(this)) {
            permissionRole = receiver
            permission.launch(RollingHlsLanRoute.PERMISSION)
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
        val route = runCatching { RollingHlsLanRoute.capture(this) }.getOrNull()
        if (route == null) { message = "No eligible private Wi-Fi/Ethernet connection."; return }
        val token = generation.incrementAndGet()
        val raw = input
        val selected = fixture
        val live = liveMode
        input = ""
        busy = true
        message = "Preparing temporary identity…"
        val active = RollingHlsLanSession(route.address, route.prefix, { token == generation.get() && route.current() })
        session = active
        io.execute {
            val result = runCatching {
                active.use {
                    if (receiver) active.receive(
                        onInvitation = { text, deadline ->
                            val bitmap = checkNotNull(QrCodeGenerator.generate(text, 640, 640))
                            main.post {
                                if (visible && token == generation.get()) {
                                    invitation = text
                                    qr = bitmap
                                    expires = deadline
                                    message = "Scan this invitation on the sender. Waiting for enrollment…"
                                } else bitmap.recycle()
                            }
                        },
                        onPending = { pin, deadline ->
                            val answer = CompletableFuture<Boolean>()
                            post(token) {
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
                        live = if (!live) null else { socket, peer, remote ->
                            RollingHlsLanLiveBridge.receive(socket, peer, route.address, remote, active::active) { client ->
                                RollingHlsLanPlayback.play(this@RollingLanLabActivity, client, active::active,
                                    { engine -> if (token == generation.get()) player = engine },
                                    { text -> post(token) { message = text } })
                            }
                        })
                    else checkNotNull(RollingHlsLanInvitation.decode(raw.trim())).use { decoded ->
                        active.send(decoded,
                            { post(token) { message = "Waiting for approval on the receiver." } },
                            { post(token) { message = if (live) "Preparing verified live stream…" else "Sending authenticated synthetic bytes…" } },
                            live = if (!live) null else { socket, peer, remote ->
                                val bytes = readOfficialFixture(checkNotNull(selected))
                                try {
                                    RollingHlsLanLiveBridge.send(socket, peer, route.address, remote, cacheDir,
                                        ByteArrayInputStream(bytes), active::active) { seq ->
                                        post(token) { message = "Published test fragment $seq / 12" }
                                    }
                                } finally { bytes.fill(0) }
                            })
                    }
                }
            }
            post(token) {
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
                message = if (result.isSuccess) "Invitation decoded. Send the connectivity test."
                    else "Invalid QR photo. Try a clearer image or paste the invitation."
            }
        }
    }
}
