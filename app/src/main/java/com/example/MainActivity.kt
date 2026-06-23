package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.utils.NotificationHelper
import com.example.ui.screens.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawBehind
import com.example.ui.theme.GuardianAITheme
import com.example.ui.theme.NearBlack
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.DeepIndigo
import com.example.ui.viewmodel.GuardianViewModel
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import com.example.data.worker.NotificationWorker

class MainActivity : ComponentActivity() {

    private val viewModel: GuardianViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create alert notifications setup
        NotificationHelper.createNotificationChannels(this)

        // Enqueue background risk assessment and upcoming deadline notifier check every hour
        try {
            val workRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "GuardianPeriodicUpdates",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule NotificationWorker", e)
        }

        setContent {
            // Respect dynamic theme options from settings
            GuardianAITheme(themeStyle = viewModel.themeStyle) {

                // Track current active screen (simple robust state machine navigation)
                var currentMainTab by remember { mutableStateOf("Dashboard") } // "Dashboard", "Tasks", "Calendar", "Profile"
                var activeOverlayScreen by remember { mutableStateOf<String?>(null) } // "VoiceAssistant", "TaskCreation", "Recommendations", "WorldClock"

                if (!viewModel.profileSetupCompleted) {
                    OnboardingScreen(
                        viewModel = viewModel,
                        onFinished = { viewModel.profileSetupCompleted = true }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            // Top Bar containing Voice Assistant trigger & Briefing trigger
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = "GUARDIAN AI",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 3.sp
                                        ),
                                        color = NeonCyan
                                    )
                                },
                                actions = {
                                    IconButton(
                                        onClick = { activeOverlayScreen = "Recommendations" }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timeline,
                                            contentDescription = "Personal Briefing",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(
                                        onClick = { activeOverlayScreen = "VoiceAssistant" }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice assistant",
                                            tint = NeonCyan
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = NearBlack
                                )
                            )
                        },
                        bottomBar = {
                            // Bottom Navigation for: Dashboard | Tasks | Calendar | Profile
                            NavigationBar(
                                containerColor = NearBlack,
                                modifier = Modifier.height(84.dp)
                            ) {
                                val tabs = listOf(
                                    NavigationItem("Dashboard", Icons.Default.Dashboard, "dashboard_tab"),
                                    NavigationItem("Tasks", Icons.Default.Task, "tasks_tab"),
                                    NavigationItem("Calendar", Icons.Default.CalendarMonth, "calendar_tab"),
                                    NavigationItem("Profile", Icons.Default.AccountCircle, "profile_tab")
                                )

                                tabs.forEach { tab ->
                                    val isSelected = currentMainTab == tab.name
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = {
                                            currentMainTab = tab.name
                                            activeOverlayScreen = null // close any overlays
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.name,
                                                tint = if (isSelected) NeonCyan else Color.Gray,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = tab.name,
                                                color = if (isSelected) Color.White else Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.testTag(tab.tag)
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .background(NearBlack)
                                .drawBehind {
                                    // Top-left glowing cyan orb (opacity ~12% for atmospheric depth)
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(NeonCyan.copy(alpha = 0.12f), Color.Transparent),
                                            center = Offset(0f, 0f),
                                            radius = size.minDimension * 0.9f
                                        ),
                                        radius = size.minDimension * 0.9f,
                                        center = Offset(0f, 0f)
                                    )
                                    // Bottom-right glowing deep purple/indigo orb (opacity ~35%)
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(DeepIndigo.copy(alpha = 0.35f), Color.Transparent),
                                            center = Offset(size.width, size.height),
                                            radius = size.minDimension * 1.1f
                                        ),
                                        radius = size.minDimension * 1.1f,
                                        center = Offset(size.width, size.height)
                                    )
                                }
                        ) {
                            // Core Screen container mapping
                            when (currentMainTab) {
                                "Dashboard" -> {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        onNavigateToTasks = { currentMainTab = "Tasks" }
                                    )
                                }
                                "Tasks" -> {
                                    TaskListScreen(
                                        viewModel = viewModel,
                                        onNavigateToCreation = { activeOverlayScreen = "TaskCreation" }
                                    )
                                }
                                "Calendar" -> {
                                    CalendarScreen(viewModel = viewModel)
                                }
                                "Profile" -> {
                                    ProfileSettingsScreen(viewModel = viewModel)
                                }
                            }

                            // Overlay Views for helper workflows (using slide animated configurations)
                            AnimatedVisibility(
                                visible = activeOverlayScreen != null,
                                enter = slideInVertically(initialOffsetY = { it }),
                                exit = slideOutVertically(targetOffsetY = { it }),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(NearBlack)
                                ) {
                                    // Render appropriate overlay Screen
                                    when (activeOverlayScreen) {
                                        "VoiceAssistant" -> {
                                            VoiceOverlayContainer(
                                                onClose = { activeOverlayScreen = null },
                                                content = { VoiceAssistantScreen(viewModel = viewModel) }
                                            )
                                        }
                                        "TaskCreation" -> {
                                            TaskCreationScreen(
                                                viewModel = viewModel,
                                                onBack = { activeOverlayScreen = null }
                                            )
                                        }
                                        "Recommendations" -> {
                                            OverlayContainer(
                                                title = "GUARDIAN DIRECTIVE BRIEFING",
                                                onClose = { activeOverlayScreen = null },
                                                content = { RecommendationsScreen(viewModel = viewModel) }
                                            )
                                        }
                                        "WorldClock" -> {
                                            OverlayContainer(
                                                title = "WORLD RUNWAY TIMING",
                                                onClose = { activeOverlayScreen = null },
                                                content = { WorldClockScreen(viewModel = viewModel) }
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
}

// Custom Support Nav classes
data class NavigationItem(
    val name: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tag: String
)

@Composable
fun OverlayContainer(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NearBlack)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = NeonCyan
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NearBlack.copy(alpha = 0.5f))
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
fun VoiceOverlayContainer(
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NearBlack)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray.copy(alpha = 0.4f))
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
