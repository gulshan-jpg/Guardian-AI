package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.Task
import com.example.ui.theme.*
import com.example.ui.viewmodel.GuardianViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TaskListScreen(
    viewModel: GuardianViewModel,
    onNavigateToCreation: () -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") } // All, Today, Overdue, Upcoming, Completed
    var sortBy by remember { mutableStateOf("Deadline") } // Deadline, Priority, Priority Percentage, Category

    val filterOptions = listOf("All", "Today", "Overdue", "Upcoming", "Completed")
    val sortOptions = listOf("Deadline", "Priority", "Priority Percentage")

    // Dynamic Filter lists
    val filteredTasks = tasks.filter { task ->
        when (selectedFilter) {
            "Today" -> {
                val calendar = Calendar.getInstance()
                val todayDay = calendar.get(Calendar.DAY_OF_YEAR)
                val todayYear = calendar.get(Calendar.YEAR)

                val taskCal = Calendar.getInstance().apply { timeInMillis = task.deadline }
                val taskDay = taskCal.get(Calendar.DAY_OF_YEAR)
                val taskYear = taskCal.get(Calendar.YEAR)

                todayDay == taskDay && todayYear == taskYear && !task.isCompleted
            }
            "Overdue" -> {
                task.deadline < System.currentTimeMillis() && !task.isCompleted
            }
            "Upcoming" -> {
                task.deadline >= System.currentTimeMillis() && !task.isCompleted
            }
            "Completed" -> {
                task.isCompleted
            }
            else -> true
        }
    }.sortedWith { a, b ->
        when (sortBy) {
            "Priority" -> b.priority.compareTo(a.priority)
            "Priority Percentage" -> b.riskScore.compareTo(a.riskScore)
            else -> a.deadline.compareTo(b.deadline)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Title Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Deadline Vault",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )

                // Sort Options trigger Dropdown simulator
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkCard)
                        .clickable {
                            sortBy = when (sortBy) {
                                "Deadline" -> "Priority"
                                "Priority" -> "Priority Percentage"
                                else -> "Deadline"
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Sort, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Sort: $sortBy", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filtering Tab list row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { opt ->
                    val isSelected = selectedFilter == opt
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ElectricBlue else DarkCard)
                            .clickable { selectedFilter = opt }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = opt,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) NeonCyan else Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Core list element render
            if (filteredTasks.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        TaskItemCard(
                            task = task,
                            onCompleteToggle = { viewModel.markTaskCompleted(task, !task.isCompleted) },
                            onDelete = { viewModel.deleteTask(task.id) },
                            onSelect = {
                                viewModel.selectTask(task)
                            },
                            onEdit = {
                                viewModel.editingTask = task
                                onNavigateToCreation()
                            }
                        )
                    }
                }
            } else {
                // Illustrated empty states with Contextual actions (satisfies empty lists requirements exactly)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(DarkCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Inbox, contentDescription = "", tint = Color.Gray, modifier = Modifier.size(34.dp))
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(text = "No metrics listed here", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You are currently immune to failing. Protect more goals by adding tasks manually below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onNavigateToCreation,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "", tint = NearBlack)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "ADD NEW GOAL", color = NearBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Floating Action Button to Add Goals
        FloatingActionButton(
            onClick = onNavigateToCreation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp)
                .testTag("add_task_fab"),
            containerColor = NeonCyan,
            contentColor = NearBlack
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add task")
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    onCompleteToggle: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    val isCritical = task.riskScore >= 75
    val isCompleted = task.isCompleted

    val borderStroke = if (isCritical && !isCompleted) {
        Modifier.border(1.dp, Crimson, RoundedCornerShape(16.dp))
    } else {
        Modifier
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderStroke)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .testTag("task_card_${task.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted) DarkCard.copy(alpha = 0.4f) else DarkCard.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Category Circle marker
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when (task.category.uppercase()) {
                                    "WORK" -> NeonCyan
                                    "MEETING" -> Emerald
                                    "ASSIGNMENT" -> ElectricBlue
                                    "BILL" -> Amber
                                    else -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = task.category.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }

                // Small risk indicator dot
                if (!isCompleted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when (task.riskLevel) {
                                    "CRITICAL" -> Crimson.copy(alpha = 0.2f)
                                    "HIGH" -> Crimson.copy(alpha = 0.15f)
                                    "MEDIUM" -> Amber.copy(alpha = 0.15f)
                                    else -> Emerald.copy(alpha = 0.15f)
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRIORITY: ${task.riskScore}%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (task.riskLevel) {
                                "CRITICAL", "HIGH" -> Crimson
                                "MEDIUM" -> Amber
                                else -> Emerald
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                ),
                color = if (isCompleted) Color.Gray else Color.White
            )

            val diff = task.deadline - System.currentTimeMillis()
            val isUpcomingNotificationEligible = (task.frequency == "Weekly" || 
                                                 task.frequency == "Monthly" || 
                                                 task.frequency == "Custom Dates") && 
                                                 !task.isCompleted && 
                                                 diff in 0..(24*60*60*1000L)

            if (isUpcomingNotificationEligible) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Amber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Alert Tomorrow",
                        tint = Amber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NOTIFICATION ACTIVE: DUE TOMORROW (${task.frequency.uppercase()})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Amber
                    )
                }
            }

            if (task.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action triggers row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateStr = SimpleDateFormat("EEE, MMM d, HH:mm", Locale.US).format(Date(task.deadline))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AccessTime, contentDescription = "", tint = Color.Gray, modifier = Modifier.size(12.dp))
                    Text(
                        text = " Due: $dateStr",
                        fontSize = 11.sp,
                        color = if (task.deadline < System.currentTimeMillis() && !isCompleted) Crimson else Color.Gray,
                        fontWeight = if (task.deadline < System.currentTimeMillis() && !isCompleted) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onCompleteToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isCompleted) Emerald.copy(alpha = 0.2f) else DarkCard)
                    ) {
                        Icon(
                            imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Complete",
                            tint = if (isCompleted) Emerald else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DarkCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Crimson,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
