package com.futura.privox_app.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futura.privox_app.data.remote.AuthService
import com.futura.privox_app.ui.components.PrivoxTopBar
import com.futura.privox_app.ui.components.LogoutDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersRequestsScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val authService = remember { AuthService(context) }
    val scope = rememberCoroutineScope()

    var showIncoming by remember { mutableStateOf(true) }
    var allRequests by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
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
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            PrivoxTopBar(
                title = "",
                onBack = onBack,
                onSettingsClick = onSettingsClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Solicitudes",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (showIncoming) "Solicitudes de amistad recibidas" else "Solicitudes de amistad enviadas",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Selector estilo píldora minimalista
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(24.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (showIncoming) Color.White else Color.Transparent)
                        .clickable { showIncoming = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Recibidas",
                        color = if (showIncoming) Color(0xFF2575FC) else Color(0xFF6B7280),
                        fontWeight = if (showIncoming) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!showIncoming) Color.White else Color.Transparent)
                        .clickable { showIncoming = false }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Enviadas",
                        color = if (!showIncoming) Color(0xFF2575FC) else Color(0xFF6B7280),
                        fontWeight = if (!showIncoming) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage!!,
                            color = Color(0xFFEF4444),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { fetchRequests() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2575FC)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Reintentar", color = Color.White)
                        }
                    }
                } else if (allRequests.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item{
                            Column(
                                modifier = Modifier.fillParentMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color(0xFFD1D5DB),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (showIncoming) "No tienes solicitudes recibidas" else "No has enviado solicitudes",
                                    color = Color(0xFF6B7280),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allRequests) { req ->
                            val username = req["username"] ?: ""
                            val displayName = req["displayName"] ?: ""
                            val nameToShow = if (username.isNotEmpty()) username else displayName
                            val avatarLetter = if (nameToShow.isNotEmpty()) nameToShow.first().uppercaseChar().toString() else "?"

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedRequest = req
                                        showBottomSheet = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEFF6FF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = avatarLetter,
                                            color = Color(0xFF2575FC),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = nameToShow,
                                            color = Color(0xFF111827),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                        if (displayName.isNotEmpty() && displayName != nameToShow) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = displayName,
                                                color = Color(0xFF6B7280),
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedRequest = req
                                            showBottomSheet = true
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Opciones",
                                            tint = Color(0xFF9CA3AF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showBottomSheet && selectedRequest != null) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFFE5E7EB)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp, bottom = 32.dp)
                ) {
                    val requestId = selectedRequest!!["requestId"] ?: ""
                    val username = selectedRequest!!["username"] ?: ""
                    val displayName = selectedRequest!!["displayName"] ?: ""
                    val nameToShow = if (username.isNotEmpty()) username else displayName

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        IconButton(
                            onClick = { showBottomSheet = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = nameToShow.take(1).uppercase(),
                                color = Color(0xFF2575FC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 64.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = nameToShow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF111827)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                    ) {
                        if (showIncoming) {
                            // Reject Button
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .clickable {
                                        scope.launch {
                                            val result = authService.updateRequestStatus(requestId, "rejected")
                                            if (result.isSuccess) {
                                                Toast.makeText(context, "Solicitud rechazada", Toast.LENGTH_SHORT).show()
                                                fetchRequests()
                                            }
                                            showBottomSheet = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Rechazar solicitud",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                            // Accept Button
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFDCFCE7))
                                    .clickable {
                                        scope.launch {
                                            val result = authService.updateRequestStatus(requestId, "accepted")
                                            if (result.isSuccess) {
                                                Toast.makeText(context, "Solicitud aceptada", Toast.LENGTH_SHORT).show()
                                                fetchRequests()
                                            }
                                            showBottomSheet = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Aceptar solicitud",
                                    tint = Color(0xFF16A34A)
                                )
                            }
                        } else {
                            // Cancel Button
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFEE2E2))
                                    .clickable {
                                        scope.launch {
                                            val result = authService.updateRequestStatus(requestId, "cancelled")
                                            if (result.isSuccess) {
                                                Toast.makeText(context, "Solicitud cancelada", Toast.LENGTH_SHORT).show()
                                                fetchRequests()
                                            }
                                            showBottomSheet = false
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar solicitud",
                                    tint = Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        LogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                onLogoutClick()
            }
        )
    }
}

@Composable
fun SheetOptionItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) Color(0xFFEF4444) else Color(0xFF4B5563),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = if (isDestructive) Color(0xFFEF4444) else Color(0xFF1F2937),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
