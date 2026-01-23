package com.example.runapp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.runapp.ui.theme.*

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val viewModel: LoginViewModel = viewModel()
    val authState by viewModel.loginState.collectAsState()

    // Form State
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") } // New Field
    var height by remember { mutableStateOf("") } // New Field

    // UI State
    var isSignUpMode by remember { mutableStateOf(false) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Auto-navigate on success
    LaunchedEffect(authState) {
        if (authState is AuthResult.Success) onLoginSuccess()
    }

    RunAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(HeaderBlueStart, HeaderBlueEnd) // Uses your Color.kt
                    )
                )
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(), // Allows card to grow
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = White), // Uses Color.kt
                elevation = CardDefaults.cardElevation(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState()), // Makes it scrollable on small phones
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUpMode) "Create Account" else "Welcome Back",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue // Uses Color.kt
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = if (isSignUpMode) "Enter your details to track your runs" else "Login to continue your journey",
                        fontSize = 14.sp,
                        color = MediumGray // Uses Color.kt
                    )

                    Spacer(Modifier.height(24.dp))

                    // --- SIGN UP FIELDS (Name, Weight, Height) ---
                    AnimatedVisibility(
                        visible = isSignUpMode,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            // Name
                            RunTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = "Full Name",
                                icon = Icons.Default.Person
                            )
                            Spacer(Modifier.height(16.dp))

                            // Weight & Height in a Row
                            Row(modifier = Modifier.fillMaxWidth()) {
                                RunTextField(
                                    value = weight,
                                    onValueChange = { if (it.length <= 3) weight = it.filter { char -> char.isDigit() } },
                                    label = "Wgt (kg)",
                                    icon = Icons.Default.FitnessCenter,
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                RunTextField(
                                    value = height,
                                    onValueChange = { if (it.length <= 3) height = it.filter { char -> char.isDigit() } },
                                    label = "Hgt (cm)",
                                    icon = Icons.Default.Height,
                                    keyboardType = KeyboardType.Number,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // --- COMMON FIELDS (Email, Password) ---

                    RunTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        icon = Icons.Default.Email,
                        keyboardType = KeyboardType.Email
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", color = MediumGray) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = PrimaryBlue) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Password",
                                    tint = MediumGray
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = LightGrayDivider,
                            focusedLabelColor = PrimaryBlue,
                            cursorColor = PrimaryBlue
                        )
                    )

                    Spacer(Modifier.height(24.dp))

                    // --- ERROR MESSAGE ---
                    if (authState is AuthResult.Error) {
                        Text(
                            text = (authState as AuthResult.Error).message,
                            color = RedError, // Uses Color.kt
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    // --- ACTION BUTTON ---
                    Button(
                        onClick = {
                            if (isSignUpMode) {
                                // TODO: Later we will pass name, weight, and height to the database here
                                viewModel.signUp(email, password)
                            } else {
                                viewModel.login(email, password)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(8.dp)
                    ) {
                        if (authState is AuthResult.Loading) {
                            CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp))
                        } else {
                            Text(
                                text = if (isSignUpMode) "Sign Up" else "Login",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // --- TOGGLE MODE ---
                    TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
                        Text(
                            text = if (isSignUpMode) "Already have an account? Login" else "New here? Create Account",
                            color = SecondaryBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RunTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = MediumGray) },
        leadingIcon = { Icon(icon, null, tint = PrimaryBlue) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = LightGrayDivider,
            focusedLabelColor = PrimaryBlue,
            cursorColor = PrimaryBlue
        )
    )
}