package com.futura.privox_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import com.futura.privox_app.data.remote.Constants
import com.futura.privox_app.AudioDistortionEngine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("privox_prefs", Context.MODE_PRIVATE) }
    
    var voiceStyle by remember {
        mutableStateOf(prefs.getString("voice_style", "ROBOT") ?: "ROBOT")
    }
    
    var expandedVoiceStyle by remember { mutableStateOf(false) }
    val voiceStyles = AudioDistortionEngine.DistortionMode.values()
    val currentMode = voiceStyles.find { it.name == voiceStyle } ?: AudioDistortionEngine.DistortionMode.ROBOT

    fun getVoiceIcon(mode: AudioDistortionEngine.DistortionMode) = when (mode) {
        AudioDistortionEngine.DistortionMode.NONE -> Icons.AutoMirrored.Filled.VolumeUp
        AudioDistortionEngine.DistortionMode.ROBOT -> Icons.Default.SmartToy
        AudioDistortionEngine.DistortionMode.PITCH -> Icons.Default.GraphicEq
        AudioDistortionEngine.DistortionMode.VOCODER -> Icons.Default.Waves
        AudioDistortionEngine.DistortionMode.ALIEN -> Icons.Default.Public
        AudioDistortionEngine.DistortionMode.FEMALE -> Icons.Default.Female
        AudioDistortionEngine.DistortionMode.MAN -> Icons.Default.Male
        AudioDistortionEngine.DistortionMode.SQUIRREL -> Icons.Default.Pets
    }

    Scaffold(
        containerColor = Color(0xFFF9FAFB),
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color(0xFF111827)
                        )
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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = "Configuración",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ajusta tus preferencias de voz y cuenta",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SectionHeader("Efecto de Voz")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ExposedDropdownMenuBox(
                            expanded = expandedVoiceStyle,
                            onExpandedChange = { expandedVoiceStyle = !expandedVoiceStyle },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .clickable { expandedVoiceStyle = true }
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getVoiceIcon(currentMode),
                                        contentDescription = null,
                                        tint = Color(0xFF2575FC),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Estilo de Voz",
                                        color = Color(0xFF111827),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Efecto activo: ${currentMode.label}",
                                        color = Color(0xFF6B7280),
                                        fontSize = 13.sp
                                    )
                                }
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVoiceStyle)
                            }

                            ExposedDropdownMenu(
                                expanded = expandedVoiceStyle,
                                onDismissRequest = { expandedVoiceStyle = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                voiceStyles.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = mode.label,
                                                fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal,
                                                color = if (mode == currentMode) Color(0xFF2575FC) else Color(0xFF1F2937)
                                            ) 
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = getVoiceIcon(mode),
                                                contentDescription = null,
                                                tint = if (mode == currentMode) Color(0xFF2575FC) else Color(0xFF4B5563)
                                            )
                                        },
                                        onClick = {
                                            voiceStyle = mode.name
                                            prefs.edit().putString("voice_style", voiceStyle).apply()
                                            expandedVoiceStyle = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("Información")
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val packageInfo = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val versionName = packageInfo?.versionName ?: "1.0.0"

                    Column {
                        SettingsRowItem(
                            title = "Versión de la app",
                            subtitle = versionName,
                            icon = Icons.Default.Info,
                            onClick = null
                        )
                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                        SettingsRowItem(
                            title = "Política de privacidad",
                            icon = Icons.Default.Info,
                            onClick = { /* Mostrar política */ },
                            showArrow = true
                        )
                        HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                        
                        var showLogoutDialog by remember { mutableStateOf(false) }
                        SettingsRowItem(
                            title = "Cerrar sesión",
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            onClick = { showLogoutDialog = true },
                            isDestructive = true,
                            showArrow = true
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
                                        Text("Cerrar sesión", color = Color(0xFFEF4444))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLogoutDialog = false }) {
                                        Text("Cancelar", color = Color(0xFF4B5563))
                                    }
                                },
                                containerColor = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF9CA3AF),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsRowItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    isDestructive: Boolean = false,
    showArrow: Boolean = false
) {
    val modifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isDestructive) Color(0xFFFEF2F2) else Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) Color(0xFFEF4444) else Color(0xFF2575FC),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isDestructive) Color(0xFFEF4444) else Color(0xFF111827),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp
                )
            }
        }
        if (showArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (isDestructive) Color(0xFFFCA5A5) else Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Para retrocompatibilidad
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
