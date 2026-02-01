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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.Calendar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.LocalFireDepartment
import kotlin.collections.emptyList

enum class RunState {
    READY, RUNNING, PAUSED
}

enum class StatsType {
    CALORIES, DURATION, DISTANCE
}

enum class SortOption {
    DATE, DISTANCE, DURATION, CALORIES, SPEED
}

enum class SortDirection {
    DESCENDING, ASCENDING
}

class RunViewModel(application: Application) : AndroidViewModel(application) {
    // CLOUD SETUP
    private val auth = FirebaseAuth.getInstance()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    private val _isAppDarkMode = MutableStateFlow(false)
    val isAppDarkMode = _isAppDarkMode.asStateFlow()

    private val _isShakeEnabled = MutableStateFlow(true)
    val isShakeEnabled = _isShakeEnabled.asStateFlow()

    private val _isLightSensorEnabled = MutableStateFlow(true)
    val isLightSensorEnabled = _isLightSensorEnabled.asStateFlow()

    // LIVE RUN STATE
    private val _runState = MutableStateFlow(RunState.READY)
    val runState = _runState.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE) // Default: Date
    val sortOption = _sortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(SortDirection.DESCENDING) // Default: Newest first
    val sortDirection = _sortDirection.asStateFlow()

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

    private val _currentWeekTotal = MutableStateFlow("")
    val currentWeekTotal = _currentWeekTotal.asStateFlow()

    // Logic to process data when runs change or type changes
    fun setStatsType(type: StatsType) {
        _selectedStatsType.value = type
        calculateWeeklyStats()
    }

    // TRACK WEEK OFFSET (0 = This Week, -1 = Last Week, etc.)
    private var weekOffset = 0

    // EXPOSE START DATE OF THE SELECTED WEEK
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

        // FIND START OF THE TARGET WEEK
        val calendar = Calendar.getInstance()
        // Reset to Today 00:00
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Go back to Monday
        val currentDayInt = calendar.get(Calendar.DAY_OF_WEEK)
        val daysToMonday = if (currentDayInt == Calendar.SUNDAY) 6 else currentDayInt - 2           //because in java calendar sunday = 1 and monday=2
        calendar.add(Calendar.DAY_OF_YEAR, -daysToMonday)

        // Apply offset (Shift by X weeks)
        calendar.add(Calendar.WEEK_OF_YEAR, weekOffset)

        val startOfWeek = calendar.timeInMillis
        _currentWeekStartMillis.value = startOfWeek // Tell UI what week this is

        // End of that week (Start + 7 days)
        val endOfWeek = startOfWeek + (7 * 24 * 60 * 60 * 1000)

        // Calculate stats
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

    // --- CALORIE STATS ---
    private val _weeklyCalories = MutableStateFlow(0)
    val weeklyCalories = _weeklyCalories.asStateFlow()

    private val _goalDistance = MutableStateFlow(10f)
    val goalDistance = _goalDistance.asStateFlow()

    private val _goalCalories = MutableStateFlow(2000)
    val goalCalories = _goalCalories.asStateFlow()

    private val _userWeight = MutableStateFlow(70f)
    val userWeight = _userWeight.asStateFlow()

    private val _userHeight = MutableStateFlow(175f)
    val userHeight = _userHeight.asStateFlow()

    private val _showWelcomeDialog = MutableStateFlow(false)
    val showWelcomeDialog = _showWelcomeDialog.asStateFlow()

    val sortedRuns: StateFlow<List<RunEntity>> = combine(
        _allRuns,
        _sortOption,
        _sortDirection
    ) { runs, option, direction ->
        sortRuns(runs, option, direction)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun sortRuns(
        runs: List<RunEntity>,
        option: SortOption,
        direction: SortDirection
    ): List<RunEntity> {
        // Define how to compare two runs based on the selected option
        val comparator = when (option) {
            SortOption.DATE -> compareBy<RunEntity> { it.timestamp }
            SortOption.DISTANCE -> compareBy { it.distanceKm }
            SortOption.DURATION -> compareBy { it.durationMillis }
            SortOption.CALORIES -> compareBy { it.caloriesBurned }
            SortOption.SPEED -> compareBy { it.avgSpeedKmh }
        }

        // 2. Apply the comparator in the correct direction
        return if (direction == SortDirection.ASCENDING) {
            runs.sortedWith(comparator)
        } else {
            runs.sortedWith(comparator.reversed())
        }
    }

    // NEW FUNCTIONS FOR UI TO CALL
    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun updateSortDirection(direction: SortDirection) {
        _sortDirection.value = direction
    }

    fun loadProfileFromCloud() = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch

        // Fetch from Server
        val profile = RunApi.getUserProfile(user.uid)

        if (profile != null) {
            val (_, weight, height) = profile

            // 1. Update UI State
            _userWeight.value = weight
            _userHeight.value = height

            // 2. Update Local Storage (so it remembers next time)
            val prefs = getApplication<Application>().getSharedPreferences("run_app_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putFloat("weight", weight)
                .putFloat("height", height)
                .apply()
        }
    }

    fun refreshData() = viewModelScope.launch {

        // Reload everything from server
        val runsDeferred = async { loadRunsFromCloud() }
        val goalsDeferred = async { loadGoalsFromCloud() }
        val profileDeferred = async { loadProfileFromCloud() }

        // 3. Wait for all to finish
        awaitAll(runsDeferred, goalsDeferred, profileDeferred)
    }

    init {
        loadRunsFromCloud()
        loadGoalsFromCloud()
        loadLocalProfile()
        loadSettings()
        refreshData()
    }

    private fun loadSettings() {
        val prefs = getApplication<Application>().getSharedPreferences("run_app_prefs", android.content.Context.MODE_PRIVATE)
        _isAppDarkMode.value = prefs.getBoolean("dark_mode", false)
        _isShakeEnabled.value = prefs.getBoolean("shake_enabled", true)
        _isLightSensorEnabled.value = prefs.getBoolean("light_sensor_enabled", true)
    }

    fun toggleDarkMode(enabled: Boolean) {
        _isAppDarkMode.value = enabled
        saveBoolean("dark_mode", enabled)
    }

    fun toggleShake(enabled: Boolean) {
        _isShakeEnabled.value = enabled
        saveBoolean("shake_enabled", enabled)
    }

    fun toggleLightSensor(enabled: Boolean) {
        _isLightSensorEnabled.value = enabled
        saveBoolean("light_sensor_enabled", enabled)
    }

    private fun saveBoolean(key: String, value: Boolean) {
        val prefs = getApplication<Application>().getSharedPreferences("run_app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, value).apply()
    }

    // 1. Define the Trophy List
    val achievementsList = listOf(
        Achievement(
            id = "first_run",
            title = "First Steps",
            description = "Complete your first run.",
            icon = Icons.Rounded.DirectionsRun,
            condition = { runs -> runs.isNotEmpty() }
        ),
        Achievement(
            id = "speed_demon",
            title = "Speed Demon",
            description = "Reach an average speed of 12 km/h in a single run.",
            icon = Icons.Rounded.Speed,
            condition = { runs -> runs.any { it.avgSpeedKmh >= 12f } }
        ),
        Achievement(
            id = "marathoner",
            title = "Marathoner",
            description = "Run a total of 42 km across all sessions.",
            icon = Icons.Rounded.Map,
            condition = { runs -> runs.map { it.distanceKm }.sum() >= 42f }
        ),
        Achievement(
            id = "calorie_burner",
            title = "Furnace",
            description = "Burn 500 kcal in a single run.",
            icon = Icons.Rounded.LocalFireDepartment,
            condition = { runs -> runs.any { it.caloriesBurned >= 500 } }
        ),
        Achievement(
            id = "night_owl",
            title = "Night Owl",
            description = "Go for a run after 8 PM.",
            icon = Icons.Rounded.Bedtime,
            condition = { runs ->
                runs.any {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = it.timestamp
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    hour >= 20 // 8 PM
                }
            }
        ),
        Achievement(
            id = "consistent",
            title = "Consistent",
            description = "Complete 10 runs total.",
            icon = Icons.Rounded.EmojiEvents,
            condition = { runs -> runs.size >= 10 }
        )
    )

    // LOCATION CALLBACK
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

    data class Achievement(
        val id: String,
        val title: String,
        val description: String,
        val icon: ImageVector,
        val condition: (List<RunEntity>) -> Boolean
    )

    // ACTIONS
    //Request location and send it to the locationCallback function
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateDistanceMeters(0f)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    //GET CURRENT MONDAY DATE STRING
    private fun getCurrentWeekStartDate(): String {
        val calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        // Go to Monday
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return dateFormat.format(calendar.time)
    }

    private fun loadLocalProfile() {
        val prefs = getApplication<Application>().getSharedPreferences("run_app_prefs", android.content.Context.MODE_PRIVATE)
        _userWeight.value = prefs.getFloat("weight", 70f)
        _userHeight.value = prefs.getFloat("height", 175f)
    }

    fun updateUserProfile(weight: Float, height: Float) = viewModelScope.launch {
        _userWeight.value = weight
        _userHeight.value = height

        // 1. Save Locally for speed
        val prefs = getApplication<Application>().getSharedPreferences("run_app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putFloat("weight", weight).putFloat("height", height).apply()

        // 2. Sync with Cloud DB
        val user = auth.currentUser
        if (user != null) {
            val name = user.displayName ?: "Runner"
            RunApi.saveUserToDb(user.uid, name, weight.toString(), height.toString())
        }
    }

    // FETCH GOALS FROM SERVER
    fun loadGoalsFromCloud() = viewModelScope.launch {
        val user = auth.currentUser ?: return@launch
        val dateString = getCurrentWeekStartDate()

        val result = RunApi.fetchGoal(user.uid, dateString)

        if (result != null) {
            // Case A: User HAS goals in DB (Server returned 200)
            _goalDistance.value = result.first
            _goalCalories.value = result.second
            // Ensure dialog is closed
            _showWelcomeDialog.value = false
        } else {
            // Case B: User has NO goals (Server returned 404) -> SHOW DIALOG
            _showWelcomeDialog.value = true
        }
    }

    // Call this when the user clicks "Save" in the dialog
    fun completeOnboarding(distance: Float, calories: Int) {
        updateWeeklyGoals(distance, calories)
        _showWelcomeDialog.value = false
    }

    fun updateWeeklyGoals(distance: Float, calories: Int) = viewModelScope.launch {
        // 1. Update UI immediately
        _goalDistance.value = distance
        _goalCalories.value = calories

        val user = auth.currentUser ?: return@launch
        val dateString = getCurrentWeekStartDate()

        // 2. Send to Python Server
        RunApi.saveGoal(user.uid, dateString, distance, calories)
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

    fun clearData() {
        // 1. Clear In-Memory State
        _allRuns.value = emptyList()
        _totalDistance.value = 0f
        _weeklyCalories.value = 0
        _goalDistance.value = 10f
        _goalCalories.value = 2000
        _userWeight.value = 70f  // Reset to default
        _userHeight.value = 175f // Reset to default
        _showWelcomeDialog.value = false

        // 2. Clear Local Storage (Shared Preferences)
        val prefs = getApplication<Application>().getSharedPreferences("run_app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .remove("weight")
            .remove("height")
            .apply()
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