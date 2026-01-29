package com.example.runapp

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.runapp.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSignOut: () -> Unit,
    onNavigateToStats: () -> Unit = {}
) {
    val viewModel: RunViewModel = viewModel()
    val isAppDarkMode by viewModel.isAppDarkMode.collectAsState() // Observe Dark Mode

    LaunchedEffect(Unit) {
        viewModel.loadGoalsFromCloud()
    }

    RunAppTheme(darkTheme = isAppDarkMode) {
        // STATES
        val runState by viewModel.runState.collectAsState()
        val isRunActive = runState != RunState.READY

        val currentDuration by viewModel.currentDuration.collectAsState()
        val currentDistance by viewModel.currentDistance.collectAsState()
        val currentCalories by viewModel.currentCalories.collectAsState()

        var showFullRunScreen by remember { mutableStateOf(false) }
        var showAllActivities by remember { mutableStateOf(false) }
        var selectedRun by remember { mutableStateOf<RunEntity?>(null) }

        val recentRuns by viewModel.allRuns.collectAsState()
        val totalDistance by viewModel.totalDistance.collectAsState()
        val weeklyCalories by viewModel.weeklyCalories.collectAsState()
        val goalDistance by viewModel.goalDistance.collectAsState()
        val goalCalories by viewModel.goalCalories.collectAsState()
        var showStatsScreen by remember {mutableStateOf(false)}
        var showSettingsScreen by remember { mutableStateOf(false) } // New State
        var showProfileScreen by remember { mutableStateOf(false) }
        val showWelcome by viewModel.showWelcomeDialog.collectAsState()

        // PERMISSIONS
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        LaunchedEffect(Unit) {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
            permissionLauncher.launch(perms.toTypedArray())
        }

        val pullRefreshState = rememberPullToRefreshState()
        if (pullRefreshState.isRefreshing) {
            LaunchedEffect(true) {
                viewModel.refreshData().join() // Wait for refresh to finish
                pullRefreshState.endRefresh()
            }
        }

        // --- MAIN UI STRUCTURE ---
        if (showFullRunScreen) {
            RunSessionScreen(
                viewModel = viewModel,
                onStopClick = { runData ->
                    viewModel.saveRun(runData)
                    showFullRunScreen = false
                },
                onBackClick = { showFullRunScreen = false }
            )
        } else if (showStatsScreen) {
            StatsScreen(
                viewModel = viewModel,
                onBackClick = { showStatsScreen = false }
            )
        } else if (showAllActivities) {
            AllActivitiesScreen(
                onBackClick = { showAllActivities = false },
                onItemClick = { run -> selectedRun = run }
            )
        } else if (showProfileScreen) {
                ProfileScreen(
                    viewModel = viewModel,
                    onBackClick = { showProfileScreen = false },
                    onLogoutClick = onSignOut
                )
                androidx.activity.compose.BackHandler { showProfileScreen = false }
            }
        else if (showSettingsScreen) {
            // NEW SETTINGS SCREEN
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { showSettingsScreen = false }
            )
            androidx.activity.compose.BackHandler { showSettingsScreen = false }
        } else {
            // DASHBOARD with Pull-to-Refresh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .nestedScroll(pullRefreshState.nestedScrollConnection) // <--- 1. Detect Pull Gestures
            ) {

                // 1. BLUE HEADER BACKGROUND
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(235.dp)
                        .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(HeaderBlueStart, HeaderBlueEnd)
                            )
                        )
                )

                // 2. SCAFFOLD
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = { AppHeader(whiteText = true, onProfileClick = { showProfileScreen = true }) },
                    bottomBar = {
                        BottomAppBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                            IconButton(onClick = {}, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Home, "Home", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { showSettingsScreen = true}, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Settings, "Settings", tint = MediumGray)
                            }
                        }
                    },
                    floatingActionButton = {
                        if (!isRunActive) {
                            FloatingActionButton(
                                onClick = { showFullRunScreen = true },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(70.dp)
                                    .offset(y = 50.dp)
                            ) {
                                Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(35.dp))
                            }
                        }
                    },
                    floatingActionButtonPosition = FabPosition.Center
                ) { paddingValues ->

                    // --- SCROLLABLE COLUMN ---
                    Column(
                        modifier = Modifier
                            .padding(paddingValues)
                            .padding(horizontal = 20.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()) // Allow normal scrolling
                    ) {
                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            "READY TO RUN?",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = White.copy(alpha = 0.9f)
                        )

                        Spacer(modifier = Modifier.height(15.dp))

                        // WEEKLY GOAL CARD
                        WeeklyGoalCard(
                            distanceKm = totalDistance,
                            goalKm = goalDistance,
                            calories = weeklyCalories,
                            goalCalories = goalCalories,
                            onClick = { showStatsScreen = true }
                        )

                        // CURRENT SESSION PANEL
                        if (isRunActive) {
                            CurrentSessionPanel(
                                duration = currentDuration,
                                distance = currentDistance,
                                calories = currentCalories,
                                onClick = { showFullRunScreen = true }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // RECENT ACTIVITY HEADER
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recent Activity",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(onClick = { showAllActivities = true }) {
                                Text("See All", color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // RECENT ACTIVITY LIST
                        RecentActivityPanel(
                            runs = recentRuns,
                            onItemClick = { run -> selectedRun = run }
                        )

                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                // --- 2. THE REFRESH SPINNER ---
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = MaterialTheme.colorScheme.primary,
                    containerColor = Color.White
                )
            }
        }

        if (selectedRun != null) {
            RunDetailDialog(
                run = selectedRun!!,
                onDismiss = { selectedRun = null },
                onDelete = {
                    viewModel.deleteRun(selectedRun!!)
                    selectedRun = null
                }
            )
        }
        if (showWelcome) {
            WelcomeGoalsDialog(
                onSave = { d, c -> viewModel.completeOnboarding(d, c) }
            )
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun WelcomeGoalsDialog(onSave: (Float, Int) -> Unit) {
    var distText by remember { mutableStateOf("") }
    var calText by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = {  }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(HeaderBlueStart, HeaderBlueEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Welcome, Runner!",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Let's set your first targets.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                // Inputs
                Column(modifier = Modifier.padding(24.dp)) {
                    OutlinedTextField(
                        value = distText,
                        onValueChange = { distText = it },
                        label = { Text("Weekly Distance (km)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = calText,
                        onValueChange = { calText = it },
                        label = { Text("Weekly Calories (kcal)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val d = distText.toFloatOrNull() ?: 10f
                            val c = calText.toIntOrNull() ?: 2000
                            onSave(d, c)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Let's Go!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
@Composable
fun WeeklyGoalCard(
    distanceKm: Float,
    goalKm: Float,
    calories: Int,
    goalCalories: Int,
    onClick: () -> Unit
) {
    val distProgress = (distanceKm / goalKm).coerceIn(0f, 1f)
    val calProgress = (calories.toFloat() / goalCalories.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp) // Reduced padding
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) { // Reduced internal padding

            // Header with Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Goals",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Details",
                    tint = Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Distance Bar (Blue)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Distance", fontSize = 14.sp, color = Color.Gray)
                Text("${String.format("%.1f", distanceKm)} / ${goalKm.toInt()} km", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = distProgress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Calories Bar (Red)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Calories", fontSize = 14.sp, color = Color.Gray)
                Text("$calories / $goalCalories kcal", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE53935))
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = calProgress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color(0xFFE53935), // Red
                trackColor = Color(0xFFE53935).copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
fun CurrentSessionPanel(
    duration: Long,
    distance: Float,
    calories: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(50.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DirectionsRun, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Current Session", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text(formatDurationMain(duration), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("%.2f km".format(distance), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("$calories kcal", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun RecentActivityPanel(runs: List<RunEntity>, onItemClick: (RunEntity) -> Unit) {
    val topRuns = runs.take(3)
    if (topRuns.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                topRuns.forEachIndexed { index, run ->
                    RecentActivityItem(run = run, onClick = { onItemClick(run) })
                    if (index < topRuns.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 86.dp, end = 16.dp), thickness = 1.dp, color = LightGrayDivider)
                    }
                }
            }
        }
    }
}

@Composable
fun RecentActivityItem(run: RunEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(16.dp)).background(NearWhite),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = run.imagePath,
                contentDescription = "Map",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(id = android.R.drawable.ic_menu_mapmode)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            Text(dateFormat.format(Date(run.timestamp)), fontSize = 12.sp, color = MediumGray)
            Text("%.2f km".format(run.distanceKm), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                SmallStat(Icons.Default.Schedule, formatDurationMain(run.durationMillis))
                Spacer(modifier = Modifier.width(12.dp))
                SmallStat(Icons.Default.LocalFireDepartment, "${run.caloriesBurned} kcal")
                Spacer(modifier = Modifier.width(12.dp))
                SmallStat(Icons.Default.Speed, "%.1f".format(run.avgSpeedKmh))
            }
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun SmallStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MediumGray, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = MediumGray)
    }
}

fun formatDurationMain(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun AppHeader(whiteText: Boolean = false, onProfileClick: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser
    val displayName = user?.displayName ?: "Runner"
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp).statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Hello,", color = if (whiteText) White.copy(alpha = 0.8f) else MediumGray)
            Text(displayName, color = if (whiteText) White else MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .size(45.dp)
                .background(White.copy(alpha = 0.2f), CircleShape)
                .clip(CircleShape)
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = if(whiteText) White else MaterialTheme.colorScheme.primary)
        }
    }
}