package com.vaydelux.bousygram.tdlib

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real JNI path without faking Telegram responses or credentials. */
@RunWith(AndroidJUnit4::class)
class TdLibNativeSmokeTest {
    @Test
    fun nativeClient_handlesVersionAuthAndFileRequests() {
        assertTrue("TDLib JNI was not loaded", Client.isNativeAvailable())

        val results = CountDownLatch(3)
        val updates = CountDownLatch(1)
        val observed = mutableListOf<TdApi.Object>()
        val client = Client.create(
            { update -> updates.countDown() },
            { error -> throw AssertionError("TDLib update callback failed", error) },
            { error -> throw AssertionError("TDLib result callback failed", error) }
        )

        client.send(TdApi.GetOption("version")) { result ->
            synchronized(observed) { observed += result }
            results.countDown()
        }
        client.send(TdApi.GetAuthorizationState()) { result ->
            synchronized(observed) { observed += result }
            results.countDown()
        }
        client.send(TdApi.GetFile(1)) { result ->
            synchronized(observed) { observed += result }
            results.countDown()
        }

        assertTrue("TDLib did not return genuine JNI responses", results.await(20, TimeUnit.SECONDS))
        assertNotNull(Client.execute(TdApi.GetOption("version")))
        assertTrue("Expected version/auth/file responses", synchronized(observed) { observed.size >= 3 })
        assertTrue("TDLib did not deliver an update", updates.await(20, TimeUnit.SECONDS))

        val closed = CountDownLatch(1)
        client.send(TdApi.Close()) { result ->
            assertEquals(TdApi.Ok.CONSTRUCTOR, result.constructor)
            closed.countDown()
        }
        assertTrue("TDLib client did not close", closed.await(20, TimeUnit.SECONDS))
    }
}
