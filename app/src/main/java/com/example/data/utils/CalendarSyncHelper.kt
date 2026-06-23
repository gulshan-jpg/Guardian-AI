package com.example.data.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import com.example.data.database.Task
import java.util.TimeZone

object CalendarSyncHelper {

    fun syncTaskToCalendar(context: Context, task: Task): Long {
        try {
            val contentUri: Uri = CalendarContract.Events.CONTENT_URI
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, 1) // Default primary calendar is typically 1
                put(CalendarContract.Events.TITLE, "🛡️ [Guardian] ${task.title}")
                put(CalendarContract.Events.DESCRIPTION, "Category: ${task.category}\nGuardian Risk Level: ${task.riskLevel} (${task.riskScore}%)\nNotes: ${task.notes}")
                put(CalendarContract.Events.DTSTART, task.deadline - 3600000) // set block start 1 hour before deadline
                put(CalendarContract.Events.DTEND, task.deadline)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
            }

            val uri = context.contentResolver.insert(contentUri, values)
            if (uri != null) {
                val eventId = uri.lastPathSegment?.toLong() ?: -1L
                Log.d("CalendarSyncHelper", "Task synced successfully to Android system calendar. Event ID: $eventId")
                
                // Add an alarm/reminder
                val reminderUri = CalendarContract.Reminders.CONTENT_URI
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    put(CalendarContract.Reminders.MINUTES, 30) // 30 minutes before
                }
                context.contentResolver.insert(reminderUri, reminderValues)
                return eventId
            }
        } catch (e: Exception) {
            Log.e("CalendarSyncHelper", "Failed to sync task events to device calendar", e)
        }
        return -1L
    }
}
