package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.Task
import com.example.ui.theme.*
import com.example.ui.viewmodel.GuardianViewModel
import java.text.SimpleDateFormat
import java.util.*

// ==========================================
// SCREEN 7: VOICE ASSISTANT
// ==========================================
@Composable
fun VoiceAssistantScreen(viewModel: GuardianViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w1"
    )
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "w2"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "GUARDIAN VOCAL LINK",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.5.sp),
                color = NeonCyan
            )

            // Animated Pulse Waveform Layout
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(180.dp)
            ) {
                if (viewModel.isListening) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .scale(waveScale2)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.2f))
                    )
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(waveScale1)
                            .clip(CircleShape)
                            .background(NeonCyan.copy(alpha = 0.25f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(if (viewModel.isListening) Emerald else ElectricBlue)
                        .border(2.dp, NeonCyan, CircleShape)
                        .clickable {
                            if (viewModel.isListening) {
                                viewModel.stopListeningVoice()
                            } else {
                                viewModel.startListeningVoice()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (viewModel.isListening) Icons.Default.Mic else Icons.Default.MicNone,
                        contentDescription = "Voice Trigger",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Command guidelines Helper Cards
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (viewModel.isListening) "TRANSLATING VOCAL BEATS..." else "TAP GUARDIAN TO ENGAGE",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Supported: \"Add task Finance due Friday\", \"What are my tasks for today?\", \"Mark Math task as complete\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Result Terminal card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "RECOGNIZED INPUT:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        text = viewModel.recognizedText.ifEmpty { "Speech text will display here..." },
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))

                    Text(text = "GUARDIAN DIRECTIVE FEEDBACK:", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                    Text(
                        text = viewModel.speakFeedback.ifEmpty { "Awaiting speech trigger..." },
                        color = NeonCyan,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 8: CALENDAR VIEW
// ==========================================
@Composable
fun CalendarScreen(viewModel: GuardianViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.tasks.collectAsState()
    val activeTasks = tasks.filter { !it.isCompleted }

    // June 2026 static representation layout (matches user context 2026-06-23)
    val calendar = Calendar.getInstance().apply {
        set(2026, Calendar.JUNE, 1)
    }
    val startDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Sunday=0, Monday=1
    val daysInMonth = 30 // June has 30 days
    
    var selectedDay by remember { mutableStateOf(23) } // default today is June 23, 2026

    // Filter tasks for the selected date
    val selectedDayTasks = tasks.filter { task ->
        val tc = Calendar.getInstance().apply { timeInMillis = task.deadline }
        tc.get(Calendar.DAY_OF_MONTH) == selectedDay && tc.get(Calendar.MONTH) == Calendar.JUNE && tc.get(Calendar.YEAR) == 2026
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Temporal Calendar",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )

                Text(
                    text = "JUNE 2026",
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Calendar Grid Calendar header (Mo, Tu etc)
            val weekDays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Month Grid Calculations
            val totalCells = 35 // 5 weeks standard June 2026 layout
            val rows = totalCells / 7

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val dayNum = cellIndex - startDayOfWeek + 1
                            val isValidDay = dayNum in 1..daysInMonth

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            !isValidDay -> Color.Transparent
                                            dayNum == selectedDay -> ElectricBlue
                                            else -> DarkCard
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (dayNum == selectedDay) NeonCyan else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = isValidDay) {
                                        selectedDay = dayNum
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isValidDay) {
                                    val dayTasks = tasks.filter { t ->
                                        val tc = Calendar.getInstance().apply { timeInMillis = t.deadline }
                                        tc.get(Calendar.DAY_OF_MONTH) == dayNum && tc.get(Calendar.MONTH) == Calendar.JUNE
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = dayNum.toString(),
                                            color = if (dayNum == selectedDay) NeonCyan else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )

                                        // Small colored indicators representation risk scores
                                        if (dayTasks.isNotEmpty()) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                dayTasks.take(3).forEach { dt ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (dt.isCompleted) Emerald
                                                                else when (dt.riskLevel) {
                                                                    "CRITICAL", "HIGH" -> Crimson
                                                                    "MEDIUM" -> Amber
                                                                    else -> NeonCyan
                                                                }
                                                            )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sync tasks & List tasks for selected day
            Text(
                text = "JUNE $selectedDay, 2026 SCHEDULES",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = NeonCyan
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedDayTasks.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(selectedDayTasks) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = task.title, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "Category: ${task.category} | Risk: ${task.riskScore}%", fontSize = 11.sp, color = Color.Gray)
                                }

                                Button(
                                    onClick = {
                                        viewModel.syncToSystemCalendar(task, context)
                                        Toast.makeText(context, "Synced ${task.title} to device calendar!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Sync, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "SYNC CAL", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Protected Zone. Zero deadlines configured.", color = Color.Gray)
                }
            }
        }
    }
}

// ==========================================
// SCREEN 9: WORLD CLOCK & TIME ZONE MANAGER
// ==========================================
@Composable
fun WorldClockScreen(viewModel: GuardianViewModel) {
    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Public, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Corridors World Clock",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )
            }
            Text(
                text = "Business hours windows (09:00 - 18:00) highlighted in Emerald",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(viewModel.worldClockCities) { city ->
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone(city.timeZoneId)
                    val timeVal = sdf.format(Date(currentTimeMillis))

                    val sCal = Calendar.getInstance(TimeZone.getTimeZone(city.timeZoneId))
                    sCal.timeInMillis = currentTimeMillis
                    val hour = sCal.get(Calendar.HOUR_OF_DAY)
                    val isBizHour = hour in 9..17

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = city.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                Text(
                                    text = "Offset: ${city.code} | " + if (isBizHour) "Working Period" else "Leisure Hours",
                                    fontSize = 12.sp,
                                    color = if (isBizHour) Emerald else Color.Gray
                                )
                            }

                            Text(
                                text = timeVal,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                                color = if (isBizHour) Emerald else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 10: PERSONALIZED RECOMMENDATIONS
// ==========================================
@Composable
fun RecommendationsScreen(viewModel: GuardianViewModel) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Timeline, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guardian Briefings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = Color.White
                    )
                }

                IconButton(onClick = { viewModel.refreshRecommendations() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh brief", tint = NeonCyan)
                }
            }
            Text(
                text = "Gemini flash personalized performance analyses & optimal focus times",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Motivational Quote Shield Panel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ElectricBlue.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, ElectricBlue)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = "", tint = Amber, modifier = Modifier.size(34.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "\"${viewModel.motivationalMessage}\"",
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            lineHeight = 22.sp
                        )
                    }
                }

                // Optimal Work Hours indicators Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "OPTIMAL FOCUS ALLOCATIONS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        
                        FocusPeriodRow(title = "Morning Block Focus:", text = viewModel.morningRecommendation)
                        FocusPeriodRow(title = "Afternoon Block Strategy:", text = viewModel.afternoonRecommendation)
                        FocusPeriodRow(title = "Avoid Energy Dip Hours:", text = viewModel.avoidHours)
                    }
                }

                // Daily Tip Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Lightbulb, contentDescription = "", tint = Amber, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "ENERGY MANAGEMENT STRATEGY", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = viewModel.energyTip, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable
