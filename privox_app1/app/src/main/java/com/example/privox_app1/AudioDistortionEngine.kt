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
import kotlin.math.sin

class AudioDistortionEngine {
    enum class DistortionMode(val label: String, val pitchFactor: Float) {
        NONE("Sin efecto", 1.0f),
        ROBOT("Robot", 0.7f),
        PITCH("Pitch", 1.3f),
        VOCODER("Vocoder", 0.85f),
        ALIEN("Alien", 1.45f),
        FEMALE("Mujer", 1.42f),
        MAN("Hombre", 0.78f),
        SQUIRREL("Ardilla", 1.70f)
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 1024
        init {
            System.loadLibrary("native-lib")
        }
    }

    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private val logTag = "VoiceChangerEngine"

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null
    
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneOn = false

    private val highPassState = HighPassState(80.0)
    private val bandPassState = BandPassState(300.0, 3000.0)
    private val robotState = RobotState()
    private val pitchState = PitchState()
    private val vocoderState = VocoderState()
    private val alienState = AlienState()
    
    external fun processPitchShift(input: ShortArray, pitchFactor: Float): ShortArray

    fun start(context: Context, mode: DistortionMode, onError: (String) -> Unit, onStatus: (String) -> Unit) {
        if (running.get()) return
        running.set(true)
        Log.d(logTag, "🚀 Iniciando motor de prueba local...")

        workerThread = Thread {
            try {
                val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager = manager
                previousAudioMode = manager.mode
                previousSpeakerphoneOn = manager.isSpeakerphoneOn
                
                manager.mode = AudioManager.MODE_NORMAL
                manager.isSpeakerphoneOn = false

                val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING).coerceAtLeast(FRAME_SIZE * 4)
                audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_IN)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build()

                val trackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING).coerceAtLeast(FRAME_SIZE * 4)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build())
                    .setBufferSizeInBytes(trackBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioRecord?.startRecording()
                audioTrack?.play()
                
                onStatus("Probando: ${mode.label}")
                val inputBuffer = ShortArray(FRAME_SIZE)
                val outputBuffer = ShortArray(FRAME_SIZE)

                while (running.get()) {
                    val read = audioRecord?.read(inputBuffer, 0, FRAME_SIZE) ?: -1
                    if (read > 0) {
                        processFrame(inputBuffer, outputBuffer, read, mode)
                        audioTrack?.write(outputBuffer, 0, read)
                    }
                }
            } catch (ex: Exception) {
                Log.e(logTag, "Error en el motor local", ex)
                onError(ex.message ?: "Error desconocido")
            } finally {
                stopInternal()
                onStatus("Detenido")
            }
        }
        workerThread?.start()
    }

    fun stop() {
        Log.d(logTag, "⏹️ Deteniendo motor local...")
        running.set(false)
        workerThread?.join(1000)
        stopInternal()
    }

    private fun stopInternal() {
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) stop()
                release()
                Log.d(logTag, "AudioRecord liberado")
            }
            audioRecord = null

            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                release()
                Log.d(logTag, "AudioTrack liberado")
            }
            audioTrack = null

            audioManager?.let { manager ->
                manager.mode = previousAudioMode
                manager.isSpeakerphoneOn = previousSpeakerphoneOn
            }
        } catch (e: Exception) {
            Log.e(logTag, "Error en stopInternal", e)
        }
    }

    fun processByteBuffer(buffer: java.nio.ByteBuffer, length: Int, mode: DistortionMode) {
        val shortsCount = length / 2
        val inputShorts = ShortArray(shortsCount)
        val outputShorts = ShortArray(shortsCount)
        val originalPosition = buffer.position()
        buffer.order(java.nio.ByteOrder.nativeOrder())
        buffer.asShortBuffer().get(inputShorts)
        
        processFrame(inputShorts, outputShorts, shortsCount, mode)
        
        buffer.position(originalPosition)
        buffer.asShortBuffer().put(outputShorts)
        buffer.position(originalPosition)
    }

    private fun processFrame(input: ShortArray, output: ShortArray, count: Int, mode: DistortionMode): ShortArray {
        val samples = DoubleArray(count)
        for (i in 0 until count) samples[i] = input[i].toDouble() / 32768.0

        when (mode) {
            DistortionMode.NONE -> { /* No hacer nada, se devuelve el input */ }
            else -> {
                // TODOS los estilos ahora usan el motor nativo de alta calidad como base
                val shifted = processPitchShift(input, mode.pitchFactor)
                for (i in 0 until count) samples[i] = shifted[i].toDouble() / 32768.0
                
                // Aplicar filtros específicos de carácter después del cambio de tono
                when (mode) {
                    DistortionMode.ROBOT -> robotState.apply(samples)
                    DistortionMode.ALIEN -> alienState.apply(samples)
                    DistortionMode.VOCODER -> vocoderState.apply(samples)
                    else -> formantCorrection(samples, mode)
                }
            }
        }

        for (i in 0 until count) {
            val s = (samples[i] * 32768.0).toInt().coerceIn(-32768, 32767)
            output[i] = s.toShort()
        }
        return output
    }

    private fun formantCorrection(samples: DoubleArray, mode: DistortionMode) {
        when (mode) {
            DistortionMode.SQUIRREL -> {
                // Paso 1: HighPass ligero para eliminar frecuencias bajas que
                // suenan graves/borrosas después del pitch-up.
                val hpFilter = HighPassState(120.0)
                hpFilter.apply(samples)

                // Paso 2: Soft-clip muy suave solo para evitar clipping duro.
                // Sin mezcla Dry/Wet: 100% señal procesada para máxima claridad.
                for (i in samples.indices) {
                    samples[i] = kotlin.math.tanh(samples[i] * 1.05) * 0.92
                }
            }
            DistortionMode.FEMALE -> {
                highPassState.apply(samples)
                for (i in samples.indices) {
                    val x = samples[i]
                    samples[i] = kotlin.math.tanh(x * 1.1)
                }
            }
            DistortionMode.MAN -> {
                for (i in samples.indices) {
                    val x = samples[i]
                    samples[i] = x * 0.9 + (kotlin.math.sin(x * 2.5) * 0.05)
                }
            }
            else -> {}
        }
    }

    private class HighPassState(cutoffHz: Double) {
        private val alpha: Double
        private var lastY = 0.0
        private var lastX = 0.0
        init {
            val rc = 1.0 / (2.0 * PI * cutoffHz)
            val dt = 1.0 / 48000.0
            alpha = rc / (rc + dt)
        }
        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                val x = samples[i]
                val y = alpha * (lastY + x - lastX)
                lastY = y
                lastX = x
                samples[i] = y
            }
        }
    }

    private class BandPassState(private val lowHz: Double, private val highHz: Double) {
        private val hp = HighPassState(lowHz)
        private var lastY = 0.0
        private val lpAlpha: Double
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
        private val frequency = 50.0 // Frecuencia más baja para un robot más profundo
        private val phaseIncrement = 2.0 * PI * frequency / 48000.0
        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                // Modulación de amplitud suave (Ring Modulation)
                val carrier = 0.7 + 0.3 * sin(phase)
                samples[i] *= carrier
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
    }

    private class PitchState {
        private var phase = 0.0
        private val frequency = 120.0
        private val phaseIncrement = 2.0 * PI * frequency / 48000.0
        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                val modulation = 0.8 + 0.2 * sin(phase)
                samples[i] *= modulation
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
    }

    private class VocoderState {
        private val bp = BandPassState(400.0, 3500.0) // Rango de voz clara
        fun apply(samples: DoubleArray) {
            bp.apply(samples)
            for (i in samples.indices) {
                // Bitcrushing ligero para efecto digital
                val x = samples[i]
                samples[i] = (kotlin.math.round(x * 16.0) / 16.0) * 0.9
            }
        }
    }

    private class AlienState {
        private val delayBuffer = DoubleArray(1024) // Buffer más corto para evitar eco molesto
        private var writeIndex = 0
        private var phase = 0.0
        private val phaseIncrement = 2.0 * PI * 1.5 / 48000.0
        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                val modulation = sin(phase)
                val delaySamples = (modulation * 20 + 10).toInt()
                val readIndex = (writeIndex - delaySamples + delayBuffer.size) % delayBuffer.size
                val delayed = delayBuffer[readIndex]
                delayBuffer[writeIndex] = samples[i]
                writeIndex = (writeIndex + 1) % delayBuffer.size
                
                // Mezcla de coro espacial
                samples[i] = (samples[i] * 0.7 + delayed * 0.3)
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
    }
}
