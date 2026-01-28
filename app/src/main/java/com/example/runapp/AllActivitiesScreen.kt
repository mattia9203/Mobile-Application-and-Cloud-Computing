package com.example.runapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

// --- COLOR CHANGES ---
val CardBorderColor = Color(0xFFE0E0E0)
val CardBackgroundColor = Color(0xFFF5F7FA) // <--- GREY CARDS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllActivitiesScreen(
    onBackClick: () -> Unit,
    onItemClick: (RunEntity) -> Unit
) {
    val viewModel: RunViewModel = viewModel()

    val sortedRuns by viewModel.sortedRuns.collectAsState()
    val currentSortOption by viewModel.sortOption.collectAsState()
    val currentSortDirection by viewModel.sortDirection.collectAsState()

    var showDeleteDialog by remember { mutableStateOf<RunEntity?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Activities", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort Activities")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.surface,
                    actionIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (sortedRuns.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No activities yet.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedRuns, key = { it.id }) { run ->
                        RunHistoryItem(
                            run = run,
                            onClick = { onItemClick(run) },
                            onDeleteClick = { showDeleteDialog = run }
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    if (showSortDialog) {
        SortDialog(
            currentOption = currentSortOption,
            currentDirection = currentSortDirection,
            onDismiss = { showSortDialog = false },
            onApply = { option, direction ->
                viewModel.updateSortOption(option)
                viewModel.updateSortDirection(direction)
                showSortDialog = false
            }
        )
    }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Run?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog?.let { viewModel.deleteRun(it) }
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

// --- HELPER COMPOSABLES ---

@Composable
fun SortDialog(
    currentOption: SortOption,
    currentDirection: SortDirection,
    onDismiss: () -> Unit,
    onApply: (SortOption, SortDirection) -> Unit
) {
    var selectedOption by remember { mutableStateOf(currentOption) }
    var selectedDirection by remember { mutableStateOf(currentDirection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort Activities By") },
        text = {
            Column {
                SortOption.values().forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedOption = option }.padding(vertical = 8.dp)
                    ) {
                        RadioButton(selected = (option == selectedOption), onClick = { selectedOption = option })
                        Text(
                            text = when (option) {
                                SortOption.DATE -> "Date"
                                SortOption.DISTANCE -> "Distance"
                                SortOption.DURATION -> "Duration"
                                SortOption.CALORIES -> "Calories"
                                SortOption.SPEED -> "Speed"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Order", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                SortDirection.values().forEach { direction ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { selectedDirection = direction }.padding(vertical = 8.dp)
                    ) {
                        RadioButton(selected = (direction == selectedDirection), onClick = { selectedDirection = direction })
                        Text(
                            text = when (direction) {
                                SortDirection.ASCENDING -> "Ascending (Low to High)"
                                SortDirection.DESCENDING -> "Descending (High to Low)"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(selectedOption, selectedDirection) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RunHistoryItem(
    run: RunEntity,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy, hh:mm a", Locale.getDefault())
    val dateString = dateFormat.format(Date(run.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CardBorderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), // Uses the new Grey Color
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)
            ) {
                if (!run.imagePath.isNullOrEmpty()) {
                    AsyncImage(
                        model = run.imagePath,
                        contentDescription = "Run Map",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(dateString, fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${"%.2f".format(Locale.US, run.distanceKm)} km",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(formatDuration(run.durationMillis), fontSize = 14.sp, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${run.caloriesBurned} kcal", fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }
            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Run", tint = Color.Gray)
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}