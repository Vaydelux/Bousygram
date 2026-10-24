package com.vaydelux.bousygram.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.vaydelux.bousygram.core.tdlib.client.TdLibClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

sealed class AppNetworkState {
    data object Offline : AppNetworkState()
    data class WiFi(val isMetered: Boolean = false) : AppNetworkState()
    data class Cellular(val isRoaming: Boolean = false) : AppNetworkState()
    data object Other : AppNetworkState()

    val isCellular: Boolean get() = this is Cellular
    val isConnected: Boolean get() = this !is Offline
}

object NetworkConnectivityObserver {
    private const val TAG = "NetworkObserver"

    private val _networkState = MutableStateFlow<AppNetworkState>(AppNetworkState.Offline)
    val networkState: StateFlow<AppNetworkState> = _networkState.asStateFlow()

    val isCellular: Boolean get() = _networkState.value.isCellular
    val isConnected: Boolean get() = _networkState.value.isConnected

    private var connectivityManager: ConnectivityManager? = null
    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkState()
        }

        override fun onLost(network: Network) {
            updateNetworkState()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateNetworkState()
        }
    }

    fun init(context: Context) {
        if (isRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = cm

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, networkCallback)
            isRegistered = true
            updateNetworkState(forceDispatch = true)
            Log.i(TAG, "NetworkConnectivityObserver successfully registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback", e)
        }
    }

    private fun updateNetworkState(forceDispatch: Boolean = false) {
        val cm = connectivityManager ?: return
        val activeNetwork = cm.activeNetwork
        val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val newState = when {
            capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> {
                AppNetworkState.Offline
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                AppNetworkState.WiFi(isMetered)
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val isRoaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                AppNetworkState.Cellular(isRoaming)
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                AppNetworkState.WiFi(isMetered = false)
            }
            else -> AppNetworkState.Other
        }

        val oldState = _networkState.value
        if (forceDispatch || oldState != newState) {
            _networkState.value = newState
            Log.i(TAG, "Network state changed to: $newState (forceDispatch=$forceDispatch)")
            if (oldState is AppNetworkState.WiFi && newState is AppNetworkState.Cellular) {
                Log.i(TAG, "Wi-Fi to cellular handoff detected: rotating stream capability tokens")
                com.vaydelux.bousygram.core.tdlib.server.StreamAccessRegistry.rotateTokens()
            }
            dispatchNetworkTypeToTdLib(newState)
        }
    }

    private fun dispatchNetworkTypeToTdLib(state: AppNetworkState) {
        val tdNetworkType: TdApi.NetworkType = when (state) {
            is AppNetworkState.Offline -> TdApi.NetworkTypeNone()
            is AppNetworkState.WiFi -> TdApi.NetworkTypeWiFi()
            is AppNetworkState.Cellular -> TdApi.NetworkTypeMobile()
            is AppNetworkState.Other -> TdApi.NetworkTypeOther()
        }

        try {
            TdLibClient.client.send(TdApi.SetNetworkType(tdNetworkType)) { result ->
                if (result is TdApi.Ok) {
                    Log.d(TAG, "Successfully synced SetNetworkType to TDLib: ${tdNetworkType.javaClass.simpleName}")
                } else if (result is TdApi.Error) {
                    Log.w(TAG, "Error syncing SetNetworkType to TDLib: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception dispatching SetNetworkType to TDLib", e)
        }
    }

    /** Forces an immediate refresh and sync of network state to TDLib */
    fun refresh() {
        updateNetworkState(forceDispatch = true)
    }
}
