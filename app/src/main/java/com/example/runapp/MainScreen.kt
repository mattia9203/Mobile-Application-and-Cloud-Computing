package com.example.runapp

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage

@Composable
fun MainScreen(onSignOut: () -> Unit) {
    val viewModel: RunViewModel = viewModel()
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    // 2. Get the name (If name is empty, use "Runner" as backup)
    val displayName = user?.displayName ?: "Runner"
    RunAppTheme {
        // STATES
        // Observe runState
        val runState by viewModel.runState.collectAsState()
        // Calculate isRunActive based on the state (Running or Paused = Active)
        val isRunActive = runState != RunState.READY

        val currentDuration by viewModel.currentDuration.collectAsState()
        val currentDistance by viewModel.currentDistance.collectAsState()
        val currentCalories by viewModel.currentCalories.collectAsState()

        // LOCAL STATE TO CONTROL FULL SCREEN MAP VIEW
        var showFullRunScreen by remember { mutableStateOf(false) }

        var showAllActivities by remember { mutableStateOf(false) }
        var selectedRun by remember { mutableStateOf<RunEntity?>(null) }

        val recentRuns by viewModel.allRuns.collectAsState()
        val totalDistance by viewModel.totalDistance.collectAsState()

        // PERMISSIONS
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        LaunchedEffect(Unit) {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
            permissionLauncher.launch(perms.toTypedArray())
        }

        // --- MAIN UI STRUCTURE ---
        if (showFullRunScreen) {
            // Screen handles map logic. We don't force start here.
            RunSessionScreen(
                viewModel = viewModel,
                onStopClick = { runData ->
                    viewModel.saveRun(runData)
                    showFullRunScreen = false
                },
                onBackClick = {
                    showFullRunScreen = false
                }
            )
        } else if (showAllActivities) {
            AllActivitiesScreen(
                onBackClick = { showAllActivities = false },
                onItemClick = { run -> selectedRun = run }
            )
        } else {
            // DASHBOARD
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // 1. THE BLUE HEADER BACKGROUND SHAPE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(HeaderBlueStart, HeaderBlueEnd)
                            )
                        )
                )

                // 2. THE SCAFFOLD
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = { AppHeader(whiteText = true) },
                    bottomBar = {
                        BottomAppBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                            IconButton(onClick = {}, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Home, "Home", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {}, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Person, "Profile", tint = MediumGray)
                            }
                        }
                    },
                    floatingActionButton = {
                        // Only show FAB if run is NOT active (READY state)
                        if (!isRunActive) {
                            FloatingActionButton(
                                onClick = {
                                    // Just open the screen. Do NOT auto-start.
                                    showFullRunScreen = true
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = White,
                                shape = CircleShape,
                                modifier = Modifier.size(70.dp).offset(y = 50.dp)
                            ) {
                                Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(35.dp))
                            }
                        }
                    },
                    floatingActionButtonPosition = FabPosition.Center
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .padding(paddingValues)
                            .padding(horizontal = 20.dp)
                            .fillMaxSize()
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("READY TO RUN?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = White.copy(alpha = 0.9f))
                        Spacer(modifier = Modifier.height(30.dp))

                        IconButton(onClick = {
                            auth.signOut() // 1. Tell Firebase to Log Out
                            onSignOut()    // 2. Tell the App to switch screens
                        }) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Logout",
                                tint = Color.Red
                            )
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 25.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Weekly Goal", color = MediumGray)
                                    Text("10 km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                val progress = ((totalDistance ?: 0f) / 10f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = NearWhite
                                )
                                Text("%.1f km done".format(totalDistance ?: 0f), fontSize = 12.sp, color = MediumGray, modifier = Modifier.padding(top = 8.dp))
                            }
                        }

                        // CURRENT SESSION PANEL (Only if run is active)
                        if (isRunActive) {
                            CurrentSessionPanel(
                                duration = currentDuration,
                                distance = currentDistance,
                                calories = currentCalories,
                                onClick = { showFullRunScreen = true }
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent Activity", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                            TextButton(onClick = { showAllActivities = true }) {
                                Text("See All", color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        RecentActivityPanel(runs = recentRuns, onItemClick = { run -> selectedRun = run })
                    }
                }
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
    }
}

// --- HELPER COMPONENTS ---

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
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Current Session",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatDurationMain(duration),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "%.2f km".format(distance),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$calories kcal",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(16.dp)).background(NearWhite),
            contentAlignment = Alignment.Center
        ) {
            // FIXED: Handles both Local Files (Old) and Cloud URLs (New)
            AsyncImage(
                model = run.imagePath, // Coil handles File path OR Url automatically
                contentDescription = "Map",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(id = android.R.drawable.ic_menu_mapmode) // Optional fallback
            )
            // Note: If you don't have a fallback icon, removing 'error' is fine too
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
fun AppHeader(whiteText: Boolean = false) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    // 2. Get the name (If name is empty, use "Runner" as backup)
    val displayName = user?.displayName ?: "Runner"
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp).statusBarsPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Hello,", color = if (whiteText) White.copy(alpha = 0.8f) else MediumGray)
            Text("$displayName", color = if (whiteText) White else MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.size(45.dp).background(White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = if(whiteText) White else MaterialTheme.colorScheme.primary)
        }
    }
}
