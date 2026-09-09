package com.example.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SupabaseConfig {
    private const val PREFS_NAME = "qivo_supabase_prefs"
    private const val KEY_URL = "supabase_url"
    private const val KEY_KEY = "supabase_key"
    private const val KEY_GOOGLE_CLIENT_ID = "supabase_google_client_id"

    // Default configuration
    var supabaseUrl: String by mutableStateOf("https://nfenuymzzvbxebqmtdqz.supabase.co")
    var supabaseApiKey: String by mutableStateOf("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5mZW51eW16enZieGVicW10ZHF6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODY2MjMyNjEsImV4cCI6MjEwMjE5OTI2MX0.tkoamavx5fuF7WDag77vUczNKs6hSabcmv_gPde-2-8")
    var googleWebClientId: String by mutableStateOf("862712666541-vagavnjvmi48npc9ibnc86ea0u4q8ouf.apps.googleusercontent.com")

    // Compatibility alias
    var supabaseAnonKey: String
        get() = supabaseApiKey
        set(value) { supabaseApiKey = value }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUrl = prefs.getString(KEY_URL, null)
        val savedKey = prefs.getString(KEY_KEY, null)
        val savedClientId = prefs.getString(KEY_GOOGLE_CLIENT_ID, null)
        if (!savedUrl.isNullOrEmpty()) supabaseUrl = savedUrl
        if (!savedKey.isNullOrEmpty()) supabaseApiKey = savedKey
        if (!savedClientId.isNullOrEmpty() && savedClientId != "862712666541-12ef8ke7467g703g6iqe0p6fftm6sg2m.apps.googleusercontent.com") {
            googleWebClientId = savedClientId
        } else {
            googleWebClientId = "862712666541-vagavnjvmi48npc9ibnc86ea0u4q8ouf.apps.googleusercontent.com"
            prefs.edit().putString(KEY_GOOGLE_CLIENT_ID, googleWebClientId).apply()
        }

        // Ensure any legacy service_role keys stored locally are cleaned up
        if (prefs.contains("supabase_service_role_key")) {
            prefs.edit().remove("supabase_service_role_key").apply()
        }
    }

    fun save(
        context: Context,
        url: String,
        apiKey: String,
        clientId: String? = null
    ) {
        supabaseUrl = url.trim().removeSuffix("/")
        supabaseApiKey = apiKey.trim()
        if (!clientId.isNullOrBlank()) googleWebClientId = clientId.trim()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_URL, supabaseUrl)
            .putString(KEY_KEY, supabaseApiKey)
            .remove("supabase_service_role_key")
            .putString(KEY_GOOGLE_CLIENT_ID, googleWebClientId)
            .apply()
    }
}
