package com.example.privox_app1

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.privox_app1.ui.theme.Privox_app1Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.privox_app1.data.remote.SocketService

class MainActivity : ComponentActivity() {
    private val engine = AudioDistortionEngine()
    private val intentAction = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intentAction.value = intent
        setContent {
            Privox_app1Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = getSharedPreferences("privox_prefs", android.content.Context.MODE_PRIVATE)
                    val savedUsername = prefs.getString("username", "") ?: ""
                    var currentScreen by remember { mutableStateOf(if (savedUsername.isNotEmpty()) "Home" else "Login") }
                    var loggedInUser by remember { mutableStateOf(savedUsername) }
                    var currentContact by remember { mutableStateOf("") }
                    val scope = rememberCoroutineScope()

                    // Request permissions at start
                    val launcher = rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        Log.d("MainActivity", "Permissions granted: $permissions")
                    }

                    LaunchedEffect(Unit) {
                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        launcher.launch(permissions.toTypedArray())
                    }
                    
                    var currentCallFromId by remember { mutableStateOf("") }
                    var callDuration by remember { mutableStateOf(0) }
                    var isMuted by remember { mutableStateOf(false) }
                    var isSpeakerOn by remember { mutableStateOf(false) }
                    var isDistortionEnabled by remember { mutableStateOf(false) }

                    val socketService = remember { com.example.privox_app1.data.remote.SocketService.getInstance(this@MainActivity) }
                    val isConnected by socketService.isConnected.collectAsState()
                    
                    // Handle Intent Actions (like clicking notifications)
                    val action by intentAction
                    LaunchedEffect(action) {
                        action?.let { intent ->
                            val screen = intent.getStringExtra("screen")
                            val callId = intent.getStringExtra("callId")
                            val fromId = intent.getStringExtra("fromId")
                            val fromName = intent.getStringExtra("fromName")
                            
                            if (screen == "CallingIncoming" && callId != null && fromId != null) {
                                Log.d("MainActivity", "🚀 Notificación clickeada: Navegando a CallingIncoming de $fromName ($fromId)")
                                socketService.currentCallId = callId
                                currentCallFromId = fromId
                                currentContact = fromName ?: fromId
                                currentScreen = "CallingIncoming"
                                intentAction.value = null // Consume action
                            } else {
                                Log.d("MainActivity", "Intent recibido pero no es para llamada entrante: screen=$screen")
                            }
                        }
                    }

                    LaunchedEffect(loggedInUser) {
                        if (loggedInUser.isNotEmpty()) {
                            socketService.connect()
                        } else {
                            socketService.disconnect()
                        }
                    }

                    // Timer for active call and state resets
                    LaunchedEffect(currentScreen) {
                        if (loggedInUser.isNotEmpty()) {
                            socketService.connect()
                        }
                        if (currentScreen == "Call" || currentScreen == "CallingIncoming" || currentScreen == "CallingOutgoing") {
                            // Reset audio states at the start of any call flow
                            isMuted = false
                            isSpeakerOn = false
                            
                            // Ensure system speaker is off
                            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            audioManager.isSpeakerphoneOn = false
                            
                            if (currentScreen == "Call") {
                                callDuration = 0
                                while (currentScreen == "Call") {
                                    delay(1000)
                                    callDuration++
                                }
                            }
                        }
                    }

                    // Refresco dinámico de estilo de voz al iniciar cualquier fase de llamada
                    LaunchedEffect(currentScreen) {
                        if (currentScreen == "CallingIncoming" || currentScreen == "CallingOutgoing" || currentScreen == "Call") {
                            val savedStyle = prefs.getString("voice_style", "ROBOT") ?: "ROBOT"
                            try {
                                val mode = com.example.privox_app1.AudioDistortionEngine.DistortionMode.valueOf(savedStyle)
                                socketService.currentDistortionMode = mode
                                socketService.isDistortionEnabled = true
                                isDistortionEnabled = true
                                Log.d("MainActivity", "🔄 Estilo de voz configurado para la sesión: ${mode.label}")
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error al cargar estilo de voz: $e")
                            }
                        }
                    }

