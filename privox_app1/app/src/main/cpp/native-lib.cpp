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
    if (output == nullptr) return nullptr;

    // Bypass para factor neutro
    if (fabs(pitchFactor - 1.0f) < 0.01f) {
        env->SetShortArrayRegion(output, 0, length, raw.data());
        return output;
    }

    vector<jshort> outShort(length);
    const double dLen = static_cast<double>(length);

    if (pitchFactor > 1.0f) {
        // ── PITCH UP (SQUIRREL, FEMALE, ALIEN, PITCH) ───────────────────────
        // step = pitchFactor > 1: avanza rápido por el input → sube el tono.
        // Wrap-around circular: nunca produce silencio, el buffer siempre
        // contiene audio válido → buena inteligibilidad incluso hablando rápido.
        const double step = static_cast<double>(pitchFactor);
        // Longitud de la zona de crossfade alrededor del punto de wrap.
        // Suaviza el "click" que ocurre al reiniciar la lectura circular.
        const int FADE = 24;
        double readPos = 0.0;

        for (jsize i = 0; i < length; ++i) {
            double wrappedPos = fmod(readPos, dLen);
            int intPos  = static_cast<int>(wrappedPos);
            int nextPos = (intPos + 1) % length;
            double frac = wrappedPos - intPos;

            double s1 = static_cast<double>(raw[intPos])  / 32768.0;
            double s2 = static_cast<double>(raw[nextPos]) / 32768.0;
            double sample = s1 * (1.0 - frac) + s2 * frac;

            // Fade-out suave justo antes del wrap y fade-in después.
            // Esto elimina el pop/click en el punto de reinicio circular.
            double distToEnd   = dLen - wrappedPos;          // muestras hasta el wrap
            double distFromEnd = fmod(readPos, dLen);        // muestras desde el wrap
            double gain = 1.0;
            if (distToEnd < FADE)   gain = distToEnd   / FADE; // acercándose al wrap
            else if (distFromEnd < FADE && readPos >= dLen)     // justo tras el wrap
                gain = distFromEnd / FADE;

            outShort[i] = static_cast<jshort>(
                max(-32768.0, min(32767.0, sample * gain * 32767.0))
            );
            readPos += step;
        }

    } else {
        // ── PITCH DOWN (ROBOT, MAN, VOCODER) ────────────────────────────────
        // CORRECCIÓN: step = pitchFactor (< 1), NO 1/pitchFactor.
        //
        // Con step = pitchFactor < 1:
        //   • readPos avanza LENTO → leemos menos input por output sample
        //   • Los N samples de input se ESTIRAN para llenar el buffer completo
        //   • readPos final = length * pitchFactor < length → SIEMPRE dentro
        //     del rango → CERO silencios/zeros de relleno
        //   • Tono resultante: más GRAVE (período más largo en el output) ✓
        //
        // Con el bug anterior (step = 1/pitchFactor > 1):
        //   • readPos avanzaba RÁPIDO → se agotaba el input antes del final
        //   • El resto del buffer se rellenaba con ceros (silencio)
        //   • Esos gaps de silencio (~10-22% por buffer) destruían la
        //     inteligibilidad al hablar rápido: las consonantes caían en ellos
        const double step = static_cast<double>(pitchFactor);
        double readPos = 0.0;

        for (jsize i = 0; i < length; ++i) {
            int intPos  = static_cast<int>(readPos);
            double frac = readPos - intPos;

            // intPos + 1 siempre < length porque readPos máx = length * pitchFactor < length
            if (intPos + 1 < length) {
                double s1 = static_cast<double>(raw[intPos])     / 32768.0;
                double s2 = static_cast<double>(raw[intPos + 1]) / 32768.0;
                outShort[i] = static_cast<jshort>((s1 * (1.0 - frac) + s2 * frac) * 32767.0);
            } else {
                outShort[i] = raw[intPos < length ? intPos : length - 1];
            }
            readPos += step;
        }
    }

    env->SetShortArrayRegion(output, 0, length, outShort.data());
    return output;
}
