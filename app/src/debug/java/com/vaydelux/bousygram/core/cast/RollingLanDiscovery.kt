package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.annotation.RequiresApi
import java.util.UUID

/** Main-thread foreground owner. No TXT data, certificate, secret, account,
 * media identity or name from the OS is advertised. Never resolves or connects
 * to a discovered peer; visual pairing remains the trust boundary. */
@RequiresApi(33)
internal class RollingLanDiscovery(
    context: Context,
    private val route: RollingHlsLanRoute,
    private val changed: (List<String>) -> Unit,
    private val advertised: (String?) -> Unit = {},
    private val unavailable: () -> Unit = {}
) : AutoCloseable {
    private val manager = checkNotNull(context.getSystemService(NsdManager::class.java))
    private val executor = context.mainExecutor
    private val policy = RollingLanDiscoveryPolicy()
    private var closed = false
    private var discovering = false
    private var registering = false
    private var started = false
    private var registered = false
    private val discovery = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            started = true
            if (closed || !route.current()) stopDiscovery()
        }
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            discovering = false
            started = false
            if (!closed) { changed(emptyList()); unavailable() }
        }
        override fun onDiscoveryStopped(serviceType: String) {
            discovering = false
            started = false
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            // Do not accept callbacks after close; best-effort retry once.
            if (started) {
                started = false
                runCatching { manager.stopServiceDiscovery(this) }
            }
        }
        override fun onServiceFound(info: NsdServiceInfo) {
            if (!closed && route.current() && info.network == route.network &&
                RollingLanDiscoveryPolicy.matchesType(info.serviceType) &&
                policy.found(info.serviceName)) changed(policy.snapshot())
        }
        override fun onServiceLost(info: NsdServiceInfo) {
            if (!closed && info.network == route.network &&
                RollingLanDiscoveryPolicy.matchesType(info.serviceType) &&
                policy.lost(info.serviceName)) changed(policy.snapshot())
        }
    }
    private val registration = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) {
            registered = true
            if (closed || !route.current()) stopAdvertising()
            else if (RollingLanDiscoveryPolicy.validName(info.serviceName)) advertised(info.serviceName)
            else { stopAdvertising(); unavailable() }
        }
        override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            registering = false
            registered = false
            if (!closed) { advertised(null); unavailable() }
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) {
            registering = false
            registered = false
        }
        override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
            if (registered) {
                registered = false
                runCatching { manager.unregisterService(this) }
            }
        }
    }
    fun browse() {
        check(!closed && !discovering && route.current())
        discovering = true
        try {
            manager.discoverServices(RollingLanDiscoveryPolicy.TYPE, NsdManager.PROTOCOL_DNS_SD,
                route.network, executor, discovery)
        } catch (error: Exception) {
            discovering = false
            throw error
        }
    }
    fun advertise(port: Int) {
        check(!closed && !registering && route.current() && port in 1..65535)
        val name = "Bousygram-" + UUID.randomUUID().toString().replace("-", "").take(12)
        val service = NsdServiceInfo().apply {
            serviceName = name
            serviceType = RollingLanDiscoveryPolicy.TYPE
            this.port = port
            network = route.network
        }
        registering = true
        try {
            manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, executor, registration)
        } catch (error: Exception) {
            registering = false
            throw error
        }
    }
    private fun stopDiscovery() {
        if (discovering && started) runCatching { manager.stopServiceDiscovery(discovery) }
    }
    private fun stopAdvertising() {
        if (registering && registered) runCatching { manager.unregisterService(registration) }
    }
    override fun close() {
        if (closed) return
        closed = true
        policy.clear()
        advertised(null)
        stopDiscovery()
        stopAdvertising()
        // Pending registrations/discoveries are stopped in their success callbacks.
    }
}
