package com.vaydelux.bousygram.core.cast

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.media3.common.util.UnstableApi
import android.graphics.SurfaceTexture
import android.view.Surface
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Two independently enrolled Android Keystore identities using actual private
 * interface TCP sockets on one emulator. Not separate physical devices. */
@RunWith(AndroidJUnit4::class)
class RollingHlsLanSessionInstrumentedTest {
    @Test fun privateInterfaceEnrollmentConsentAndMutualTlsProbe() {
        val route = checkNotNull(RollingHlsLanRoute.capture(InstrumentationRegistry.getInstrumentation().targetContext))
        val workers = Executors.newSingleThreadExecutor()
        try {
            for (approved in listOf(false, true)) {
                val invitation = CompletableFuture<String>()
                RollingHlsLanSession(route.address, route.prefix, route::current).use { receiver ->
                    val result = workers.submit<RollingHlsLanSession.Result> {
                        receiver.receive({ text, _ -> invitation.complete(text) },
                            { pin, _ -> assertEquals(95, pin.length); approved }, {})
                    }
                    checkNotNull(RollingHlsLanInvitation.decode(invitation.get(15, TimeUnit.SECONDS))).use { decoded ->
                        RollingHlsLanSession(route.address, route.prefix, route::current).use { sender ->
                            val sent = sender.send(decoded, {}, {})
                            val expected = if (approved) RollingHlsLanSession.Result.VERIFIED else RollingHlsLanSession.Result.DENIED
                            assertEquals(expected, sent)
                            assertEquals(expected, result.get(30, TimeUnit.SECONDS))
                        }
                    }
                }
            }
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)) }
    }

    @Test fun privateInterfaceLivePlaybackAfterExplicitConsent() = livePlayback(false)

    @Test fun shortLocalClipPlaybackAfterExplicitConsent() = livePlayback(true)

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun livePlayback(localClip: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val route = checkNotNull(RollingHlsLanRoute.capture(context))
        val clip = File(context.getExternalFilesDir("decoder-fixtures"),
            if (localClip) "rolling-receiver.mp4" else "rolling-receiver-live.mp4")
        check(clip.isFile)
        val worker = Executors.newSingleThreadExecutor()
        val invitation = CompletableFuture<String>()
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        try {
            RollingHlsLanSession(route.address, route.prefix, route::current).use { receiver ->
                val result = worker.submit<RollingHlsLanSession.Result> {
                    receiver.receive({ text, _ -> invitation.complete(text) }, { _, _ -> true }, {},
                        live = { socket, peer, remote ->
                            RollingHlsLanLiveBridge.receive(socket, peer, route.address, remote, receiver::active,
                                localClip = localClip) { client ->
                                RollingHlsLanPlayback.play(context, client, receiver::active, { engine ->
                                    if (engine != null) {
                                        texture = SurfaceTexture(10)
                                        surface = Surface(checkNotNull(texture))
                                        engine.setVideoSurface(surface)
                                    } else {
                                        surface?.release()
                                        texture?.release()
                                        surface = null
                                        texture = null
                                    }
                                }, localClip = localClip)
                            }
                        })
                }
                checkNotNull(RollingHlsLanInvitation.decode(invitation.get(15, TimeUnit.SECONDS))).use { decoded ->
                    RollingHlsLanSession(route.address, route.prefix, route::current).use { sender ->
                        val sent = sender.send(decoded, {}, {}, live = { socket, peer, remote ->
                            RollingHlsLanLiveBridge.send(socket, peer, route.address, remote, context.cacheDir,
                                clip.inputStream(), sender::active, localClip = localClip,
                                sharedAdmission = if (localClip) ({ additional -> additional in 0L..RollingHlsBudget.MAX_SEGMENT_BYTES }) else null)
                        })
                        assertEquals(RollingHlsLanSession.Result.VERIFIED, sent)
                        assertEquals(RollingHlsLanSession.Result.VERIFIED, result.get(30, TimeUnit.SECONDS))
                    }
                }
            }
        } finally { worker.shutdownNow(); assertTrue(workersTerminated(worker)) }
    }

    @Test fun authenticatedTelegramReferenceAndReceiverDenial() {
        val route = checkNotNull(RollingHlsLanRoute.capture(InstrumentationRegistry.getInstrumentation().targetContext))
        val worker = Executors.newSingleThreadExecutor()
        val request = TelegramTvHandoff(-100123, 44, 5000, false)
        try {
            for (approved in listOf(false, true)) {
                val invitation = CompletableFuture<String>()
                val received = CompletableFuture<Boolean>()
                RollingHlsLanSession(route.address, route.prefix, route::current).use { receiver ->
                    val result = worker.submit<RollingHlsLanSession.Result> {
                        receiver.receive({ text, _ -> invitation.complete(text) }, { _, _ -> true }, {},
                            live = { socket, peer, _ ->
                                received.complete(RollingLanTelegramHandoff.receive(socket, peer, receiver::active) {
                                    assertEquals(request, it)
                                    approved
                                })
                            })
                    }
                    checkNotNull(RollingHlsLanInvitation.decode(invitation.get(15, TimeUnit.SECONDS))).use { decoded ->
                        RollingHlsLanSession(route.address, route.prefix, route::current).use { sender ->
                            var accepted: Boolean? = null
                            val sent = sender.send(decoded, {}, {}, live = { socket, peer, _ ->
                                accepted = RollingLanTelegramHandoff.send(socket, peer, request, sender::active)
                            })
                            assertEquals(approved, accepted)
                            assertEquals(approved, received.get(15, TimeUnit.SECONDS))
                            assertEquals(RollingHlsLanSession.Result.VERIFIED, sent)
                            assertEquals(RollingHlsLanSession.Result.VERIFIED, result.get(15, TimeUnit.SECONDS))
                        }
                    }
                }
            }
        } finally { worker.shutdownNow(); assertTrue(workersTerminated(worker)) }
    }

    private fun workersTerminated(worker: java.util.concurrent.ExecutorService) = worker.awaitTermination(10, TimeUnit.SECONDS)

    @Test fun cancellationClosesPendingInvitation() {
        val route = checkNotNull(RollingHlsLanRoute.capture(InstrumentationRegistry.getInstrumentation().targetContext))
        val worker = Executors.newSingleThreadExecutor()
        val ready = CompletableFuture<Boolean>()
        try {
            RollingHlsLanSession(route.address, route.prefix, route::current).use { receiver ->
                val result = worker.submit<Boolean> {
                    runCatching { receiver.receive({ _, _ -> ready.complete(true) }, { _, _ -> false }, {}) }.isFailure
                }
                assertTrue(ready.get(15, TimeUnit.SECONDS))
                receiver.close()
                assertTrue(result.get(5, TimeUnit.SECONDS))
            }
        } finally { worker.shutdownNow(); assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS)) }
    }
}
