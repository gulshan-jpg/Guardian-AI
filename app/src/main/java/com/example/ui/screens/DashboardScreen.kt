package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.Task
import com.example.ui.theme.*
import com.example.ui.viewmodel.GuardianViewModel
import com.example.ui.viewmodel.CityClock
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: GuardianViewModel,
    onNavigateToTasks: () -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val activeTasks = tasks.filter { !it.isCompleted }

    // Live Clock timer
    var currentUtcTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentUtcTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    // High risk elements detection
    val highRiskTasks = activeTasks.filter { it.riskScore > 70 }

    // Calculate segments for Donut chart
    val completedCount = tasks.count { it.isCompleted }.toFloat()
    val overdueCount = activeTasks.count { it.deadline < System.currentTimeMillis() }.toFloat()
    val upcomingCount = activeTasks.count { it.deadline >= System.currentTimeMillis() && it.riskScore <= 40 }.toFloat()
    val inProgressCount = activeTasks.count { it.riskScore > 40 && it.riskScore <= 70 }.toFloat()
    val totalCount = tasks.size.toFloat()

    val completionPercentage = if (totalCount > 0) ((completedCount / totalCount) * 100).toInt() else 0

    // Stagger animation for the ring
    val ringSweepAnim = remember { Animatable(0f) }
    LaunchedEffect(tasks) {
        ringSweepAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(1500, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 100.dp)
        ) {
            // Header Top Welcome Setup
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Good Morning, ${viewModel.userName}",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "You have ${activeTasks.size} outstanding targets today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                    }

                    // Simple display indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (viewModel.isAiProcessing) NeonCyan else Color.Gray.copy(alpha = 0.5f))
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // World Clock Sub-Widget Row
            item {
                var searchOpen by remember { mutableStateOf(false) }
                var searchQuery by remember { mutableStateOf("") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WORLD CLOCK CORRIDORS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = NeonCyan
                    )

                    IconButton(
                        onClick = { searchOpen = !searchOpen },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.AddCircle,
                            contentDescription = "Search Cities",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (searchOpen) {
                    val availableCities = listOf(
                        CityClock("Paris", "Europe/Paris", "CEST"),
                        CityClock("Mumbai", "Asia/Kolkata", "IST"),
                        CityClock("Dubai", "Asia/Dubai", "GST"),
                        CityClock("Singapore", "Asia/Singapore", "SGT"),
                        CityClock("New York", "America/New_York", "EST"),
                        CityClock("London", "Europe/London", "GMT"),
                        CityClock("Tokyo", "Asia/Tokyo", "JST"),
                        CityClock("Sydney", "Australia/Sydney", "AEST"),
                        CityClock("Los Angeles", "America/Los_Angeles", "PST"),
                        CityClock("Toronto", "America/Toronto", "EST"),
                        CityClock("Berlin", "Europe/Berlin", "CET"),
                        CityClock("Rome", "Europe/Rome", "CET"),
                        CityClock("Beijing", "Asia/Shanghai", "CST"),
                        CityClock("Seoul", "Asia/Seoul", "KST"),
                        CityClock("Istanbul", "Europe/Istanbul", "TRT")
                    )

                    val filteredCities = availableCities.filter {
                        it.name.lowercase().contains(searchQuery.lowercase())
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkCard)
                            .padding(12.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            placeholder = { Text("Search city (e.g. Paris, Mumbai)", color = Color.Gray, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredCities.isEmpty()) {
                            Text(text = "No matching global cities", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filteredCities.take(8).forEach { city ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NearBlack)
                                            .clickable {
                                                viewModel.addWorldClockCity(city)
                                                searchOpen = false
                                                searchQuery = ""
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = city.name, color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    viewModel.worldClockCities.forEach { city ->
                        val sdf = SimpleDateFormat("HH:mm", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone(city.timeZoneId)
                        val formattedTime = sdf.format(Date(currentUtcTime))

                        // Check if in business hours (9AM-6PM)
                        val calendar = Calendar.getInstance(TimeZone.getTimeZone(city.timeZoneId))
                        calendar.timeInMillis = currentUtcTime
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        val isBizHour = hour in 9..17

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = city.name.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = if (isBizHour) Emerald else Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isBizHour) "BIZ HOUR" else "OFF HOUR",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isBizHour) Emerald.copy(alpha = 0.7f) else Color.Gray
                                )
                            }
                            IconButton(
                                onClick = { viewModel.removeWorldClockCity(city.name) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove clock",
                                    tint = Crimson,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Donut Analytics ring
            item {
                Text(
                    text = "CRITICAL TARGET SEGMENTATION",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = NeonCyan,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Drawing local Canvas Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(130.dp)
                        ) {
                            Canvas(modifier = Modifier.size(110.dp)) {
                                val strokeWidth = 14.dp.toPx()
                                drawArc(
                                    color = Color.DarkGray.copy(alpha = 0.2f),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth)
                                )

                                if (totalCount > 0) {
                                    val sweepCompleted = (completedCount / totalCount) * 360f * ringSweepAnim.value
                                    val sweepOverdue = (overdueCount / totalCount) * 360f * ringSweepAnim.value
                                    val sweepInProgress = (inProgressCount / totalCount) * 360f * ringSweepAnim.value
                                    val sweepUpcoming = 360f * ringSweepAnim.value - sweepCompleted - sweepOverdue - sweepInProgress

                                    var startAngle = -90f
                                    // 1. Completed
                                    drawArc(
                                        color = Emerald,
                                        startAngle = startAngle,
                                        sweepAngle = sweepCompleted,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweepCompleted

                                    // 2. Overdue
                                    drawArc(
                                        color = Crimson,
                                        startAngle = startAngle,
                                        sweepAngle = sweepOverdue,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweepOverdue

                                    // 3. In Progress
                                    drawArc(
                                        color = NeonCyan,
                                        startAngle = startAngle,
                                        sweepAngle = sweepInProgress,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweepInProgress

                                    // 4. Upcoming
                                    drawArc(
                                        color = DeepIndigo,
                                        startAngle = startAngle,
                                        sweepAngle = sweepUpcoming,
                                        useCenter = false,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$completionPercentage%",
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "DONE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Legends setup
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LegendItem(color = Emerald, label = "Completed (${completedCount.toInt()})")
                            LegendItem(color = NeonCyan, label = "In Progress (${inProgressCount.toInt()})")
                            LegendItem(color = Crimson, label = "Overdue (${overdueCount.toInt()})")
                            LegendItem(color = DeepIndigo, label = "Upcoming (${upcomingCount.toInt()})")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // High risk critical items list banner
            if (highRiskTasks.isNotEmpty()) {
                item {
                    val pulseTransition = rememberInfiniteTransition(label = "pulse_warning")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.05f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "p_alpha"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Crimson.copy(alpha = pulseAlpha))
                            .border(1.5.dp, Crimson, RoundedCornerShape(16.dp))
                            .clickable { onNavigateToTasks() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert logo",
                            tint = Crimson,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DEADLINE BREACH PREDICTIONS",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${highRiskTasks.size} tasks have an extremely high priority level and need attention. Tap to manage actions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Bottom focus cards row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Today's Focus Card (Highest priority)
                    val topPriorityTask = activeTasks.maxByOrNull { it.priority }
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "TODAY'S INTENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (topPriorityTask != null) {
                                Text(
                                    text = topPriorityTask.title,
                                    maxLines = 2,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Priority",
                                        tint = Amber,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = " Priority ${topPriorityTask.priority}/5",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber
                                    )
                                }
                            } else {
                                Text(
                                    text = "Zero pending targets. Clean slate!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Next Deadline Countdown Card
                    val nextDeadlineTask = activeTasks.filter { it.deadline > System.currentTimeMillis() }
                        .minByOrNull { it.deadline }
                    
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "NEXT DEADLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Crimson
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (nextDeadlineTask != null) {
                                val diffInMillis = nextDeadlineTask.deadline - currentUtcTime
                                val diffInHours = diffInMillis / 3600000
                                val diffInMins = (diffInMillis % 3600000) / 60000
                                val diffInSecs = (diffInMillis % 60000) / 1000

                                val displayTimer = if (diffInMillis > 0) {
                                    String.format("%02dh %02dm %02ds", diffInHours, diffInMins, diffInSecs)
                                } else {
                                    "Missed!"
                                }

                                Text(
                                    text = displayTimer,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = nextDeadlineTask.title,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.LightGray
                                )
                            } else {
                                Text(
                                    text = "No upcoming targets on deck.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Quick access section of High risk items
            if (highRiskTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "HIGH PRIORITY DETAILS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = Crimson,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                items(highRiskTasks.take(2)) { task ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .border(1.dp, Crimson.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable { onNavigateToTasks() },
                        colors = CardDefaults.cardColors(containerColor = DarkCard.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Due: " + SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(task.deadline)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.LightGray
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Crimson.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "PRIORITY: ${task.riskScore}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Crimson
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.LightGray
        )
    }
}
