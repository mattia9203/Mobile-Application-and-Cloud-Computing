package com.example.runapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert
    suspend fun insertRun(run: RunEntity)

    // Delete command
    @Delete
    suspend fun deleteRun(run: RunEntity)

    @Query("SELECT * FROM runs_table ORDER BY timestamp DESC")
    fun getAllRuns(): Flow<List<RunEntity>>

    @Query("SELECT SUM(distanceKm) FROM runs_table")
    fun getTotalDistance(): Flow<Float?>
}