package com.example.runapp

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.example.runapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSessionScreen(
    viewModel: RunViewModel,
    onStopClick: (RunEntity) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- OBSERVE VIEWMODEL ---
    val runState by viewModel.runState.collectAsState()
    val durationMillis by viewModel.currentDuration.collectAsState()
    val distanceKm by viewModel.currentDistance.collectAsState()
    val currentSpeedKmh by viewModel.currentSpeed.collectAsState()
    val calories by viewModel.currentCalories.collectAsState()

    // Map Data
    val currentLatLng by viewModel.currentLocation.collectAsState()
    val pathPoints by viewModel.pathPoints.collectAsState()

    // Local UI State
    var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isSnapshotMode by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf(0) }

    // Start Location Updates Immediately
    LaunchedEffect(Unit) {
        viewModel.startLocationUpdates()
    }

    // Step Counter
    val stepCounter = remember { StepCounter(context) }
    LaunchedEffect(runState) {
        if (runState == RunState.RUNNING) {
            stepCounter.stepFlow.collect { steps += it }
        }
    }

    // --- MAIN UI ---
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. MAP
        MapWithRunnerIcon(
            currentLocation = currentLatLng,
            currentSpeedKmh = currentSpeedKmh,
            pathPoints = pathPoints,
            isSnapshotMode = isSnapshotMode,
            onMapLoaded = { googleMapRef = it }
        )

        // 2. BACK BUTTON
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 40.dp)
                .size(45.dp)
                .align(Alignment.TopStart)
                .zIndex(2f)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    onBackClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, "Back", tint = Color.Black)
        }

        // 3. BOTTOM PANEL
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .zIndex(2f)
        ) {
            if (runState == RunState.READY) {
                Button(
                    onClick = { viewModel.startRun() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("START RUNNING", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                RunInfoPanel(
                    durationMillis = durationMillis,
                    distanceKm = distanceKm,
                    calories = calories,
                    speedKmh = currentSpeedKmh,
                    runState = runState,
                    isSaving = isSaving,
                    onTogglePause = {
                        if (runState == RunState.RUNNING) viewModel.pauseRun() else viewModel.resumeRun()
                    },
                    onStop = {
                        // THIS FUNCTION HANDLES THE SNAPSHOT LOGIC
                        finishRun(
                            scope = scope,
                            isSaving = isSaving,
                            mapRef = googleMapRef,
                            context = context,
                            dist = distanceKm,
                            speed = currentSpeedKmh,
                            time = durationMillis,
                            cals = calories,
                            pathPoints = pathPoints,
                            onStartSave = {
                                isSaving = true
                                isSnapshotMode = true // Stops the camera from following the user
                            },
                            onResult = {
                                viewModel.stopRun()
                                onStopClick(it)
                            }
                        )
                    }
                )
            }
        }
    }
}

// --- UPDATED FINISH RUN LOGIC (THE FIX) ---
fun finishRun(
    scope: kotlinx.coroutines.CoroutineScope,
    isSaving: Boolean,
    mapRef: GoogleMap?,
    context: Context,
    dist: Float,
    speed: Float,
    time: Long,
    cals: Int,
    pathPoints: List<LatLng>,
    onStartSave: () -> Unit,
    onResult: (RunEntity) -> Unit
) {
    if (isSaving) return
    onStartSave() // 1. Freeze the map tracking

    scope.launch {
        // 2. Adjust Camera to fit the WHOLE PATH
        if (mapRef != null && pathPoints.isNotEmpty()) {
            try {
                val boundsBuilder = LatLngBounds.Builder()
                pathPoints.forEach { boundsBuilder.include(it) }
                val bounds = boundsBuilder.build()

                if (pathPoints.size > 1) {
                    // Padding = 300 pixels to ensure the path isn't cut off by edges
                    mapRef.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 300))
                } else {
                    // If only 1 point, just center on it
                    mapRef.moveCamera(CameraUpdateFactory.newLatLngZoom(pathPoints[0], 16f))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Wait for the camera animation/tiles to load
        delay(2000L)

        // 4. Capture the pixels
        var savedPath: String? = null
        if (mapRef != null) savedPath = captureMapSnapshot(context, mapRef)

        val hours = time / 1000f / 3600f
        val avgSpeed = if (hours > 0) dist / hours else 0f

        onResult(RunEntity(distanceKm = dist, avgSpeedKmh = avgSpeed, durationMillis = time, caloriesBurned = cals, imagePath = savedPath))
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun RunInfoPanel(
    durationMillis: Long,
    distanceKm: Float,
    calories: Int,
    speedKmh: Float,
    runState: RunState,
    isSaving: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Running Time", fontSize = 14.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(durationMillis), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (runState == RunState.PAUSED) {
                        FilledIconButton(onClick = onStop, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.size(56.dp)) {
                            if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Icon(Icons.Rounded.Stop, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    FilledIconButton(onClick = onTogglePause, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.size(56.dp)) {
                        Icon(imageVector = if (runState == RunState.RUNNING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Toggle", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(thickness = 1.dp, color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PanelStatItem(Icons.Default.DirectionsRun, Color(0xFFFF9800), "%.2f".format(distanceKm), "km")
                PanelVerticalDivider()
                PanelStatItem(Icons.Default.LocalFireDepartment, Color(0xFFFF5722), "$calories", "kcal")
                PanelVerticalDivider()
                PanelStatItem(Icons.Default.Bolt, Color(0xFFFFC107), "%.2f".format(speedKmh), "km/hr")
            }
        }
    }
}

@Composable
fun PanelStatItem(icon: ImageVector, iconColor: Color, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Text(unit, fontSize = 12.sp, color = Color.Gray)
    }
}
@Composable
fun PanelVerticalDivider() { Box(modifier = Modifier.height(24.dp).width(1.dp).background(Color.LightGray)) }

@Composable
fun MapWithRunnerIcon(
    currentLocation: LatLng?,
    currentSpeedKmh: Float,
    pathPoints: List<LatLng>,
    isSnapshotMode: Boolean,
    onMapLoaded: (GoogleMap) -> Unit
) {
    val defaultPos = LatLng(0.0, 0.0)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(defaultPos, 17f) }
    val infiniteTransition = rememberInfiniteTransition(label = "runnerAnim")
    val bounceOffset by infiniteTransition.animateFloat(initialValue = 0f, targetValue = -10f, animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce")
    val actualBounce = if (!isSnapshotMode && currentSpeedKmh > 1.0f) bounceOffset else 0f

    LaunchedEffect(currentLocation, isSnapshotMode) {
        if (currentLocation != null && !isSnapshotMode) {
            cameraPositionState.animate(update = CameraUpdateFactory.newLatLngZoom(currentLocation, 17f), durationMs = 1000)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = false)
    ) {
        MapEffect(Unit) { map -> onMapLoaded(map) }
        if (pathPoints.isNotEmpty()) Polyline(points = pathPoints, color = MaterialTheme.colorScheme.primary, width = 15f)
        if (currentLocation != null) {
            key(isSnapshotMode) {
                MarkerComposable(state = MarkerState(position = currentLocation)) {
                    val iconVector = if (isSnapshotMode) Icons.Default.Place else Icons.Default.DirectionsRun
                    val tintColor = if (isSnapshotMode) Color.Red else MaterialTheme.colorScheme.primary
                    val size = if (isSnapshotMode) 40.dp else 36.dp
                    Icon(imageVector = iconVector, contentDescription = "User", tint = tintColor, modifier = Modifier.size(size).offset(y = actualBounce.dp))
                }
            }
        }
    }
}

suspend fun captureMapSnapshot(context: Context, googleMap: GoogleMap): String? {
    return kotlin.coroutines.suspendCoroutine { continuation ->
        googleMap.snapshot { bitmap ->
            if (bitmap != null) {
                try {
                    val file = File(context.filesDir, "run_map_${System.currentTimeMillis()}.jpg")
                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    out.flush()
                    out.close()
                    continuation.resumeWith(Result.success(file.absolutePath))
                } catch (e: Exception) { continuation.resumeWith(Result.success(null)) }
            } else continuation.resumeWith(Result.success(null))
        }
    }
}

fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}