package com.example.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.AppDatabase
import com.example.data.utils.NotificationHelper
import kotlinx.coroutines.flow.first

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("NotificationWorker", "Guardian executing background risk scan...")
        val database = AppDatabase.getDatabase(applicationContext)
        val taskDao = database.taskDao()

        try {
            // Retrieve all unresolved tasks
            val tasks = taskDao.getAllTasks().first()
            val incompleteTasks = tasks.filter { !it.isCompleted }

            if (incompleteTasks.isNotEmpty()) {
                val highRiskCount = incompleteTasks.count { it.riskScore > 70 }
                
                // Show Briefing
                NotificationHelper.showGeneralBriefing(
                    applicationContext,
                    incompleteTasks.size,
                    highRiskCount
                )

                // Check for weekly, monthly, and custom date upcoming deadlines
                NotificationHelper.checkAndNotifyUpcomingDeadlines(applicationContext, tasks)

                // Trigger individual risk warnings for critical tasks
                incompleteTasks.forEach { task ->
                    if (task.riskScore > 75) {
                        NotificationHelper.showRiskAlert(
                            context = applicationContext,
                            taskId = task.id,
                            taskTitle = task.title,
                            riskScore = task.riskScore,
                            riskLevel = task.riskLevel
                        )
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("NotificationWorker", "Error executing scan in worker", e)
            return Result.failure()
        }
    }
}
