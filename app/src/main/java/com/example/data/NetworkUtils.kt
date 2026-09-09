package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.example.ui.components.AppToast
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkConnectionState {
    ONLINE_STABLE,
    ONLINE_UNSTABLE,
    OFFLINE
}

data class NetworkStatusInfo(
    val state: NetworkConnectionState,
    val isOnline: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val isStable: Boolean,
    val message: String
)

object NetworkUtils {

    @Volatile
    private var lastToastMessage: String = ""
    @Volatile
    private var lastToastTime: Long = 0L

    /**
     * Checks if the device has an active, validated internet connection.
     */
    fun isOnline(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
             capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED))
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Checks Wi-Fi connection and enabled state.
     */
    fun isWifiConnected(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if Wi-Fi hardware is enabled.
     */
    fun isWifiEnabled(context: Context): Boolean {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.isWifiEnabled ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Evaluates current network status and stability in detail.
     */
    fun getDetailedNetworkStatus(context: Context): NetworkStatusInfo {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkStatusInfo(
                    state = NetworkConnectionState.OFFLINE,
                    isOnline = false,
                    isWifi = false,
                    isCellular = false,
                    isStable = false,
                    message = "No internet connection"
                )

            val activeNetwork = connectivityManager.activeNetwork
                ?: return NetworkStatusInfo(
                    state = NetworkConnectionState.OFFLINE,
                    isOnline = false,
                    isWifi = false,
                    isCellular = false,
                    isStable = false,
                    message = "No internet connection"
                )

            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                ?: return NetworkStatusInfo(
                    state = NetworkConnectionState.OFFLINE,
                    isOnline = false,
                    isWifi = false,
                    isCellular = false,
                    isStable = false,
                    message = "No internet connection"
                )

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isNotSuspended = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            val isNotCongested = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)

            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

            if (!hasInternet) {
                return NetworkStatusInfo(
                    state = NetworkConnectionState.OFFLINE,
                    isOnline = false,
                    isWifi = isWifi,
                    isCellular = isCellular,
                    isStable = false,
                    message = "No internet connection"
                )
            }

            // Check if connection is unstable or congested
            val isStable = isValidated && isNotSuspended && isNotCongested

            return if (isStable) {
                NetworkStatusInfo(
                    state = NetworkConnectionState.ONLINE_STABLE,
                    isOnline = true,
                    isWifi = isWifi,
                    isCellular = isCellular,
                    isStable = true,
                    message = "" // Silent when connection is stable
                )
            } else {
                NetworkStatusInfo(
                    state = NetworkConnectionState.ONLINE_UNSTABLE,
                    isOnline = true,
                    isWifi = isWifi,
                    isCellular = isCellular,
                    isStable = false,
                    message = "Unstable internet connection"
                )
            }
        } catch (e: Exception) {
            return NetworkStatusInfo(
                state = NetworkConnectionState.OFFLINE,
                isOnline = false,
                isWifi = false,
                isCellular = false,
                isStable = false,
                message = "No internet connection"
            )
        }
    }

    /**
     * Observes network connectivity status in real time as a Flow of Boolean values.
     */
    fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            trySend(true)
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnline(context))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                         networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED))
                trySend(hasInternet)
            }
        }

        // Send current initial state
        trySend(isOnline(context))

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
        }
    }.distinctUntilChanged()

    /**
     * Observes detailed network status (Stable, Unstable, Offline) and broadcasts changes.
     */
    fun observeDetailedNetworkStatus(context: Context): Flow<NetworkStatusInfo> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            trySend(getDetailedNetworkStatus(context))
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getDetailedNetworkStatus(context))
            }

            override fun onLost(network: Network) {
                trySend(getDetailedNetworkStatus(context))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(getDetailedNetworkStatus(context))
            }
        }

        trySend(getDetailedNetworkStatus(context))

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }.distinctUntilChanged()

    /**
     * Verifies network connection. If offline, shows clean toast: "No internet connection"
     */
    fun requireOnline(context: Context, message: String = "No internet connection"): Boolean {
        if (!isOnline(context)) {
            showToast(context, message)
            return false
        }
        return true
    }

    /**
     * Sanitizes any raw exception/error message so Supabase URLs, SQL errors, or internal technical terms are never displayed to users.
     */
    fun sanitizeErrorMessage(context: Context, rawMessage: String?): String {
        if (!isOnline(context)) {
            return "No internet connection"
        }
        val msg = rawMessage?.trim() ?: ""
        if (msg.isBlank()) {
            return "An unexpected error occurred. Please try again."
        }
        val lower = msg.lowercase()
        if (lower.contains("supabase") ||
            lower.contains("http://") ||
            lower.contains("https://") ||
            lower.contains("rest/v1") ||
            lower.contains(".co") ||
            lower.contains("column") ||
            lower.contains("relation") ||
            lower.contains("postgres") ||
            lower.contains("operator") ||
            lower.contains("42p01") ||
            lower.contains("42883") ||
            lower.contains("42p13") ||
            lower.contains("p_user_id") ||
            lower.contains("nullpointer") ||
            lower.contains("exception") ||
            lower.contains("syntax") ||
            lower.contains("timeout") ||
            lower.contains("connectexception") ||
            lower.contains("socket") ||
            lower.contains("failed to connect") ||
            lower.contains("unknownhost")
        ) {
            val status = getDetailedNetworkStatus(context)
            return if (!status.isOnline) {
                "No internet connection"
            } else if (!status.isStable) {
                "Unstable internet connection"
            } else {
                "Network error. Please try again."
            }
        }
        return msg
    }

    /**
     * Checks current state and toasts appropriate message if unstable or offline.
     */
    fun checkAndToastState(context: Context) {
        val status = getDetailedNetworkStatus(context)
        if (status.message.isNotBlank()) {
            showToast(context, status.message)
        }
    }

    /**
     * Shows a clean standard toast notification with debouncing to prevent duplicates and sanitizes any technical/Supabase URLs.
     */
    fun showToast(context: Context, message: String, isLong: Boolean = false) {
        val cleanMsg = sanitizeErrorMessage(context, message)
        if (cleanMsg.isBlank()) return

        val now = System.currentTimeMillis()
        if (cleanMsg == lastToastMessage && (now - lastToastTime) < 2500) {
            return
        }
        lastToastMessage = cleanMsg
        lastToastTime = now
        AppToast.show(cleanMsg, isLong)
    }
}
