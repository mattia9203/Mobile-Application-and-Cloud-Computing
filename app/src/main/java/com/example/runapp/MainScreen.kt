package com.example.runapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.runapp.ui.theme.RunAppTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.rememberCameraPositionState
@Composable
fun MainScreen() {
    RunAppTheme {
        val context = LocalContext.current
        var isRunning by remember { mutableStateOf(false) }
        var stepCount by remember { mutableStateOf(0) }


        var lastCapturedPhoto by remember { mutableStateOf<Bitmap?>(null) }

        var showCamera by remember { mutableStateOf(false) }

        // --- PERMISSIONS ---
        var hasPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        }
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> -> // <-- Specify the type here
            hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        }

        LaunchedEffect(Unit) {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
            permissionLauncher.launch(perms.toTypedArray())
        }

        // --- SENSOR LOGIC ---
        LaunchedEffect(isRunning) {
            if (isRunning) {
                val sensor = StepCounter(context)
                sensor.stepFlow.collect { stepCount += it }
            }
        }

        if (showCamera) {
            // MODE 3: THE CAMERA (Full Screen)
            CameraScreen(
                onImageCaptured = { bitmap ->
                    lastCapturedPhoto = bitmap
                    showCamera = false // Close camera
                },
                onError = { /* Handle error */ }
            )
        } else {
            // MODE 1 & 2: THE DASHBOARD (Map & Controls)
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { AppHeader() },
                bottomBar = {
                    if (!isRunning) {
                        BottomAppBar(containerColor = MaterialTheme.colorScheme.surface) {
                            IconButton(onClick = {}, modifier = Modifier.weight(1f)) { Icon(Icons.Default.History, "History") }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {}, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Person, "Profile") }
                        }
                    }
                },
                floatingActionButton = {
                    if (!isRunning) {
                        FloatingActionButton(
                            onClick = { isRunning = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.size(80.dp).offset(y = 50.dp)
                        ) {
                            Icon(Icons.Default.DirectionsRun, null, modifier = Modifier.size(40.dp))
                        }
                    }
                },
                floatingActionButtonPosition = FabPosition.Center
            ) { padding ->
                Column(modifier = Modifier.padding(padding).padding(horizontal = 20.dp)) {

                    // IF WE TOOK A PHOTO, SHOW IT!
                    if (lastCapturedPhoto != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 16.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Image(
                                bitmap = lastCapturedPhoto!!.asImageBitmap(),
                                contentDescription = "Captured",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    } else {
                        // Otherwise show the Map
                        Card(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 20.dp).clip(RoundedCornerShape(24.dp)),
                            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
                        ) {
                            MapWithDot(hasPermission)
                        }
                    }

                    if (isRunning) {
                        Text("$stepCount", fontSize = 80.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { stepCount++ })

                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FloatingActionButton(onClick = { isRunning = false }, containerColor = MaterialTheme.colorScheme.error) {
                                Icon(Icons.Default.Stop, "Stop")
                            }
                            Button(
                                onClick = { showCamera = true }, // <--- OPEN CAMERA
                                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(64.dp).weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, null)
                                Text("SNAP PHOTO")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- KEEP YOUR HELPER FUNCTIONS ---
@Composable
fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Hello,", color = Color.Gray)
            Text("Runner", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Icon(
            Icons.Default.Person, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun MapWithDot(isLocationEnabled: Boolean) {
    val rome = LatLng(41.9028, 12.4964)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(rome, 14f)
    }
    val mapProperties = remember(isLocationEnabled) {
        MapProperties(isMyLocationEnabled = isLocationEnabled)
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        uiSettings = com.google.maps.android.compose.MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false
        )
    )
}