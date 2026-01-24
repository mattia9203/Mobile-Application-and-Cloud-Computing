package com.example.runapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RunApi {
    // 10.0.2.2 is the special IP for "Localhost" on Android Emulator
    private const val BASE_URL = "http://10.0.2.2:5000"

    suspend fun saveUserToDb(uid: String, name: String, weight: String, height: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$BASE_URL/create_user")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                // Create the JSON Object
                val json = JSONObject().apply {
                    put("uid", uid)
                    put("name", name)
                    put("weight", weight)
                    put("height", height)
                }

                // Send the data
                conn.outputStream.use { os ->
                    val input = json.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                // Check result (201 means Created)
                val responseCode = conn.responseCode
                println("SERVER RESPONSE: $responseCode")
                return@withContext responseCode == 201
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }
}