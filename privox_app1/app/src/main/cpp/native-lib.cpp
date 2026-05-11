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

    if (fabs(pitchFactor - 1.0f) < 0.01f) {
        env->SetShortArrayRegion(output, 0, length, raw.data());
        return output;
    }

    vector<jshort> outShort(length);
    double readPos = 0.0;
    double step = 1.0 / static_cast<double>(pitchFactor);

    for (jsize i = 0; i < length; ++i) {
        int intPos = static_cast<int>(readPos);
        double frac = readPos - intPos;

        if (intPos + 1 < length) {
            double sample1 = static_cast<double>(raw[intPos]) / 32768.0;
            double sample2 = static_cast<double>(raw[intPos + 1]) / 32768.0;
            double interpolated = sample1 * (1.0 - frac) + sample2 * frac;
            outShort[i] = static_cast<jshort>(interpolated * 32767.0);
        } else if (intPos < length) {
            outShort[i] = raw[intPos];
        } else {
            outShort[i] = 0;
        }

        readPos += step;
    }

    env->SetShortArrayRegion(output, 0, length, outShort.data());
    return output;
}
