package com.example.runapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

class ShakeDetector(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Flow that emits "Unit" (an empty signal) whenever a shake is detected
    val shakeEvent: Flow<Unit> = callbackFlow {
        if (accelerometer == null) {
            close()
            return@callbackFlow
        }

        // Configuration
        val shakeThresholdGravity = 1.3F // Sensitivity
        val minTimeBetweenShakesMs = 1000 // Cooldown to prevent spamming

        var lastShakeTime: Long = 0

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    // Calculate G-Force (Total Acceleration / Gravity)
                    val gX = x / SensorManager.GRAVITY_EARTH
                    val gY = y / SensorManager.GRAVITY_EARTH
                    val gZ = z / SensorManager.GRAVITY_EARTH

                    // Formula for 3D G-Force
                    val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

                    if (gForce > shakeThresholdGravity) {
                        val now = System.currentTimeMillis()
                        // Only trigger if cooldown has passed
                        if (lastShakeTime + minTimeBetweenShakesMs < now) {
                            lastShakeTime = now
                            trySend(Unit) // Send Signal
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}