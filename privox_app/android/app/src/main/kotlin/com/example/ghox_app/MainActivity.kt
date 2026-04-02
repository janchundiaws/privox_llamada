package com.example.ghox_app

import android.content.Context
import android.os.PowerManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "proximity_wakelock"
    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
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
