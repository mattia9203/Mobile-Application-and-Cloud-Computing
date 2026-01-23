package com.example.runapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppNavigation() // <--- This decides which screen to show
        }
    }
}

@Composable
fun AppNavigation() {
    val loginViewModel: LoginViewModel = viewModel()

    // 0 = Login, 1 = Main App
    var currentScreen by remember { mutableStateOf(if (loginViewModel.isUserLoggedIn()) 1 else 0) }

    if (currentScreen == 0) {
        LoginScreen(
            onLoginSuccess = { currentScreen = 1 }
        )
    } else {
        // PASS THE LOGOUT ACTION HERE
        MainScreen(
            onSignOut = { currentScreen = 0 }
        )
    }
}

