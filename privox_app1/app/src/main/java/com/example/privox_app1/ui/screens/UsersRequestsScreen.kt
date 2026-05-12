package com.example.privox_app1.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privox_app1.data.remote.AuthService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersRequestsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val authService = remember { AuthService(context) }
    val scope = rememberCoroutineScope()

    var showIncoming by remember { mutableStateOf(true) }
    var allRequests by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedRequest by remember { mutableStateOf<Map<String, String>?>(null) }

    fun fetchRequests() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val direction = if (showIncoming) "incoming" else "outgoing"
            val result = authService.getRequests(direction)
            if (result.isSuccess) {
                allRequests = result.getOrNull() ?: emptyList()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
            }
            isLoading = false
        }
    }

    LaunchedEffect(showIncoming) {
        fetchRequests()
    }

    Scaffold(
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (showIncoming) Color(0xFF2575FC) else Color.Transparent)
                        .clickable { showIncoming = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Recibidas", color = if (showIncoming) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!showIncoming) Color(0xFF2575FC) else Color.Transparent)
                        .clickable { showIncoming = false }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Enviadas", color = if (!showIncoming) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { fetchRequests() },
                modifier = Modifier.weight(1f)
            ) {
                if (isLoading && allRequests.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF2575FC))
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage!!, color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { fetchRequests() }) {
                            Text("Reintentar")
                        }
                    }
                } else if (allRequests.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (showIncoming) "No hay solicitudes recibidas" else "No hay solicitudes enviadas")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(allRequests) { req ->
                            val username = req["username"] ?: ""
                            val displayName = req["displayName"] ?: ""
                            val nameToShow = if (username.isNotEmpty()) username else displayName
                            val avatarLetter = if (nameToShow.isNotEmpty()) nameToShow.first().uppercaseChar().toString() else "?"

                            ListItem(
                                headlineContent = { Text(nameToShow, fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(displayName) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFAB47BC)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(avatarLetter, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        selectedRequest = req
                                        showBottomSheet = true
                                    }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                                    }
                                },
                                modifier = Modifier.clickable {
                                    selectedRequest = req
                                    showBottomSheet = true
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (showBottomSheet && selectedRequest != null) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val requestId = selectedRequest!!["requestId"] ?: ""
                    
                    if (showIncoming) {
                        ListItem(
                            headlineContent = { Text("Aceptar solicitud") },
                            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val result = authService.updateRequestStatus(requestId, "accepted")
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Solicitud aceptada", Toast.LENGTH_SHORT).show()
                                        fetchRequests()
                                    }
                                    showBottomSheet = false
                                }
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Rechazar solicitud") },
                            leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val result = authService.updateRequestStatus(requestId, "rejected")
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Solicitud rechazada", Toast.LENGTH_SHORT).show()
                                        fetchRequests()
                                    }
                                    showBottomSheet = false
                                }
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text("Cancelar solicitud") },
                            leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val result = authService.updateRequestStatus(requestId, "cancelled")
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Solicitud cancelada", Toast.LENGTH_SHORT).show()
                                        fetchRequests()
                                    }
                                    showBottomSheet = false
                                }
                            }
                        )
                    }
                    
                    ListItem(
                        headlineContent = { Text("Cerrar opciones") },
                        leadingContent = { Icon(Icons.Default.ArrowBack, contentDescription = null) },
                        modifier = Modifier.clickable { showBottomSheet = false }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
