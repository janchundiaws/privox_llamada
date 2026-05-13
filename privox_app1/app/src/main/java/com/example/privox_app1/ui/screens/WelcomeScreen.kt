package com.example.privox_app1.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.example.privox_app1.data.remote.AuthService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    username: String,
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
        val name = it["username"] ?: ""
        name.contains(searchQuery, ignoreCase = true) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión", tint = Color.Red)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Profile Card
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF42A5F5), Color(0xFFAB47BC))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = username.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = username,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Id: ${username.hashCode()}",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(if (isConnected) Color(0xFFE8F5E9) else Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) Color(0xFF4CAF50) else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Online" else "Offline",
                                color = if (isConnected) Color(0xFF4CAF50) else Color.Gray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                placeholder = { Text("Buscar contacto...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay contactos disponibles")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredContacts) { contact ->
                            val cId = contact["userId"] ?: ""
                            val cName = contact["username"] ?: ""
                            ContactItem(
                                contactName = cName,
                                onCallClick = { requestCall(cId, cName) },
                                onChatClick = { onChatClick(cId, cName) },
                                onDeleteClick = { contactToDelete = contact }
                            )
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que deseas cerrar sesión?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    }
                ) {
                    Text("Cerrar sesión", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
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
fun ContactItem(contactName: String, onCallClick: () -> Unit, onChatClick: () -> Unit, onDeleteClick: () -> Unit) {
    var showBottomSheet by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(contactName, fontWeight = FontWeight.Bold) },
        supportingContent = { Text("ID: ${contactName.hashCode()}") },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2575FC)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contactName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { showBottomSheet = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
        },
        modifier = Modifier.clickable { showBottomSheet = true }
    )

    if (showBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text("Llamada por voz") },
                    leadingContent = { Icon(Icons.Default.Call, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showBottomSheet = false
                        onCallClick()
                    }
                )
                ListItem(
                    headlineContent = { Text("Eliminar contacto") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                    modifier = Modifier.clickable { 
                        showBottomSheet = false
                        onDeleteClick()
                    }
                )
                ListItem(
                    headlineContent = { Text("Cancelar") },
                    leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                    modifier = Modifier.clickable { showBottomSheet = false }
                )
            }
        }
    }
}
