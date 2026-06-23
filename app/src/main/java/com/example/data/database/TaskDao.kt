package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    // --- Tasks ---
    @Query("SELECT * FROM tasks ORDER BY deadline ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskFlowById(id: Long): Flow<Task?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    // --- SubTasks ---
    @Query("SELECT * FROM subtasks WHERE parentTaskId = :taskId ORDER BY day ASC, id ASC")
    fun getSubtasksForTask(taskId: Long): Flow<List<SubTask>>

    @Query("SELECT * FROM subtasks WHERE parentTaskId = :taskId ORDER BY day ASC, id ASC")
    suspend fun getSubtasksForTaskSync(taskId: Long): List<SubTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtasks(subtasks: List<SubTask>)

    @Update
    suspend fun updateSubtask(subtask: SubTask)

    @Query("DELETE FROM subtasks WHERE parentTaskId = :taskId")
    suspend fun deleteSubtasksForTask(taskId: Long)

    @Query("UPDATE subtasks SET isCompleted = :completed WHERE id = :id")
    suspend fun updateSubtaskStatus(id: Long, completed: Boolean)

    // --- Productivity Logs ---
    @Query("SELECT * FROM productivity_logs ORDER BY date DESC")
    fun getProductivityLogs(): Flow<List<ProductivityLog>>

    @Query("SELECT * FROM productivity_logs ORDER BY date DESC")
    suspend fun getProductivityLogsSync(): List<ProductivityLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductivityLog(log: ProductivityLog)

    // --- Extracted Messages ---
    @Query("SELECT * FROM extracted_messages ORDER BY processedAt DESC")
    fun getExtractedMessages(): Flow<List<ExtractedMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtractedMessage(message: ExtractedMessage): Long

    @Query("UPDATE extracted_messages SET isProcessed = 1, extractedTaskId = :taskId WHERE id = :id")
    suspend fun markMessageProcessed(id: Long, taskId: Long)
}