                    // Observe socket events
                    LaunchedEffect(Unit) {
                        socketService.events.collect { event ->
                            scope.launch {
                                val type = event["type"] as? String
                                when (type) {
                                    "incoming-call" -> {
                                        Log.d("MainActivity", "🔔 Evento socket: Llamada entrante de $currentCallFromId")
                                        // Safety: only accept incoming calls if we are not busy
                                        if (currentScreen == "Home") {
                                            val fromId = event["from"] as? String ?: ""
                                            val callId = event["callId"] as? String ?: ""
                                            val fromUsername = event["fromUsername"] as? String ?: socketService.getUsernameById(fromId)
                                            
                                            socketService.currentCallId = callId
                                            currentCallFromId = fromId
                                            currentContact = fromUsername
                                            currentScreen = "CallingIncoming"
                                        } else {
                                            Log.d("MainActivity", "⚠️ Ignorando llamada entrante porque el usuario está ocupado en $currentScreen")
                                        }
                                    }
                                    "call-accepted" -> {
                                        Log.d("MainActivity", "✅ Evento socket: Llamada aceptada por el destino")
                                        val fromId = event["from"] as? String ?: currentCallFromId
                                        socketService.initWebRTC(fromId, true)
                                        currentScreen = "Call"
                                    }
                                    "call-reject", "hangup" -> {
                                        Log.d("MainActivity", "📴 Evento socket: $type recibido")
                                        if (currentScreen != "Home") {
                                            Log.d("MainActivity", "Finalizando sesión actual y regresando a Home")
                                            currentScreen = "Home"
                                            socketService.disposeWebRTC(currentCallFromId)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    when (currentScreen) {
                        "Login" -> {
                            com.example.privox_app1.ui.screens.LoginScreen(
                                onLoginSuccess = { user ->
                                    loggedInUser = user
                                    currentScreen = "Home"
                                    prefs.edit().putString("username", user).apply()
                                }
                            )
                        }
                        "CallingIncoming" -> {
                            com.example.privox_app1.ui.screens.CallingScreen(
                                username = currentContact,
                                isEmisor = false,
                                onAccept = {
                                    scope.launch {
                                        socketService.acceptCall(socketService.currentCallId, currentCallFromId, loggedInUser)
                                        socketService.initWebRTC(currentCallFromId, false)
                                        currentScreen = "Call"
                                    }
                                },
                                onReject = {
                                    socketService.rejectCall(socketService.currentCallId, currentCallFromId)
                                    currentScreen = "Home"
                                }
                            )
                        }
                        "CallingOutgoing" -> {
                            com.example.privox_app1.ui.screens.CallingScreen(
                                username = currentContact,
                                isEmisor = true,
                                onAccept = {},
                                onReject = {
                                    socketService.hangupCall(socketService.currentCallId, currentCallFromId)
                                    socketService.disposeWebRTC(currentCallFromId)
                                    currentScreen = "Home"
                                }
                            )
                        }
                        "Call" -> {
                            com.example.privox_app1.ui.screens.CallScreen(
                                username = currentContact,
                                callDurationSeconds = callDuration,
                                isMuted = isMuted,
                                isSpeakerOn = isSpeakerOn,
                                isDistortionEnabled = isDistortionEnabled,
                                distortionMode = socketService.currentDistortionMode,
                                onMuteToggle = {
                                    isMuted = !isMuted
                                    socketService.localStream?.audioTracks?.forEach { it.setEnabled(!isMuted) }
                                },
                                onSpeakerToggle = {
                                    isSpeakerOn = !isSpeakerOn
                                    socketService.setSpeakerphoneOn(isSpeakerOn)
                                },
                                onHangup = {
                                    Log.d("MainActivity", "⏹️ Botón colgar presionado")
                                    socketService.hangupCall(socketService.currentCallId, currentCallFromId)
                                    socketService.disposeWebRTC(currentCallFromId)
                                    currentScreen = "Home"
                                }
                            )
                        }
                        "Home" -> {
                            com.example.privox_app1.ui.screens.MainTabsScreen(
                                username = loggedInUser,
                                engine = engine,
                                onSettingsClick = { currentScreen = "Settings" },
                                requestCall = { targetId, targetName ->
                                    scope.launch {
                                        if (!socketService.isConnected.value) {
                                            android.widget.Toast.makeText(this@MainActivity, "Debes estar Online para realizar llamadas", android.widget.Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val result = socketService.initiateCall(targetId, targetName)
                                        val callId = result?.first
                                        val type = result?.second

                                        if (callId != null) {
                                            socketService.currentCallId = callId
                                            currentCallFromId = targetId
                                            currentContact = targetName
                                            currentScreen = "CallingOutgoing"
                                        } else {
                                            val errorMsg = when (type) {
                                                "call-init-denied" -> "$targetName está ocupado en otra llamada."
                                                "call-missed" -> "Llamada perdida."
                                                "peer-offline" -> "Usuario destino offline."
                                                else -> "$targetName no está online."
                                            }
                                            android.widget.Toast.makeText(this@MainActivity, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onChatClick = { targetId, targetName ->
                                    currentContact = targetName
                                    currentScreen = "Chat"
                                },
                                onLogoutClick = {
                                    val authService = com.example.privox_app1.data.remote.AuthService(this@MainActivity)
                                    authService.logout()
                                    loggedInUser = ""
                                    currentScreen = "Login"
                                },
                                isConnected = isConnected
                            )
                        }
                        "Settings" -> {
                            com.example.privox_app1.ui.screens.SettingsScreen(
                                onBack = { currentScreen = "Home" },
                                onLogout = {
                                    val authService = com.example.privox_app1.data.remote.AuthService(this@MainActivity)
                                    authService.logout()
                                    loggedInUser = ""
                                    currentScreen = "Login"
                                }
                            )
                        }
                        "VoiceChanger" -> {
                            // Handled as a tab in MainTabsScreen
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val socketService = SocketService.getInstance(this)
        if (!socketService.isConnected.value) {
            socketService.connect()
        }
    }

    override fun onRestart() {
        super.onRestart()
        val socketService = SocketService.getInstance(this)
        if (!socketService.isConnected.value) {
            socketService.connect()
        }
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentAction.value = intent
    }

    private fun handleCallIntent(intent: Intent) {
        val callId = intent.getStringExtra("callId")
        if (callId != null) {
            Log.d("MainActivity", "Handling call intent for callId: $callId")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChangerTestScreen(engine: AudioDistortionEngine) {
    val effects = AudioDistortionEngine.DistortionMode.values().toList()
    val selectedEffect = rememberSaveable { mutableStateOf(AudioDistortionEngine.DistortionMode.NONE) }
    val isRunning = remember { mutableStateOf(false) }
    val statusText = remember { mutableStateOf("Listo para probar cambio de voz") }
    val permissionGranted = rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        permissionGranted.value = granted
        statusText.value = if (granted) "Permiso de micrófono concedido" else "Permiso de micrófono denegado"
    }

    LaunchedEffect(Unit) {
        permissionGranted.value = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Prueba de cambio de voz local con STFT Phase Vocoder",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Pipeline: Mic → Noise Suppression → VAD → STFT Pitch Shift → Formant Correction → Reproducción local",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Efecto activo: ${selectedEffect.value.label}",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )

        var expanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedEffect.value.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Seleccionar efecto") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                effects.forEach { effect ->
                    DropdownMenuItem(
                        text = { Text(effect.label) },
                        onClick = {
                            selectedEffect.value = effect
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (!permissionGranted.value) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@Button
                    }
                    if (!isRunning.value) {
                        engine.start(
                            context,
                            selectedEffect.value,
                            onError = { statusText.value = "Error: $it" },
                            onStatus = { statusText.value = it }
                        )
                        isRunning.value = true
                    } else {
                        engine.stop()
                        isRunning.value = false
                        statusText.value = "Detenido"
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(text = if (isRunning.value) "Detener" else "Iniciar voz")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = statusText.value,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Nota: Esta prueba usa solo audio local y STFT Phase Vocoder en el NDK. Habla al micrófono y escucha el resultado en el altavoz.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
