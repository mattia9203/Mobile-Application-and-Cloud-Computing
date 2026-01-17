package com.example.runapp

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable // <--- ADDED IMPORT
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun RunDetailDialog(
    run: RunEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(550.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // --- TOP: MAP IMAGE & BUTTONS ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .background(Color.LightGray)
                ) {
                    // 1. Map Image
                    if (run.imagePath != null) {
                        val bitmap = remember(run.imagePath) { BitmapFactory.decodeFile(run.imagePath) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Run Map",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Fallback
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Map, null, tint = Color.Gray, modifier = Modifier.size(50.dp))
                        }
                    }

                    // 2. Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SmallIconButton(Icons.Default.Close, onClick = onDismiss)
                        SmallIconButton(Icons.Default.Delete, onClick = onDelete, tint = Color.Red)
                    }
                }

                // --- BOTTOM: STATS ---
                Column(
                    modifier = Modifier
                        .weight(0.8f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
                    Text(
                        text = dateFormat.format(Date(run.timestamp)),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = formatDurationBig(run.durationMillis),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatColumn(Icons.Default.DirectionsRun, Color(0xFFFFA000), "%.2f".format(run.distanceKm), "km")
                        VerticalDivider(height = 30.dp)
                        StatColumn(Icons.Default.LocalFireDepartment, Color(0xFFFF5722), "${run.caloriesBurned}", "kcal")
                        VerticalDivider(height = 30.dp)
                        StatColumn(Icons.Default.Bolt, Color(0xFFFFC107), "%.2f".format(run.avgSpeedKmh), "km/hr")
                    }
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun SmallIconButton(icon: ImageVector, onClick: () -> Unit, tint: Color = Color.Black) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Color.White.copy(alpha = 0.8f), CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }, // <--- FIXED: Just .clickable, no extra package path
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StatColumn(icon: ImageVector, iconColor: Color, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text(unit, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun VerticalDivider(height: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.height(height).width(1.dp).background(Color.LightGray))
}

// Make sure formatDurationBig is defined here or imported
// If you deleted it from RunDetailScreen, paste it here:
fun formatDurationBig(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
    else String.format("%02d:%02d", minutes, seconds)
}