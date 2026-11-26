package com.vaydelux.bousygram.core.cast

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import java.net.Inet4Address

/** Explicit opt-in on the currently selected Wi-Fi/Ethernet network only.
 * No discovery broadcast, mobile data, VPN, hostname or public address. */
internal class RollingHlsLanRoute private constructor(
    private val context: Context, internal val network: Network,
    val address: Inet4Address, val prefix: Int
) {
    fun contains(peer: Inet4Address) = RollingHlsLanInvitation.sameSubnet(address, peer, prefix)
    fun current(): Boolean = try {
        if (!hasPermission(context)) false else {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            manager != null && manager.activeNetwork == network &&
                allowedTransport(manager.getNetworkCapabilities(network)) &&
                manager.getLinkProperties(network)?.linkAddresses?.any {
                    it.address == address && it.prefixLength == prefix
                } == true
        }
    } catch (_: Exception) { false }

    companion object {
        const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

        private fun allowedTransport(caps: NetworkCapabilities?): Boolean =
            caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))

        fun capture(context: Context): RollingHlsLanRoute? {
            if (!hasPermission(context)) return null
            val app = context.applicationContext
            val manager = app.getSystemService(ConnectivityManager::class.java) ?: return null
            val network = manager.activeNetwork ?: return null
            if (!allowedTransport(manager.getNetworkCapabilities(network))) return null
            val local = manager.getLinkProperties(network)?.linkAddresses?.firstOrNull {
                val address = it.address
                address is Inet4Address && RollingHlsLanInvitation.isPrivate(address) && it.prefixLength in 1..32
            } ?: return null
            return RollingHlsLanRoute(app, network, local.address as Inet4Address, local.prefixLength)
                .takeIf { it.current() }
        }
    }
}
