package com.example.privox_app1

import android.content.Context
import android.util.Log

class AgoraVoiceChanger(private val context: Context) {

    private val logTag = "AgoraVoiceChanger"
    private var isInitialized = false

    fun initialize(appId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Mock initialization
        Log.d(logTag, "Mock Agora initialized with App ID: $appId")
        isInitialized = true
        onSuccess()
    }

    fun joinChannel(token: String?, onJoined: () -> Unit) {
        Log.d(logTag, "Mock joined channel with token: $token")
        onJoined()
    }

    fun leaveChannel() {
        Log.d(logTag, "Mock left channel")
    }

    fun pushAudioFrame(audioData: ShortArray, length: Int) {
        // Mock: do nothing, just log
        Log.d(logTag, "Mock pushed audio frame of length $length")
    }

    fun destroy() {
        Log.d(logTag, "Mock Agora destroyed")
        isInitialized = false
    }
}