package com.example.runapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val _loginState = MutableStateFlow<AuthResult>(AuthResult.Idle)
    val loginState = _loginState.asStateFlow()

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    fun login(email: String, pass: String) = viewModelScope.launch {
        if (email.isBlank() || pass.isBlank()) { _loginState.value = AuthResult.Error("Empty fields"); return@launch }
        _loginState.value = AuthResult.Loading
        try {
            auth.signInWithEmailAndPassword(email, pass).await()
            _loginState.value = AuthResult.Success
        } catch (e: Exception) { _loginState.value = AuthResult.Error(e.message ?: "Error") }
    }

    fun signUp(email: String, pass: String) = viewModelScope.launch {
        if (email.isBlank() || pass.isBlank()) { _loginState.value = AuthResult.Error("Empty fields"); return@launch }
        _loginState.value = AuthResult.Loading
        try {
            auth.createUserWithEmailAndPassword(email, pass).await()
            _loginState.value = AuthResult.Success
        } catch (e: Exception) { _loginState.value = AuthResult.Error(e.message ?: "Error") }
    }
}

sealed class AuthResult {
    object Idle : AuthResult()
    object Loading : AuthResult()
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}