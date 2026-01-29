package com.example.runapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.DisposableEffect


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 1. Listen to Auth State
            var user by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }

            DisposableEffect(Unit) {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    user = auth.currentUser
                }
                FirebaseAuth.getInstance().addAuthStateListener(listener)
                onDispose {
                    FirebaseAuth.getInstance().removeAuthStateListener(listener)
                }
            }

            // 2. Navigate based on User State
            if (user != null) {
                MainScreen(
                    onSignOut = {
                    }
                )
            } else {
                LoginScreen(
                    onLoginSuccess = {
                        // The listener will detect the login and switch to MainScreen
                    }
                )
            }
        }
    }
}


