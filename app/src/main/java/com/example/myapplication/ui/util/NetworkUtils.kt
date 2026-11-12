package com.example.myapplication.ui.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

object NetworkUtils {

    /**
     * Synchronous one-shot check if an active, validated internet connection is available.
     */
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Reactive Compose state observing online/offline status using [ConnectivityManager.NetworkCallback].
     */
    @Composable
    fun rememberIsOnline(): State<Boolean> {
        val context = LocalContext.current
        return produceState(initialValue = isInternetAvailable(context), context) {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

            if (connectivityManager == null) {
                value = false
                return@produceState
            }

            value = isInternetAvailable(context)

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    value = true
                }

                override fun onLost(network: Network) {
                    value = isInternetAvailable(context)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    val isValidated = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    ) && networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_VALIDATED
                    )
                    value = isValidated
                }
            }

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            try {
                connectivityManager.registerNetworkCallback(request, callback)
                awaitDispose {
                    try {
                        connectivityManager.unregisterNetworkCallback(callback)
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
                value = isInternetAvailable(context)
            }
        }
    }
}
