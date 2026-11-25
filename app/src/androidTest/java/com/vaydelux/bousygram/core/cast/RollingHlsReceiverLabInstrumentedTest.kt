package com.vaydelux.bousygram.core.cast

import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.vaydelux.bousygram.core.account.AccountSnapshot
import com.vaydelux.bousygram.presentation.debug.RollingReceiverLabActivity
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RollingHlsReceiverLabInstrumentedTest {
    @Test fun sweepOnlyUnownedExactQualificationAliases() {
        val owner = AccountSnapshot(1, 1)
        val orphan = "bousygram.qualification.receiver." + UUID.randomUUID()
        val unrelated = "bousygram.qualification.receiver.not-a-uuid-" + UUID.randomUUID()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        fun generate(alias: String) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256).build())
                generateKeyPair()
            }
        }
        try {
            RollingHlsKeystoreIdentity.create(owner) { it == owner }.use { active ->
                generate(orphan)
                generate(unrelated)
                assertTrue(RollingHlsKeystoreIdentity.sweepOrphans() >= 1)
                assertFalse(store.containsAlias(orphan))
                assertTrue(store.containsAlias(unrelated))
                assertTrue(store.containsAlias(active.alias))
                assertEquals(32, active.fingerprint().size)
            }
        } finally {
            store.deleteEntry(orphan)
            store.deleteEntry(unrelated)
        }
    }

    @Test fun syntheticPairingExpiresAndRejectsConsent() {
        var now = 100L
        RollingHlsReceiverLabSession({ true }) { now }.use { lab ->
            val invite = lab.issue()
            invite.bitmap.recycle()
            now = invite.expiresAtMs
            assertThrows(IllegalStateException::class.java) { lab.simulateSender() }
        }
        RollingHlsReceiverLabSession({ true }) { now }.use { lab ->
            lab.issue().bitmap.recycle()
            assertEquals(95, lab.simulateSender().length)
            assertFalse(lab.confirm(false))
            assertThrows(IllegalStateException::class.java) { lab.confirm(true) }
        }
    }

    @Test fun launcherLabRequiresExplicitApprovalAndClosesCredentials() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val activity = instrumentation.startActivitySync(Intent(context, RollingReceiverLabActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            fun click(text: String) {
                val node = device.wait(Until.findObject(By.text(text)), 10_000)
                assertNotNull("Missing lab control: $text", node)
                node.click()
            }
            click("Create invitation")
            assertTrue(device.wait(Until.hasObject(By.desc("One-time receiver invitation")), 10_000))
            click("Simulate sender")
            assertTrue(device.wait(Until.hasObject(By.text("Review the simulated certificate. Approval is required.")), 10_000))
            click("Reject sender")
            assertTrue(device.wait(Until.hasObject(By.text("Sender rejected. Credentials deleted.")), 10_000))
            click("Create invitation")
            click("Simulate sender")
            click("Approve simulated sender")
            assertTrue(device.wait(Until.hasObject(By.text("Local TLS signing preflight passed. Credentials deleted. No network connected.")), 10_000))
            click("Create invitation")
            assertTrue(device.wait(Until.hasObject(By.desc("One-time receiver invitation")), 10_000))
            instrumentation.runOnMainSync { activity.finish() }
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            while (store.aliases().toList().any { it.startsWith("bousygram.qualification.receiver.") } &&
                android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(100)
            assertFalse(store.aliases().toList().any { it.startsWith("bousygram.qualification.receiver.") })
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
