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

class RobotVoiceProcessor : AudioProcessingAdapter.ExternalAudioFrameProcessing {

    @Volatile var enabled = true

    // Cargar la librería C++ compilada
    init {
        System.loadLibrary("oboe_audio_engine")
    }

    // Declaración del método nativo que escribimos en voice_processor.cpp
    private external fun processPitchShiftNative(buffer: ByteBuffer, numBands: Int, numFrames: Int, pitchFactor: Float)

    override fun initialize(sampleRateHz: Int, numChannels: Int) {}
    override fun reset(newRate: Int) {}

    override fun process(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        if (!enabled) return

        // Aquí usamos el pitchFactor del proyecto anterior. 
        // 0.85 es para un tono más grave (efecto oscuro/robot).
        // 1.5 sería para efecto ardilla.
        val pitchFactor = 0.85f 
        
        // Llamada ultra-rápida a C++ enviando el buffer directo de WebRTC
        processPitchShiftNative(buffer, numBands, numFrames, pitchFactor)
    }
}



// ───────────────────────────────────────────────────────────────────────────

class MainActivity : FlutterActivity() {
    private val PROXIMITY_CHANNEL = "proximity_wakelock"
    private val DISTORTION_CHANNEL = "voice_distortion"
    private var proximityWakeLock: PowerManager.WakeLock? = null

    // Single shared processor instance
    private val robotProcessor = RobotVoiceProcessor()
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
        robotProcessor.enabled = true
        plugin.audioProcessingController.capturePostProcessing.addProcessor(robotProcessor)
        distortionEnabled = true
        println("🤖 Distorsión de voz activada")
    }

    private fun disableDistortion() {
        if (!distortionEnabled) return
        val plugin = FlutterWebRTCPlugin.sharedSingleton ?: return
        plugin.audioProcessingController.capturePostProcessing.removeProcessor(robotProcessor)
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