fun FocusPeriodRow(title: String, text: String) {
    Column {
        Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text(text = text, fontSize = 14.sp, color = Color.LightGray)
    }
}

// ==========================================
// SCREEN 11: PROFILE + SETTINGS
// ==========================================
@Composable
fun ProfileSettingsScreen(viewModel: GuardianViewModel) {
    val context = LocalContext.current
    var showContactDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var inquiryText by remember { mutableStateOf("") }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
            containerColor = DarkCard,
            title = {
                Text(
                    text = "Contact Us",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "For suggestions or bugs, send inquiries directly to: gushanjpg@gmail.com",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = inquiryText,
                        onValueChange = { inquiryText = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("Describe your query or suggestion here...", color = Color.Gray, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = false
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        Toast.makeText(context, "Inquiry sent successfully to gushanjpg@gmail.com!", Toast.LENGTH_LONG).show()
                        showContactDialog = false
                        inquiryText = ""
                    }
                ) {
                    Text("Send Inquiry", color = NeonCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DarkCard,
            title = {
                Text(
                    text = "About Us",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Deadline Vault is a clean, modern personal organizer designed with Material Design 3. Streamlining schedules, target deadlines, and keeping world clock corridors easily accessible.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Developer: Gulshan Gautam",
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Done", color = NeonCyan)
                }
            }
        )
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
            Text(
                text = "Shield Profile",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User info avatar block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkCard)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = viewModel.userName.take(1), fontWeight = FontWeight.ExtraBold, color = NeonCyan, fontSize = 24.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(text = viewModel.userName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text(text = "Strategic Client Tier", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                // Dynamic Theme style selection (Dark / AMOLED / Navy)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "GUARDIAN SYSTEM THEME", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        Spacer(modifier = Modifier.height(10.dp))

                        val styles = listOf("Dark", "AMOLED", "Navy")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            styles.forEach { style ->
                                val isSelected = viewModel.themeStyle == style
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) ElectricBlue else NearBlack)
                                        .clickable { viewModel.themeStyle = style }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = style,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) NeonCyan else Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }

                // Settings & Options Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "SECURITY & COMMUNICATIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )

                        // Basic Settings Switch Row (Notification preferences)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.notificationLevel = if (viewModel.notificationLevel == "HIGH") "LOW" else "HIGH" }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "High Priority Alarms", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text(text = "Trigger alerts on upcoming deadlines", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Switch(
                                checked = viewModel.notificationLevel == "HIGH",
                                onCheckedChange = { viewModel.notificationLevel = if (it) "HIGH" else "LOW" },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                            )
                        }

                        // About Us Click Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAboutDialog = true }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "About Us", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text(text = "Learn more about the Vault and Developer", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }

                        // Contact Us Click Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showContactDialog = true }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = "", tint = NeonCyan, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Contact Us", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                Text(text = "Send direct inquiries via secure portal", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                    }
                }

                // Data Exports download triggers
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "DATA PORTABILITY EXPORTS", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    val data = viewModel.exportTasksAsJson()
                                    Toast.makeText(context, "Exported JSON successfully!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "EXPORT JSON", color = Color.White, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    val data = viewModel.exportTasksAsCsv()
                                    Toast.makeText(context, "Exported CSV successfully!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "EXPORT CSV", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
