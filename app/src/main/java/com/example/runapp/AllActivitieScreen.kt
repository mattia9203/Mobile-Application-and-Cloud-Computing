package com.example.runapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllActivitiesScreen(
    // FIXED: Removed viewModel parameter from here
    onBackClick: () -> Unit,
    onItemClick: (RunEntity) -> Unit
) {
    // FIXED: Defined it here instead
    val viewModel: RunViewModel = viewModel()

    val allRuns by viewModel.allRuns.collectAsState()

    // White background for Light Theme
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("All Activities", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(allRuns) { run ->
                // Reuse the updated white item
                RecentActivityItem(
                    run = run,
                    onClick = { onItemClick(run) }
                )
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}