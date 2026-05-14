package com.example.privox_app1.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivoxTopBar(
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onBack: (() -> Unit)? = null,
    title: String = ""
) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = "Settings", 
                    tint = Color.Gray
                )
            }
            IconButton(onClick = onLogoutClick) {
                Icon(
                    Icons.Default.ExitToApp, 
                    contentDescription = "Cerrar sesión", 
                    tint = Color.Red
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
