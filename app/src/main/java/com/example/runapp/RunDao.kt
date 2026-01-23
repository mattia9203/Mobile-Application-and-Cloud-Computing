package com.example.runapp

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: RunEntity)

    @Delete
    suspend fun deleteRun(run: RunEntity)

    // Used for the "Recent Activity" list
    @Query("SELECT * FROM run_table ORDER BY timestamp DESC")
    fun getAllRuns(): Flow<List<RunEntity>>

    // Used for the "Weekly Goal" progress bar
    @Query("SELECT SUM(distanceKm) FROM run_table")
    fun getTotalDistance(): Flow<Float?>
}