package com.example.runapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class StepCounter(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    val stepFlow: Flow<Int> = callbackFlow {
        if (stepSensor == null) {
            trySend(0) // No sensor found (Emulator case)
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                // When the sensor detects a step, send "1"
                trySend(1)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not needed for step detection
            }
        }

        sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}