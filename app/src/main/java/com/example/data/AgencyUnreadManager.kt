package com.example.data

import android.content.Context

object AgencyUnreadManager {
    private const val PREFS_NAME = "qivo_agency_unread_prefs"

    fun getLastReadMessageId(context: Context, agencyId: String, userId: String): Long {
        if (agencyId.isBlank() || userId.isBlank()) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("last_read_${agencyId}_$userId", 0L)
    }

    fun markAsRead(context: Context, agencyId: String, userId: String, latestMessageId: Long) {
        if (agencyId.isBlank() || userId.isBlank() || latestMessageId <= 0L) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getLong("last_read_${agencyId}_$userId", 0L)
        if (latestMessageId > current) {
            prefs.edit().putLong("last_read_${agencyId}_$userId", latestMessageId).apply()
        }
    }

    fun getUnreadCount(context: Context, agencyId: String, userId: String, messages: List<AgencyGroupMessage>): Int {
        if (agencyId.isBlank() || userId.isBlank() || messages.isEmpty()) return 0
        val lastRead = getLastReadMessageId(context, agencyId, userId)
        return messages.count { it.id > lastRead && it.senderId != userId }
    }
}
