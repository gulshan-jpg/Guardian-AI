package com.example.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.GuardianViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCreationScreen(
    viewModel: GuardianViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val editingTask = viewModel.editingTask
    var taskName by remember { mutableStateOf(editingTask?.title ?: "") }
    var selectedCategory by remember { mutableStateOf(editingTask?.category ?: "Work") }
    var estimatedEffortHours by remember { mutableStateOf(if (editingTask != null) editingTask.estimatedEffort / 60 else 2) }
    var selectedFrequency by remember { mutableStateOf(editingTask?.frequency ?: "Once") }
    var customDatesStr by remember { mutableStateOf(editingTask?.customDates ?: "") }
    var isAutoPriority by remember { mutableStateOf(editingTask?.autoPriority ?: false) }
    var manualPriority by remember { mutableStateOf(editingTask?.priority ?: 3) }
    var notesText by remember { mutableStateOf(editingTask?.notes ?: "") }
    var locationText by remember { mutableStateOf(editingTask?.location ?: "") }

    // DateTime Picker values
    val calendar = remember {
        Calendar.getInstance().apply {
            timeInMillis = editingTask?.deadline ?: (System.currentTimeMillis() + 24 * 60 * 60 * 1000)
        }
    }
    var selectedDateStr by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)) }
    var selectedTimeStr by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.US).format(calendar.time)) }

    val categories = listOf("Assignment", "Meeting", "Bill", "Flight", "Personal", "Work", "Health", "Other")

    val scrollState = rememberScrollState()

    fun updateDeadline() {
        // Parse date and time to set calendar timestamp
        try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val path = "$selectedDateStr $selectedTimeStr"
            format.parse(path)?.time?.let {
                calendar.timeInMillis = it
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.editingTask = null
                    onBack()
                }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (viewModel.editingTask != null) "Modify Target Status" else "Shield New Target",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Task Name
                Column {
                    Text(text = "TARGET TITLE", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = taskName,
                        onValueChange = { taskName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_name_input"),
                        placeholder = { Text("e.g. Finance Exam Submission", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Category Selection
                Column {
                    Text(text = "CATEGORY COHORT", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            val isSelected = selectedCategory == category
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ElectricBlue else DarkCard)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedCategory = category }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = category,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) NeonCyan else Color.LightGray
                                )
                            }
                        }
                    }
                }

                // Pickers for Date & Time
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "DEADLINE TIMELINE", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Date Picker trigger button
                        Button(
                            onClick = {
                                DatePickerDialog(
                                    context,
                                    { _, year, m, day ->
                                        selectedDateStr = String.format("%04d-%02d-%02d", year, m + 1, day)
                                        updateDeadline()
                                    },
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH),
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Date", tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = selectedDateStr, color = Color.White)
                        }

                        // Time Picker trigger button
                        Button(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, min ->
                                        selectedTimeStr = String.format("%02d:%02d", hour, min)
                                        updateDeadline()
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = selectedTimeStr, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Task Frequency Selection (Once, Daily, Weekly, Monthly, Custom Dates)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "TASK FREQUENCY", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Once", "Daily", "Weekly", "Monthly", "Custom Dates").forEach { freq ->
                            val isSelected = selectedFrequency == freq
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ElectricBlue else DarkCard)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedFrequency = freq }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = freq,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) NeonCyan else Color.LightGray
                                )
                            }
                        }
                    }
                    if (selectedFrequency == "Custom Dates") {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customDatesStr,
                            onValueChange = { customDatesStr = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. 2026-06-25, 2026-06-28", color = Color.Gray, fontSize = 13.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // AI Auto priority toggle vs add buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "LET AI DECIDE PRIORITY", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text(text = "Gemini flash assesses overall loads to score priority", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Switch(
                            checked = isAutoPriority,
                            onCheckedChange = { isAutoPriority = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                        )
                    }

                    if (!isAutoPriority) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Manual Scale:", style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..5).forEach { badgeIndex ->
                                    Icon(
                                        imageVector = Icons.Default.AddCircle,
                                        contentDescription = "Priority selector",
                                        tint = if (badgeIndex <= manualPriority) Amber else Color.Gray,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clickable { manualPriority = badgeIndex }
                                    )
                                }
                            }
                        }
                    }
                }

                // Rich notes text area
                Column {
                    Text(text = "RICH NOTES & SPECS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        placeholder = { Text("Details or specific sub-items to target", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Location field
                Column {
                    Text(text = "VENUE LOCATION DIRECTIVES (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Digital Portal or physical geography", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(100.dp))
            }

            // Save floating trigger button
            Button(
                onClick = {
                    if (taskName.trim().isNotEmpty()) {
                        val editing = viewModel.editingTask
                        if (editing != null) {
                            viewModel.updateTask(
                                id = editing.id,
                                title = taskName,
                                category = selectedCategory,
                                deadlineTimestamp = calendar.timeInMillis,
                                estimatedHours = 2,
                                notes = notesText,
                                isAutoPriority = isAutoPriority,
                                manualPriority = manualPriority,
                                location = locationText.ifEmpty { null },
                                voicePath = null,
                                frequency = selectedFrequency,
                                customDates = if (selectedFrequency == "Custom Dates") customDatesStr else null
                            )
                        } else {
                            viewModel.createTask(
                                title = taskName,
                                category = selectedCategory,
                                deadlineTimestamp = calendar.timeInMillis,
                                estimatedHours = 2,
                                notes = notesText,
                                isAutoPriority = isAutoPriority,
                                manualPriority = manualPriority,
                                location = locationText.ifEmpty { null },
                                voicePath = null,
                                frequency = selectedFrequency,
                                customDates = if (selectedFrequency == "Custom Dates") customDatesStr else null
                            )
                        }
                        viewModel.editingTask = null
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .height(56.dp)
                    .testTag("submit_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                if (viewModel.isAiProcessing) {
                    CircularProgressIndicator(color = NearBlack, modifier = Modifier.size(24.dp))
                } else {
                    Text(text = "CONFIRM DEADLINE", color = NearBlack, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
