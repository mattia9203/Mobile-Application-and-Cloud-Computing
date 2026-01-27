package com.example.runapp

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import com.example.runapp.ui.theme.*
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Star
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun RunSessionScreen(
    viewModel: RunViewModel,
    onStopClick: (RunEntity) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- SENSORS SETUP ---
    // 1. Light Sensor (Night Mode)
    val lightSensor = remember { LightSensor(context) }
    val luxLevel by lightSensor.lightLevel.collectAsState(initial = 1000f)
    val isNightMode = luxLevel < 50f

    // 2. Shake Detector (Stop Confirmation)
    val shakeDetector = remember { ShakeDetector(context) }
    var showShakeDialog by remember { mutableStateOf(false) }

    // --- VIEWMODEL STATE ---
    val runState by viewModel.runState.collectAsState()
    val durationMillis by viewModel.currentDuration.collectAsState()
    val distanceKm by viewModel.currentDistance.collectAsState()
    val currentSpeedKmh by viewModel.currentSpeed.collectAsState()
    val calories by viewModel.currentCalories.collectAsState()

    val currentLatLng by viewModel.currentLocation.collectAsState()
    val pathPoints by viewModel.pathPoints.collectAsState()

    var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isSnapshotMode by remember { mutableStateOf(false) }
    val weatherData by viewModel.weatherState.collectAsState()

    // Start Location
    LaunchedEffect(Unit) { viewModel.startLocationUpdates() }

    // Listen for Shake Events (Only when Running)
    LaunchedEffect(runState) {
        if (runState == RunState.RUNNING) {
            shakeDetector.shakeEvent.collect {
                showShakeDialog = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // MAP
        MapWithRunnerIcon(
            currentLocation = currentLatLng,
            currentSpeedKmh = currentSpeedKmh,
            pathPoints = pathPoints,
            isSnapshotMode = isSnapshotMode,
            isNightMode = isNightMode,
            onMapLoaded = { googleMapRef = it }
        )

        // BACK BUTTON
        Box(
            modifier = Modifier
                .padding(start = 20.dp, top = 40.dp)
                .size(45.dp)
                .align(Alignment.TopStart)
                .zIndex(2f)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onBackClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, "Back", tint = Color.Black)
        }

        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            WeatherWidget(weatherData = weatherData)
        }

        // BOTTOM PANEL
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
                    onTogglePause = { if (runState == RunState.RUNNING) viewModel.pauseRun() else viewModel.resumeRun() },
                    onStop = {
                        finishRun(
                            scope = scope, isSaving = isSaving, mapRef = googleMapRef, context = context,
                            dist = distanceKm, speed = currentSpeedKmh, time = durationMillis, cals = calories, pathPoints = pathPoints,
                            onStartSave = { isSaving = true; isSnapshotMode = true },
                            onResult = { entity -> viewModel.stopRun(); onStopClick(entity) }
                        )
                    }
                )
            }
        }

        // --- SHAKE CONFIRMATION DIALOG ---
        if (showShakeDialog) {
            AlertDialog(
                onDismissRequest = { showShakeDialog = false },
                icon = { Icon(Icons.Default.Vibration, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Shake Detected!") },
                text = { Text("Do you want to stop your run?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showShakeDialog = false
                            // Trigger the exact same stop logic as the button
                            finishRun(
                                scope = scope, isSaving = isSaving, mapRef = googleMapRef, context = context,
                                dist = distanceKm, speed = currentSpeedKmh, time = durationMillis, cals = calories, pathPoints = pathPoints,
                                onStartSave = { isSaving = true; isSnapshotMode = true },
                                onResult = { entity -> viewModel.stopRun(); onStopClick(entity) }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Stop Run", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showShakeDialog = false }) {
                        Text("Keep Running")
                    }
                }
            )
        }
    }
}

@Composable
fun DebugChip(text: String) {
    Box(modifier = Modifier.background(Color.Black.copy(alpha=0.5f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        Text(text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun MapWithRunnerIcon(
    currentLocation: LatLng?,
    currentSpeedKmh: Float,
    pathPoints: List<LatLng>,
    isSnapshotMode: Boolean,
    isNightMode: Boolean, // New Parameter
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

    // APPLY STYLE HERE
    val mapProperties = remember(isNightMode) {
        MapProperties(
            isMyLocationEnabled = false,
            mapStyleOptions = if (isNightMode) DarkMapStyle else null
        )
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties, // UPDATED
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, compassEnabled = false)
    ) {
        MapEffect(Unit) { map -> onMapLoaded(map) }

        // Change Polyline color if night mode (White looks better on dark map)
        key(isNightMode) {

            // Recalculate color inside the key block
            val polyColor = if (isNightMode) Color.Cyan else MaterialTheme.colorScheme.primary

            if (pathPoints.isNotEmpty()) {
                Polyline(points = pathPoints, color = polyColor, width = 15f)
            }

            if (currentLocation != null) {
                MarkerComposable(state = MarkerState(position = currentLocation)) {
                    val iconVector = if (isSnapshotMode) Icons.Default.Place else Icons.Default.DirectionsRun
                    val tintColor = if (isSnapshotMode) Color.Red else polyColor
                    val size = if (isSnapshotMode) 40.dp else 36.dp

                    Icon(
                        imageVector = iconVector,
                        contentDescription = "User",
                        tint = tintColor,
                        modifier = Modifier.size(size).offset(y = actualBounce.dp)
                    )
                }
            }
        }
    }
}

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
    onStartSave() // Shows loading indicator

    scope.launch {
        var savedPath: String? = null

        // We delegate the "Move & Snap" logic to the helper function
        if (mapRef != null && pathPoints.isNotEmpty()) {
            // This will suspend (wait) until the map is fully loaded and snapped
            savedPath = captureMapSnapshot(context, mapRef, pathPoints)
        }

        // Stats Calculation
        val hours = time / 1000f / 3600f
        val avgSpeed = if (hours > 0) dist / hours else 0f

        // Create & Save Entity
        val runEntity = RunEntity(
            distanceKm = dist,
            avgSpeedKmh = avgSpeed,
            durationMillis = time,
            caloriesBurned = cals,
            imagePath = savedPath,
            timestamp = System.currentTimeMillis()
        )

        onResult(runEntity)
    }
}
// --- HELPER COMPONENTS (Restored) ---

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
            MarkerComposable(state = MarkerState(position = currentLocation)) {
                val iconVector = if (isSnapshotMode) Icons.Default.Place else Icons.Default.DirectionsRun
                val tintColor = if (isSnapshotMode) Color.Red else MaterialTheme.colorScheme.primary
                val size = if (isSnapshotMode) 40.dp else 36.dp
                Icon(imageVector = iconVector, contentDescription = "User", tint = tintColor, modifier = Modifier.size(size).offset(y = actualBounce.dp))
            }
        }
    }
}

// --- WEATHER HELPERS ---

@Composable
fun getWeatherIcon(code: Int, isDay: Int): ImageVector {
    // 1. NIGHT MODE CHECK (If it's night and the sky is clear, show Moon)
    if (isDay == 0 && (code == 0 || code == 1 || code == 2)) {
        return Icons.Rounded.Star // 🌙 Moon Icon
    }

    // 2. WEATHER CODES
    return when (code) {
        0, 1 -> Icons.Rounded.WbSunny        // ☀️ Sunny
        2, 3 -> Icons.Rounded.Cloud          // ☁️ Cloudy
        45, 48 -> Icons.Rounded.Cloud        // 🌫️ Fog (Cloud is better than nothing)

        // RAIN: Used to be "Grain" (dots), now "Thunderstorm" (Cloud + Rain/Bolt)
        // Note: If you have extended library, use 'Icons.Rounded.WeatherRainy'
        51, 53, 55 -> Icons.Rounded.Thunderstorm // 🌧️ Cloud + Rain
        61, 63, 65 -> Icons.Rounded.Thunderstorm
        80, 81, 82 -> Icons.Rounded.Thunderstorm

        71, 73, 75 -> Icons.Rounded.AcUnit   // ❄️ Snow
        95, 96, 99 -> Icons.Rounded.Thunderstorm // ⛈️ Thunderstorm
        else -> Icons.Rounded.WbSunny
    }
}

@Composable
fun getWeatherColor(code: Int, isDay: Int): Color {
    return when (code) {
        0, 1 -> Color(0xFFFFB300)        // Clear Sky: Golden Yellow ☀️
        2, 3 -> Color.Gray               // Cloudy: Gray ☁️
        45, 48 -> Color.LightGray        // Fog: Light Gray 🌫️
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Color(0xFF42A5F5) // Rain: Blue 🌧️
        71, 73, 75 -> Color(0xFF26C6DA)  // Snow: Cyan ❄️
        95, 96, 99 -> Color(0xFF5E35B1)  // Thunderstorm: Purple ⛈️
        else -> Color(0xFFFFB300)        // Default
    }
}

@Composable
fun getThermometerColor(tempString: String): Color {
    // Extract number from "22.5°C" -> 22.5
    val value = tempString.replace("°C", "").trim().toDoubleOrNull() ?: 20.0
    return when {
        value < 10.0 -> Color(0xFF2196F3) // Blue (Cold)
        value < 25.0 -> Color(0xFFFF9800) // Orange (Nice)
        else -> Color(0xFFF44336)         // Red (Hot)
    }
}

@Composable
fun WeatherWidget(weatherData: Triple<String, Int, Int>?) {
    if (weatherData == null) return // Don't show anything if loading

    val (temp, code, isDay) = weatherData

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
        elevation = CardDefaults.cardElevation(4.dp),
        modifier = Modifier
            .padding(top = 50.dp, end = 20.dp)
            .wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon (Pass isDay to choose Sun vs Moon)
            Icon(
                imageVector = getWeatherIcon(code, isDay),
                contentDescription = null,
                tint = getWeatherColor(code, isDay),
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.LightGray))
            Spacer(modifier = Modifier.width(12.dp))

            // Thermometer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DeviceThermostat,
                    contentDescription = null,
                    tint = getThermometerColor(temp),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = temp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }
        }
    }
}

