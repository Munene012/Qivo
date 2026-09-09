package com.example.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object PesapalConfig {
    private const val PREFS_NAME = "qivo_pesapal_prefs"
    private const val KEY_CONSUMER_KEY = "pesapal_consumer_key"
    private const val KEY_CONSUMER_SECRET = "pesapal_consumer_secret"
    private const val KEY_IPN_URL = "pesapal_ipn_url"
    private const val KEY_IPN_ID = "pesapal_ipn_id"
    private const val KEY_IS_LIVE = "pesapal_is_live"

    // Live and Sandbox endpoints
    const val LIVE_BASE_URL = "https://pay.pesapal.com/v3"
    const val SANDBOX_BASE_URL = "https://cybqa.pesapal.com/pesapalv3"
    const val DEFAULT_CALLBACK_URL = "https://nfenuymzzvbxebqmtdqz.supabase.co/functions/v1/pesapal?action=callback"
    const val DEFAULT_IPN_URL = "https://nfenuymzzvbxebqmtdqz.supabase.co/functions/v1/pesapal?action=ipn"

    // By default, Live mode is active as requested
    var isLive: Boolean by mutableStateOf(true)

    // User can customize or supply live keys; provide default live keys/placeholders
    var consumerKey: String by mutableStateOf("")
    var consumerSecret: String by mutableStateOf("")
    var ipnUrl: String by mutableStateOf(DEFAULT_IPN_URL)
    var ipnId: String by mutableStateOf("")
    var callbackUrl: String by mutableStateOf(DEFAULT_CALLBACK_URL)

    val baseUrl: String
        get() = if (isLive) LIVE_BASE_URL else SANDBOX_BASE_URL

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isLive = prefs.getBoolean(KEY_IS_LIVE, true)
        val savedKey = prefs.getString(KEY_CONSUMER_KEY, "") ?: ""
        val savedSecret = prefs.getString(KEY_CONSUMER_SECRET, "") ?: ""
        val savedIpnUrl = prefs.getString(KEY_IPN_URL, DEFAULT_IPN_URL) ?: DEFAULT_IPN_URL
        val savedIpnId = prefs.getString(KEY_IPN_ID, "") ?: ""

        consumerKey = savedKey
        consumerSecret = savedSecret
        ipnUrl = savedIpnUrl
        ipnId = savedIpnId
    }

    fun saveConfig(
        context: Context,
        key: String,
        secret: String,
        live: Boolean = true,
        customIpnUrl: String = DEFAULT_IPN_URL,
        customIpnId: String = ""
    ) {
        consumerKey = key.trim()
        consumerSecret = secret.trim()
        isLive = live
        ipnUrl = customIpnUrl.trim().ifEmpty { DEFAULT_IPN_URL }
        if (customIpnId.isNotBlank()) {
            ipnId = customIpnId.trim()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CONSUMER_KEY, consumerKey)
            .putString(KEY_CONSUMER_SECRET, consumerSecret)
            .putBoolean(KEY_IS_LIVE, isLive)
            .putString(KEY_IPN_URL, ipnUrl)
            .putString(KEY_IPN_ID, ipnId)
            .apply()
    }

    fun saveRegisteredIpnId(context: Context, newIpnId: String, newIpnUrl: String = ipnUrl) {
        ipnId = newIpnId.trim()
        ipnUrl = newIpnUrl.trim()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_IPN_ID, ipnId)
            .putString(KEY_IPN_URL, ipnUrl)
            .apply()
    }
}
