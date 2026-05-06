package com.example.ghox_app

import android.content.Context
import android.os.PowerManager
import com.cloudwebrtc.webrtc.FlutterWebRTCPlugin
import com.cloudwebrtc.webrtc.audio.AudioProcessingAdapter
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ── Sonic Voice Processor ───────────────────────────────────────────────────
// Implements ExternalAudioFrameProcessing to hook directly into WebRTC's
// capture pipeline. Uses the Sonic library for high-quality pitch shifting.
class SonicVoiceProcessor : AudioProcessingAdapter.ExternalAudioFrameProcessing {

    @Volatile var enabled = true
    private var sonic: Sonic? = null
    private var sampleRate = 48000
    private var numChannels = 1
    
    // Configuración para "Ardilla": Pitch 2.0 (más agudo), Speed 1.0, Rate 1.0
    private val pitchShift = 2.0f 
    
    // Umbral de silencio (Gate). Si el RMS es menor a esto, enviamos silencio.
    // 30-100 es un rango típico para sensibilidad moderada.
    private val silenceThreshold = 50.0 

    override fun initialize(sampleRateHz: Int, numChannels: Int) {
        this.sampleRate = sampleRateHz
        this.numChannels = numChannels
        setupSonic()
    }

    override fun reset(newRate: Int) {
        this.sampleRate = newRate
        setupSonic()
    }

    private fun setupSonic() {
        sonic = Sonic(sampleRate, numChannels).apply {
            pitch = pitchShift
            speed = 1.0f
            rate = 1.0f
        }
    }

    private fun calculateRMS(shorts: ShortArray): Double {
        var sum = 0.0
        for (s in shorts) {
            sum += (s.toInt() * s.toInt()).toDouble()
        }
        return Math.sqrt(sum / shorts.size)
    }

    override fun process(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        if (!enabled || sonic == null) return

        val totalSamples = numFrames * numChannels
        val startPos = buffer.position()
        
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // Leer del buffer
        val inputShorts = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            inputShorts[i] = buffer.getShort(startPos + i * 2)
        }

        // 1. Umbral de Silencio (Gate)
        val rms = calculateRMS(inputShorts)
        if (rms < silenceThreshold) {
            // Si es silencio, llenamos el buffer con ceros y no procesamos con Sonic
            // Esto evita que Sonic intente procesar el ruido de fondo.
            for (i in 0 until totalSamples) {
                buffer.putShort(startPos + i * 2, 0)
            }
            return
        }

        // 2. Procesar con Sonic
        sonic?.writeShortToStream(inputShorts, numFrames)
        
        // Leer resultado de Sonic
        val available = sonic?.samplesAvailable() ?: 0
        if (available >= numFrames) {
            val outputShorts = ShortArray(totalSamples)
            sonic?.readShortFromStream(outputShorts, numFrames)
            
            // Escribir de vuelta al buffer
            for (i in 0 until totalSamples) {
                buffer.putShort(startPos + i * 2, outputShorts[i])
            }
        }
    }
}



// ───────────────────────────────────────────────────────────────────────────

class MainActivity : FlutterActivity() {
    private val PROXIMITY_CHANNEL = "proximity_wakelock"
    private val DISTORTION_CHANNEL = "voice_distortion"
    private var proximityWakeLock: PowerManager.WakeLock? = null

    // Single shared processor instance
    private val voiceProcessor = SonicVoiceProcessor()
    private var distortionEnabled = false

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ── Proximity WakeLock channel (unchanged) ──────────────────────────
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, PROXIMITY_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "enable" -> {
                    try {
                        enableProximityWakeLock()
                        result.success("Proximity WakeLock enabled")
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to enable proximity wakelock: ${e.message}", null)
                    }
                }
                "disable" -> {
                    try {
                        disableProximityWakeLock()
                        result.success("Proximity WakeLock disabled")
                    } catch (e: Exception) {
                        result.error("ERROR", "Failed to disable proximity wakelock: ${e.message}", null)
                    }
                }
                else -> result.notImplemented()
            }
        }

        // ── Voice Distortion channel ────────────────────────────────────────
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, DISTORTION_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "enable" -> {
                    enableDistortion()
                    result.success(null)
                }
                "disable" -> {
                    disableDistortion()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun enableDistortion() {
        if (distortionEnabled) return
        val plugin = FlutterWebRTCPlugin.sharedSingleton ?: run {
            println("⚠️ FlutterWebRTCPlugin no disponible todavía")
            return
        }
        voiceProcessor.enabled = true
        plugin.audioProcessingController.capturePostProcessing.addProcessor(voiceProcessor)
        distortionEnabled = true
        println("🤖 Distorsión de voz activada")
    }

    private fun disableDistortion() {
        if (!distortionEnabled) return
        val plugin = FlutterWebRTCPlugin.sharedSingleton ?: return
        plugin.audioProcessingController.capturePostProcessing.removeProcessor(voiceProcessor)
        distortionEnabled = false
        println("🔇 Distorsión de voz desactivada")
    }

    private fun enableProximityWakeLock() {
        // Si ya está activo, no hacer nada
        proximityWakeLock?.let {
            if (it.isHeld) {
                println("⚠️ ProximityWakeLock ya está activo")
                return
            }
        }
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        
        // Crear y activar el wake lock de proximidad
        proximityWakeLock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "GhoxApp::ProximityWakeLock"
        )
        
        proximityWakeLock?.acquire()
        println("✅ ProximityWakeLock adquirido en Android")
    }

    private fun disableProximityWakeLock() {
        proximityWakeLock?.let {
            if (it.isHeld) {
                it.release()
                println("✅ ProximityWakeLock liberado en Android")
            }
        }
        proximityWakeLock = null
    }

    override fun onDestroy() {
        disableProximityWakeLock()
        super.onDestroy()
    }
}
