package com.futura.privox_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.util.Log
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.futura.privox_app.data.remote.AuthService
import com.futura.privox_app.ui.components.PrivoxTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    username: String,
    displayName: String,
    onSettingsClick: () -> Unit,
    onAddUserClick: () -> Unit,
    onRequestsClick: () -> Unit,
    requestCall: (String, String) -> Unit,
    onChatClick: (String, String) -> Unit,
    onLogoutClick: () -> Unit,
    isConnected: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    var allContacts by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var contactToDelete by remember { mutableStateOf<Map<String, String>?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val authService = remember { AuthService(context) }
    val scope = rememberCoroutineScope()

    fun fetchContacts() {
        isLoading = true
        scope.launch {
            val result = authService.getUsers()
            if (result.isSuccess) {
                allContacts = result.getOrNull() ?: emptyList()
            } else {
                android.widget.Toast.makeText(context, "Error cargando contactos", android.widget.Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchContacts()
    }

    val filteredContacts = allContacts.filter {
        val display = it["displayName"] ?: ""
        val name = it["username"] ?: ""
        display.contains(searchQuery, ignoreCase = true) || username.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            PrivoxTopBar(
                onSettingsClick = onSettingsClick,
                onLogoutClick = { showLogoutDialog = true }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Profile Card (Modernized)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp)
                ) {
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
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF2575FC), Color(0xFF38BDF8))
                                )
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = username.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 64.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF111827)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isConnected) Color(0xFFDCFCE7) else Color(0xFFF3F4F6),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                        Text(
                            text = displayName,
                            color = Color(0xFF6B7280),
                            fontSize = 13.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isConnected) Color(0xFFDCFCE7) else Color(0xFFF3F4F6),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF16A34A) else Color(0xFF9CA3AF))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Online" else "Offline",
                                color = if (isConnected) Color(0xFF16A34A) else Color(0xFF4B5563),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Search Bar (Minimalist)
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar contacto...", color = Color(0xFF9CA3AF)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF9CA3AF)) },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    disabledContainerColor = Color(0xFFF3F4F6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            Text(
                text = "Mis Contactos",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp)
            )

            // Pull to Refresh Box
            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { fetchContacts() },
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (isLoading && allContacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF2575FC))
                    }
                } else if (filteredContacts.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier.fillParentMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFFD1D5DB),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isEmpty()) "No hay contactos disponibles" else "No se encontraron resultados",
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
                        items(filteredContacts) { contact ->
                            val cId = contact["userId"] ?: ""
                            val cName = contact["username"] ?: ""
                            val cDisplayName = contact["displayName"] ?: ""
                            ContactItem(
                                contactName = cName,
                                displayName = cDisplayName,
                                onCallClick = { requestCall(cId, cName) },
                                onChatClick = { onChatClick(cId, cName) },
                                onDeleteClick = { contactToDelete = contact }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        com.futura.privox_app.ui.components.LogoutDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                onLogoutClick()
            }
        )
    }

    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Eliminar contacto") },
            text = { Text("¿Estás seguro de que deseas eliminar a ${contactToDelete!!["username"]} de tus contactos?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val contactUserId = contactToDelete!!["userId"] ?: ""
                        scope.launch {
                            val result = authService.removeContact(contactUserId)
                            Log.d("Contact", "Contact deleted: $result")
                            if (result.isSuccess) {
                                fetchContacts()
                            } else {
                                errorMessage = result.exceptionOrNull()?.message ?: "No se pudo eliminar el contacto"
                            }
                            contactToDelete = null
                        }
                    }
                ) {
                    Text("Eliminar", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error al eliminar contacto") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("Entendido")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactItem(
    contactName: String,
    displayName: String,
    onCallClick: () -> Unit,
    onChatClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showBottomSheet = true }
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
                    text = contactName.take(1).uppercase(),
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
                    text = contactName,
                    color = Color(0xFF111827),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayName,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp
                )
            }


            // Call Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50.dp))
                    .clickable {
                        showBottomSheet = false
                        onCallClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Llamada",
                    tint = Color(0xFF16A34A)
                )
            }
        }
    }

    if (showBottomSheet) {
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
                            text = contactName.take(1).uppercase(),
                            color = Color(0xFF2575FC),
                            fontWeight = FontWeight.Bold,
                            fontSize = 64.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = contactName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF111827)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
                ) {
                    // Delete Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFEE2E2))
                            .clickable {
                                showBottomSheet = false
                                onDeleteClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = Color(0xFFEF4444)
                        )
                    }
                    // Call Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFDCFCE7))
                            .clickable {
                                showBottomSheet = false
                                onCallClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Llamada",
                            tint = Color(0xFF16A34A)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeSheetOptionItem(
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
