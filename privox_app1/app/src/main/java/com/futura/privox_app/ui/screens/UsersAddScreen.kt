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
fun UsersAddScreen(
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val authService = remember { AuthService(context) }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var allUsers by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<Map<String, String>?>(null) }

    fun fetchUsers() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = authService.getUsersToAdd()
            if (result.isSuccess) {
                allUsers = result.getOrNull() ?: emptyList()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Error desconocido"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchUsers()
    }

    val filteredUsers = allUsers.filter {
        val display = it["displayName"] ?: ""
        val username = it["username"] ?: ""
        display.contains(searchQuery, ignoreCase = true) || username.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            PrivoxTopBar(
                title = "",
                onBack = onBack,
                onSettingsClick = onSettingsClick,
                onLogoutClick = { showLogoutDialog = true }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar contacto...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            PullToRefreshBox(
                isRefreshing = isLoading,
                onRefresh = { fetchUsers() },
                modifier = Modifier.weight(1f)
            ) {
                if (isLoading && allUsers.isEmpty()) {
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
                        Button(onClick = { fetchUsers() }) {
                            Text("Reintentar")
                        }
                    }
                } else if (filteredUsers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay contactos disponibles")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredUsers) { user ->
                            val username = user["username"] ?: ""
                            val displayName = user["displayName"] ?: ""
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
                                            .background(Color(0xFF42A5F5)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(avatarLetter, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                },
                                trailingContent = {
                                    IconButton(onClick = {
                                        selectedUser = user
                                        showBottomSheet = true
                                    }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                                    }
                                },
                                modifier = Modifier.clickable {
                                    selectedUser = user
                                    showBottomSheet = true
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (showBottomSheet && selectedUser != null) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ListItem(
                        headlineContent = { Text("Agregar contacto") },
                        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val targetId = selectedUser!!["userId"] ?: ""
                            scope.launch {
                                val result = authService.createRequest(targetId)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Solicitud creada correctamente", Toast.LENGTH_SHORT).show()
                                    fetchUsers()
                                } else {
                                    Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                                showBottomSheet = false
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Cancelar") },
                        leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                        modifier = Modifier.clickable { showBottomSheet = false }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
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
