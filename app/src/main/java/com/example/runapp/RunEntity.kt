package com.example.runapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runs_table")
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    // Timestamp
    val timestamp: Long = System.currentTimeMillis(),

    // Stats
    val distanceKm: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val durationMillis: Long = 0L,
    val caloriesBurned: Int = 0,

    // Photo
    val imagePath: String? = null
)