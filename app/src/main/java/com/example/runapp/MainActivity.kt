package com.example.runapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // This is the background of the app
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White
            ) {
                // HERE IS THE CHANGE: Call MainScreen(), NOT MapScreen()
                MainScreen()
            }
        }
    }
}



