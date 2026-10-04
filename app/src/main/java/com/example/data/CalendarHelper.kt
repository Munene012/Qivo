package com.example.data

import android.content.Context
import android.provider.CalendarContract
import android.content.ContentValues
import android.net.Uri
import com.example.ui.components.AppToast
import java.util.TimeZone

object CalendarHelper {
    fun addCoinCollectionReminder(context: Context) {
        try {
            val cr = context.contentResolver
            val values = ContentValues().apply {
                // Set reminder for 24 hours from now
                put(CalendarContract.Events.DTSTART, System.currentTimeMillis() + 24 * 60 * 60 * 1000)
                put(CalendarContract.Events.DTEND, System.currentTimeMillis() + 24 * 60 * 60 * 1000 + 30 * 60 * 1000)
                put(CalendarContract.Events.TITLE, "Collect QIVO Coins! 🪙")
                put(CalendarContract.Events.DESCRIPTION, "Daily reminder to claim your coins in the QIVO app.")
                put(CalendarContract.Events.CALENDAR_ID, 1) // Default calendar
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val uri: Uri? = cr.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val eventId = uri.lastPathSegment?.toLongOrNull()
                if (eventId != null) {
                    val remValues = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        put(CalendarContract.Reminders.MINUTES, 15) // 15 min before
                    }
                    cr.insert(CalendarContract.Reminders.CONTENT_URI, remValues)
                }
                AppToast.show("Reminder set in your calendar for tomorrow! 📅")
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarHelper", "Error adding reminder", e)
            AppToast.show("Failed to set reminder. Please check permissions.")
        }
    }
}
