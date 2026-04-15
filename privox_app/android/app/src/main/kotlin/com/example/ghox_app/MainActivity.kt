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

// ── Robot Voice Processor ───────────────────────────────────────────────────
// Implements ExternalAudioFrameProcessing to hook directly into WebRTC's
// capture pipeline. The buffer is PCM-16-bit (little-endian) and is
// modified in-place before the frame is encoded and sent.
class RadioMilitaryVoiceProcessor : AudioProcessingAdapter.ExternalAudioFrameProcessing {

    @Volatile var enabled = true

    private var lastSample = 0f

    // Parámetros estilo radio militar
    private val wetMix = 0.55f          // 55% efecto, 45% voz original
    private val hpfCoeff = 0.85f        // High-pass suave (quita graves)
    private val lpfCoeff = 0.25f        // Low-pass suave (quita agudos)
    private val drive = 1.35f           // Distorsión analógica suave
    private val compressRatio = 0.65f   // Compresión estilo radio

    private var hpfState = 0f
    private var lpfState = 0f

    override fun initialize(sampleRateHz: Int, numChannels: Int) {
        hpfState = 0f
        lpfState = 0f
        lastSample = 0f
    }

    override fun reset(newRate: Int) {
        hpfState = 0f
        lpfState = 0f
        lastSample = 0f
    }

    override fun process(numBands: Int, numFrames: Int, buffer: ByteBuffer) {
        if (!enabled) return

        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val numSamples = numFrames * numBands
        val startPos = buffer.position()

        for (i in 0 until numSamples) {
            val bytePos = startPos + i * 2
            if (bytePos + 2 > buffer.limit()) break

            val dry = buffer.getShort(bytePos).toFloat()

            // 1) High-pass filter (quita graves)
            hpfState = dry - (hpfCoeff * hpfState)
            var processed = hpfState

            // 2) Low-pass filter (quita agudos)
            lpfState = lpfState + lpfCoeff * (processed - lpfState)
            processed = lpfState

            // 3) Compresión estilo radio militar
            processed = (processed * compressRatio) + (lastSample * (1 - compressRatio))
            lastSample = processed

            // 4) Distorsión analógica suave (tanh)
            processed = (Math.tanh(processed * drive.toDouble()) * 12000).toFloat()

            // 5) Mezcla dry/wet
            val mixed = (dry * (1 - wetMix) + processed * wetMix)
                .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                .toInt()
                .toShort()

            buffer.putShort(bytePos, mixed)
        }
    }
}



// ───────────────────────────────────────────────────────────────────────────

class MainActivity : FlutterActivity() {
    private val PROXIMITY_CHANNEL = "proximity_wakelock"
    private val DISTORTION_CHANNEL = "voice_distortion"
    private var proximityWakeLock: PowerManager.WakeLock? = null

    // Single shared processor instance
    private val robotProcessor = RadioMilitaryVoiceProcessor()
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
