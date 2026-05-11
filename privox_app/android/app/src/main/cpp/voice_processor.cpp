#include <jni.h>
#include <vector>
#include <cmath>
#include <android/log.h>

const double M_PI_VAL = 3.14159265358979323846;

#define LOG_TAG "VoiceProcessorDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT void JNICALL
Java_com_example_ghox_1app_RobotVoiceProcessor_processPitchShiftNative(
        JNIEnv* env,
        jobject /* this */,
        jobject buffer,
        jint numBands,
        jint numFrames,
        jfloat pitchFactor) {
    
    jshort* raw = static_cast<jshort*>(env->GetDirectBufferAddress(buffer));
    if (!raw) return;

    static int frame_counter = 0;

    // === FASE 1: Auditoría de Memoria y Logs ===
    // Imprimimos logs solo 1 vez por segundo (cada 100 frames) para no saturar la consola
    if (frame_counter % 100 == 0) {
        // Calcular la Amplitud (Volumen RMS) de todo el buffer entrante
        double sum_squares = 0.0;
        int total_samples = numBands * numFrames;
        
        for(int i = 0; i < total_samples; i++) {
            double sample = static_cast<double>(raw[i]) / 32768.0;
            sum_squares += (sample * sample);
        }
        double rms = sqrt(sum_squares / total_samples);
        
        LOGI("=== DIAGNOSTICO DE AUDIO ===");
        LOGI("numBands (Canales o Bandas): %d", numBands);
        LOGI("numFrames (Muestras de tiempo): %d", numFrames);
        LOGI("Energia RMS (Volumen detectado): %f", rms);
        LOGI("============================");
    }
    // === FASE DEFINITIVA: SCRAMBLER MILITAR (Inversión de Frecuencia) ===
    // En lugar de estirar el tiempo (lo cual causaba clicks en los bordes de los 10ms),
    // invertimos el espectro de la voz multiplicándola por una onda de 2000Hz.
    // Esto convierte la voz en algo anónimo (estilo radio militar) sin cortes de memoria.
    
    static double carrier_phase = 0.0;
    double carrier_freq = 2000.0; 
    double increment = 2.0 * M_PI_VAL * carrier_freq / 16000.0;

    for (int i = 0; i < numFrames; ++i) {
        double dry = static_cast<double>(raw[i]) / 32768.0;
        
        // Multiplicar por la portadora invierte el sonido (convierte graves en agudos y viceversa)
        double scrambled = dry * cos(carrier_phase);

        // Mezclamos: 70% Voz Anónima (Scrambler) + 30% Voz Natural (Para garantizar que se entiendan las palabras)
        double processed = (scrambled * 0.7) + (dry * 0.3);

        // Aumentamos el volumen un 50% para que no se escuche bajito
        processed *= 1.5;

        // Soft-Clipper para proteger el altavoz
        if (processed > 1.0) processed = 1.0;
        if (processed < -1.0) processed = -1.0;

        raw[i] = static_cast<jshort>(processed * 32767.0);

        carrier_phase += increment;
        if (carrier_phase > 2.0 * M_PI_VAL) {
            carrier_phase -= 2.0 * M_PI_VAL;
        }
    }

    frame_counter++;
}
