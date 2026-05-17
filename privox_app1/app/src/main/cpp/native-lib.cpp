#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cmath>

using namespace std;

extern "C" JNIEXPORT jshortArray JNICALL
Java_com_example_privox_1app1_AudioDistortionEngine_processPitchShift(
        JNIEnv* env,
        jobject /* this */,
        jshortArray input,
        jfloat pitchFactor) {

    jsize length = env->GetArrayLength(input);
    vector<jshort> raw(length);
    env->GetShortArrayRegion(input, 0, length, raw.data());

    jshortArray output = env->NewShortArray(length);
    if (output == nullptr) {
        return nullptr;
    }

    // Bypass para factor neutro
    if (fabs(pitchFactor - 1.0f) < 0.01f) {
        env->SetShortArrayRegion(output, 0, length, raw.data());
        return output;
    }

    vector<jshort> outShort(length);

    if (pitchFactor > 1.0f) {
        // ── PITCH UP (e.g. SQUIRREL) ────────────────────────────────────────
        // step > 1: avanzamos rápido por el input → voz más aguda.
        // Usamos wrap-around circular para no generar silencios cuando
        // agotamos el buffer original.
        double step     = static_cast<double>(pitchFactor);
        double readPos  = 0.0;

        for (jsize i = 0; i < length; ++i) {
            // Posición circular dentro del input
            double wrappedPos = fmod(readPos, static_cast<double>(length));
            int intPos  = static_cast<int>(wrappedPos);
            int nextPos = (intPos + 1) % length;
            double frac = wrappedPos - intPos;

            double s1 = static_cast<double>(raw[intPos])  / 32768.0;
            double s2 = static_cast<double>(raw[nextPos]) / 32768.0;
            double interpolated = s1 * (1.0 - frac) + s2 * frac;

            outShort[i] = static_cast<jshort>(
                max(-32768.0, min(32767.0, interpolated * 32767.0))
            );
            readPos += step;
        }
    } else {
        // ── PITCH DOWN (ROBOT, MAN, VOCODER, etc.) ──────────────────────────
        // step < 1: avanzamos lento por el input → estiramos → voz más grave.
        // Algoritmo original sin cambios.
        double step    = 1.0 / static_cast<double>(pitchFactor);
        double readPos = 0.0;

        for (jsize i = 0; i < length; ++i) {
            int intPos = static_cast<int>(readPos);
            double frac = readPos - intPos;

            if (intPos + 1 < length) {
                double s1 = static_cast<double>(raw[intPos])     / 32768.0;
                double s2 = static_cast<double>(raw[intPos + 1]) / 32768.0;
                double interpolated = s1 * (1.0 - frac) + s2 * frac;
                outShort[i] = static_cast<jshort>(interpolated * 32767.0);
            } else if (intPos < length) {
                outShort[i] = raw[intPos];
            } else {
                outShort[i] = 0;
            }
            readPos += step;
        }
    }

    env->SetShortArrayRegion(output, 0, length, outShort.data());
    return output;
}
