package com.example.data.repository

import com.example.data.database.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskRepository(
    private val taskDao: TaskDao,
    private val geminiRepository: GeminiRepository
) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
    val allLogs: Flow<List<ProductivityLog>> = taskDao.getProductivityLogs()
    val extractedMessages: Flow<List<ExtractedMessage>> = taskDao.getExtractedMessages()

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)
    fun getTaskFlowById(id: Long): Flow<Task?> = taskDao.getTaskFlowById(id)

    suspend fun insertTask(task: Task): Long = taskDao.insertTask(task)

    suspend fun updateTask(task: Task) = taskDao.updateTask(task)

    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)

    suspend fun deleteTaskById(id: Long) {
        taskDao.deleteTaskById(id)
        taskDao.deleteSubtasksForTask(id)
    }

    // --- SubTasks ---
    fun getSubtasksForTask(taskId: Long): Flow<List<SubTask>> = taskDao.getSubtasksForTask(taskId)
    suspend fun getSubtasksForTaskSync(taskId: Long): List<SubTask> = taskDao.getSubtasksForTaskSync(taskId)

    suspend fun updateSubtask(subTask: SubTask) = taskDao.updateSubtask(subTask)
    suspend fun updateSubtaskStatus(id: Long, completed: Boolean) = taskDao.updateSubtaskStatus(id, completed)

    // --- Productivity Logs ---
    suspend fun insertProductivityLog(log: ProductivityLog) = taskDao.insertProductivityLog(log)
    suspend fun getProductivityLogsSync(): List<ProductivityLog> = taskDao.getProductivityLogsSync()

    // --- Extracted Message List ---
    suspend fun insertExtractedMessage(message: ExtractedMessage): Long = taskDao.insertExtractedMessage(message)
    suspend fun markMessageProcessed(id: Long, taskId: Long) = taskDao.markMessageProcessed(id, taskId)

    // --- Gemini Call Integration ---
    suspend fun suggestTaskPriority(task: Task, pendingCount: Int): Pair<Int, String> {
        val deadlineDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(task.deadline))
        return geminiRepository.calculatePriority(
            taskName = task.title,
            deadlineStr = deadlineDate,
            category = task.category,
            effortMinutes = task.estimatedEffort,
            pendingTasksCount = pendingCount
        )
    }

    suspend fun calculateTaskRiskScore(task: Task, avgHours: Float, pendingCount: Int): RiskAnalysisResult {
        val deadlineDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(task.deadline))
        return geminiRepository.calculateRiskScore(
            taskName = task.title,
            deadlineStr = deadlineDate,
            priority = task.priority,
            effortMinutes = task.estimatedEffort,
            avgHours = avgHours,
            pendingCount = pendingCount
        )
    }

    suspend fun generateTaskExecutionPlan(task: Task, availableHours: Float): ExecutionPlanResult {
        val deadlineDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(task.deadline))
        val plan = geminiRepository.generateExecutionPlan(
            taskName = task.title,
            deadlineStr = deadlineDate,
            availableHours = availableHours
        )
        return plan
    }

    suspend fun saveExecutionPlan(taskId: Long, planResult: ExecutionPlanResult) {
        // Find existing subtasks and clear them
        taskDao.deleteSubtasksForTask(taskId)

        // Save new subtasks
        val subtaskList = mutableListOf<SubTask>()
        planResult.dailyPlan.forEach { dayPlan ->
            dayPlan.tasks.forEach { planTask ->
                val durationMin = try {
                    planTask.duration.replace(Regex("[^0-9]"), "").toInt()
                } catch (e: Exception) {
                    20
                }
                subtaskList.add(
                    SubTask(
                        parentTaskId = taskId,
                        day = dayPlan.day,
                        scheduledTime = planTask.time,
                        action = planTask.action,
                        durationMinutes = durationMin,
                        isCompleted = false
                    )
                )
            }
        }
        taskDao.insertSubtasks(subtaskList)

        // Update task with executionPlanJson
        val task = taskDao.getTaskById(taskId)
        if (task != null) {
            // Build simple plan JSON string for reference if needed
            val tipsStr = planResult.successTips.joinToString(separator = "||")
            val planSummary = "Time: ${planResult.totalEstimatedTime} | Action: ${planResult.immediateAction} | Tips: $tipsStr"
            taskDao.updateTask(task.copy(executionPlanJson = planSummary))
        }
    }

    suspend fun parseVoice(voiceText: String): VoiceIntentResult {
        return geminiRepository.parseVoiceCommand(voiceText)
    }

    suspend fun getDailyRecommendations(
        completionHistory: String,
        productiveHours: String,
        taskListJson: String
    ): RecommendationsResult {
        return geminiRepository.getPersonalizedRecommendations(
            completionHistory,
            productiveHours,
            taskListJson
        )
    }
}
