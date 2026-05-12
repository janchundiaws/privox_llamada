package com.example.privox_app1

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

class AudioDistortionEngine {
    enum class DistortionMode(val label: String, val pitchFactor: Float) {
        NONE("Sin efecto", 1.0f),
        ROBOT("Robot", 0.85f),
        PITCH("Pitch", 1.15f),
        VOCODER("Vocoder", 0.9f),
        ALIEN("Alien", 1.25f),
        FEMALE("Mujer", 1.15f),
        MAN("Hombre", 0.85f)
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 1024
        const val PREVIEW_BUFFER = 2048
        init {
            System.loadLibrary("native-lib")
        }
    }

    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var frameCounter = 0
    private val logTag = "VoiceChangerEngine"

    private val audioRecord: AudioRecord by lazy {
        AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_IN)
                    .build()
            )
            .setBufferSizeInBytes(AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(FRAME_SIZE * 4))
            .build()
    }

    private val audioTrack: AudioTrack by lazy {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .build()
            )
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING).coerceAtLeast(FRAME_SIZE * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneOn = false
    private var audioManager: AudioManager? = null

    private val highPassState = HighPassState(80.0)
    private val bandPassState = BandPassState(300.0, 3000.0)
    private val robotState = RobotState()
    private val pitchState = PitchState()
    private val vocoderState = VocoderState()
    private val alienState = AlienState()

    // Native method for STFT Phase Vocoder pitch shift
    external fun processPitchShift(input: ShortArray, pitchFactor: Float): ShortArray

    private class HighPassState(cutoffHz: Double) {
        private val alpha: Double
        private var previous = 0.0

        init {
            val rc = 1.0 / (2.0 * PI * cutoffHz)
            val dt = 1.0 / 48000.0
            alpha = rc / (rc + dt)
        }

        fun apply(samples: DoubleArray) {
            var lastY = 0.0
            for (i in samples.indices) {
                val x = samples[i]
                val y = alpha * (lastY + x - previous)
                samples[i] = y
                previous = x
                lastY = y
            }
        }
    }

    private class BandPassState(private val lowHz: Double, private val highHz: Double) {
        private val hp = HighPassState(lowHz)
        private val lpAlpha: Double
        private var lastY = 0.0

        init {
            val rc = 1.0 / (2.0 * PI * highHz)
            val dt = 1.0 / 48000.0
            lpAlpha = dt / (rc + dt)
        }

        fun apply(samples: DoubleArray) {
            hp.apply(samples)
            for (i in samples.indices) {
                val x = samples[i]
                lastY += lpAlpha * (x - lastY)
                samples[i] = lastY
            }
        }
    }

    private class RobotState {
        private var phase = 0.0
        private val frequency = 60.0
        private val phaseIncrement = 2.0 * PI * frequency / 48000.0

        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                val rectified = abs(samples[i])
                val carrier = 0.6 + 0.4 * sin(phase)
                samples[i] = rectified * carrier
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
    }

    private class PitchState {
        fun apply(samples: DoubleArray) {
            // Placeholder pitch effect
            for (i in samples.indices) {
                samples[i] *= 1.1
            }
        }
    }

    private class VocoderState {
        private var phase = 0.0
        private val carrierFrequency = 120.0
        private val phaseIncrement = 2.0 * PI * carrierFrequency / 48000.0
        private var envelope = 0.0

        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                envelope = 0.85 * envelope + 0.15 * abs(samples[i])
                val carrier = sin(phase)
                samples[i] = envelope * carrier * 1.3
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
    }

    private class AlienState {
        private val delayBuffer = DoubleArray(2048)
        private var writeIndex = 0
        private var phase = 0.0
        private val frequency = 1.8
        private val phaseIncrement = 2.0 * PI * frequency / 48000.0

        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                val modulation = (sin(phase) + 1.0) * 0.5
                val delaySamples = (modulation * 30 + 15).toInt()
                val readIndex = (writeIndex - delaySamples + delayBuffer.size) % delayBuffer.size
                val delayed = delayBuffer[readIndex]
                delayBuffer[writeIndex] = samples[i]
                writeIndex = (writeIndex + 1) % delayBuffer.size
                val chorus = (samples[i] * 0.65 + delayed * 0.35)
                samples[i] = chorus * (0.9 + modulation * 0.2)
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
    }

    fun start(context: Context, mode: DistortionMode, onError: (String) -> Unit, onStatus: (String) -> Unit) {
        if (running.get()) return
        running.set(true)
        Log.d(logTag, "start() called. mode=${mode.label}")
        workerThread = Thread {
            try {
                val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager = manager
                previousAudioMode = manager.mode
                previousSpeakerphoneOn = manager.isSpeakerphoneOn
                manager.mode = AudioManager.MODE_NORMAL
                manager.isSpeakerphoneOn = false
                Log.d(logTag, "audioManager mode=${manager.mode}, speakerOn=${manager.isSpeakerphoneOn}, streamMusicVolume=${manager.getStreamVolume(AudioManager.STREAM_MUSIC)}")

                val recordStateBefore = audioRecord.state
                val trackStateBefore = audioTrack.state
                Log.d(logTag, "AudioRecord state before start=$recordStateBefore, AudioTrack state before start=$trackStateBefore")

                audioRecord.startRecording()
                audioTrack.play()
                audioTrack.setVolume(1.0f)
                Log.d(logTag, "AudioRecord recordingState=${audioRecord.recordingState}, AudioTrack playState=${audioTrack.playState}")
                onStatus("Grabando con efecto: ${mode.label}")
                val inputBuffer = ShortArray(FRAME_SIZE)
                val outputBuffer = ShortArray(FRAME_SIZE)

                while (running.get()) {
                    val read = audioRecord.read(inputBuffer, 0, FRAME_SIZE, AudioRecord.READ_BLOCKING)
                    if (read <= 0) {
                        Log.d(logTag, "read returned $read")
                        continue
                    }

                    val inputRms = calculateRms(inputBuffer, read)
                    val processedBuffer = processFrame(inputBuffer, outputBuffer, read, mode)
                    val outputRms = calculateRms(processedBuffer, read)
                    if (++frameCounter % 20 == 0) {
                        Log.d(logTag, "frame=$frameCounter read=$read inputRms=$inputRms outputRms=$outputRms")
                    }

                    // Local playback for verification
                    val written = audioTrack.write(processedBuffer, 0, read, AudioTrack.WRITE_BLOCKING)
                    if (written != read) {
                        Log.d(logTag, "audioTrack.write wrote=$written expected=$read")
                    }
                }
            } catch (ex: Exception) {
                Log.e(logTag, "Error in audio loop", ex)
                onError(ex.message ?: "Error desconocido en el motor de audio")
            } finally {
                stopInternal()
                onStatus("Detenido")
            }
        }
        workerThread?.start()
    }

    fun stop() {
        running.set(false)
        workerThread?.join()
    }

    fun processByteBuffer(buffer: java.nio.ByteBuffer, length: Int, mode: DistortionMode) {
        // 16-bit PCM: 2 bytes per sample
        val shortsCount = length / 2
        val inputShorts = ShortArray(shortsCount)
        val outputShorts = ShortArray(shortsCount)
        
        // Save current position and limit
        val originalPosition = buffer.position()
        
        // Read from ByteBuffer
        buffer.order(java.nio.ByteOrder.nativeOrder())
        buffer.asShortBuffer().get(inputShorts)
        
        // Process
        processFrame(inputShorts, outputShorts, shortsCount, mode)
        
        // Write back to ByteBuffer
        buffer.position(originalPosition)
        buffer.asShortBuffer().put(outputShorts)
        buffer.position(originalPosition)
    }

    private fun stopInternal() {
        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop()
        }
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause()
            audioTrack.flush()
        }
        audioManager?.let { manager ->
            manager.isSpeakerphoneOn = previousSpeakerphoneOn
            manager.mode = previousAudioMode
            Log.d(logTag, "audioManager restored mode=${manager.mode}, speakerOn=${manager.isSpeakerphoneOn}")
        }
    }

    internal fun processFrame(
        input: ShortArray,
        output: ShortArray,
        length: Int,
        mode: DistortionMode,
    ): ShortArray {
        val samples = DoubleArray(length)
        for (i in 0 until length) {
            samples[i] = input[i] / 32768.0
        }

        // Pipeline: Noise suppression -> VAD -> STFT Pitch Shift -> Formant correction
        noiseSuppression(samples)
        if (!voiceActivityDetection(samples)) {
            // If no voice, pass through
            for (i in 0 until length) {
                output[i] = input[i]
            }
            return output
        }

        if (mode != DistortionMode.NONE) {
            stftPitchShift(samples, mode.pitchFactor)
            formantCorrection(samples)
        }

        // Convert back to ShortArray
        for (i in 0 until length) {
            val clamped = samples[i].coerceIn(-1.0, 1.0)
            output[i] = (clamped * 32767.0).toInt().toShort()
        }
        return output
    }

    private fun noiseSuppression(samples: DoubleArray) {
        // Simple noise gate as placeholder
        val threshold = 0.01
        for (i in samples.indices) {
            if (abs(samples[i]) < threshold) {
                samples[i] *= 0.1
            }
        }
    }

    private fun voiceActivityDetection(samples: DoubleArray): Boolean {
        val rms = kotlin.math.sqrt(samples.map { it * it }.average())
        return rms > 0.02 // Simple threshold
    }

    private fun stftPitchShift(samples: DoubleArray, pitchFactor: Float) {
        if (kotlin.math.abs(pitchFactor - 1.0f) < 0.01f) return
        val shortSamples = ShortArray(samples.size)
        for (i in samples.indices) {
            shortSamples[i] = (samples[i] * 32767.0).toInt().toShort()
        }
        val shifted = processPitchShift(shortSamples, pitchFactor)
        if (shifted.size != samples.size) {
            return
        }
        for (i in samples.indices) {
            samples[i] = shifted[i] / 32768.0
        }
    }

    private fun formantCorrection(samples: DoubleArray) {
        // Placeholder: simple filter
        bandPassState.apply(samples)
    }

    private fun calculateRms(samples: ShortArray, length: Int): Double {
        if (length <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until length) {
            val normalized = samples[i] / 32768.0
            sum += normalized * normalized
        }
        return kotlin.math.sqrt(sum / length)
    }
}
