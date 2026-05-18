package com.futura.privox_app

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
    // wetMix: porcentaje del efecto procesado en la mezcla final.
    // El resto (1 - wetMix) es el audio pitch-shifted limpio, que ancla la
    // inteligibilidad de la voz sin eliminar el caracter del efecto.
    enum class DistortionMode(val label: String, val pitchFactor: Float, val wetMix: Double) {
        NONE("Sin efecto",  1.00f, 1.00),
        ROBOT("Robot",      0.80f, 0.72), // AM modulation: 28% de voz limpia mejora claridad
        PITCH("Pitch",      1.30f, 0.92),
        VOCODER("Vocoder",  0.85f, 0.68), // Bitcrush agresivo: 32% limpio restaura inteligibilidad
        ALIEN("Alien",      1.45f, 0.78), // Chorus: 22% limpio evita que suene "borroso"
        FEMALE("Mujer",     1.18f, 0.90),
        MAN("Hombre",       0.78f, 0.90),
        SQUIRREL("Ardilla", 1.70f, 0.88)
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE = 1024
        // Umbral del noise gate (~-40 dBFS).
        // Frames con RMS por debajo de este valor se pasan sin distorsion:
        // el ruido de habitacion no se distorsiona, solo la voz activa.
        const val NOISE_GATE_THRESHOLD = 0.010
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
    private val vocoderState = VocoderState()
    private val alienState = AlienState()
    // Estado stateful del HighPass de Squirrel: debe persistir entre frames para que
    // el filtro IIR no se reinicie en cada buffer (bug corregido).
    private val squirrelHpState = HighPassState(120.0)
    
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
            DistortionMode.NONE -> { /* bypass: sin efecto */ }
            else -> {
                // ── NOISE GATE ───────────────────────────────────────────────
                // Si el RMS del frame es menor al umbral, se devuelve el audio
                // original sin procesar: el ruido ambiente no se distorsiona.
                if (calculateRms(samples, count) < NOISE_GATE_THRESHOLD) {
                    for (i in 0 until count) output[i] = input[i]
                    return output
                }

                val shifted = processPitchShift(input, mode.pitchFactor)
                // pitchClean: audio con pitch-shift pero sin filtros de caracter.
                // Es la referencia "limpia" para la mezcla de inteligibilidad.
                val pitchClean = DoubleArray(count) { shifted[it].toDouble() / 32768.0 }
                for (i in 0 until count) samples[i] = pitchClean[i]

                // ── FILTROS DE CARACTER ──────────────────────────────────────
                when (mode) {
                    DistortionMode.ROBOT   -> robotState.apply(samples)
                    DistortionMode.ALIEN   -> alienState.apply(samples)
                    DistortionMode.VOCODER -> vocoderState.apply(samples)
                    else                   -> formantCorrection(samples, mode)
                }

                // ── MEZCLA DRY/WET ───────────────────────────────────────────
                // Combina el efecto procesado (wet) con el pitch-shift limpio (dry).
                // Preserva la inteligibilidad de la voz sin perder el estilo.
                val wet = mode.wetMix
                val dry = 1.0 - wet
                for (i in 0 until count) {
                    samples[i] = samples[i] * wet + pitchClean[i] * dry
                }
            }
        }

        for (i in 0 until count) {
            val s = (samples[i] * 32768.0).toInt().coerceIn(-32768, 32767)
            output[i] = s.toShort()
        }
        return output
    }

    // Calcula el RMS (Root Mean Square) del frame normalizado en [-1, 1].
    // Usado por el noise gate para detectar actividad de voz.
    private fun calculateRms(samples: DoubleArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) sum += samples[i] * samples[i]
        return kotlin.math.sqrt(sum / count)
    }

    private fun formantCorrection(samples: DoubleArray, mode: DistortionMode) {
        when (mode) {
            DistortionMode.SQUIRREL -> {
                // HighPass stateful (instancia de clase) → mantiene estado IIR
                // entre frames consecutivos, evitando la reinicialización por buffer.
                squirrelHpState.apply(samples)

                // Soft-clip suave para evitar clipping duro sin distorsionar.
                for (i in samples.indices) {
                    samples[i] = kotlin.math.tanh(samples[i] * 1.05) * 0.92
                }
            }
            DistortionMode.FEMALE -> {
                // HighPass a 80 Hz: elimina graves excesivos tras el pitch-up.
                highPassState.apply(samples)

                // LP suavizado con alpha=0.50 → corte ~3820 Hz.
                // Anterior (alpha=0.08 → corte ~611 Hz) destruía las consonantes
                // sibilantes (s, t, f, ch) haciendo la voz ininteligible.
                var prev = if (samples.isNotEmpty()) samples[0] else 0.0
                for (i in samples.indices) {
                    prev += 0.50 * (samples[i] - prev)
                    samples[i] = prev * 0.92
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
        // 120Hz: crea el buzz metálico clásico de robot (tipo HAL-9000).
        // 50Hz era demasiado lento — solo generaba un tremolo audible, no robot.
        private val frequency = 120.0
        private val phaseIncrement = 2.0 * PI * frequency / 48000.0
        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                // AM con profundidad 0.65+0.35: suficiente carácter mecánico
                // sin destruir la inteligibilidad de las palabras.
                val carrier = 0.65 + 0.35 * sin(phase)
                samples[i] *= carrier
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
        // Buffer de 2048 muestras → soporta hasta ~42 ms a 48 kHz.
        // Anterior (1024 con delay 10-30 samples = 0.2-0.6 ms) producía flanger,
        // no chorus. Un chorus real requiere delays de 15-30 ms (720-1440 samples).
        private val delayBuffer = DoubleArray(2048)
        private var writeIndex = 0
        private var phase = 0.0
        // LFO a 0.3 Hz: modulación lenta y suave, más espacial y menos mareante.
        private val phaseIncrement = 2.0 * PI * 0.3 / 48000.0
        fun apply(samples: DoubleArray) {
            for (i in samples.indices) {
                val modulation = sin(phase)
                // Delay oscila entre 720 y 1440 muestras (15–30 ms): rango real de chorus.
                val delaySamples = (modulation * 360 + 1080).toInt().coerceIn(1, delayBuffer.size - 1)
                val readIndex = (writeIndex - delaySamples + delayBuffer.size) % delayBuffer.size
                val delayed = delayBuffer[readIndex]
                delayBuffer[writeIndex] = samples[i]
                writeIndex = (writeIndex + 1) % delayBuffer.size

                // Mezcla chorus: 70% directo + 30% delayed
                samples[i] = samples[i] * 0.7 + delayed * 0.3
                phase += phaseIncrement
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
        }
    }
}
