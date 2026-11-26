package com.vaydelux.bousygram.core.cast

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.vaydelux.bousygram.core.account.AccountSnapshot
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.UUID
import javax.security.auth.x500.X500Principal

/** Debug-only per-session Android Keystore identity. No private key export,
 * trust-manager bypass, persistent pairing registry or automatic LAN use.
 * Caller owns close; unowned exact-namespace aliases are swept before new identities. */
internal class RollingHlsKeystoreIdentity private constructor(
    private val owner: AccountSnapshot,
    private val current: (AccountSnapshot) -> Boolean,
    internal val alias: String,
    private val store: KeyStore
) : AutoCloseable {
    private var closed = false

    @Synchronized fun certificate(): X509Certificate {
        check(!closed && current(owner))
        val cert = store.getCertificate(alias) as? X509Certificate ?: error("Receiver identity missing")
        cert.checkValidity()
        check(current(owner))
        return cert
    }

    @Synchronized fun privateKey(): PrivateKey {
        check(!closed && current(owner))
        val key = store.getKey(alias, null) as? PrivateKey ?: error("Receiver key missing")
        check(current(owner) && key.encoded == null)
        return key
    }

    fun fingerprint(): ByteArray = RollingHlsPeerIdentity.fingerprint(certificate())

    @Synchronized override fun close() {
        closed = true
        // Retry deletion if a previous close failed.
        synchronized(registryLock) {
            activeAliases.remove(alias)
            if (store.containsAlias(alias)) store.deleteEntry(alias)
            check(!store.containsAlias(alias))
        }
    }

    companion object {
        private const val PREFIX = "bousygram.qualification.receiver."
        private val registryLock = Any()
        private val activeAliases = mutableSetOf<String>()

        private fun ownedAlias(alias: String): Boolean {
            if (!alias.startsWith(PREFIX)) return false
            val suffix = alias.removePrefix(PREFIX)
            return runCatching { UUID.fromString(suffix).toString() == suffix }.getOrDefault(false)
        }

        /** Single application process only (no remote process is registered).
         * Never touch unrelated aliases or any currently owned identity. */
        internal fun sweepOrphans(): Int = synchronized(registryLock) {
            sweep(KeyStore.getInstance("AndroidKeyStore").apply { load(null) })
        }

        private fun sweep(store: KeyStore): Int {
            val stale = store.aliases().toList().filter { ownedAlias(it) && it !in activeAliases }
            for (alias in stale) {
                store.deleteEntry(alias)
                check(!store.containsAlias(alias))
            }
            return stale.size
        }

        fun create(owner: AccountSnapshot, current: (AccountSnapshot) -> Boolean): RollingHlsKeystoreIdentity =
            synchronized(registryLock) {
            check(current(owner))
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            sweep(store)
            check(current(owner))
            val alias = PREFIX + UUID.randomUUID()
            check(activeAliases.add(alias))
            val result = RollingHlsKeystoreIdentity(owner, current, alias, store)
            try {
                val now = System.currentTimeMillis()
                val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    // Conscrypt signs the already-computed TLS ECDSA digest
                    // using NONEwithECDSA. Restrict this to the ephemeral EC
                    // session key; TLS itself still hashes its transcript.
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_NONE)
                    .setCertificateSubject(X500Principal("CN=Bousygram Qualification Receiver"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(Date(now - 60_000))
                    .setCertificateNotAfter(Date(now + 24L * 60 * 60 * 1000))
                    .build()
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                    initialize(spec)
                    generateKeyPair()
                }
                val certificate = result.certificate()
                certificate.verify(certificate.publicKey)
                result.privateKey()
                check(current(owner))
                result
            } catch (error: Exception) {
                try { result.close() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                throw error
            }
        }
    }
}
