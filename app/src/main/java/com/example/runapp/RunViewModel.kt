package com.example.runapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class RunViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = RunDatabase.getDatabase(application).dao()

    val allRuns = dao.getAllRuns().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalDistance = dao.getTotalDistance().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    fun saveRun(run: RunEntity) {
        viewModelScope.launch {
            dao.insertRun(run)
        }
    }

    // --- NEW: Function to delete a run and its image ---
    fun deleteRun(run: RunEntity) {
        viewModelScope.launch {
            // 1. Delete the image file if it exists to save space
            run.imagePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
            // 2. Delete from database
            dao.deleteRun(run)
        }
    }
}