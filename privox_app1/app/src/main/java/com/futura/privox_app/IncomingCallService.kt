package com.futura.privox_app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground Service que mantiene el proceso de la app vivo mientras hay
 * una llamada entrante en segundo plano.
 *
 * Problema que resuelve: Android Doze Mode mata las conexiones TCP de apps
 * en background a los ~1-2 segundos. Al declarar un ForegroundService con
 * tipo "phoneCall", el SO garantiza que el proceso y su red permanezcan activos.
 *
 * Ciclo de vida:
 *   START → cuando llega "incoming-call" y la app está en background
 *   STOP  → cuando la llamada es aceptada, rechazada o cancelada por el llamante
 */
class IncomingCallService : Service() {

    companion object {
        const val ACTION_START = "com.futura.privox_app.ACTION_INCOMING_CALL_START"
        const val ACTION_STOP  = "com.futura.privox_app.ACTION_INCOMING_CALL_STOP"

        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALL_ID     = "call_id"
        const val EXTRA_FROM_ID     = "from_id"

        // Comparte el mismo ID que CALL_NOTIFICATION_ID en SocketService
        // para que startForeground() y la notificación existente sean la misma.
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID      = "call_channel"

        fun start(context: Context, callerName: String, callId: String, fromId: String) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_FROM_ID, fromId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d("IncomingCallService", "▶️ startForegroundService solicitado para $callerName")
        }

        fun stop(context: Context) {
            val intent = Intent(context, IncomingCallService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            Log.d("IncomingCallService", "⏹️ Detención del servicio solicitada")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Llamada"
                val callId     = intent.getStringExtra(EXTRA_CALL_ID)     ?: ""
                val fromId     = intent.getStringExtra(EXTRA_FROM_ID)     ?: ""

                val notification = buildNotification(callerName, callId, fromId)
                // En API 29+ hay que pasar el tipo que coincide con el declarado en el manifest.
                // dataSync = mantener conexión de red activa (WebSocket de señalización).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.d("IncomingCallService", "✅ Foreground activo (dataSync) — red protegida de Doze Mode")
            }
            ACTION_STOP -> {
                Log.d("IncomingCallService", "🛑 Deteniendo foreground service")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        // START_NOT_STICKY: si el SO mata el servicio, no lo reinicia automáticamente.
        // El socket reconectará solo si se implementa scheduleReconnect().
        return START_NOT_STICKY
    }

    private fun buildNotification(callerName: String, callId: String, fromId: String): Notification {
        // El canal ya fue creado por SocketService.createNotificationChannel()
        // pero nos aseguramos de que exista si el servicio arranca primero.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = android.app.NotificationChannel(
                    CHANNEL_ID, "Llamadas", NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(ch)
            }
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("callId",   callId)
            putExtra("fromId",   fromId)
            putExtra("fromName", callerName)
            putExtra("screen",   "CallingIncoming")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, NOTIFICATION_ID, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 Llamada entrante")
            .setContentText("$callerName está llamando...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)       // no se puede deslizar para quitar
            .setAutoCancel(false)   // solo se quita cuando el servicio se detiene
            .build()
    }
}
