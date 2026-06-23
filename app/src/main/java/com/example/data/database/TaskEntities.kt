package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // Assignment, Meeting, Bill, Flight, Personal, Work, Health, Other
    val deadline: Long, // timestamp
    val estimatedEffort: Int, // minutes
    val priority: Int, // 1 to 5 scale
    val autoPriority: Boolean = false,
    val priorityReason: String? = null,
    val riskScore: Int = 0, // 0 to 100
    val riskLevel: String = "LOW", // LOW, MEDIUM, HIGH, CRITICAL
    val riskReason: String? = null,
    val notes: String = "",
    val voiceNotePath: String? = null,
    val attachments: String = "", // comma-separated or JSON list of paths
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val source: String = "MANUAL", // MANUAL, EMAIL, SMS, WHATSAPP
    val executionPlanJson: String? = null, // JSON structure of plans
    val location: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val frequency: String = "Once",
    val customDates: String? = null
)

@Entity(tableName = "subtasks")
data class SubTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentTaskId: Long,
    val day: String,
    val scheduledTime: String,
    val action: String,
    val durationMinutes: Int,
    val isCompleted: Boolean = false
)

@Entity(tableName = "productivity_logs")
data class ProductivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val tasksCompleted: Int,
    val hoursWorked: Float,
    val topProductiveHour: String, // e.g. "10:00"
    val consistencyScore: Int // 0 to 100
)

@Entity(tableName = "extracted_messages")
data class ExtractedMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String, // EMAIL, SMS, WHATSAPP
    val rawContent: String,
    val extractedTaskId: Long? = null,
    var isProcessed: Boolean = false,
    val processedAt: Long = System.currentTimeMillis()
)
