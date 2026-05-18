package com.futura.privox_app.ui.screens

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
        AudioDistortionEngine.DistortionMode.NONE -> Icons.Default.VolumeUp
        AudioDistortionEngine.DistortionMode.ROBOT -> Icons.Default.SmartToy
        AudioDistortionEngine.DistortionMode.PITCH -> Icons.Default.GraphicEq
        AudioDistortionEngine.DistortionMode.VOCODER -> Icons.Default.Waves
        AudioDistortionEngine.DistortionMode.ALIEN -> Icons.Default.Public
        AudioDistortionEngine.DistortionMode.FEMALE -> Icons.Default.Female
        AudioDistortionEngine.DistortionMode.MAN -> Icons.Default.Male
        AudioDistortionEngine.DistortionMode.SQUIRREL -> Icons.Default.Pets
    }

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
                SectionTitle("Configuración")
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ExposedDropdownMenuBox(
                            expanded = expandedVoiceStyle,
                            onExpandedChange = { expandedVoiceStyle = !expandedVoiceStyle },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text("Estilo de Voz") },
                                supportingContent = { Text("Efecto activo: ${currentMode.label}") },
                                leadingContent = { Icon(getVoiceIcon(currentMode), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingContent = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedVoiceStyle) },
                                modifier = Modifier.menuAnchor().clickable { expandedVoiceStyle = true }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedVoiceStyle,
                                onDismissRequest = { expandedVoiceStyle = false }
                            ) {
                                voiceStyles.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        leadingIcon = { Icon(getVoiceIcon(mode), contentDescription = null) },
                                        onClick = {
                                            voiceStyle = mode.name
                                            prefs.edit().putString("voice_style", voiceStyle).apply()
                                            expandedVoiceStyle = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
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
                    val packageInfo = remember {
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val versionName = packageInfo?.versionName ?: "1.0.0"

                    Column {
                        ListItem(
                            headlineContent = { Text("Versión de la app") },
                            supportingContent = { Text(versionName) },
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
