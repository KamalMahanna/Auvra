package com.auvra.app.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkIsOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            _isOnline.value = checkIsOnline()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                     networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                     networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            _isOnline.value = hasInternet
        }
    }

    init {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, networkCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}", e)
        }
    }

    fun isOnlineNow(): Boolean = checkIsOnline()

    private fun checkIsOnline(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        private const val TAG = "NetworkMonitor"

        fun isNetworkError(e: Throwable): Boolean {
            var cause: Throwable? = e
            while (cause != null) {
                val name = cause.javaClass.simpleName
                val msg = cause.message ?: ""
                if (name.contains("UnknownHost", ignoreCase = true) ||
                    name.contains("SocketTimeout", ignoreCase = true) ||
                    name.contains("ConnectException", ignoreCase = true) ||
                    name.contains("NoRouteToHost", ignoreCase = true) ||
                    msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("Failed to connect", ignoreCase = true) ||
                    msg.contains("Software caused connection abort", ignoreCase = true) ||
                    msg.contains("network", ignoreCase = true)
                ) {
                    return true
                }
                cause = cause.cause
            }
            return false
        }
    }
}
