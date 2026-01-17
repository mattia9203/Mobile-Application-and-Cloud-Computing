package com.example.runapp

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runapp.ui.theme.RunAppTheme
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun MainScreen(viewModel: RunViewModel = viewModel()) {
    RunAppTheme {
        // STATE
        var isRunActive by remember { mutableStateOf(false) }
        var showAllActivities by remember { mutableStateOf(false) }
        var selectedRun by remember { mutableStateOf<RunEntity?>(null) } // Controls the Dialog

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
        if (isRunActive) {
            RunSessionScreen(
                onStopClick = { runData ->
                    viewModel.saveRun(runData)
                    isRunActive = false
                }
            )
        } else if (showAllActivities) {
            AllActivitiesScreen(
                onBackClick = { showAllActivities = false },
                onItemClick = { run -> selectedRun = run }
            )
        } else {
            // DASHBOARD
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { AppHeader() },
                bottomBar = {
                    BottomAppBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                        IconButton(onClick = {}, modifier = Modifier.weight(1f)) { Icon(Icons.Default.History, "History", tint = Color.Gray) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {}, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Person, "Profile", tint = Color.Gray) }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { isRunActive = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp).offset(y = 50.dp)
                    ) {
                        Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(40.dp), tint = Color.Black)
                    }
                },
                floatingActionButtonPosition = FabPosition.Center
            ) { paddingValues ->
                Column(
                    modifier = Modifier.padding(paddingValues).padding(horizontal = 20.dp).fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("READY TO RUN?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(20.dp))

                    // GOAL CARD
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Weekly Goal", color = Color.Gray)
                                Text("10 km", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            val progress = ((totalDistance ?: 0f) / 10f).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.DarkGray
                            )
                            Text("%.1f km done".format(totalDistance ?: 0f), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                        }
                    }

                    // HEADER
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent Activity", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { showAllActivities = true }) {
                            Text("All", color = Color.Gray)
                        }
                    }

                    // RECENT ACTIVITY PANEL
                    RecentActivityPanel(runs = recentRuns, onItemClick = { run -> selectedRun = run })
                }
            }
        }

        // --- THE POPUP DIALOG LOGIC ---
        // This sits "on top" of everything else
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

@Composable
fun RecentActivityPanel(runs: List<RunEntity>, onItemClick: (RunEntity) -> Unit) {
    val topRuns = runs.take(3)
    if (topRuns.isNotEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                topRuns.forEachIndexed { index, run ->
                    RecentActivityItem(run = run, onClick = { onItemClick(run) })
                    if (index < topRuns.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 86.dp, end = 16.dp), thickness = 1.dp, color = Color.DarkGray)
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
        // MAP IMAGE
        Box(
            modifier = Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)).background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (run.imagePath != null) {
                val bitmap: Bitmap? = remember(run.imagePath) { BitmapFactory.decodeFile(run.imagePath) }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Map, null, tint = Color.Gray)
                }
            } else {
                Icon(Icons.Default.Map, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // STATS
        Column(modifier = Modifier.weight(1f)) {
            val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
            Text(dateFormat.format(Date(run.timestamp)), fontSize = 12.sp, color = Color.LightGray)
            Text("%.2f km".format(run.distanceKm), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(formatDurationMain(run.durationMillis), fontSize = 12.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.width(10.dp))
                Text("${run.caloriesBurned} kcal", fontSize = 12.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.width(10.dp))
                Text("%.1f km/h".format(run.avgSpeedKmh), fontSize = 12.sp, color = Color.LightGray)
            }
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
    }
}

fun formatDurationMain(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Hello,", color = Color.Gray)
            Text("Runner", color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}