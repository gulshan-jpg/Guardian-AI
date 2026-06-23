package com.example.data.utils

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.database.Task
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

object NotificationHelper {
    private const val RISK_CHANNEL_ID = "guardian_risk_alerts"
    private const val GENERAL_CHANNEL_ID = "guardian_general_updates"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val riskChannel = NotificationChannel(
                RISK_CHANNEL_ID,
                "Guardian Risk Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent notifications when tasks are at extreme risk of failure"
            }

            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "Guardian Briefings & Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily briefs, streak accomplishments and timing reminders"
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(riskChannel)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }

    @SuppressLint("MissingPermission")
    fun showRiskAlert(context: Context, taskId: Long, taskTitle: String, riskScore: Int, riskLevel: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("target_screen", "ai_plan")
            putExtra("task_id", taskId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, RISK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // Standard Android resource icon
            .setContentTitle("⚠️ CRITICAL RISK WARNING")
            .setContentText("\"$taskTitle\" is now at $riskLevel risk ($riskScore% score)! Tap for AI EMERGENCY recovery plan.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(100 + taskId.toInt(), builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    fun showGeneralBriefing(context: Context, totalTasks: Int, riskCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = "Your daily briefing is ready. You have $totalTasks tasks today, with $riskCount at high risk of being missed."
        val builder = NotificationCompat.Builder(context, GENERAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🛡️ Guardian Morning Briefing")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(201, builder.build())
        }
    }

    @SuppressLint("MissingPermission")
    fun showStreakMilestone(context: Context, streakDays: Int) {
        val builder = NotificationCompat.Builder(context, GENERAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle("🏆 Bulletproof Streak Milestone!")
            .setContentText("You completed your goals successfully! Current execution streak: $streakDays days.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(202, builder.build())
        }
    }

    fun checkAndNotifyUpcomingDeadlines(context: Context, tasks: List<Task>) {
        val sharedPrefs = context.getSharedPreferences("guardian_notifications", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000L

        tasks.forEach { task ->
            if (task.isCompleted) return@forEach

            val isTargetFreq = task.frequency == "Weekly" || 
                               task.frequency == "Monthly" || 
                               task.frequency == "Custom Dates"
            if (!isTargetFreq) return@forEach

            // 1. Check standard deadline
            val diff = task.deadline - now
            // One day before the deadline (within 24 hours of it)
            if (diff in 0..oneDayInMillis) {
                val key = "notified_deadline_${task.id}_${task.deadline}"
                if (!sharedPrefs.getBoolean(key, false)) {
                    val remainingHours = diff / (60 * 60 * 1000)
                    showDeadlineNotification(
                        context,
                        task.id,
                        task.title,
                        "⏰ Upcoming Deadline Task!",
                        "Your ${task.frequency} task \"${task.title}\" is due in $remainingHours hours."
                    )
                    sharedPrefs.edit().putBoolean(key, true).apply()
                }
            }

            // 2. If Custom Dates frequency, also check individual custom dates
            if (task.frequency == "Custom Dates" && !task.customDates.isNullOrBlank()) {
                val dateStrings = task.customDates.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                dateStrings.forEach { dateStr ->
                    try {
                        val dateObj = sdf.parse(dateStr)
                        if (dateObj != null) {
                            val customDeadline = dateObj.time
                            val cDiff = customDeadline - now
                            if (cDiff in 0..oneDayInMillis) {
                                val key = "notified_custom_${task.id}_$dateStr"
                                if (!sharedPrefs.getBoolean(key, false)) {
                                    val remainingHours = cDiff / (60 * 60 * 1000)
                                    showDeadlineNotification(
                                        context,
                                        task.id + 1000000, // Offset to avoid ID collision
                                        task.title,
                                        "⏰ Custom Date Alert!",
                                        "Your custom target date ($dateStr) for task \"${task.title}\" is due in $remainingHours hours."
                                    )
                                    sharedPrefs.edit().putBoolean(key, true).apply()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("NotificationHelper", "Failed to parse custom date: $dateStr", e)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDeadlineNotification(context: Context, id: Long, taskTitle: String, alertTitle: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, GENERAL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(alertTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            notify(3000 + id.toInt(), builder.build())
        }
    }
}
