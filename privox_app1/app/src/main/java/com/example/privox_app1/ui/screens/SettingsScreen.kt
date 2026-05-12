package com.example.privox_app1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    var isOnline by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SectionTitle("Seguridad")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingSwitchItem(
                            title = "Notificaciones",
                            subtitle = "Recibir alertas de llamadas entrantes",
                            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it }
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Eliminar cuenta") },
                            supportingContent = { Text("Esta acción no se puede deshacer") },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
                            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.clickable { /* Mostrar diálogo eliminar */ }
                        )
                    }
                }
            }

            item {
                SectionTitle("Información")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text("Versión de la app") },
                            supportingContent = { Text("1.0.0") },
                            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                        HorizontalDivider()
                        ListItem(
                            headlineContent = { Text("Política de privacidad") },
                            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.clickable { /* Mostrar política */ }
                        )
                        HorizontalDivider()
                        var showLogoutDialog by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = { Text("Cerrar sesión", color = Color.Red) },
                            leadingContent = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = Color.Red) },
                            trailingContent = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.clickable { showLogoutDialog = true }
                        )
                        if (showLogoutDialog) {
                            AlertDialog(
                                onDismissRequest = { showLogoutDialog = false },
                                title = { Text("Cerrar sesión") },
                                text = { Text("¿Estás seguro de que deseas cerrar sesión?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showLogoutDialog = false
                                            onLogout()
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
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun SettingSwitchItem(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}
