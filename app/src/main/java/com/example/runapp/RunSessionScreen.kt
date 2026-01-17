package com.example.runapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.location.Location
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.*
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

enum class RunState {
    READY, RUNNING, PAUSED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunSessionScreen(
    onStopClick: (RunEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- STATE MANAGEMENT ---
    var runState by remember { mutableStateOf(RunState.READY) }

    // Stats
    var durationMillis by remember { mutableStateOf(0L) }
    var distanceKm by remember { mutableStateOf(0f) }
    var currentSpeedKmh by remember { mutableStateOf(0f) }
    var calories by remember { mutableStateOf(0) }

    // Timer Logic
    var startTime by remember { mutableStateOf(0L) }
    var accumulatedTime by remember { mutableStateOf(0L) }

    // Map & Location
    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var pathPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var lastLocationTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    // UI
    var googleMapRef by remember { mutableStateOf<GoogleMap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isSnapshotMode by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var lastCapturedPhoto by remember { mutableStateOf<Bitmap?>(null) }

    // --- TIMER LOOP ---
    LaunchedEffect(runState) {
        if (runState == RunState.RUNNING) {
            startTime = System.currentTimeMillis()
            while (runState == RunState.RUNNING) {
                durationMillis = accumulatedTime + (System.currentTimeMillis() - startTime)
                if (System.currentTimeMillis() - lastLocationTimestamp > 3000) {
                    currentSpeedKmh = 0f
                }
                delay(100)
            }
        } else if (runState == RunState.PAUSED) {
            accumulatedTime += System.currentTimeMillis() - startTime
        }
    }

    // --- LOCATION TRACKING ---
    val locationManager = remember {
        LocationManager(context) { distanceDeltaKm, newSpeed, newPos ->
            currentLatLng = newPos

            if (runState == RunState.RUNNING) {
                distanceKm += distanceDeltaKm
                currentSpeedKmh = newSpeed
                calories = (distanceKm * 70).toInt()
                pathPoints = pathPoints + newPos
                lastLocationTimestamp = System.currentTimeMillis()
            }
        }
    }

    DisposableEffect(Unit) {
        locationManager.startTracking()
        onDispose { locationManager.stopTracking() }
    }

    Scaffold(containerColor = Color.Black) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 1. TIMER
                BigTimerText(durationMillis)

                // 2. STATS
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(value = "%.2f".format(distanceKm), unit = "Km")
                    StatItem(value = "%.1f".format(currentSpeedKmh), unit = "Km/h")
                    StatItem(value = "$calories", unit = "Kcal")
                }

                // 3. TABS
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1C1E)), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TabButton(Icons.Default.Map, "Map", selectedTab == 0) { selectedTab = 0 }
                    TabButton(Icons.Default.CameraAlt, "Camera", selectedTab == 1) { selectedTab = 1 }
                    TabButton(Icons.Default.Flag, "Goal", selectedTab == 2) { selectedTab = 2 }
                }

                // 4. CONTENT
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> MapWithRunnerIcon(currentLatLng, currentSpeedKmh, pathPoints, isSnapshotMode) { googleMapRef = it }
                        1 -> {
                            if (lastCapturedPhoto != null) {
                                Box(Modifier.fillMaxSize()) {
                                    Image(lastCapturedPhoto!!.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    IconButton(onClick = { lastCapturedPhoto = null }, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.White, CircleShape)) { Icon(Icons.Default.Close, null) }
                                }
                            } else CameraScreen(onImageCaptured = { lastCapturedPhoto = it }, onError = {})
                        }
                        2 -> {
                            Column(Modifier.fillMaxSize().background(Color.White), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Weekly Goal: 10 km", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                LinearProgressIndicator(progress = (distanceKm / 10f).coerceIn(0f, 1f), modifier = Modifier.padding(top = 16.dp).width(200.dp).height(8.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 5. BUTTONS
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp).height(80.dp), contentAlignment = Alignment.Center) {
                    when (runState) {
                        RunState.READY -> {
                            Button(
                                onClick = { runState = RunState.RUNNING },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(28.dp)
                            ) { Text("START RUN", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
                        }
                        RunState.RUNNING -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(
                                    onClick = { runState = RunState.PAUSED },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(28.dp)
                                ) { Text("PAUSE", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black) }

                                EndButton(isSaving) {
                                    finishRun(scope, isSaving, googleMapRef, context, distanceKm, currentSpeedKmh, durationMillis, calories, pathPoints, // <--- PASS PATH POINTS
                                        onStartSave = { isSaving = true; isSnapshotMode = true },
                                        onResult = { onStopClick(it) })
                                }
                            }
                        }
                        RunState.PAUSED -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(
                                    onClick = { runState = RunState.RUNNING },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(28.dp)
                                ) { Text("RESUME", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }

                                EndButton(isSaving) {
                                    finishRun(scope, isSaving, googleMapRef, context, distanceKm, currentSpeedKmh, durationMillis, calories, pathPoints, // <--- PASS PATH POINTS
                                        onStartSave = { isSaving = true; isSnapshotMode = true },
                                        onResult = { onStopClick(it) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.EndButton(isSaving: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
        modifier = Modifier.weight(1f).height(56.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
        else Text("END", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// --- LOGIC HELPER: ZOOM TO FIT PATH ---
fun finishRun(
    scope: kotlinx.coroutines.CoroutineScope,
    isSaving: Boolean,
    mapRef: GoogleMap?,
    context: Context,
    dist: Float,
    speed: Float,
    time: Long,
    cals: Int,
    pathPoints: List<LatLng>, // <--- NEW PARAMETER
    onStartSave: () -> Unit,
    onResult: (RunEntity) -> Unit
) {
    if (isSaving) return
    onStartSave()

    scope.launch {
        // 1. ZOOM OUT TO FIT THE WHOLE RUN
        if (mapRef != null && pathPoints.isNotEmpty()) {
            val builder = LatLngBounds.Builder()
            pathPoints.forEach { builder.include(it) }
            try {
                val bounds = builder.build()
                // 100 pixels padding so the line isn't touching the edge
                mapRef.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            } catch (e: Exception) {
                // Fails if pathPoints has only 1 point, ignore it
            }
        }

        // 2. Wait for map to animate zoom & switch icon
        delay(1500L)

        var savedPath: String? = null
        if (mapRef != null) savedPath = captureMapSnapshot(context, mapRef)

        val hours = time / 1000f / 3600f
        val avgSpeed = if (hours > 0) dist / hours else 0f

        onResult(RunEntity(distanceKm = dist, avgSpeedKmh = avgSpeed, durationMillis = time, caloriesBurned = cals, imagePath = savedPath))
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

// --- UI HELPERS (Unchanged) ---
@Composable
fun BigTimerText(millis: Long) {
    Text(formatTime(millis), fontSize = 70.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).padding(vertical = 10.dp))
}

@Composable
fun StatItem(value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(unit, fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
fun TabButton(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
    Column(modifier = Modifier.clickable { onClick() }.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = color)
        if (isSelected) Box(Modifier.height(2.dp).width(40.dp).background(MaterialTheme.colorScheme.primary))
    }
}

fun formatTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

// --- MAP COMPONENT ---
@Composable
fun MapWithRunnerIcon(
    currentLocation: LatLng?,
    currentSpeedKmh: Float,
    pathPoints: List<LatLng>,
    isSnapshotMode: Boolean,
    onMapLoaded: (GoogleMap) -> Unit
) {
    if (currentLocation == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        return
    }

    // UPDATED: If snapshot mode, don't force follow user anymore (so we can zoom out)
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(currentLocation, 17f) }

    val infiniteTransition = rememberInfiniteTransition(label = "runnerAnim")
    val bounceOffset by infiniteTransition.animateFloat(initialValue = 0f, targetValue = -10f, animationSpec = infiniteRepeatable(tween(300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce")
    val actualBounce = if (!isSnapshotMode && currentSpeedKmh > 1.0f) bounceOffset else 0f

    // UPDATED: Only animate camera to user if NOT in snapshot mode
    LaunchedEffect(currentLocation, isSnapshotMode) {
        if (!isSnapshotMode) {
            cameraPositionState.animate(update = CameraUpdateFactory.newLatLngZoom(currentLocation, 17f), durationMs = 800)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
    ) {
        MapEffect(Unit) { map -> onMapLoaded(map) }
        if (pathPoints.isNotEmpty()) Polyline(points = pathPoints, color = Color.Red, width = 15f)
        key(isSnapshotMode) {
            MarkerComposable(state = MarkerState(position = currentLocation), title = "Me") {
                val iconVector = if (isSnapshotMode) Icons.Default.Place else Icons.Default.DirectionsRun
                val tintColor = if (isSnapshotMode) Color.Red else Color.Black
                val size = if (isSnapshotMode) 40.dp else 36.dp
                Icon(imageVector = iconVector, contentDescription = "User", tint = tintColor, modifier = Modifier.size(size).offset(y = actualBounce.dp))
            }
        }
    }
}

class LocationManager(
    context: Context,
    private val onLocationUpdate: (Float, Float, LatLng) -> Unit
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastLocation: Location? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                if (lastLocation != null) {
                    val distanceGap = lastLocation!!.distanceTo(location)
                    if (distanceGap > 1.0) {
                        val timeGapSeconds = (location.time - lastLocation!!.time) / 1000.0
                        var calculatedSpeedKmh = 0f
                        if (timeGapSeconds > 0) {
                            val speedMps = distanceGap / timeGapSeconds
                            calculatedSpeedKmh = (speedMps * 3.6).toFloat()
                        }
                        if (calculatedSpeedKmh > 40f) calculatedSpeedKmh = 0f

                        lastLocation = location
                        val currentPos = LatLng(location.latitude, location.longitude)
                        onLocationUpdate(distanceGap / 1000f, calculatedSpeedKmh, currentPos)
                    }
                } else {
                    lastLocation = location
                    val currentPos = LatLng(location.latitude, location.longitude)
                    onLocationUpdate(0f, 0f, currentPos)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).setMinUpdateDistanceMeters(2f).build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}