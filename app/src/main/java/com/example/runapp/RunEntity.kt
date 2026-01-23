package com.example.runapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "run_table")
data class RunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMillis: Long,
    val distanceKm: Float,
    val avgSpeedKmh: Float,
    val caloriesBurned: Int,
    val imagePath: String? = null
)