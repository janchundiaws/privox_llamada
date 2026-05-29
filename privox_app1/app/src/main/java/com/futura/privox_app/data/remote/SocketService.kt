package com.futura.privox_app.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.futura.privox_app.IncomingCallService
import com.futura.privox_app.MainActivity
import com.futura.privox_app.utils.CryptoManager.encrypt
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

        // Se actualiza desde MainActivity.onStart / onStop.
        // Cuando la app está en primer plano la UI ya navega a CallingIncoming
        // via _events, por lo que la notificación sería redundante y al tocarla
        // generaría un segundo onNewIntent() causando doble navegación.
        @Volatile
        var isAppInForeground: Boolean = false

        // IDs fijos para notificaciones. Solo puede existir una llamada activa a la vez,
        // por lo que IDs constantes son más seguros que callId.hashCode() (puede colisionar).
        private const val CALL_NOTIFICATION_ID = 1001
        private const val CALL_CANCELLED_NOTIFICATION_ID = 1002
    }

    private val TAG = "SocketService"
    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "call_channel"
    // Indica si la notificación de llamada entrante está actualmente visible.
    // internal: necesario para que MainActivity pueda verificar el estado antes
    // de llamar a cancelNotificationOnForeground() en onStart().
    internal var incomingCallNotificationShowing = false

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

    private val _iceConnectionState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val iceConnectionState = _iceConnectionState.asStateFlow()
    
    private var reconnectionJob: Job? = null

    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val usersCache = mutableMapOf<String, String>()
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val rootEglBase: EglBase = EglBase.create()

    private val prefs: SharedPreferences = context.getSharedPreferences("privox_prefs", Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // 1. Inicialización global única de WebRTC
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
            Log.d(TAG, "WebRTC Factory inicializada globalmente")
        } catch (e: Exception) {
            Log.e(TAG, "Error en inicialización global de WebRTC: ${e.message}")
        }
        createNotificationChannel()
    }

    @Volatile
    var currentDistortionMode = com.futura.privox_app.AudioDistortionEngine.DistortionMode.ROBOT
    
    @Volatile
    var isDistortionEnabled: Boolean = false
        set(value) {
            field = value
            Log.d("SocketService", "Distorsión activada: $value")
        }
    private val distortionEngine = com.futura.privox_app.AudioDistortionEngine()

    private fun ensureFactoryInitialized() {
        if (peerConnectionFactory != null) return
        
        Log.d(TAG, "🏗️ Creando nueva Factory y ADM...")
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
        Log.d(TAG, "✅ Factory y ADM creados")
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
        try{
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
                val thread = Thread.currentThread().name
                val fg = isAppInForeground
                Log.d(TAG, "🔌 [WS-OPEN] hilo=$thread | foreground=$fg | code=${response.code} | url=${response.request.url}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = gson.fromJson(text, Map::class.java) as Map<String, Any?>
                    val msgType = data["type"] as? String ?: "unknown"
                    val thread = Thread.currentThread().name
                    val fg = isAppInForeground
                    Log.d(TAG, "📨 [WS-MSG] tipo=$msgType | hilo=$thread | foreground=$fg | connected=${isConnected.value}")
                    coroutineScope.launch {
                        // For incoming calls, pre-fetch username to avoid UI lag
                        if (data["type"] == "incoming-call") {
                            val fromId = data["from"] as? String
                            Log.d(TAG, "📞 [WS-MSG incoming-call] fromId=$fromId | foreground=$fg")
                            if (fromId != null) {
                                val username = getUsernameById(fromId)
                                Log.d(TAG, "📞 [WS-MSG incoming-call] username resuelto=$username")
                                val mutableData = data.toMutableMap()
                                mutableData["fromUsername"] = username
                                _events.emit(mutableData)
                                handleIncomingMessage(mutableData)
                                return@launch
                            } else {
                                Log.w(TAG, "⚠️ [WS-MSG incoming-call] fromId es NULL — datos incompletos del servidor: $data")
                            }
                        }
                        _events.emit(data)
                        handleIncomingMessage(data)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [WS-MSG] Error parseando mensaje: $e | texto=$text")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _isConnected.value = false
                isConnecting = false
                message = "❌ Desconectado del servidor"
                val thread = Thread.currentThread().name
                val fg = isAppInForeground
                // code 1000 = cierre normal; otros = cierre anormal
                Log.w(TAG, "🔒 [WS-CLOSED] code=$code | reason='$reason' | hilo=$thread | foreground=$fg")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _isConnected.value = false
                isConnecting = false
                message = "❌ Error en la conexión: ${t.message}"
                val thread = Thread.currentThread().name
                val fg = isAppInForeground
                val causeMsg  = t.cause?.message ?: "sin causa"
                val causeType = t.cause?.javaClass?.simpleName ?: "N/A"
            }
        })

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error janchundia: ${e}")
        }

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

    // --- CHAT METHODS ---

    fun sendChatMessage(toUserId: String, content: String) {
        val payload = mapOf(
            "type" to "chat-message",
            "to" to toUserId,
            "content" to content
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun sendChatDeliveredAck(messageId: String, fromUserId: String) {
        val payload = mapOf(
            "type" to "chat-delivered-ack",
            "messageId" to messageId,
            "from" to fromUserId
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun sendChatRead(messageId: String, fromUserId: String) {
        val payload = mapOf(
            "type" to "chat-read",
            "messageId" to messageId,
            "from" to fromUserId
        )
        webSocket?.send(gson.toJson(payload))
    }

    fun sendChatTyping(toUserId: String, isTyping: Boolean) {
        val payload = mapOf(
            "type" to "chat-typing",
            "to" to toUserId,
            "isTyping" to isTyping
        )
        webSocket?.send(gson.toJson(payload))
    }

    // --- END CHAT METHODS ---

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
                // Llamante canceló antes de que aceptemos.
                val fromId = (data["from"] as? String) ?: ""
                val callerName = usersCache[fromId] ?: "El llamante"
                val wasWaiting = !isAppInForeground && incomingCallNotificationShowing
                cancelIncomingCallNotification()
                if (wasWaiting) showCallCancelledNotification(callerName)
                message = "❌ Llamada cancelada por $callerName"
            }
            "hangup" -> {
                // Puede ser colgado antes de aceptar (desde fondo) o durante la llamada.
                val fromId = (data["from"] as? String) ?: ""
                val callerName = usersCache[fromId] ?: "El llamante"
                val wasWaiting = !isAppInForeground && incomingCallNotificationShowing
                cancelIncomingCallNotification()
                if (wasWaiting) showCallCancelledNotification(callerName)
                // MainActivity maneja la navegación y liberación de recursos
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
            "chat-message" -> {
                // Auto-enviar ACK de entrega al recibir un mensaje
                val messageId = data["messageId"] as? String ?: ""
                val fromUserId = data["from"] as? String ?: ""
                if (messageId.isNotEmpty() && fromUserId.isNotEmpty()) {
                    sendChatDeliveredAck(messageId, fromUserId)
                }
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
        
        // 2. Asegurar que la Factory existe para esta llamada
        ensureFactoryInitialized()
        
        // 3. Configurar el modo de audio para comunicación
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "AudioManager configurado para comunicación")
        } catch (e: Exception) {
            Log.e(TAG, "Error configurando AudioManager: ${e.message}")
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
                    state?.let { 
                        _iceConnectionState.value = it 
                        handleReconnectionTimeout(it)
                    }
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

    private fun handleReconnectionTimeout(state: PeerConnection.IceConnectionState) {
        when (state) {
            PeerConnection.IceConnectionState.DISCONNECTED,
            PeerConnection.IceConnectionState.FAILED -> {
                if (reconnectionJob == null) {
                    Log.w(TAG, "⚠️ Conexión inestable. Iniciando temporizador de 15s para colgar...")
                    reconnectionJob = coroutineScope.launch {
                        delay(15000)
                        Log.e(TAG, "❌ Tiempo de reconexión agotado (15s). Colgando llamada...")
                        
                        // Notificar a la UI para que salga de la pantalla de llamada
                        val payload = mapOf(
                            "type" to "hangup",
                            "from" to (currentTargetUserId ?: "unknown")
                        )
                        _events.emit(payload)
                        
                        // Limpiar recursos
                        disposeWebRTC(currentTargetUserId)
                    }
                }
            }
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                if (reconnectionJob != null) {
                    Log.i(TAG, "✅ Conexión recuperada. Cancelando temporizador de colgado.")
                    reconnectionJob?.cancel()
                    reconnectionJob = null
                }
            }
            else -> {}
        }
    }

    fun disposeWebRTC(userId: String?) {
        coroutineScope.launch {
            val threadName = Thread.currentThread().name
            Log.d(TAG, "[$threadName] --- INICIO DEEP DISPOSE para $userId ---")
            try {
                // 1. Detener motor de distorsión por si acaso
                try { distortionEngine.stop() } catch (e: Exception) { Log.e(TAG, "Error stop engine: ${e.message}") }
                
                // 2. Liberar tracks y streams
                try {
                    localAudioTrack?.let {
                        Log.d(TAG, "[$threadName] Liberando AudioTrack...")
                        it.setEnabled(false)
                        it.dispose()
                    }
                } catch (e: Exception) { Log.e(TAG, "Error dispose track: ${e.message}") }
                localAudioTrack = null
                
                try {
                    localAudioSource?.let {
                        Log.d(TAG, "[$threadName] Liberando AudioSource...")
                        it.dispose()
                    }
                } catch (e: Exception) { Log.e(TAG, "Error dispose source: ${e.message}") }
                localAudioSource = null
                
                try {
                    localStream?.let {
                        Log.d(TAG, "[$threadName] Liberando Stream...")
                        it.dispose()
                    }
                } catch (e: Exception) { Log.e(TAG, "Error dispose stream: ${e.message}") }
                localStream = null
                
                // 3. Cerrar conexión
                try {
                    peerConnection?.let {
                        Log.d(TAG, "[$threadName] Cerrando PeerConnection...")
                        it.close()
                        it.dispose()
                    }
                } catch (e: Exception) { Log.e(TAG, "Error dispose PC: ${e.message}") }
                peerConnection = null
                
                // 4. DESTUIR FACTORY (Esto garantiza que el ADM/Micrófono se liberen)
                try {
                    peerConnectionFactory?.let {
                        Log.d(TAG, "[$threadName] Destruyendo PeerConnectionFactory...")
                        it.dispose()
                    }
                } catch (e: Exception) { Log.e(TAG, "Error dispose factory: ${e.message}") }
                peerConnectionFactory = null
                
                currentTargetUserId = null
                currentTargetUsername = null
                pendingCandidates.clear()
                
                // 5. Resetear AudioManager
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    audioManager.mode = android.media.AudioManager.MODE_NORMAL
                    audioManager.isSpeakerphoneOn = false
                    Log.d(TAG, "✅ [$threadName] AudioManager reseteado a NORMAL")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reseteando AudioManager: ${e.message}")
                }

                reconnectionJob?.cancel()
                reconnectionJob = null
                
                _iceConnectionState.value = PeerConnection.IceConnectionState.NEW
                Log.d(TAG, "✅ [$threadName] Liberación total completada")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [$threadName] Error general en deep dispose: ${e.message}")
            }
            Log.d(TAG, "--- FIN DEEP DISPOSE ---")
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ringtoneUri = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_RINGTONE
            )
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Llamadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de llamadas entrantes"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                setSound(ringtoneUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showIncomingCallNotification(username: String, callerId: String, callId: String) {
        // Si la app está en primer plano, _events ya ha navegado la UI a CallingIncoming.
        // Mostrar la notificación sería redundante y al tocarla generaría un segundo
        // onNewIntent() causando una doble navegación hacia la pantalla de llamada.
        if (isAppInForeground) {
            Log.d(TAG, "📱 App en primer plano: omitiendo notificación para $username")
            return
        }

        // Iniciar el Foreground Service: esto llama a startForeground() internamente,
        // lo que le indica al SO que el proceso tiene una operación visible para el
        // usuario (la llamada) y NO debe matar la red bajo Doze Mode.
        IncomingCallService.start(context, username, callId, callerId)
        incomingCallNotificationShowing = true
        Log.d(TAG, "🟢 IncomingCallService iniciado — socket protegido de Doze Mode")
    }

    // Cancela la notificación de llamada entrante si estaba visible.
    // Idempotente: si la notificación no existía, no hace nada.
    private fun cancelIncomingCallNotification() {
        if (incomingCallNotificationShowing) {
            // Detener el ForegroundService: esto retira la notificación y
            // libera al proceso de la protección de Doze Mode (ya no es necesaria).
            IncomingCallService.stop(context)
            incomingCallNotificationShowing = false
            Log.d(TAG, "🔕 IncomingCallService detenido y notificación cancelada")
        }
    }

    /**
     * Llamado desde MainActivity.onStart() cuando la app vuelve a primer plano
     * con una notificación de llamada entrante activa.
     * Detiene el IncomingCallService (y su notificación) sin mostrar
     * "llamada cancelada" — el usuario ya está en la app viendo CallingIncoming.
     */
    fun cancelNotificationOnForeground() {
        if (incomingCallNotificationShowing) {
            IncomingCallService.stop(context)
            incomingCallNotificationShowing = false
            Log.d(TAG, "🔕 Notificación eliminada al entrar a primer plano")
        }
    }

    // Muestra una notificación informativa de que el llamante canceló la llamada.
    // Solo se invoca cuando la app está en segundo plano y había una notificación activa.
    private fun showCallCancelledNotification(callerName: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📵 Llamada cancelada")
            .setContentText("$callerName canceló la llamada")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        notificationManager.notify(CALL_CANCELLED_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "Notificación de llamada cancelada mostrada para $callerName")
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
