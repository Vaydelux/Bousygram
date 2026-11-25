package com.vaydelux.bousygram.presentation.debug

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.vaydelux.bousygram.core.cast.RollingHlsReceiverLabSession
import com.vaydelux.bousygram.presentation.theme.BousygramTheme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Debug launcher-only synthetic lab. Intents carry no inputs. All Keystore
 * and pairing work serialized off main; leaving the screen revokes the
 * generation immediately, then schedules cleanup on the same owner. */
class RollingReceiverLabActivity : ComponentActivity() {
    private enum class Stage { IDLE, WORKING, INVITATION, PENDING, COMPLETE, FAILED }
    private var stage by mutableStateOf(Stage.IDLE)
    private var message by mutableStateOf("Create a one-time invitation to begin.")
    private var fingerprint by mutableStateOf("")
    private var qr by mutableStateOf<Bitmap?>(null)
    private var remaining by mutableStateOf(0L)
    private var expiresAt = 0L
    private val generation = AtomicLong()
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var session: RollingHlsReceiverLabSession? = null // IO owner only
    private var visible = false
    private val tick = object : Runnable {
        override fun run() {
            if (!visible) return
            if (expiresAt > 0L) {
                remaining = ((expiresAt - SystemClock.elapsedRealtime() + 999L) / 1000L).coerceAtLeast(0L)
                if (remaining == 0L) reset("Invitation expired. Create a new one.")
            }
            main.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            BousygramTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Receiver Lab", style = MaterialTheme.typography.headlineMedium)
                            Text("Debug only · synthetic pairing · no phone or TV network connection",
                                style = MaterialTheme.typography.bodyMedium)
                            Text(message, style = MaterialTheme.typography.bodyLarge)
                            if (expiresAt > 0L && stage != Stage.IDLE) Text("Invitation expires in ${remaining}s")
                            qr?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), "One-time receiver invitation",
                                    Modifier.align(Alignment.CenterHorizontally).size(220.dp))
                            }
                            if (fingerprint.isNotEmpty()) Text("Simulated sender certificate SHA-256\n$fingerprint")
                            when (stage) {
                                Stage.IDLE, Stage.COMPLETE, Stage.FAILED ->
                                    Button(onClick = ::issue, modifier = Modifier.fillMaxWidth()) { Text("Create invitation") }
                                Stage.INVITATION ->
                                    Button(onClick = ::simulate, modifier = Modifier.fillMaxWidth()) { Text("Simulate sender") }
                                Stage.PENDING -> {
                                    Button(onClick = { confirm(true) }, modifier = Modifier.fillMaxWidth()) { Text("Approve simulated sender") }
                                    OutlinedButton(onClick = { confirm(false) }, modifier = Modifier.fillMaxWidth()) { Text("Reject sender") }
                                }
                                Stage.WORKING -> Text("Working…")
                            }
                            if (stage == Stage.INVITATION || stage == Stage.PENDING || stage == Stage.WORKING)
                                OutlinedButton(onClick = { reset("Invitation cancelled.") }, modifier = Modifier.fillMaxWidth()) { Text("Cancel invitation") }
                            OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Close lab") }
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
        reset("Create a new invitation to begin.")
        super.onStop()
    }

    override fun onDestroy() {
        visible = false
        main.removeCallbacks(tick)
        generation.incrementAndGet()
        clearQr()
        io.shutdown()
        super.onDestroy()
    }

    private fun clearQr() {
        // Drop the UI reference immediately. Do not recycle a bitmap that
        // Compose may still have submitted to the render thread.
        qr = null
    }

    private fun closeOwned() {
        session?.close()
        session = null
    }

    private fun reset(text: String) {
        val token = generation.incrementAndGet()
        clearQr()
        fingerprint = ""
        expiresAt = 0L
        remaining = 0L
        stage = Stage.IDLE
        message = text
        io.execute {
            try { closeOwned() }
            catch (_: Exception) { main.post {
                if (visible && token == generation.get()) {
                    stage = Stage.FAILED
                    message = "Credential cleanup incomplete. Create invitation to retry."
                }
            } }
        }
    }

    private fun work(token: Long, action: () -> Unit) {
        stage = Stage.WORKING
        message = "Checking session…"
        io.execute {
            if (token != generation.get()) return@execute
            try { action() }
            catch (_: Exception) {
                val cleaned = runCatching { closeOwned() }.isSuccess
                main.post {
                    if (visible && token == generation.get()) {
                        clearQr()
                        fingerprint = ""
                        expiresAt = 0L
                        stage = Stage.FAILED
                        message = if (cleaned) "Qualification failed. Create a new invitation."
                            else "Credential cleanup incomplete. Create invitation to retry."
                    }
                }
            }
        }
    }

    private fun issue() {
        val token = generation.incrementAndGet()
        clearQr()
        fingerprint = ""
        expiresAt = 0L
        work(token) {
            closeOwned()
            val next = RollingHlsReceiverLabSession({ token == generation.get() }).also { session = it }
            val invitation = next.issue()
            main.post {
                if (visible && token == generation.get()) {
                    qr = invitation.bitmap
                    expiresAt = invitation.expiresAtMs
                    remaining = 120
                    stage = Stage.INVITATION
                    message = "Invitation ready. This lab accepts a simulated sender only."
                } else {
                    invitation.bitmap.eraseColor(android.graphics.Color.BLACK)
                    invitation.bitmap.recycle()
                }
            }
        }
    }

    private fun simulate() {
        val token = generation.get()
        clearQr()
        work(token) {
            val pin = checkNotNull(session).simulateSender()
            main.post {
                if (visible && token == generation.get()) {
                    fingerprint = pin
                    stage = Stage.PENDING
                    message = "Review the simulated certificate. Approval is required."
                }
            }
        }
    }

    private fun confirm(accept: Boolean) {
        val token = generation.get()
        work(token) {
            val accepted = checkNotNull(session).confirm(accept)
            closeOwned()
            main.post {
                if (visible && token == generation.get()) {
                    expiresAt = 0L
                    remaining = 0L
                    fingerprint = ""
                    stage = Stage.COMPLETE
                    message = if (accepted) "Local TLS signing preflight passed. Credentials deleted. No network connected."
                        else "Sender rejected. Credentials deleted."
                }
            }
        }
    }
}
