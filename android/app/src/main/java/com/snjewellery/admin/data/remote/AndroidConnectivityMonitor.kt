package com.snjewellery.admin.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.snjewellery.admin.domain.net.ConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `ConnectivityManager`, as a flow.
 *
 * ── Validated, not merely connected ──────────────────────────────────
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] is checked as well as
 * `INTERNET`. Without it, a Wi-Fi network the phone has associated with
 * but which has no working route reads as online — and the shop's
 * back room, where a signal comes and goes, is exactly where that
 * happens. It is still only a hint; see the interface.
 *
 * The callback is registered for as long as something collects and
 * unregistered when nothing does, which `callbackFlow`'s [awaitClose]
 * makes automatic rather than something to remember.
 */
@Singleton
class AndroidConnectivityMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) : ConnectivityMonitor {

    override val online: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)

        if (manager == null) {
            // Nothing to watch. Reporting "online" rather than "offline"
            // is deliberate: a sync that tries and fails tells the owner
            // something true, whereas one that never tries would leave a
            // draft waiting forever for an event that cannot arrive.
            send(true)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(manager.isOnline())
            }

            override fun onLost(network: Network) {
                trySend(manager.isOnline())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                // The one that matters on mobile data: a network becomes
                // available first and validated a moment later, and only
                // the second is worth acting on.
                trySend(manager.isOnline())
            }
        }

        send(manager.isOnline())
        manager.registerDefaultNetworkCallback(callback)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .conflate()
        .distinctUntilChanged()

    private fun ConnectivityManager.isOnline(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
