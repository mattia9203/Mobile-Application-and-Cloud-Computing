package com.example.runapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: RunViewModel,
    onBackClick: () -> Unit, // <--- 1. NEW PARAMETER
    onLogoutClick: () -> Unit
) {
    // 1. Get User Data
    val user = FirebaseAuth.getInstance().currentUser
    val userName = user?.displayName ?: "Runner"
    val email = user?.email ?: ""

    // 2. Calculate Total Stats from History
    val allRuns by viewModel.allRuns.collectAsState()

    val totalDistance = allRuns.map { it.distanceKm }.sum()
    val totalCalories = allRuns.map { it.caloriesBurned }.sum()
    val totalTimeMillis = allRuns.map { it.durationMillis }.sum()
    val totalHours = totalTimeMillis / 1000f / 3600f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        // --- HEADER SECTION ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
                .background(MaterialTheme.colorScheme.primary)
        ) {
            // BACK BUTTON (Top Left)
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 10.dp) // Adjust for status bar
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            // CENTERED PROFILE INFO
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Profile",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = userName,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = email,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- CONTENT SECTION ---
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {

            Text(
                "Total Progress",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth().height(100.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 1. Distance
                    ProgressItem(
                        icon = Icons.Rounded.DirectionsRun,
                        color = Color(0xFF5E35B1), // Purple
                        value = "%.1f".format(Locale.US, totalDistance),
                        unit = "km"
                    )

                    VerticalDivider(modifier = Modifier.height(40.dp))

                    // 2. CALORIES (Moved to Center)
                    ProgressItem(
                        icon = Icons.Rounded.LocalFireDepartment,
                        color = Color(0xFFF4511E), // Orange
                        value = "$totalCalories",
                        unit = "kcal"
                    )

                    VerticalDivider(modifier = Modifier.height(40.dp))

                    // 3. TIME (Moved to End)
                    ProgressItem(
                        icon = Icons.Rounded.Timer,
                        color = Color(0xFF00897B), // Teal
                        value = "%.1f".format(Locale.US, totalHours),
                        unit = "hr"
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // 2. MENU LIST
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {

                    ProfileMenuItem(
                        icon = Icons.Default.Person,
                        iconBgColor = Color(0xFFFFE0B2),
                        iconColor = Color(0xFFF57C00),
                        text = "Personal Parameter",
                        onClick = { }
                    )

                    Divider(color = Color(0xFFF0F0F0), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                    ProfileMenuItem(
                        icon = Icons.Default.EmojiEvents,
                        iconBgColor = Color(0xFFE1BEE7),
                        iconColor = Color(0xFF8E24AA),
                        text = "Achievements",
                        onClick = { }
                    )

                    Divider(color = Color(0xFFF0F0F0), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                    ProfileMenuItem(
                        icon = Icons.Default.Logout,
                        iconBgColor = Color(0xFFFFCDD2),
                        iconColor = Color(0xFFD32F2F),
                        text = "Logout",
                        isDestructive = true,
                        onClick = {
                            FirebaseAuth.getInstance().signOut()
                            onLogoutClick()
                        }
                    )
                }
            }
        }
    }
}

// --- HELPER COMPONENTS ---

@Composable
fun ProgressItem(icon: ImageVector, color: Color, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Text(unit, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) Color.Red else Color.Black
            )
        }

        if (!isDestructive) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}