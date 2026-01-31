package com.example.runapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val _loginState = MutableStateFlow<AuthResult>(AuthResult.Idle)
    val loginState = _loginState.asStateFlow()

    fun login(email: String, pass: String) = viewModelScope.launch {
        if (email.isBlank() || pass.isBlank()) {
            _loginState.value = AuthResult.Error("Empty fields")
            return@launch
        }
        _loginState.value = AuthResult.Loading
        try {
            auth.signInWithEmailAndPassword(email, pass).await()
            _loginState.value = AuthResult.Success
        } catch (e: Exception) {
            _loginState.value = AuthResult.Error(e.message ?: "Error")
        }
    }

    fun signUp(email: String, pass: String, name: String, weight: String, height: String) = viewModelScope.launch {
        if (email.isBlank() || pass.isBlank() || name.isBlank()) {
            _loginState.value = AuthResult.Error("Please fill in all fields")
            return@launch
        }
        _loginState.value = AuthResult.Loading
        try {
            // 1. Create the user
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user
            val uid = user?.uid ?: ""

            // 2. SAVE THE NAME TO FIREBASE PROFILE
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build()
            user?.updateProfile(profileUpdates)?.await()

            user?.reload()?.await()

            // 3. PYTHON API (Save Weight/Height)
            val apiSuccess = RunApi.saveUserToDb(uid, name, weight, height)

            if (apiSuccess) {
                _loginState.value = AuthResult.Success
            } else {
                _loginState.value = AuthResult.Success
            }
        } catch (e: Exception) {
            _loginState.value = AuthResult.Error(e.message ?: "Error")
        }
    }
}

sealed class AuthResult {
    object Idle : AuthResult()
    object Loading : AuthResult()
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}