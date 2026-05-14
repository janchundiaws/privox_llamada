package com.example.privox_app1.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.privox_app1.MainActivity
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.*
import org.webrtc.*
import java.util.concurrent.TimeUnit

class SocketService private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: SocketService? = null

        fun getInstance(context: Context): SocketService {
            return instance ?: synchronized(this) {
                instance ?: SocketService(context.applicationContext).also { instance = it }
            }
        }
    }

    private val TAG = "SocketService"
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "call_channel"

    var webSocket: WebSocket? = null
    var peerConnection: PeerConnection? = null
    var localStream: MediaStream? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()
    var isConnecting = false
    var message: String? = null
    var currentTargetUserId: String? = null
    var currentTargetUsername: String? = null
    var currentCallId: String = ""
    var missedCallId: String? = null

    private val _events = MutableSharedFlow<Map<String, Any?>>()
    val events = _events.asSharedFlow()

    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val usersCache = mutableMapOf<String, String>()
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val rootEglBase: EglBase = EglBase.create()

    private val prefs: SharedPreferences = context.getSharedPreferences("privox_prefs", Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        initWebRtcFactory()
        createNotificationChannel()
    }

    @Volatile
    var currentDistortionMode = com.example.privox_app1.AudioDistortionEngine.DistortionMode.ROBOT
    
    @Volatile
    var isDistortionEnabled: Boolean = false
        set(value) {
            field = value
            Log.d("SocketService", "Distorsión activada: $value")
        }
    private val distortionEngine = com.example.privox_app1.AudioDistortionEngine()

    private fun initWebRtcFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
        
        val options = PeerConnectionFactory.Options()
        
        var recordLogCount = 0
        var playLogCount = 0

        val adm = org.webrtc.audio.JavaAudioDeviceModule.builder(context)
            .setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            .setAudioRecordDataCallback(object : org.webrtc.audio.AudioRecordDataCallback {
                override fun onAudioDataRecorded(format: Int, channels: Int, rate: Int, buffer: java.nio.ByteBuffer) {
                    if (isDistortionEnabled) {
                        // Modificamos el búfer directamente en memoria para que WebRTC lo transmita procesado
                        distortionEngine.processByteBuffer(buffer, buffer.remaining(), currentDistortionMode)
                        
                        recordLogCount++
                        if (recordLogCount >= 100) {
                            Log.d("AudioDistortion", "🎙️ Transmitiendo con distorsión activa: ${currentDistortionMode.label}")
                            recordLogCount = 0
                        }
                    }
                }
            })
            .setSamplesReadyCallback { samples ->
                // Mantenemos este callback solo para telemetría de niveles (RMS)
                if (recordLogCount % 100 == 0) {
                    val rms = calculateRMS(samples.data)
                    // Log opcional para monitorear volumen
                }
            }
            .createAudioDeviceModule()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(adm)
            .createPeerConnectionFactory()
    }

    fun setSpeakerphoneOn(on: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = on
            Log.d(TAG, "Altavoz conmutado: $on")
        } catch (e: Exception) {
            Log.e(TAG, "Error al conmutar altavoz: ${e.message}")
        }
    }

    fun connect() {
        if (isConnecting || isConnected.value) return
        isConnecting = true
        
        val token = prefs.getString("token", "") ?: ""
        var wsBase = Constants.URL_API
        wsBase = wsBase.replace("https://", "wss://").replace("http://", "ws://")
        if (wsBase.endsWith("/")) wsBase = wsBase.dropLast(1)
        
        val uri = "$wsBase?token=$token"
        
        val request = Request.Builder().url(uri).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _isConnected.value = true
                isConnecting = false
                message = "✅ Conectado"
                Log.d(TAG, message!!)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = gson.fromJson(text, Map::class.java) as Map<String, Any?>
                    coroutineScope.launch {
                        // For incoming calls, pre-fetch username to avoid UI lag
                        if (data["type"] == "incoming-call") {
                            val fromId = data["from"] as? String
                            if (fromId != null) {
                                val username = getUsernameById(fromId)
                                val mutableData = data.toMutableMap()
                                mutableData["fromUsername"] = username
                                _events.emit(mutableData)
                                handleIncomingMessage(mutableData)
                                return@launch
                            }
                        }
                        _events.emit(data)
                        handleIncomingMessage(data)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing message: $e")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                isConnecting = false
                message = "❌ Desconectado del servidor"
                Log.d(TAG, message!!)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                isConnecting = false
                message = "❌ Error en la conexión: ${t.message}"
                Log.e(TAG, message!!)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _isConnected.value = false
        message = "❌ Desconectado"
    }

    suspend fun initiateCall(toUserId: String, toUsername: String): Pair<String?, String?>? {
        currentTargetUserId = toUserId
        currentTargetUsername = toUsername
        
        val payload = mapOf(
            "type" to "call-init",
            "to" to toUserId,
            "toUsername" to toUsername,
            "meta" to mapOf("mode" to "voice")
        )
        
        if (webSocket == null || !isConnected.value) {
            return null
        }
        
        webSocket?.send(gson.toJson(payload))

        // Wait for ack with a timeout
        return withTimeoutOrNull(10000) {
            val event = events.first { data ->
                data["type"] == "call-init-ack" || 
                data["type"] == "call-init-denied" || 
                data["type"] == "call-missed" ||
                data["type"] == "peer-offline"
            }
            
            val type = event["type"]?.toString()
            val callId = if (type == "call-init-ack") event["callId"]?.toString() else null
            
            Pair(callId, type)
        }
    }

    fun acceptCall(callId: String, fromUserId: String, toUsername: String) {
        currentTargetUserId = fromUserId
        val payload = mapOf(
            "type" to "call-accept",
            "callId" to callId,
            "from" to fromUserId,
            "toUsername" to toUsername
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun rejectCall(callId: String, fromUserId: String) {
        val payload = mapOf(
            "type" to "call-reject",
            "callId" to callId,
            "from" to fromUserId
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun hangupCall(callId: String, toUserId: String) {
        val payload = mapOf(
            "type" to "hangup",
            "callId" to callId,
            "to" to toUserId
        )
        webSocket?.send(gson.toJson(payload))
    }

    private suspend fun handleIncomingMessage(data: Map<String, Any?>) {
        val type = data["type"] as? String ?: return
        when (type) {
            "incoming-call" -> {
                val fromUserId = data["from"] as? String ?: return
                currentCallId = data["callId"] as? String ?: ""
                val username = data["fromUsername"] as? String ?: getUsernameById(fromUserId)
                Log.d(TAG, "Llamada entrante de $username ($fromUserId)")
                
                showIncomingCallNotification(username, fromUserId, currentCallId)
            }
            "call-accepted" -> {
                if (currentTargetUserId != null) {
                    startOffer(currentTargetUserId!!)
                }
            }
            "call-reject" -> {
                message = "❌ Llamada rechazada servicio: ${data["callId"]}"
            }
            "hangup" -> {
                // Let MainActivity handle disposal and navigation
            }
            "offer", "answer", "ice" -> {
                handleSignal(data)
            }
            "call-missed" -> {
                missedCallId = data["callId"] as? String
            }
            "peer-offline" -> {
                Log.w(TAG, "⚠️ Usuario destino offline: ${data["to"]}")
            }
        }
    }

    suspend fun getUsernameById(userId: String): String = withContext(Dispatchers.IO) {
        if (usersCache.containsKey(userId)) {
            return@withContext usersCache[userId]!!
        }
        try {
            val token = prefs.getString("token", "") ?: ""
            val request = Request.Builder()
                .url("${Constants.URL_API}api/users/usersaccount")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    val body = gson.fromJson(bodyString, Map::class.java) as Map<*, *>
                    val users = body["users"] as? List<*> ?: emptyList<Any>()

                    for (u in users) {
                        if (u is Map<*, *>) {
                            val id = u["userId"]?.toString()
                            val displayName = u["displayName"]?.toString()
                            val username = u["username"]?.toString()
                            val name = if (!username.isNullOrEmpty()) username else displayName
                            if (id != null && name != null) {
                                usersCache[id] = name
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting username for $userId: $e")
        }
        usersCache[userId] ?: userId
    }

    private suspend fun getIceServers(): List<PeerConnection.IceServer> = withContext(Dispatchers.IO) {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        
        try {
            val token = prefs.getString("token", "") ?: ""
            val request = Request.Builder()
                .url("${Constants.URL_API}api/ice")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isNotEmpty()) {
                        val body = gson.fromJson(bodyString, Map::class.java) as Map<*, *>
                        val servers = body["iceServers"] as? List<Map<String, Any>> ?: emptyList()
                        for (server in servers) {
                            val urls = server["urls"] as? String ?: continue
                            val username = server["username"] as? String ?: ""
                            val credential = server["credential"] as? String ?: ""
                            iceServers.add(
                                PeerConnection.IceServer.builder(urls)
                                    .setUsername(username)
                                    .setPassword(credential)
                                    .createIceServer()
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ICE servers: $e")
        }
        iceServers
    }

    suspend fun initWebRTC(toUserId: String, isEmisor: Boolean) {
        if (peerConnection != null) {
            Log.d(TAG, "WebRTC already initialized, skipping...")
            return
        }
        try {
            val iceServers = getIceServers()
            
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }

            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(TAG, "SignalingState: $state")
                }
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "IceConnectionState: $state")
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate) {
                    val payload = mapOf(
                        "type" to "ice",
                        "candidate" to candidate.sdp,
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex,
                        "to" to toUserId
                    )
                    webSocket?.send(gson.toJson(payload))
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                    Log.d(TAG, "Remote track added")
                }
            })

            // Setup local audio
            val audioConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "false"))
            }
            
            localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
            
            localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)

            localStream = peerConnectionFactory?.createLocalMediaStream("ARDAMS")
            localStream?.addTrack(localAudioTrack)

            if (localAudioTrack != null) {
                peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))
                Log.d("WebRTCLog", "✅ Track de audio local añadido correctamente")
            }

            if (isEmisor) {
                startOffer(toUserId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error init WebRTC: $e")
        }
    }

    private fun startOffer(toUserId: String) {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(this, desc)
                    val payload = mapOf(
                        "type" to desc.type.canonicalForm(),
                        "sdp" to desc.description,
                        "to" to toUserId
                    )
                    webSocket?.send(gson.toJson(payload))
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    suspend fun handleSignal(data: Map<String, Any?>) {
        val type = data["type"] as? String ?: return
        Log.d("WebRTCLog", "Recibiendo señal: $type")
        when (type) {
            "offer" -> {
                val from = data["from"] as? String ?: return
                if (peerConnection == null) {
                    initWebRTC(from, false)
                }
                val sdp = SessionDescription(SessionDescription.Type.OFFER, data["sdp"] as String)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        val constraints = MediaConstraints()
                        peerConnection?.createAnswer(object : SdpObserver {
                            override fun onCreateSuccess(desc: SessionDescription?) {
                                if (desc != null) {
                                    peerConnection?.setLocalDescription(this, desc)
                                    val payload = mapOf(
                                        "type" to desc.type.canonicalForm(),
                                        "sdp" to desc.description,
                                        "to" to from
                                    )
                                    webSocket?.send(gson.toJson(payload))
                                }
                            }
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(e: String?) {}
                            override fun onSetFailure(e: String?) {}
                        }, constraints)
                    }
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onCreateFailure(e: String?) {}
                    override fun onSetFailure(e: String?) {}
                }, sdp)

                pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
                pendingCandidates.clear()
            }
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, data["sdp"] as String)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
                        pendingCandidates.clear()
                    }
                    override fun onCreateSuccess(desc: SessionDescription?) {}
                    override fun onCreateFailure(e: String?) {}
                    override fun onSetFailure(e: String?) {}
                }, sdp)
            }
            "ice" -> {
                val sdpMid = data["sdpMid"] as? String ?: return
                val sdpMLineIndex = (data["sdpMLineIndex"] as? Double)?.toInt() ?: return
                val candidateStr = data["candidate"] as? String ?: return
                
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
                if (peerConnection?.remoteDescription != null) {
                    peerConnection?.addIceCandidate(candidate)
                } else {
                    pendingCandidates.add(candidate)
                }
            }
        }
    }

    fun disposeWebRTC(userId: String?) {
        Log.d(TAG, "--- INICIO DISPOSE WEBRTC para $userId ---")
        try {
            // 1. Desactivar y liberar track de audio
            localAudioTrack?.let {
                Log.d(TAG, "Paso 1: Liberando AudioTrack...")
                it.setEnabled(false)
                it.dispose()
                Log.d(TAG, "Paso 1: OK")
            }
            localAudioTrack = null
            
            // 2. Liberar fuente de audio
            localAudioSource?.let {
                Log.d(TAG, "Paso 2: Liberando AudioSource...")
                it.dispose()
                Log.d(TAG, "Paso 2: OK")
            }
            localAudioSource = null
            
            // 3. Desactivar track en el stream
            localStream?.let { stream ->
                Log.d(TAG, "Paso 3: Limpiando tracks del stream...")
                stream.audioTracks?.forEach { 
                    try { 
                        it.setEnabled(false)
                        it.dispose() 
                    } catch (e: Exception) {
                        Log.e(TAG, "Error liberando track del stream: ${e.message}")
                    }
                }
                stream.dispose()
                Log.d(TAG, "Paso 3: OK")
            }
            localStream = null
            
            // 4. Cerrar y liberar PeerConnection
            peerConnection?.let {
                Log.d(TAG, "Paso 4: Cerrando PeerConnection...")
                it.close()
                it.dispose()
                Log.d(TAG, "Paso 4: OK")
            }
            peerConnection = null
            
            currentTargetUserId = null
            currentTargetUsername = null
            pendingCandidates.clear()
            
            // 5. Resetear audio manager para liberar micrófono
            try {
                Log.d(TAG, "Paso 5: Reseteando AudioManager...")
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                audioManager.mode = android.media.AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
                Log.d(TAG, "✅ Paso 5: AudioManager reseteado a NORMAL")
            } catch (e: Exception) {
                Log.e(TAG, "Error reseteando AudioManager: ${e.message}")
            }

            Log.d(TAG, "✅ WebRTC y recursos de audio liberados por completo")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error crítico al cerrar WebRTC: ${e.message}")
        }
        Log.d(TAG, "--- FIN DISPOSE WEBRTC ---")
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Llamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de llamadas entrantes"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showIncomingCallNotification(username: String, callerId: String, callId: String) {
        if (!Constants.NOTIFICATIONS_ENABLED) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("callId", callId)
            putExtra("fromId", callerId)
            putExtra("fromName", username)
            putExtra("screen", "CallingIncoming")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 Llamada entrante")
            .setContentText("$username está llamando...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)

        notificationManager.notify(0, builder.build())
    }

    private fun calculateRMS(data: ByteArray): Double {
        var sum = 0.0
        val shorts = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
        val count = shorts.remaining()
        if (count == 0) return 0.0
        
        while (shorts.hasRemaining()) {
            val sample = shorts.get().toDouble()
            sum += sample * sample
        }
        return Math.sqrt(sum / count)
    }
}
