package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages "View Once" photo state for conversations.
 * Photos in conversation screen are strictly view once:
 * 1. Fully blurred & obscured before viewing (no content visible).
 * 2. Can only be opened once.
 * 3. Once opened/viewed, permanently marked as viewed and cannot be reopened.
 */
object ViewOncePhotoManager {
    private const val PREFS_NAME = "qivo_view_once_photos_prefs"
    private const val KEY_VIEWED_SET = "viewed_photo_keys"

    private val _viewedKeysFlow = MutableStateFlow<Set<String>>(emptySet())
    val viewedKeysFlow: StateFlow<Set<String>> = _viewedKeysFlow.asStateFlow()

    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes the manager by reading previously viewed photo keys from SharedPreferences.
     */
    fun init(context: Context) {
        if (!isInitialized) {
            val prefs = getPrefs(context)
            val stored = prefs.getStringSet(KEY_VIEWED_SET, emptySet()) ?: emptySet()
            _viewedKeysFlow.value = HashSet(stored)
            isInitialized = true
        }
    }

    /**
     * Generate a unique deterministic key for a photo message.
     */
    fun generatePhotoKey(msgId: Long, senderId: String, createdAt: String, photoPayload: String): String {
        val cleanPayload = photoPayload.removePrefix("[image]").removePrefix("[photo]").trim()
        return if (msgId != 0L) {
            "view_once_id_${msgId}"
        } else {
            "view_once_${senderId}_${createdAt}_${cleanPayload.hashCode()}"
        }
    }

    /**
     * Check if a photo message has already been viewed.
     */
    fun isPhotoViewed(context: Context, photoKey: String): Boolean {
        init(context)
        return _viewedKeysFlow.value.contains(photoKey)
    }

    /**
     * Mark a photo message as viewed and persist to storage.
     */
    fun markPhotoAsViewed(context: Context, photoKey: String) {
        init(context)
        if (photoKey.isBlank()) return

        val current = HashSet(_viewedKeysFlow.value)
        if (current.add(photoKey)) {
            _viewedKeysFlow.value = current
            getPrefs(context).edit().putStringSet(KEY_VIEWED_SET, current).apply()
        }
    }
}
