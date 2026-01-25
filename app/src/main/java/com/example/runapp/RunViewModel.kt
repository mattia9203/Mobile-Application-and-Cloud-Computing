package com.example.runapp

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlinx.coroutines.Dispatchers

enum class RunState {
    READY, RUNNING, PAUSED
}

class RunViewModel(application: Application) : AndroidViewModel(application) {
    // --- CLOUD SETUP ---
    private val auth = FirebaseAuth.getInstance()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // --- LIVE RUN STATE (Restored exactly as before) ---
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

    // Map Data (Restored to LatLng for your UI)
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

    // --- HISTORY DATA (From Cloud) ---
    private val _allRuns = MutableStateFlow<List<RunEntity>>(emptyList())
    val allRuns = _allRuns.asStateFlow()

    private val _totalDistance = MutableStateFlow(0f)
    val totalDistance = _totalDistance.asStateFlow()

    init {
        loadRunsFromCloud()
    }

    // --- LOCATION CALLBACK (Restored Logic) ---
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                // 1. ALWAYS Update Current Location (So the map works in READY mode)
                val newLatLng = LatLng(location.latitude, location.longitude)
                _currentLocation.value = newLatLng

                val distFromLastWeather = lastWeatherLocation?.distanceTo(location) ?: Float.MAX_VALUE
                if (distFromLastWeather > 500) {
                    fetchWeather(location.latitude, location.longitude)
                    lastWeatherLocation = location // Remember this spot
                }
                // 2. ONLY Record Stats if RUNNING
                if (_runState.value == RunState.RUNNING) {
                    if (lastLocation != null) {
                        val distanceGap = lastLocation!!.distanceTo(location)
                        if (distanceGap > 1.0) { // Filter tiny movements
                            totalDistanceMetres += distanceGap

                            // Calculate Speed
                            val timeGapSeconds = (location.time - lastLocation!!.time) / 1000.0
                            var speed = 0f
                            if (timeGapSeconds > 0) {
                                speed = ((distanceGap / timeGapSeconds) * 3.6).toFloat()
                            }
                            if (speed > 40f) speed = 0f // Filter spikes

                            // Update StateFlows
                            _currentDistance.value = totalDistanceMetres / 1000f
                            _currentCalories.value = (_currentDistance.value * 70).toInt()
                            _currentSpeed.value = speed
                            _pathPoints.value = _pathPoints.value + newLatLng
                        }
                    }
                    lastLocation = location

                } else if (_runState.value == RunState.READY) {
                    lastLocation = location
                    if (!weatherFetched) {
                        fetchWeather(location.latitude, location.longitude)
                    }
                }
            }
        }
    }

    // --- ACTIONS ---

    // This wakes up the map when you open the screen
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
        startTimer()
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
        // Reset Data
        _currentDuration.value = 0L
        _currentDistance.value = 0f
        _currentCalories.value = 0
        _currentSpeed.value = 0f
        _pathPoints.value = emptyList()
        accumulatedTime = 0L
        totalDistanceMetres = 0f
        lastLocation = null
        // Note: We don't stop location updates so the map stays alive
    }

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

    // --- CLOUD SAVING (The Magic Part) ---
    fun saveRun(run: RunEntity) = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch
        var finalImageUrl = ""

        // 1. If we have a local file (Snapshot), Upload it!
        if (run.imagePath != null && !run.imagePath.startsWith("http")) {
            try {
                val file = Uri.fromFile(File(run.imagePath))
                val ref = FirebaseStorage.getInstance().reference
                    .child("run_maps/${user.uid}/${System.currentTimeMillis()}.jpg")

                ref.putFile(file).await()
                finalImageUrl = ref.downloadUrl.await().toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Send Data + Cloud URL to Python
        val success = RunApi.saveRun(user.uid, run, finalImageUrl)
        if (success) {
            loadRunsFromCloud()
        }
    }

    fun loadRunsFromCloud() = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch
        val runs = RunApi.getRuns(user.uid)
        _allRuns.value = runs
        _totalDistance.value = runs.map { it.distanceKm }.sum()
    }

    fun deleteRun(run: RunEntity) = viewModelScope.launch {
        // 1. Call the Cloud API to delete it from SQL
        val success = RunApi.deleteRun(run.id)

        if (success) {
            // 2. If server says "OK", remove it from the App screen immediately
            val updatedList = _allRuns.value.toMutableList()
            updatedList.remove(run)
            _allRuns.value = updatedList

            // Recalculate Total Distance
            _totalDistance.value = updatedList.map { it.distanceKm }.sum()
        } else {
            println("DEBUG: Failed to delete run from server.")
        }
    }
    // WEATHER STATE (Temperature String + Weather Code)
    private val _weatherState = MutableStateFlow<Triple<String, Int, Int>?>(null)
    val weatherState = _weatherState.asStateFlow()
    private var lastWeatherLocation: Location? = null
    private var weatherFetched = false

    // FUNCTION TO FETCH WEATHER
    fun fetchWeather(lat: Double, lon: Double) = viewModelScope.launch(Dispatchers.IO) {
        try {
            // URL for Open-Meteo (Free, No Key)
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val url = java.net.URL(urlString)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)

                val currentWeather = json.getJSONObject("current_weather")
                val temp = currentWeather.getDouble("temperature")
                val code = currentWeather.getInt("weathercode")
                val isDay = currentWeather.getInt("is_day")

                // Save to State
                _weatherState.value = Triple("$temp°C", code, isDay)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}