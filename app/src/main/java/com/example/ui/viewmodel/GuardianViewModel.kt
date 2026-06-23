package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ExtractedMessage
import com.example.data.database.ProductivityLog
import com.example.data.database.SubTask
import com.example.data.database.Task
import com.example.data.repository.ExtractedTaskData
import com.example.data.repository.GeminiRepository
import com.example.data.repository.TaskRepository
import com.example.data.utils.CalendarSyncHelper
import com.example.data.utils.NotificationHelper
import com.example.data.utils.SpeechHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GuardianViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val geminiRepo = GeminiRepository()
    private val repo = TaskRepository(db.taskDao(), geminiRepo)

    // --- State Management ---
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _extractedMessages = MutableStateFlow<List<ExtractedMessage>>(emptyList())
    val extractedMessages: StateFlow<List<ExtractedMessage>> = _extractedMessages.asStateFlow()

    private val _productivityLogs = MutableStateFlow<List<ProductivityLog>>(emptyList())
    val productivityLogs: StateFlow<List<ProductivityLog>> = _productivityLogs.asStateFlow()

    // Active Task subtasks
    private val _currentSubtasks = MutableStateFlow<List<SubTask>>(emptyList())
    val currentSubtasks: StateFlow<List<SubTask>> = _currentSubtasks.asStateFlow()

    private val _selectedTask = MutableStateFlow<Task?>(null)
    val selectedTask: StateFlow<Task?> = _selectedTask.asStateFlow()

    var editingTask by mutableStateOf<Task?>(null)

    // Voice & TTS
    private var speechHelper: SpeechHelper? = null
    var isListening by mutableStateOf(false)
    var recognizedText by mutableStateOf("")
    var speakFeedback by mutableStateOf("")

    // Profile Settings States
    var userName by mutableStateOf("David")
    var userAge by mutableStateOf("25")
    var userPhone by mutableStateOf("+1 555 123 4567")
    var userEmail by mutableStateOf("david@shieldcorp.com")
    var userCity by mutableStateOf("New York")
    var profileSetupCompleted by mutableStateOf(false)

    var workingHoursStart by mutableStateOf("09:00")
    var workingHoursEnd by mutableStateOf("18:00")
    var averageProductiveHours by mutableStateOf(5.0f)
    var aiAggressiveness by mutableStateOf("Balanced") // Conservative, Balanced, Aggressive
    var themeStyle by mutableStateOf("Dark") // Dark, AMOLED, Navy
    var notificationLevel by mutableStateOf("HIGH") // ALL, HIGH, CRITICAL

    fun saveProfile(name: String, age: String, phone: String, email: String, city: String) {
        userName = name
        userAge = age
        userPhone = phone
        userEmail = email
        userCity = city
        profileSetupCompleted = true

        val prefs = getApplication<Application>().getSharedPreferences("shield_profile_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("user_name", name)
            putString("user_age", age)
            putString("user_phone", phone)
            putString("user_email", email)
            putString("user_city", city)
            putBoolean("profile_setup_completed", true)
            apply()
        }
    }

    fun addWorldClockCity(city: CityClock) {
        if (!worldClockCities.any { it.name.lowercase() == city.name.lowercase() }) {
            worldClockCities = worldClockCities + city
        }
    }

    fun removeWorldClockCity(name: String) {
        worldClockCities = worldClockCities.filter { it.name.lowercase() != name.lowercase() }
    }

    // World Clock Cities list
    var worldClockCities by mutableStateOf(
        listOf(
            CityClock("London", "Europe/London", "GMT+1"),
            CityClock("New York", "America/New_York", "EST"),
            CityClock("Tokyo", "Asia/Tokyo", "JST"),
            CityClock("Sydney", "Australia/Sydney", "AEST")
        )
    )

    // AI Status Indicator
    var isAiProcessing by mutableStateOf(false)
    var aiErrorMessage by mutableStateOf<String?>(null)

    // Recommendation card state
    var morningRecommendation by mutableStateOf("Analyze pending items and tackle high-risk milestones first.")
    var afternoonRecommendation by mutableStateOf("Focus on secondary collaborative efforts.")
    var avoidHours by mutableStateOf("13:00 - 14:30")
    var energyTip by mutableStateOf("Take 5-minute breathing breaks every 60 minutes to maintain consistency.")
    var motivationalMessage by mutableStateOf("Guardian AI protects your deadlines. Stay absolute.")

    // Scanner Screen Temp Buffers
    var tempExtractedTasks by mutableStateOf<List<ExtractedTaskData>>(emptyList())
    var currentEmailInput by mutableStateOf("")
    var currentWhatsappInput by mutableStateOf("")

    init {
        // Load Profile preferences if they exist
        val prefs = application.getSharedPreferences("shield_profile_prefs", Context.MODE_PRIVATE)
        userName = prefs.getString("user_name", "David") ?: "David"
        userAge = prefs.getString("user_age", "25") ?: "25"
        userPhone = prefs.getString("user_phone", "+1 555 123 4567") ?: "+1 555 123 4567"
        userEmail = prefs.getString("user_email", "david@shieldcorp.com") ?: "david@shieldcorp.com"
        userCity = prefs.getString("user_city", "New York") ?: "New York"
        profileSetupCompleted = prefs.getBoolean("profile_setup_completed", false)

        // Observe local database flows
        viewModelScope.launch {
            repo.allTasks.collectLatest {
                _tasks.value = it
                NotificationHelper.checkAndNotifyUpcomingDeadlines(application, it)
            }
        }
        viewModelScope.launch {
            repo.allLogs.collectLatest { _productivityLogs.value = it }
        }
        viewModelScope.launch {
            repo.extractedMessages.collectLatest { _extractedMessages.value = it }
        }

        // Install default stats if empty
        viewModelScope.launch {
            initializeDummyStatsAndMessages()
        }

        // Set up Speech Recognizer on UI thread context
        speechHelper = SpeechHelper(
            context = application,
            onResults = { text ->
                recognizedText = text
                isListening = false
                executeVoiceCommand(text)
            },
            onErrorMsg = { error ->
                isListening = false
                speakFeedback = error
            }
        )

        // Generate recommendation once
        refreshRecommendations()
    }

    private suspend fun initializeDummyStatsAndMessages() {
        val logs = repo.getProductivityLogsSync()
        if (logs.isEmpty()) {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance()
            
            // Generate last 5 days
            for (i in 5 downTo 1) {
                cal.add(Calendar.DAY_OF_YEAR, -i)
                val dateStr = format.format(cal.time)
                repo.insertProductivityLog(
                    ProductivityLog(
                        date = dateStr,
                        tasksCompleted = (3..7).random(),
                        hoursWorked = (4..8).random().toFloat(),
                        topProductiveHour = "10:30 AM",
                        consistencyScore = (65..95).random()
                    )
                )
                cal.add(Calendar.DAY_OF_YEAR, i) // Restore
            }
        }
    }

    // --- Task CRUD Operations ---
    fun createTask(
        title: String,
        category: String,
        deadlineTimestamp: Long,
        estimatedHours: Int,
        notes: String,
        isAutoPriority: Boolean,
        manualPriority: Int,
        location: String? = null,
        voicePath: String? = null,
        frequency: String = "Once",
        customDates: String? = null
    ) {
        viewModelScope.launch {
            isAiProcessing = true
            aiErrorMessage = null
            
            var assignedPriority = manualPriority
            var priorityReasonText: String? = null

            val tempTask = Task(
                title = title,
                category = category,
                deadline = deadlineTimestamp,
                estimatedEffort = estimatedHours * 60,
                priority = assignedPriority,
                autoPriority = isAutoPriority,
                notes = notes,
                location = location,
                voiceNotePath = voicePath,
                frequency = frequency,
                customDates = customDates
            )

            if (isAutoPriority) {
                try {
                    val (pri, reason) = repo.suggestTaskPriority(tempTask, _tasks.value.size)
                    assignedPriority = pri
                    priorityReasonText = reason
                } catch (e: Exception) {
                    Log.e("GuardianViewModel", "Auto priority failure", e)
                }
            }

            val savedTask = tempTask.copy(
                priority = assignedPriority,
                priorityReason = priorityReasonText
            )

            val newId = repo.insertTask(savedTask)
            val finalTask = savedTask.copy(id = newId)

            // Auto-calculate risk score
            calculateRisk(finalTask)
            
            isAiProcessing = false
        }
    }

    fun updateTask(
        id: Long,
        title: String,
        category: String,
        deadlineTimestamp: Long,
        estimatedHours: Int,
        notes: String,
        isAutoPriority: Boolean,
        manualPriority: Int,
        location: String? = null,
        voicePath: String? = null,
        frequency: String = "Once",
        customDates: String? = null
    ) {
        viewModelScope.launch {
            isAiProcessing = true
            aiErrorMessage = null

            var assignedPriority = manualPriority
            var priorityReasonText: String? = null

            val existing = repo.getTaskById(id) ?: return@launch

            val tempTask = existing.copy(
                title = title,
                category = category,
                deadline = deadlineTimestamp,
                estimatedEffort = estimatedHours * 60,
                priority = assignedPriority,
                autoPriority = isAutoPriority,
                notes = notes,
                location = location,
                voiceNotePath = voicePath,
                frequency = frequency,
                customDates = customDates
            )

            if (isAutoPriority) {
                try {
                    val (pri, reason) = repo.suggestTaskPriority(tempTask, _tasks.value.size)
                    assignedPriority = pri
                    priorityReasonText = reason
                } catch (e: Exception) {
                    Log.e("GuardianViewModel", "Auto priority update failure", e)
                }
            }

            val savedTask = tempTask.copy(
                priority = assignedPriority,
                priorityReason = priorityReasonText ?: existing.priorityReason
            )

            repo.updateTask(savedTask)

            // Auto-calculate risk score
            calculateRisk(savedTask)

            isAiProcessing = false
        }
    }

    fun markTaskCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch {
            val updated = task.copy(
                isCompleted = completed,
                completedAt = if (completed) System.currentTimeMillis() else null
            )
            repo.updateTask(updated)
            
            if (completed) {
                // Play notification/milestone logic
                updateProductivityLogOnCompletion()
                NotificationHelper.showStreakMilestone(getApplication(), 5)
            }
        }
    }

    private suspend fun updateProductivityLogOnCompletion() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val logs = repo.getProductivityLogsSync()
        val existing = logs.find { it.date == todayStr }
        if (existing != null) {
            repo.insertProductivityLog(
                existing.copy(
                    tasksCompleted = existing.tasksCompleted + 1,
                    consistencyScore = minOf(100, existing.consistencyScore + 5)
                )
            )
        } else {
            repo.insertProductivityLog(
                ProductivityLog(
                    date = todayStr,
                    tasksCompleted = 1,
                    hoursWorked = 3.5f,
                    topProductiveHour = "11:00 AM",
                    consistencyScore = 70
                )
            )
        }
    }

    fun deleteTask(id: Long) {
        viewModelScope.launch {
            repo.deleteTaskById(id)
        }
    }

    // --- Risk Engine Operations ---
    fun calculateRisk(task: Task) {
        viewModelScope.launch {
            isAiProcessing = true
            try {
                val res = repo.calculateTaskRiskScore(
                    task = task,
                    avgHours = averageProductiveHours,
                    pendingCount = _tasks.value.count { !it.isCompleted }
                )
                val updatedTask = task.copy(
                    riskScore = res.riskScore,
                    riskLevel = res.riskLevel,
                    riskReason = res.riskReason
                )
                repo.updateTask(updatedTask)

                // High-risk smart triggers
                if (res.riskScore > 70) {
                    NotificationHelper.showRiskAlert(
                        getApplication(),
                        task.id,
                        task.title,
                        res.riskScore,
                        res.riskLevel
                    )
                }
            } catch (e: Exception) {
                Log.e("GuardianViewModel", "Error calculating risk for ${task.title}", e)
            } finally {
                isAiProcessing = false
            }
        }
    }

    fun calculateRiskForAll() {
        viewModelScope.launch {
            _tasks.value.filter { !it.isCompleted }.forEach {
                calculateRisk(it)
            }
        }
    }

    // --- Execution Planner & Subtasks ---
    fun selectTask(task: Task) {
        _selectedTask.value = task
        viewModelScope.launch {
            repo.getSubtasksForTask(task.id).collectLatest {
                _currentSubtasks.value = it
            }
        }
    }

    fun generateExecutionPlan(task: Task) {
        viewModelScope.launch {
            isAiProcessing = true
            aiErrorMessage = null
            try {
                val planResult = repo.generateTaskExecutionPlan(task, averageProductiveHours)
                repo.saveExecutionPlan(task.id, planResult)
                
                // Re-select to trigger UI update
                val updated = repo.getTaskById(task.id) ?: task
                _selectedTask.value = updated
            } catch (e: Exception) {
                Log.e("GuardianViewModel", "Failed generating plan", e)
                aiErrorMessage = "AI Execution Planner unavailable, check connection."
            } finally {
                isAiProcessing = false
            }
        }
    }

    fun toggleSubtask(subTask: SubTask, completed: Boolean) {
        viewModelScope.launch {
            repo.updateSubtask(subTask.copy(isCompleted = completed))
        }
    }

    fun applyEmergencyMode(task: Task) {
        viewModelScope.launch {
            isAiProcessing = true
            try {
                // Emergency reduction: AI compression to immediate essential subtasks
                val deadlineDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(task.deadline))
                val emergencyPlan = geminiRepo.generateExecutionPlan(
                    taskName = "🚨 EMERGENCY: ${task.title} (ULTRA-COMPRESSED)",
                    deadlineStr = deadlineDate,
                    availableHours = 12.0f // Force extreme hours/focus compression
                )
                repo.saveExecutionPlan(task.id, emergencyPlan)
                
                // Recompute risk, which should drop
                val updated = task.copy(riskScore = maxOf(0, task.riskScore - 30), riskLevel = "MEDIUM", riskReason = "Emergency compressed plans activated.")
                repo.updateTask(updated)
                _selectedTask.value = updated
            } catch (e: Exception) {
                Log.e("GuardianViewModel", "Emergency compression failed", e)
            } finally {
                isAiProcessing = false
            }
        }
    }

    // --- AI Extraction Engine Scans ---
    fun scanEmail(body: String) {
        viewModelScope.launch {
            isAiProcessing = true
            tempExtractedTasks = emptyList()
            try {
                val results = geminiRepo.extractFromEmail(body)
                tempExtractedTasks = results
                
                // Add as messages for audit trail
                repo.insertExtractedMessage(
                    ExtractedMessage(
                        source = "EMAIL",
                        rawContent = body.take(200) + "..."
                    )
                )
            } catch (e: Exception) {
                Log.e("GuardianViewModel", "Email scanning failed", e)
            } finally {
                isAiProcessing = false
            }
        }
    }

    fun scanSms(message: String) {
        viewModelScope.launch {
            isAiProcessing = true
            try {
                val extracted = geminiRepo.extractFromSms(message)
                if (extracted != null) {
                    tempExtractedTasks = listOf(extracted)
                } else {
                    tempExtractedTasks = emptyList()
                }
                
                repo.insertExtractedMessage(
                    ExtractedMessage(
                        source = "SMS",
                        rawContent = message
                    )
                )
            } catch (e: Exception) {
                Log.e("GuardianViewModel", "SMS scanning failed", e)
            } finally {
                isAiProcessing = false
            }
        }
    }

    fun scanWhatsapp(message: String) {
        viewModelScope.launch {
            isAiProcessing = true
            try {
                val extracted = geminiRepo.extractFromSms(message) // Treat as text SMS-like context
                if (extracted != null) {
                    tempExtractedTasks = listOf(extracted)
                } else {
                    tempExtractedTasks = emptyList()
                }
                repo.insertExtractedMessage(
                    ExtractedMessage(
                        source = "WHATSAPP",
                        rawContent = message
                    )
                )
            } catch (e: Exception) {
                Log.e("GuardianViewModel", "WhatsApp scanning failed", e)
            } finally {
                isAiProcessing = false
            }
        }
    }

    fun approveBulkExtractedTasks() {
        viewModelScope.launch {
            tempExtractedTasks.forEach { ext ->
                val calendar = Calendar.getInstance()
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val deadlineTime = try {
                    if (ext.deadline.isNotEmpty()) format.parse(ext.deadline)?.time ?: (System.currentTimeMillis() + 86400000)
                    else System.currentTimeMillis() + 86400000
                } catch (e: Exception) {
                    System.currentTimeMillis() + 86400000
                }

                createTask(
                    title = ext.taskName,
                    category = ext.category,
                    deadlineTimestamp = deadlineTime,
                    estimatedHours = 2,
                    notes = ext.notes,
                    isAutoPriority = false,
                    manualPriority = ext.priority
                )
            }
            tempExtractedTasks = emptyList()
        }
    }

    fun dismissExtractedAt(index: Int) {
        val updated = tempExtractedTasks.toMutableList()
        if (index in updated.indices) {
            updated.removeAt(index)
            tempExtractedTasks = updated
        }
    }

    // --- Personalization Engine ---
    fun refreshRecommendations() {
        viewModelScope.launch {
            isAiProcessing = true
            try {
                val completionHistory = _productivityLogs.value.joinToString { "[${it.date}: Done ${it.tasksCompleted}, Score ${it.consistencyScore}]" }
                val taskListJson = _tasks.value.filter { !it.isCompleted }.joinToString { "[Title: ${it.title}, Due: ${it.deadline}, Category: ${it.category}]" }
                
                val result = repo.getDailyRecommendations(
                    completionHistory = completionHistory.ifEmpty { "New user profile initialized" },
                    productiveHours = "$workingHoursStart to $workingHoursEnd",
                    taskListJson = taskListJson.ifEmpty { "No pending items" }
                )
                morningRecommendation = result.morningFocus
                afternoonRecommendation = result.afternoonFocus
                avoidHours = result.avoidTime
                energyTip = result.topTip
                motivationalMessage = result.motivationalMessage
            } catch (e: Exception) {
                Log.e("GuardianViewModel", "Error fetching daily briefs", e)
            } finally {
                isAiProcessing = false
            }
        }
    }

    // --- Voice Assistant Commands Parser ---
    fun startListeningVoice() {
        recognizedText = "Listening..."
        speakFeedback = ""
        isListening = true
        speechHelper?.startListening()
    }

    fun stopListeningVoice() {
        speechHelper?.stopListening()
        isListening = false
    }

    private fun executeVoiceCommand(command: String) {
        viewModelScope.launch {
            isAiProcessing = true
            try {
                val parseResult = repo.parseVoice(command)
                when (parseResult.intent.uppercase(Locale.US)) {
                    "ADD" -> {
                        val taskName = parseResult.taskName.ifEmpty { "Voice Task" }
                        val dFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                        val timestamp = try {
                            if (parseResult.deadline.isNotEmpty()) dFormat.parse(parseResult.deadline)?.time ?: (System.currentTimeMillis() + 86400000)
                            else System.currentTimeMillis() + 86400000
                        } catch (e: Exception) {
                            System.currentTimeMillis() + 86400000
                        }
                        createTask(
                            title = taskName,
                            category = parseResult.category,
                            deadlineTimestamp = timestamp,
                            estimatedHours = 1,
                            notes = "Added via voice commands.",
                            isAutoPriority = false,
                            manualPriority = 3
                        )
                        speakFeedback = "Guardian generated new task \"$taskName\" with optimal category."
                        speechHelper?.speak(speakFeedback)
                    }
                    "QUERY" -> {
                        val incomplete = _tasks.value.filter { !it.isCompleted }
                        speakFeedback = if (incomplete.isEmpty()) {
                            "You have zero pending tasks today. Strategic goals achieved!"
                        } else {
                            "You have ${incomplete.size} proactive items. The top urgent item is: \"${incomplete.first().title}\"."
                        }
                        speechHelper?.speak(speakFeedback)
                    }
                    "COMPLETE" -> {
                        val queryText = parseResult.taskName.lowercase()
                        val match = _tasks.value.find { it.title.lowercase().contains(queryText) }
                        if (match != null) {
                            markTaskCompleted(match, true)
                            speakFeedback = "Successfully marked \"${match.title}\" complete. Great job!"
                        } else {
                            speakFeedback = "Could not find a task matching description to complete."
                        }
                        speechHelper?.speak(speakFeedback)
                    }
                    "PLAN" -> {
                        val queryText = parseResult.taskName.lowercase()
                        val match = _tasks.value.find { it.title.lowercase().contains(queryText) }
                        if (match != null) {
                            generateExecutionPlan(match)
                            speakFeedback = "AI execution timeline created for \"${match.title}\"."
                        } else {
                            speakFeedback = "Select an existing task to generate micro-step timelines."
                        }
                        speechHelper?.speak(speakFeedback)
                    }
                    "RISK" -> {
                        val queryText = parseResult.taskName.lowercase()
                        val match = _tasks.value.find { it.title.lowercase().contains(queryText) }
                        if (match != null) {
                            calculateRisk(match)
                            speakFeedback = "\"${match.title}\" has a predicted failure risk of ${match.riskScore} percent."
                        } else {
                            speakFeedback = "Task details not selected."
                        }
                        speechHelper?.speak(speakFeedback)
                    }
                    else -> {
                        speakFeedback = "Command intent unrecognized. Reach custom screens to invoke manually."
                        speechHelper?.speak(speakFeedback)
                    }
                }
            } catch (e: Exception) {
                speakFeedback = "Voice processing error. Operating locally."
                speechHelper?.speak(speakFeedback)
            } finally {
                isAiProcessing = false
            }
        }
    }

    // --- Sync Task to Calendar ---
    fun syncToSystemCalendar(task: Task, context: Context) {
        viewModelScope.launch {
            val eventId = CalendarSyncHelper.syncTaskToCalendar(context, task)
            if (eventId > 0) {
                repo.updateTask(task.copy(attachments = if (task.attachments.isEmpty()) "cal_sync:$eventId" else "${task.attachments},cal_sync:$eventId"))
            }
        }
    }

    // --- Export Data ---
    fun exportTasksAsJson(): String {
        val array = JSONArray()
        _tasks.value.forEach { t ->
            val obj = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("category", t.category)
                put("deadline", t.deadline)
                put("riskScore", t.riskScore)
                put("riskLevel", t.riskLevel)
                put("isCompleted", t.isCompleted)
                put("source", t.source)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun exportTasksAsCsv(): String {
        val builder = StringBuilder()
        builder.append("ID,Title,Category,Deadline,PriorityPercentage,PriorityLevel,Completed,Source\n")
        _tasks.value.forEach { t ->
            builder.append("${t.id},\"${t.title.replace("\"", "\"\"")}\",${t.category},${t.deadline},${t.riskScore},${t.riskLevel},${t.isCompleted},${t.source}\n")
        }
        return builder.toString()
    }

    override fun onCleared() {
        super.onCleared()
        speechHelper?.cleanup()
    }
}

// Supporting Local Data Classes
data class CityClock(
    val name: String,
    val timeZoneId: String,
    val code: String
)
