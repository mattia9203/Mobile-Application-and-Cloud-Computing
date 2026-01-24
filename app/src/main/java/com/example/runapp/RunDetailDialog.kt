package com.example.runapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.runapp.ui.theme.*
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
            modifier = Modifier.fillMaxWidth().height(600.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // --- TOP: MAP IMAGE (UPDATED FOR CLOUD) ---
                Box(modifier = Modifier.fillMaxWidth().weight(1.1f).background(NearWhite)) {

                    // FIXED: Replaced BitmapFactory with AsyncImage (Coil)
                    AsyncImage(
                        model = run.imagePath,
                        contentDescription = "Run Map",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        // Shows the map icon if the image is loading or fails
                        error = painterResource(id = android.R.drawable.ic_menu_mapmode),
                        placeholder = painterResource(id = android.R.drawable.ic_menu_mapmode)
                    )

                    // Close / Delete Buttons overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .statusBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SmallIconButton(Icons.Default.Close, onClick = onDismiss)
                        SmallIconButton(Icons.Default.Delete, onClick = onDelete, tint = RedError)
                    }
                }

                // --- BOTTOM: STATS ---
                Column(
                    modifier = Modifier.weight(0.9f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy • HH:mm", Locale.getDefault())
                    Text(text = dateFormat.format(Date(run.timestamp)), fontSize = 14.sp, color = MediumGray)

                    Text(text = formatDurationBig(run.durationMillis), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)

                    Row(modifier = Modifier.fillMaxWidth().padding(top=16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatColumn(Icons.Default.DirectionsRun, MaterialTheme.colorScheme.primary, "%.2f".format(run.distanceKm), "km")
                        VerticalDivider(height = 40.dp)
                        StatColumn(Icons.Default.LocalFireDepartment, MaterialTheme.colorScheme.primary, "${run.caloriesBurned}", "kcal")
                        VerticalDivider(height = 40.dp)
                        StatColumn(Icons.Default.Speed, MaterialTheme.colorScheme.primary, "%.1f".format(run.avgSpeedKmh), "km/h")
                    }
                }
            }
        }
    }
}

// --- HELPERS ---

@Composable
fun SmallIconButton(icon: ImageVector, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.onBackground) {
    Box(modifier = Modifier.size(44.dp).background(White, CircleShape).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun StatColumn(icon: ImageVector, iconColor: Color, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(30.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(unit, fontSize = 14.sp, color = MediumGray)
    }
}

@Composable
fun VerticalDivider(height: androidx.compose.ui.unit.Dp) {
    Box(modifier = Modifier.height(height).width(1.dp).background(LightGrayDivider))
}

fun formatDurationBig(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}