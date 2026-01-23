package com.example.runapp

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class RunState {
    READY, RUNNING, PAUSED
}

class RunViewModel(application: Application) : AndroidViewModel(application) {
    private val runDao = RunDatabase.getDatabase(application).runDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // --- LIVE RUN STATE ---
    private val _runState = MutableStateFlow(RunState.READY)
    val runState = _runState.asStateFlow()

    private val _currentDuration = MutableStateFlow(0L)
    val currentDuration = _currentDuration.asStateFlow()

    private val _currentDistance = MutableStateFlow(0f)
    val currentDistance = _currentDistance.asStateFlow()

    private val _currentCalories = MutableStateFlow(0)
    val currentCalories = _currentCalories.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed = _currentSpeed.asStateFlow()

    // Live Map Data
    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints = _pathPoints.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    // Internal Logic Helpers
    private var timerJob: Job? = null
    private var startTime = 0L
    private var accumulatedTime = 0L
    private var lastLocation: Location? = null
    private var totalDistanceMetres = 0f

    // --- LOCATION CALLBACK ---
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                // 1. ALWAYS Update Current Location (So the map works in READY mode)
                val newLatLng = LatLng(location.latitude, location.longitude)
                _currentLocation.value = newLatLng

                // 2. ONLY Record Stats if RUNNING
                if (_runState.value == RunState.RUNNING) {
                    if (lastLocation != null) {
                        val distanceGap = lastLocation!!.distanceTo(location)
                        if (distanceGap > 1.0) {
                            totalDistanceMetres += distanceGap

                            // Calculate Speed
                            val timeGapSeconds = (location.time - lastLocation!!.time) / 1000.0
                            var speed = 0f
                            if (timeGapSeconds > 0) {
                                speed = ((distanceGap / timeGapSeconds) * 3.6).toFloat()
                            }
                            // Filter GPS spikes
                            if (speed > 40f) speed = 0f

                            // Update StateFlows
                            _currentDistance.value = totalDistanceMetres / 1000f
                            _currentCalories.value = (_currentDistance.value * 70).toInt()
                            _currentSpeed.value = speed

                            // Add to path (the red line)
                            _pathPoints.value = _pathPoints.value + newLatLng
                        }
                    }
                    lastLocation = location
                } else if (_runState.value == RunState.READY) {
                    // Keep lastLocation fresh so we don't jump when we eventually press Start
                    lastLocation = location
                }
            }
        }
    }

    // --- CONTROLS ---

    // NEW: Call this when the screen opens to wake up the map
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(2f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    fun startRun() {
        if (_runState.value == RunState.RUNNING) return

        _runState.value = RunState.RUNNING
        startTime = System.currentTimeMillis()

        // Start Timer
        startTimer()

        // Note: Location updates are already running via startLocationUpdates()
    }

    fun pauseRun() {
        _runState.value = RunState.PAUSED
        accumulatedTime += System.currentTimeMillis() - startTime
        timerJob?.cancel()
    }

    fun resumeRun() {
        _runState.value = RunState.RUNNING
        startTime = System.currentTimeMillis()
        startTimer()
    }

    fun stopRun() {
        _runState.value = RunState.READY
        timerJob?.cancel()
        // We do NOT stop location updates here, so the map stays alive if we stay on the screen

        // Reset Data
        _currentDuration.value = 0L
        _currentDistance.value = 0f
        _currentCalories.value = 0
        _currentSpeed.value = 0f
        _pathPoints.value = emptyList()
        _currentLocation.value = null // Optional: clear map marker or keep it
        accumulatedTime = 0L
        totalDistanceMetres = 0f
        lastLocation = null
    }

    // Call this when completely exiting the run screen (back to home)
    fun cleanup() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                _currentDuration.value = accumulatedTime + (System.currentTimeMillis() - startTime)
            }
        }
    }

    // --- HISTORY DATA ---
    val allRuns: StateFlow<List<RunEntity>> = runDao.getAllRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDistance: StateFlow<Float?> = runDao.getTotalDistance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun saveRun(run: RunEntity) = viewModelScope.launch {
        runDao.insertRun(run)
    }

    fun deleteRun(run: RunEntity) = viewModelScope.launch {
        runDao.deleteRun(run)
        run.imagePath?.let { File(it).delete() }
    }
}