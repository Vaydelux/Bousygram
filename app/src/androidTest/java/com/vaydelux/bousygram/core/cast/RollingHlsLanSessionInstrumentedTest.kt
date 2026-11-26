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

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun authenticatedRangeRecoveryAfterMidBodyDisconnect() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val route = checkNotNull(RollingHlsLanRoute.capture(context))
        val directory = java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "lan-retry-").toFile()
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        val owner = com.vaydelux.bousygram.core.account.AccountSnapshot(1, 1)
        val current: (com.vaydelux.bousygram.core.account.AccountSnapshot) -> Boolean = { it == owner && active.get() }
        try {
            RollingHlsLiveFileStore(directory, 6_000_000, 8, 32L * 1024 * 1024,
                { android.os.SystemClock.elapsedRealtime() * 1000L }, active::get, { true }).use { store ->
                val fixture = File(context.getExternalFilesDir("decoder-fixtures"), "rolling-receiver.mp4")
                RollingHlsLiveVerifiedInput(fixture.inputStream(), active::get).use { verified ->
                    val init = verified.initialization()
                    assertTrue(init.size > 256)
                    assertTrue(store.installInitialization(init))
                    init.fill(0)
                    val fragment = checkNotNull(verified.next())
                    assertTrue(fragment.bytes.size > 256)
                    assertTrue(store.publish(RollingHlsLiveRetention.Segment(fragment.sequence,
                        fragment.durationUs, fragment.bytes.size.toLong()), fragment.bytes.inputStream()))
                    fragment.bytes.fill(0)
                    assertTrue(store.markComplete(fragment.sequence))
                }
                val resources = RollingHlsLiveLocalTransport(store)
                RollingHlsKeystoreIdentity.create(owner, current).use { receiver ->
                    RollingHlsKeystoreIdentity.create(owner, current).use { sender ->
                        val pins = RollingHlsPeerPairing(current, android.os.SystemClock::elapsedRealtime).use { pairing ->
                            checkNotNull(pairing.issue(owner, receiver.fingerprint())).use { offer ->
                                val proof = checkNotNull(RollingHlsPeerPairing.proofFor(offer, sender.fingerprint()))
                                try { checkNotNull(pairing.confirm(checkNotNull(pairing.receive(sender.fingerprint(), proof)), true)) }
                                finally { proof.fill(0) }
                            }
                        }
                        val serverPeer = RollingHlsPairedTls.create(owner, pins, sender, receiver.certificate(), false, current)
                        val clientPeer = RollingHlsPairedTls.create(owner, pins, receiver, sender.certificate(), true, current)
                        val drops = mutableSetOf<String>() // server worker only
                        val droppedCount = java.util.concurrent.atomic.AtomicInteger()
                        RollingHlsLiveLanServer(route.address, route.address, store, resources, serverPeer,
                            interruptAfterWrite = { path, _ ->
                                if (path != "/index.m3u8" && drops.add(path)) {
                                    droppedCount.incrementAndGet()
                                    true
                                } else false
                            }, permitted = active::get).use { server ->
                            RollingHlsLiveLanClient(route.address, RollingHlsLanInvitation.Endpoint(route.address, server.port),
                                checkNotNull(server.uri().encodedAuthority), clientPeer, active::get).use { client ->
                                fun read(factory: androidx.media3.datasource.DataSource.Factory, path: String): ByteArray {
                                    val spec = androidx.media3.datasource.DataSpec(server.uri().buildUpon().encodedPath(path).build())
                                    val source = factory.createDataSource()
                                    try {
                                        val length = source.open(spec)
                                        val output = java.io.ByteArrayOutputStream()
                                        val buffer = ByteArray(128)
                                        while (true) {
                                            val count = source.read(buffer, 0, buffer.size)
                                            if (count < 0) break
                                            output.write(buffer, 0, count)
                                        }
                                        assertEquals(length, output.size().toLong())
                                        return output.toByteArray()
                                    } finally { source.close() }
                                }
                                read(client.factory(), "/index.m3u8")
                                for (path in listOf("/init.mp4", "/segment/1.m4s")) {
                                    assertArrayEquals(read(resources.factory(), path), read(client.factory(), path))
                                }
                                assertEquals(2, droppedCount.get())
                            }
                        }
                    }
                }
            }
        } finally {
            active.set(false)
            assertTrue(directory.listFiles()?.isEmpty() == true && directory.delete())
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun directSelectedMp4Playback() = directSelectedPlayback("seek-control.mp4")
    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun directSelectedMkvPlayback() = directSelectedPlayback("indexed.mkv")
    @androidx.annotation.OptIn(UnstableApi::class)
    @Test fun directSelectedTsPlayback() = directSelectedPlayback("unindexed.ts")

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun directSelectedPlayback(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val route = checkNotNull(RollingHlsLanRoute.capture(context))
        val worker = Executors.newSingleThreadExecutor()
        try {
            run {
                println("DIRECT_MEDIA_START format=$name")
                val fixture = File(context.getExternalFilesDir("decoder-fixtures"), name)
                assertTrue(fixture.isFile && fixture.length() > 8192)
                val invitation = CompletableFuture<String>()
                var texture: SurfaceTexture? = null
                var surface: Surface? = null
                RollingHlsLanSession(route.address, route.prefix, route::current).use { receiver ->
                    val result = worker.submit<RollingHlsLanSession.Result> {
                        receiver.receive({ text, _ -> invitation.complete(text) }, { _, _ -> true }, {},
                            live = { socket, peer, _ ->
                                RollingLanDirectMedia.receive(socket, peer, receiver::active) { uri, factory ->
                                    val source = factory.createDataSource()
                                    try {
                                        val spec = androidx.media3.datasource.DataSpec.Builder().setUri(uri)
                                            .setPosition(4096).setLength(4096).build()
                                        assertEquals(4096L, source.open(spec))
                                        val actual = ByteArray(4096)
                                        var offset = 0
                                        while (offset < actual.size) {
                                            val n = source.read(actual, offset, actual.size - offset)
                                            assertTrue(n > 0)
                                            offset += n
                                        }
                                        val expected = ByteArray(actual.size)
                                        java.io.RandomAccessFile(fixture, "r").use { it.seek(4096); it.readFully(expected) }
                                        assertArrayEquals(expected, actual)
                                        assertEquals(-1, source.read(actual, 0, 1))
                                    } finally { source.close() }
                                    RollingHlsLanPlayback.playDirect(context, uri, factory, receiver::active, { engine ->
                                        if (engine != null) {
                                            texture = SurfaceTexture(10)
                                            surface = Surface(checkNotNull(texture))
                                            engine.setVideoSurface(surface)
                                        } else {
                                            surface?.release(); texture?.release()
                                            surface = null; texture = null
                                        }
                                    }, { status -> println("DIRECT_MEDIA_STATE format=$name $status") })
                                }
                            })
                    }
                    checkNotNull(RollingHlsLanInvitation.decode(invitation.get(15, TimeUnit.SECONDS))).use { decoded ->
                        RollingHlsLanSession(route.address, route.prefix, route::current).use { sender ->
                            val sent = try { sender.send(decoded, {}, {}, live = { socket, peer, _ ->
                                java.io.RandomAccessFile(fixture, "r").use { file ->
                                    val source = object : RollingLanDirectMedia.Source {
                                        override val size = file.length()
                                        override fun readAt(offset: Long, buffer: ByteArray, count: Int) {
                                            file.seek(offset)
                                            file.readFully(buffer, 0, count)
                                        }
                                    }
                                    val interrupted = java.util.concurrent.atomic.AtomicBoolean(false)
                                    RollingLanDirectMedia.send(socket, peer, source, sender::active,
                                        interruptAfterWrite = { offset ->
                                            offset == 0L && interrupted.compareAndSet(false, true)
                                        })
                                    assertTrue("Expected interrupted data response for $name", interrupted.get())
                                }
                            }) } catch (senderError: Exception) {
                                try { result.get(10, TimeUnit.SECONDS) }
                                catch (receiverError: Exception) { senderError.addSuppressed(receiverError.cause ?: receiverError) }
                                throw AssertionError("Direct playback failed: $name", senderError)
                            }
                            assertEquals(name, RollingHlsLanSession.Result.VERIFIED, sent)
                            assertEquals(name, RollingHlsLanSession.Result.VERIFIED, result.get(30, TimeUnit.SECONDS))
                            println("DIRECT_MEDIA_PASS format=$name")
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
