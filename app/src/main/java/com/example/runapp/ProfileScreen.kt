package com.example.runapp

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import com.example.runapp.RunViewModel.Achievement
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.isSystemInDarkTheme



@Composable
fun ProfileScreen(
    viewModel: RunViewModel,
    onBackClick: () -> Unit,
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

    // Personal & Goals Data
    val currentWeight by viewModel.userWeight.collectAsState()
    val currentHeight by viewModel.userHeight.collectAsState()
    val currentGoalDist by viewModel.goalDistance.collectAsState()
    val currentGoalCal by viewModel.goalCalories.collectAsState()

    // Dialog Visibility States
    var showProfileDialog by remember { mutableStateOf(false) }
    var showGoalsDialog by remember { mutableStateOf(false) }
    var showAchievementsDialog by remember { mutableStateOf(false) }
    val isSystemDark = isSystemInDarkTheme()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                border = BorderStroke(1.dp, if (isSystemDark) Color.DarkGray else Color.Transparent),
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                border = BorderStroke(1.dp, if (isSystemDark) Color.DarkGray else Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {

                    ProfileMenuItem(
                        icon = Icons.Default.Person,
                        iconBgColor = Color(0xFFFFE0B2),
                        iconColor = Color(0xFFF57C00),
                        text = "Personal Parameter",
                        onClick = { showProfileDialog = true}
                    )


                    ProfileMenuItem(
                        icon = Icons.Default.Flag, // Use Flag icon for goals
                        iconBgColor = Color(0xFFBBDEFB), iconColor = Color(0xFF1976D2),
                        text = "Weekly Goals",
                        onClick = { showGoalsDialog = true } // Open Dialog
                    )

                    ProfileMenuItem(
                        icon = Icons.Default.EmojiEvents,
                        iconBgColor = Color(0xFFE1BEE7),
                        iconColor = Color(0xFF8E24AA),
                        text = "Achievements",
                        onClick = { showAchievementsDialog = true }
                    )


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
// Edit Profile Dialog
    if (showProfileDialog) {
        EditProfileDialog(
            initialWeight = currentWeight,
            initialHeight = currentHeight,
            onDismiss = { showProfileDialog = false },
            onSave = { w, h ->
                viewModel.updateUserProfile(w, h)
                showProfileDialog = false
            }
        )
    }

    // Edit Goals Dialog
    if (showGoalsDialog) {
        EditGoalsDialog(
            initialDist = currentGoalDist,
            initialCal = currentGoalCal,
            onDismiss = { showGoalsDialog = false },
            onSave = { d, c ->
                viewModel.updateWeeklyGoals(d, c)
                showGoalsDialog = false
            }
        )
    }
    if (showAchievementsDialog) {
        AchievementsDialog(
            achievements = viewModel.achievementsList,
            runHistory = allRuns,
            onDismiss = { showAchievementsDialog = false }
        )
    }
}


// --- HELPER COMPOSABLES FOR DIALOGS ---

@Composable
fun AchievementsDialog(
    achievements: List<Achievement>,
    runHistory: List<RunEntity>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth().height(500.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Your Trophy Case", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(achievements) { achievement ->
                        val isUnlocked = achievement.condition(runHistory)
                        AchievementItem(achievement, isUnlocked)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AchievementItem(achievement: Achievement, isUnlocked: Boolean) {
    val goldColor = Color(0xFFFFD700)
    val grayColor = Color.LightGray.copy(alpha = 0.4f)
    val textColor = if (isUnlocked) Color.Black else Color.Gray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(if (isUnlocked) Color(0xFFFFF8E1) else Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(if (isUnlocked) goldColor else grayColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = achievement.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = achievement.title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = textColor,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = achievement.description,
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}
@Composable
fun EditProfileDialog(
    initialWeight: Float,
    initialHeight: Float,
    onDismiss: () -> Unit,
    onSave: (Float, Float) -> Unit
) {
    var weight by remember { mutableStateOf(initialWeight.toString()) }
    var height by remember { mutableStateOf(initialHeight.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Personal Parameter") },
        text = {
            Column {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (kg)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text("Height (cm)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weight.toFloatOrNull() ?: initialWeight
                val h = height.toFloatOrNull() ?: initialHeight
                onSave(w, h)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditGoalsDialog(
    initialDist: Float,
    initialCal: Int,
    onDismiss: () -> Unit,
    onSave: (Float, Int) -> Unit
) {
    var dist by remember { mutableStateOf(initialDist.toString()) }
    var cal by remember { mutableStateOf(initialCal.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly Goals") },
        text = {
            Column {
                OutlinedTextField(
                    value = dist,
                    onValueChange = { dist = it },
                    label = { Text("Distance Goal (km)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = cal,
                    onValueChange = { cal = it },
                    label = { Text("Calories Goal (kcal)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val d = dist.toFloatOrNull() ?: initialDist
                val c = cal.toIntOrNull() ?: initialCal
                onSave(d, c)
            }) { Text("Save Goals") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// --- HELPER COMPONENTS ---

@Composable
fun ProgressItem(icon: ImageVector, color: Color, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (isDestructive) Color.Red else MaterialTheme.colorScheme.onSurface)
        }

        if (!isDestructive) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}