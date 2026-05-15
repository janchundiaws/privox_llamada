package com.example.privox_app1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import com.example.privox_app1.AudioDistortionEngine
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import android.os.PowerManager
import com.example.privox_app1.data.remote.SocketService

@Composable
fun CallScreen(
    username: String,
    callDurationSeconds: Int,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    isDistortionEnabled: Boolean,
    distortionMode: AudioDistortionEngine.DistortionMode = AudioDistortionEngine.DistortionMode.NONE,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHangup: () -> Unit
) {
    val formattedDuration = remember(callDurationSeconds) {
        val minutes = (callDurationSeconds / 60).toString().padStart(2, '0')
        val seconds = (callDurationSeconds % 60).toString().padStart(2, '0')
        "$minutes:$seconds"
    }

    val context = LocalContext.current
    val socketService = remember { SocketService.getInstance(context) }

    DisposableEffect(isSpeakerOn) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Privox:CallProximity")
            } else null
        } else null

        // Only acquire if speaker is OFF
        if (!isSpeakerOn) {
            wakeLock?.acquire()
        }

        onDispose {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D47A1), // Deep Blue
                        Color(0xFF1A237E), // Indigo
                        Color(0xFF000000)  // Black
                    )
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicator
            Surface(
                color = Color(0xFFAB47BC).copy(alpha = 0.8f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Distorsión Activa (${distortionMode.label})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Avatar
            Box(
                modifier = Modifier
                    .size(200.dp)
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
                    fontSize = 100.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(80.dp))

            Text(
                text = username,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            
            val iceState by socketService.iceConnectionState.collectAsState()
            val isCallActive = iceState == org.webrtc.PeerConnection.IceConnectionState.CONNECTED || 
                               iceState == org.webrtc.PeerConnection.IceConnectionState.COMPLETED

            Text(
                text = if (isCallActive) "En llamada - $formattedDuration" else "Conectando...",
                color = if (isCallActive) Color.Gray else Color(0xFFFF9800), // Naranja si está conectando
                fontSize = 18.sp
            )
        }

        // Controls at Bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onMuteToggle,
                        containerColor = if (isMuted) Color.Red else Color(0xFF424242),
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic, contentDescription = "Mute")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Mute", color = Color.White, fontSize = 12.sp)
                }

                // Speaker Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onSpeakerToggle,
                        containerColor = if (isSpeakerOn) Color(0xFF42A5F5) else Color(0xFF424242),
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(if (isSpeakerOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeDown, contentDescription = "Altavoz")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Altavoz", color = Color.White, fontSize = 12.sp)
                }

                // Hangup Button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onHangup,
                        containerColor = Color.Red,
                        contentColor = Color.White,
                        shape = CircleShape
                    ) {
                        Icon(Icons.Filled.CallEnd, contentDescription = "Colgar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Colgar", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
