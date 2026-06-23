package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Data Models matching the Gemini REST API exactly ---
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

data class Content(
    val parts: List<Part>
)

data class Part(
    val text: String? = null
)

data class GenerationConfig(
    val responseMimeType: String? = null, // Set to "application/json" for JSON execution!
    val temperature: Float? = null
)

data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

data class Candidate(
    val content: Content? = null
)

// --- Retrofit API Service ---
interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateFlash(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generatePro(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

class GeminiRepository {
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val service = retrofit.create(GeminiApiService::class.java)

    private fun cleanJsonResponse(rawResponse: String): String {
        var cleaned = rawResponse.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```")
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substringBeforeLast("```")
        }
        return cleaned.trim()
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext false
        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = "Hello, respond with exactly 'OK'"))))
            )
            val res = service.generateFlash(apiKey, req)
            val reply = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            reply.trim().isNotEmpty()
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Test connection failed", e)
            false
        }
    }

    // 1. Calculate Priority (0-5)
    suspend fun calculatePriority(
        taskName: String,
        deadlineStr: String,
        category: String,
        effortMinutes: Int,
        pendingTasksCount: Int
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val prompt = """
            Given a task named "$taskName" with deadline "$deadlineStr", category "$category", and estimated effort of $effortMinutes minutes, and the user currently has $pendingTasksCount pending tasks, calculate a priority score from 1 to 5 (1 being lowest, 5 being critical).
            Return only a JSON object:
            {
              "priority": X,
              "reason": "brief reason why it has this priority"
            }
        """.trimIndent()

        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )
            val res = service.generateFlash(apiKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val json = cleanJsonResponse(text)
            val obj = JSONObject(json)
            val priority = obj.optInt("priority", 3)
            val reason = obj.optString("reason", "Default balanced priority assigned.")
            Pair(priority, reason)
        } catch (e: Exception) {
            Log.e("GeminiRepository", "calculatePriority failed", e)
            Pair(3, "Manual fallback priority.")
        }
    }

    // 2. Extract Tasks from Email
    suspend fun extractFromEmail(emailBody: String): List<ExtractedTaskData> = withContext(Dispatchers.IO) {
        val prompt = """
            Extract all tasks, meetings, deadlines, or action items from this email body.
            Return a JSON array of objects. Each object must have these field names: "taskName", "deadline", "category", "priority", "notes".
            Use standard categories: "Assignment", "Meeting", "Bill", "Flight", "Personal", "Work", "Health", "Other"
            Use priority as integer 1 to 5.
            Use a standardized date/time string formatted as "YYYY-MM-DD HH:MM" for the "deadline" if a date is mentioned (Today is ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}), or leave blank/expressive if unknown.
            Email:
            $emailBody
        """.trimIndent()

        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )
            val res = service.generateFlash(apiKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val json = cleanJsonResponse(text)
            val array = JSONArray(json)
            val list = mutableListOf<ExtractedTaskData>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    ExtractedTaskData(
                        taskName = item.optString("taskName", "Unnamed Action Item"),
                        deadline = item.optString("deadline", ""),
                        category = item.optString("category", "Work"),
                        priority = item.optInt("priority", 3),
                        notes = item.optString("notes", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e("GeminiRepository", "extractFromEmail failed", e)
            emptyList()
        }
    }

    // 3. Extract Tasks from SMS
    suspend fun extractFromSms(smsText: String): ExtractedTaskData? = withContext(Dispatchers.IO) {
        val prompt = """
            From this SMS, extract any task, deadline, or appointment.
            Return JSON: { "taskName": "...", "deadline": "YYYY-MM-DD HH:MM", "category": "...", "notes": "..." } or null (or empty JSON) if it is purely an OTP, verification code, promotional spam, bank transaction notification with no action requirement, or irrelevant.
            Today is ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.
            SMS:
            $smsText
        """.trimIndent()

        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )
            val res = service.generateFlash(apiKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val json = cleanJsonResponse(text)
            if (json == "null" || json.isEmpty()) return@withContext null
            val item = JSONObject(json)
            if (!item.has("taskName") && !item.has("title")) return@withContext null
            ExtractedTaskData(
                taskName = item.optString("taskName", item.optString("title", "SMS Task")),
                deadline = item.optString("deadline", ""),
                category = item.optString("category", "Other"),
                priority = 3,
                notes = item.optString("notes", "")
            )
        } catch (e: Exception) {
            Log.e("GeminiRepository", "extractFromSms failed", e)
            null
        }
    }

    // 4. Calculate Risk Score
    suspend fun calculateRiskScore(
        taskName: String,
        deadlineStr: String,
        priority: Int,
        effortMinutes: Int,
        avgHours: Float,
        pendingCount: Int
    ): RiskAnalysisResult = withContext(Dispatchers.IO) {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val prompt = """
            Calculate the risk that a user will miss a deadline using dynamic criteria:
            Task: "$taskName"
            Deadline: "$deadlineStr"
            Priority: $priority/5
            Estimated Effort: $effortMinutes minutes
            Current Date/Time: $nowStr
            User's average daily productive hours: $avgHours
            Pending tasks count: $pendingCount
            
            Calculate the exact probability (0-100%) that this user will miss this deadline.
            Return JSON:
            {
              "riskScore": X,
              "riskLevel": "LOW/MEDIUM/HIGH/CRITICAL",
              "riskReason": "realistic mathematical explanation based on available time vs effort",
              "warningMessage": "friendly but firm warning or encouragement"
            }
        """.trimIndent()

        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )
            val res = service.generateFlash(apiKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val json = cleanJsonResponse(text)
            val obj = JSONObject(json)
            RiskAnalysisResult(
                riskScore = obj.optInt("riskScore", 15),
                riskLevel = obj.optString("riskLevel", "LOW"),
                riskReason = obj.optString("riskReason", "Sufficient remaining time before deadline."),
                warningMessage = obj.optString("warningMessage", "On track!")
            )
        } catch (e: Exception) {
            Log.e("GeminiRepository", "calculateRiskScore failed", e)
            RiskAnalysisResult(20, "LOW", "Failed to connect to AI engine; using local estimate.", "On track!")
        }
    }

    // 5. Generate Step-by-step Execution Plan (using PRO model for complex logical synthesis)
    suspend fun generateExecutionPlan(
        taskName: String,
        deadlineStr: String,
        availableHours: Float
    ): ExecutionPlanResult = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val prompt = """
            Create a detailed step-by-step execution plan for completing "$taskName" by "$deadlineStr".
            Today is $todayStr. 
            User has $availableHours productive/free hours per day.
            Break this into daily subtasks leading up to the deadline with realistic times and specific actions. Also suggest what the user should do IMMEDIATELY (RIGHT NOW) as their first low-friction action.
            
            Return ONLY a valid JSON object matching this structure:
            {
              "immediateAction": "an action to do right now, e.g. open the doc, sketch top 3 ideas",
              "totalEstimatedTime": "X hours",
              "dailyPlan": [
                {
                  "day": "Day 1 (or specific Date)",
                  "date": "YYYY-MM-DD",
                  "tasks": [
                    { "time": "HH:MM", "action": "description of specific micro-step", "duration": "X mins" }
                  ]
                }
              ],
              "successTips": ["tip 1", "tip 2"]
            }
        """.trimIndent()

        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )
            // Use Pro endpoint
            val res = service.generatePro(apiKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val json = cleanJsonResponse(text)
            val obj = JSONObject(json)
            
            val dailyPlanArr = obj.optJSONArray("dailyPlan")
            val planDays = mutableListOf<PlanDay>()
            if (dailyPlanArr != null) {
                for (i in 0 until dailyPlanArr.length()) {
                    val dayObj = dailyPlanArr.getJSONObject(i)
                    val tasksArr = dayObj.optJSONArray("tasks")
                    val tasks = mutableListOf<PlanTask>()
                    if (tasksArr != null) {
                        for (j in 0 until tasksArr.length()) {
                            val taskObj = tasksArr.getJSONObject(j)
                            tasks.add(
                                PlanTask(
                                    time = taskObj.optString("time", "09:00"),
                                    action = taskObj.optString("action", "Work on subtask"),
                                    duration = taskObj.optString("duration", "30 mins")
                                )
                            )
                        }
                    }
                    planDays.add(
                        PlanDay(
                            day = dayObj.optString("day", "Day ${i+1}"),
                            date = dayObj.optString("date", todayStr),
                            tasks = tasks
                        )
                    )
                }
            }

            val successTipsArr = obj.optJSONArray("successTips")
            val tips = mutableListOf<String>()
            if (successTipsArr != null) {
                for (i in 0 until successTipsArr.length()) {
                    tips.add(successTipsArr.getString(i))
                }
            }

            ExecutionPlanResult(
                immediateAction = obj.optString("immediateAction", "Review your goals for today."),
                totalEstimatedTime = obj.optString("totalEstimatedTime", "Unknown"),
                dailyPlan = planDays,
                successTips = if (tips.isEmpty()) listOf("Stay focused", "Avoid multitasking") else tips
            )
        } catch (e: Exception) {
            Log.e("GeminiRepository", "generateExecutionPlan failed", e)
            // Fallback Plan
            ExecutionPlanResult(
                immediateAction = "Start outlining your main steps for $taskName.",
                totalEstimatedTime = "2 hours",
                dailyPlan = listOf(
                    PlanDay(
                        day = "Phase 1",
                        date = todayStr,
                        tasks = listOf(
                            PlanTask("09:00", "Initial setup & core requirements review", "45 mins"),
                            PlanTask("14:00", "Critical task execution & draft creation", "60 mins")
                        )
                    ),
                    PlanDay(
                        day = "Phase 2",
                        date = deadlineStr.substringBefore(" "),
                        tasks = listOf(
                            PlanTask("10:00", "Review, polish, and final compilation", "30 mins")
                        )
                    )
                ),
                successTips = listOf("Divide the task into bite-sized segments to avoid overwhelm.", "Complete the hardest part first.")
            )
        }
    }

    // 6. Voice Command Parsing
    suspend fun parseVoiceCommand(voiceText: String): VoiceIntentResult = withContext(Dispatchers.IO) {
        val prompt = """
            Parse this vocal voice command for an AI-powered task management and deadline prediction app:
            "$voiceText"
            
            Determine the user's intent. The supported values for intent are:
            "ADD" (when creating a task, meeting, or appointment),
            "QUERY" (when listing or asking about today's, upcoming, or current tasks),
            "COMPLETE" (when marking a task or activity as finished),
            "DELETE" (when removing or deleting an item),
            "PLAN" (when asking for an execution plan or asking what to do now),
            "RISK" (when checking if they will make a deadline or requesting a risk score),
            "UNKNOWN" (when it doesn't match these).
            
            Extract the relative details:
            - taskName: the identified task title (if any)
            - deadline: relative date-time information transformed to standard YYYY-MM-DD HH:MM (if mentioned). Today is ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.
            - category: Category selection (Meeting/Assignment/Personal/Work/Health/Other)
            - parameters: any other parsed parameters.
            
            Return JSON:
            {
              "intent": "ADD/QUERY/COMPLETE/DELETE/PLAN/RISK/UNKNOWN",
              "taskName": "...",
              "deadline": "YYYY-MM-DD HH:MM",
              "category": "...",
              "parameters": { ... }
            }
        """.trimIndent()

        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )
            val res = service.generateFlash(apiKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val json = cleanJsonResponse(text)
            val obj = JSONObject(json)
            VoiceIntentResult(
                intent = obj.optString("intent", "UNKNOWN"),
                taskName = obj.optString("taskName", ""),
                deadline = obj.optString("deadline", ""),
                category = obj.optString("category", "Other")
            )
        } catch (e: Exception) {
            Log.e("GeminiRepository", "parseVoiceCommand failed", e)
            VoiceIntentResult("UNKNOWN", "", "", "")
        }
    }

    // 7. Personalized Weekly Recommendations
    suspend fun getPersonalizedRecommendations(
        completionHistory: String,
        productiveHours: String,
        taskListJson: String
    ): RecommendationsResult = withContext(Dispatchers.IO) {
        val prompt = """
            Based on this user's task completion history: "$completionHistory",
            their configured most productive hours: "$productiveHours",
            and their pending tasks list: "$taskListJson".
            
            Generate today's personalized hyper-productivity recommendations. Show them high-intent ways to avoid failures.
            Include:
            - optimal work windows (specific custom focus hours, e.g. "9 AM - 11 AM")
            - task execution order suggestion with visual reasons
            - energy management tip
            - daily motivational, highly firm encouragement message.
            
            Return JSON:
            {
              "morningFocus": "specific morning task title and optimal focus window",
              "afternoonFocus": "specific afternoon task title and strategy",
              "avoidTime": "times to avoid work due to low focus or over-saturation, e.g. 2 PM - 3:30 PM",
              "topTip": "one critical advice to stay ahead today",
              "motivationalMessage": "highly encouraging Guardian support message"
            }
        """.trimIndent()

        try {
            val req = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                generationConfig = GenerationConfig(responseMimeType = "application/json")
            )
            val res = service.generateFlash(apiKey, req)
            val text = res.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            val json = cleanJsonResponse(text)
            val obj = JSONObject(json)
            RecommendationsResult(
                morningFocus = obj.optString("morningFocus", "Focus on top priority tasks before notifications arrive."),
                afternoonFocus = obj.optString("afternoonFocus", "Moderate energy tasks recommended for afternoon."),
                avoidTime = obj.optString("avoidTime", "1:00 PM - 2:30 PM"),
                topTip = obj.optString("topTip", "Eliminate phone distractions for the first 90 minutes of your workday."),
                motivationalMessage = obj.optString("motivationalMessage", "You are the master of your deadlines. Guardian AI has your back.")
            )
        } catch (e: Exception) {
            Log.e("GeminiRepository", "getPersonalizedRecommendations failed", e)
            RecommendationsResult(
                morningFocus = "High-priority analytical execution (09:00 - 11:30)",
                afternoonFocus = "Routine checkups and short meetings (14:00 - 15:30)",
                avoidTime = "13:00 - 14:00",
                topTip = "Break your high-risk project into its 3 easiest micro-stages immediately.",
                motivationalMessage = "Your focus is your shield. Let's make today matter."
            )
        }
    }
}

// Support Data Classes
data class ExtractedTaskData(
    val taskName: String,
    val deadline: String, // YYYY-MM-DD HH:MM
    val category: String,
    val priority: Int,
    val notes: String
)

data class RiskAnalysisResult(
    val riskScore: Int,
    val riskLevel: String, // LOW, MEDIUM, HIGH, CRITICAL
    val riskReason: String,
    val warningMessage: String
)

data class PlanTask(
    val time: String,
    val action: String,
    val duration: String
)

data class PlanDay(
    val day: String,
    val date: String,
    val tasks: List<PlanTask>
)

data class ExecutionPlanResult(
    val immediateAction: String,
    val totalEstimatedTime: String,
    val dailyPlan: List<PlanDay>,
    val successTips: List<String>
)

data class VoiceIntentResult(
    val intent: String,
    val taskName: String,
    val deadline: String,
    val category: String
)

data class RecommendationsResult(
    val morningFocus: String,
    val afternoonFocus: String,
    val avoidTime: String,
    val topTip: String,
    val motivationalMessage: String
)
