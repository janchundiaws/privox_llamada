package com.example.privox_app1.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privox_app1.R

import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.privox_app1.data.remote.AuthService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val authService = remember { AuthService(context) }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Creando usuario automáticamente...") }
    var autoCreateFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = authService.createAutomaticUser()
        if (result.isSuccess) {
            username = result.getOrNull() ?: ""
            statusMessage = ""
            autoCreateFailed = false
        } else {
            statusMessage = "Error al crear usuario automáticamente"
            autoCreateFailed = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Logo",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF2575FC)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Privox",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2575FC)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Inicia para disfrutar de llamadas de voz privadas y de alta calidad",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "Username")
                    },
                    label = { Text("Username") },
                    placeholder = { Text("ej: juan123") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    enabled = false // Bloqueado para edición como en Flutter original
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (username.isNotBlank()) {
                            isLoading = true
                            scope.launch {
                                val result = authService.login(username)
                                isLoading = false
                                if (result.isSuccess) {
                                    android.widget.Toast.makeText(context, "Login exitoso. Token recibido", android.widget.Toast.LENGTH_SHORT).show()
                                    onLoginSuccess(username)
                                } else {
                                    statusMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
                                    android.widget.Toast.makeText(context, statusMessage, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2575FC)),
                    enabled = !isLoading && username.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Presionar para iniciar",
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
                
                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = statusMessage,
                        fontSize = 14.sp,
                        color = if (autoCreateFailed || statusMessage.contains("Error")) Color.Red else Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
                
                if (autoCreateFailed) {
                    TextButton(onClick = {
                        statusMessage = "Reintentando..."
                        autoCreateFailed = false
                        scope.launch {
                            val result = authService.createAutomaticUser()
                            if (result.isSuccess) {
                                username = result.getOrNull() ?: ""
                                statusMessage = ""
                                autoCreateFailed = false
                            } else {
                                statusMessage = "Error al crear usuario automáticamente"
                                autoCreateFailed = true
                            }
                        }
                    }) {
                        Text("Volver a intentar", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
