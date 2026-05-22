package com.futura.privox_app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFFEFF6FF))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings, 
                    contentDescription = "Settings", 
                    tint = Color.Gray
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
