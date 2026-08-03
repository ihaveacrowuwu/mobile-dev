package com.nauhaan.skycast.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes whether the device currently has validated internet access.
 *
 * An interface so that repository and view model tests can drive connectivity
 * synthetically, "what does the Today screen look like offline?" becomes a unit
 * test rather than a manual aeroplane-mode check.
 */
interface NetworkMonitor {
    /** Emits the current state immediately, then on every change. */
    val isOnline: Flow<Boolean>
}

/**
 * [NetworkMonitor] backed by [ConnectivityManager].
 *
 * Uses `NET_CAPABILITY_VALIDATED` rather than merely "connected", so a captive
 * portal or a Wi-Fi network with no upstream is correctly reported as offline.
 */
class ConnectivityNetworkMonitor(private val context: Context) : NetworkMonitor {
    override val isOnline: Flow<Boolean> =
        callbackFlow {
            val manager = context.getSystemService<ConnectivityManager>()
            if (manager == null) {
                // Without ConnectivityManager we cannot tell; assume online and let the
                // request itself fail rather than blocking the user pre-emptively.
                trySend(true)
                awaitClose { }
                return@callbackFlow
            }

            // Track networks in a set: losing Wi-Fi while cellular remains must not
            // report offline.
            val available = mutableSetOf<Network>()

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        available += network
                        trySend(true)
                    }

                    override fun onLost(network: Network) {
                        available -= network
                        trySend(available.isNotEmpty())
                    }

                    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        if (validated) available += network else available -= network
                        trySend(available.isNotEmpty())
                    }
                }

            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()

            manager.registerNetworkCallback(request, callback)

            // Seed with the current state so collectors do not wait for a change.
            trySend(manager.isCurrentlyOnline())

            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.conflate()
            .distinctUntilChanged()

    private fun ConnectivityManager.isCurrentlyOnline(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
