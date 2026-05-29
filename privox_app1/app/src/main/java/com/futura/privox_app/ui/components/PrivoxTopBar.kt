package com.futura.privox_app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivoxTopBar(
    onSettingsClick: (() -> Unit)? = null,
    onLogoutClick: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    title: String = ""
) {
    TopAppBar(
        title = { 
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            ) 
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color(0xFF111827)
                    )
                }
            }
        },
        actions = {
            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings, 
                        contentDescription = "Settings", 
                        tint = Color(0xFF6B7280)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
