package com.example.runapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object RunApi {
    // 10.0.2.2 is the special IP for "localhost" from inside the Emulator
    private const val BASE_URL = "http://10.0.2.2:5000"

    // --- 1. SAVE USER ---
    suspend fun saveUserToDb(uid: String, name: String, weight: String, height: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/create_user")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject().apply {
                    put("uid", uid)
                    put("name", name)
                    put("weight", weight)
                    put("height", height)
                }

                conn.outputStream.use { os ->
                    val input = json.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                return@withContext conn.responseCode == 201
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    // --- 2. SAVE RUN ---
    suspend fun saveRun(uid: String, run: RunEntity, imageUrl: String?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/create_run")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject().apply {
                    put("uid", uid)
                    put("timestamp", run.timestamp)
                    put("duration", run.durationMillis)
                    put("distance", run.distanceKm)
                    put("calories", run.caloriesBurned)
                    put("speed", run.avgSpeedKmh)
                    put("path_points", "[]")
                    put("image_url", imageUrl ?: "")
                }

                conn.outputStream.use { os ->
                    val input = json.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                return@withContext conn.responseCode == 201
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    // --- 3. GET RUNS ---
    suspend fun getRuns(uid: String): List<RunEntity> {
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<RunEntity>()
            try {
                val url = URL("$BASE_URL/get_runs?uid=$uid")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonArray = JSONArray(response.toString())
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        list.add(
                            RunEntity(
                                id = item.optInt("id"),
                                timestamp = item.getLong("timestamp"),
                                durationMillis = item.getLong("duration"),
                                distanceKm = item.getDouble("distance").toFloat(),
                                caloriesBurned = item.getInt("calories"),
                                avgSpeedKmh = item.getDouble("speed").toFloat(),
                                imagePath = item.optString("image_url", "")
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext list
        }
    }

    // --- 4. DELETE RUN ---
    suspend fun deleteRun(runId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/delete_run?run_id=$runId")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                return@withContext conn.responseCode == 200
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    // --- 5. SAVE GOAL ---
    suspend fun saveGoal(uid: String, date: String, km: Float, cal: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/set_weekly_goal")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject().apply {
                    put("uid", uid)
                    put("week_start_date", date)
                    put("target_km", km)
                    put("target_calories", cal)
                }

                conn.outputStream.use { os ->
                    val input = json.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }
                // 200 OK or 201 Created are both success
                return@withContext (conn.responseCode == 200 || conn.responseCode == 201)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    // --- 6. FETCH GOAL ---
    suspend fun fetchGoal(uid: String, date: String): Pair<Float, Int>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/get_weekly_goal?uid=$uid&week_start_date=$date")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    // Use optDouble/optInt with defaults to be safe
                    val km = json.optDouble("target_km", 10.0).toFloat()
                    val cal = json.optInt("target_calories", 2000)

                    return@withContext Pair(km, cal)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }
}