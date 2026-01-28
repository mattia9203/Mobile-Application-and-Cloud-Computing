package com.example.runapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RunViewModel,
    onBackClick: () -> Unit
) {
    // Collect States
    val isDarkMode by viewModel.isAppDarkMode.collectAsState()
    val isShakeEnabled by viewModel.isShakeEnabled.collectAsState()
    val isLightEnabled by viewModel.isLightSensorEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1. APPEARANCE
            SettingsSectionHeader("Appearance")
            SettingsSwitchRow(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = "Use pitch black background",
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 2. SENSORS & CONTROLS
            SettingsSectionHeader("Sensors & Controls")
            SettingsSwitchRow(
                icon = Icons.Default.Vibration,
                title = "Shake to Stop",
                subtitle = "Shake device to pause/stop run",
                checked = isShakeEnabled,
                onCheckedChange = { viewModel.toggleShake(it) }
            )
            SettingsSwitchRow(
                icon = Icons.Default.BrightnessAuto,
                title = "Auto Night Mode",
                subtitle = "Use Light Sensor to switch map style",
                checked = isLightEnabled,
                onCheckedChange = { viewModel.toggleLightSensor(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 3. ABOUT / LEGAL
            SettingsSectionHeader("About")
            SettingsStaticRow(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "Read our terms and conditions",
                onClick = { /* Open Web Link Logic */ }
            )
            SettingsStaticRow(
                icon = Icons.Default.Info,
                title = "Version",
                subtitle = "1.0.0 (Beta)",
                onClick = {}
            )
        }
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
fun SettingsStaticRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
    Spacer(modifier = Modifier.height(12.dp))
}