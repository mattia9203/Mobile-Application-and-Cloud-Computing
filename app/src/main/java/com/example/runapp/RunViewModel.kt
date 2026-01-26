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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlinx.coroutines.Dispatchers
import java.util.Calendar

enum class RunState {
    READY, RUNNING, PAUSED
}

enum class StatsType {
    CALORIES, DURATION, DISTANCE
}

class RunViewModel(application: Application) : AndroidViewModel(application) {
    // --- CLOUD SETUP ---
    private val auth = FirebaseAuth.getInstance()
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

    private val _pathPoints = MutableStateFlow<List<LatLng>>(emptyList())
    val pathPoints = _pathPoints.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation = _currentLocation.asStateFlow()

    // 2. Add State for the Stats Screen
    private val _selectedStatsType = MutableStateFlow(StatsType.CALORIES)
    val selectedStatsType = _selectedStatsType.asStateFlow()

    // Holds the 7 values for Mon -> Sun
    private val _weeklyChartData = MutableStateFlow<List<Float>>(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f))
    val weeklyChartData = _weeklyChartData.asStateFlow()

    private val _currentWeekTotal = MutableStateFlow("") // e.g., "48 kcal"
    val currentWeekTotal = _currentWeekTotal.asStateFlow()

    // 3. Logic to process data when runs change or type changes
    fun setStatsType(type: StatsType) {
        _selectedStatsType.value = type
        calculateWeeklyStats()
    }

    // 1. TRACK WEEK OFFSET (0 = This Week, -1 = Last Week, etc.)
    private var weekOffset = 0

    // 2. EXPOSE START DATE OF THE SELECTED WEEK (For the UI Date Strip)
    private val _currentWeekStartMillis = MutableStateFlow(0L)
    val currentWeekStartMillis = _currentWeekStartMillis.asStateFlow()

    fun nextWeek() {
        weekOffset++
        calculateWeeklyStats()
    }

    fun previousWeek() {
        weekOffset--
        calculateWeeklyStats()
    }

    fun calculateWeeklyStats() {
        val runs = _allRuns.value
        val type = _selectedStatsType.value

        // --- 1. FIND START OF THE TARGET WEEK ---
        val calendar = Calendar.getInstance()
        // Reset to Today 00:00
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Go back to Monday
        val currentDayInt = calendar.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = if (currentDayInt == Calendar.SUNDAY) 6 else currentDayInt - 2
        calendar.add(Calendar.DAY_OF_YEAR, -daysToMonday)

        // APPLY OFFSET (Shift by X weeks)
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)

        val startOfWeek = calendar.timeInMillis
        _currentWeekStartMillis.value = startOfWeek // Tell UI what week this is

        // End of that week (Start + 7 days)
        val endOfWeek = startOfWeek + (7 * 24 * 60 * 60 * 1000)

        // --- 2. CALCULATE STATS ---
        val daysData = FloatArray(7) { 0f }
        var total = 0f

        // Filter runs strictly within this 7-day window
        runs.filter { it.timestamp >= startOfWeek && it.timestamp < endOfWeek }.forEach { run ->
            val runCal = Calendar.getInstance()
            runCal.timeInMillis = run.timestamp

            val dayOfWeek = runCal.get(Calendar.DAY_OF_WEEK)
            val index = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2

            if (index in 0..6) {
                val value = when (type) {
                    StatsType.DISTANCE -> run.distanceKm
                    StatsType.CALORIES -> run.caloriesBurned.toFloat()
                    StatsType.DURATION -> run.durationMillis / 1000f / 60f
                }
                daysData[index] += value
                total += value
            }
        }

        _weeklyChartData.value = daysData.toList()

        _currentWeekTotal.value = when (type) {
            StatsType.DISTANCE -> "%.1f km".format(total)
            StatsType.CALORIES -> "${total.toInt()} kcal"
            StatsType.DURATION -> "${total.toInt()} min"
        }
    }


    // Internal Logic Helpers
    private var timerJob: Job? = null
    private var startTime = 0L
    private var accumulatedTime = 0L
    private var lastLocation: Location? = null
    private var totalDistanceMetres = 0f

    // --- HISTORY DATA ---
    private val _allRuns = MutableStateFlow<List<RunEntity>>(emptyList())
    val allRuns = _allRuns.asStateFlow()

    private val _totalDistance = MutableStateFlow(0f)
    val totalDistance = _totalDistance.asStateFlow()

    // --- NEW: CALORIE STATS ---
    private val _weeklyCalories = MutableStateFlow(0)
    val weeklyCalories = _weeklyCalories.asStateFlow()

    init {
        loadRunsFromCloud()
    }

    // --- LOCATION CALLBACK ---
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations) {
                val newLatLng = LatLng(location.latitude, location.longitude)
                _currentLocation.value = newLatLng

                // Weather check (every 500m)
                val distFromLastWeather = lastWeatherLocation?.distanceTo(location) ?: Float.MAX_VALUE
                if (distFromLastWeather > 500) {
                    fetchWeather(location.latitude, location.longitude)
                    lastWeatherLocation = location
                }

                // Running Logic
                if (_runState.value == RunState.RUNNING) {
                    if (lastLocation != null) {
                        val distanceGap = lastLocation!!.distanceTo(location)
                        if (distanceGap > 1.0) {
                            totalDistanceMetres += distanceGap

                            val timeGapSeconds = (location.time - lastLocation!!.time) / 1000.0
                            var speed = 0f
                            if (timeGapSeconds > 0) {
                                speed = ((distanceGap / timeGapSeconds) * 3.6).toFloat()
                            }
                            if (speed > 40f) speed = 0f

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
        _currentDuration.value = 0L
        _currentDistance.value = 0f
        _currentCalories.value = 0
        _currentSpeed.value = 0f
        _pathPoints.value = emptyList()
        accumulatedTime = 0L
        totalDistanceMetres = 0f
        lastLocation = null
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

    fun saveRun(run: RunEntity) = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch
        var finalImageUrl = ""

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

        val success = RunApi.saveRun(user.uid, run, finalImageUrl)
        if (success) {
            loadRunsFromCloud()
        }
    }

    private fun updateDashboardStats(runs: List<RunEntity>) {
        val calendar = Calendar.getInstance()
        // Reset to Today 00:00
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Find Monday of THIS week
        val currentDayInt = calendar.get(Calendar.DAY_OF_WEEK)
        // If Sunday(1) -> Go back 6 days. If Mon(2) -> Go back 0 days.
        val daysToMonday = if (currentDayInt == Calendar.SUNDAY) 6 else currentDayInt - 2
        calendar.add(Calendar.DAY_OF_YEAR, -daysToMonday)

        val startOfCurrentWeek = calendar.timeInMillis

        // Filter runs: Only those happening AFTER Monday 00:00
        val currentWeekRuns = runs.filter { it.timestamp >= startOfCurrentWeek }

        // Sum them up
        _totalDistance.value = currentWeekRuns.map { it.distanceKm }.sum()
        _weeklyCalories.value = currentWeekRuns.map { it.caloriesBurned }.sum()
    }

    // --- UPDATED LOAD FUNCTION ---
    fun loadRunsFromCloud() = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch
        val runs = RunApi.getRuns(user.uid)
        _allRuns.value = runs

        updateDashboardStats(runs)
        calculateWeeklyStats()
    }

    fun deleteRun(run: RunEntity) = viewModelScope.launch {
        val success = RunApi.deleteRun(run.id)
        if (success) {
            val updatedList = _allRuns.value.toMutableList()
            updatedList.remove(run)
            _allRuns.value = updatedList
            _totalDistance.value = updatedList.map { it.distanceKm }.sum()
            _weeklyCalories.value =
                updatedList.map { it.caloriesBurned }.sum() // Update stats on delete

            updateDashboardStats(updatedList)
            calculateWeeklyStats()
        }
    }

    // --- WEATHER STATE ---
    private val _weatherState = MutableStateFlow<Triple<String, Int, Int>?>(null)
    val weatherState = _weatherState.asStateFlow()
    private var lastWeatherLocation: Location? = null
    private var weatherFetched = false

    fun fetchWeather(lat: Double, lon: Double) = viewModelScope.launch(Dispatchers.IO) {
        try {
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
                _weatherState.value = Triple("$temp°C", code, isDay)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}