suspend fun captureMapSnapshot(
    context: Context,
    googleMap: GoogleMap,
    pathPoints: List<LatLng> // <--- NEW PARAMETER
): String? {
    if (pathPoints.isEmpty()) return null

    return suspendCoroutine { continuation ->
        try {
            // 1. Calculate Bounds
            val boundsBuilder = LatLngBounds.Builder()
            pathPoints.forEach { boundsBuilder.include(it) }
            val bounds = boundsBuilder.build()

            // 2. Move Camera (with padding)
            // 150 padding ensures the line doesn't touch the edge of the image
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 400))

            // 3. WAIT for the map to finish loading tiles!
            googleMap.setOnMapLoadedCallback {
                // 4. Now it is safe to take the picture
                googleMap.snapshot { bitmap ->
                    if (bitmap != null) {
                        try {
                            val file = File(context.filesDir, "run_map_${System.currentTimeMillis()}.jpg")
                            val out = FileOutputStream(file)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                            out.flush()
                            out.close()
                            continuation.resume(file.absolutePath)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continuation.resume(null)
                        }
                    } else {
                        continuation.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback: If bounds fail (e.g., single point), just take a snapshot of current view
            googleMap.snapshot { bitmap ->
                // ... (simple save logic if needed, or just return null)
                continuation.resume(null)
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

