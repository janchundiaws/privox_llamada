#include <jni.h>
#include <vector>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "VoiceProcessorDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)


// =========================================================
// RING BUFFER
// =========================================================
class RingBuffer {
private:
    std::vector<double> buffer;
    size_t head = 0;
    size_t tail = 0;
    size_t size = 0;
    size_t capacity;

public:
    RingBuffer(size_t cap) : capacity(cap) {
        buffer.resize(cap, 0.0);
    }

    void push(double val) {
        if (size < capacity) {
            buffer[head] = val;
            head = (head + 1) % capacity;
            size++;
        }
    }

    double pop() {
        if (size > 0) {
            double val = buffer[tail];
            tail = (tail + 1) % capacity;
            size--;
            return val;
        }

        return 0.0;
    }

    size_t available() {
        return size;
    }
};


// =========================================================
// DSP ENGINE
// =========================================================
class DSPEngine {
private:

    // voz ardilla
    double pitch_factor = 1.65;

    // noise gate
    double envelope = 0.0;

    double attack = 0.25;
    double release = 0.985;

    // gate más agresivo
    double noise_threshold = 0.18;

    // dc blocker
    double dc_last_in = 0.0;
    double dc_last_out = 0.0;

public:

    void process(
            RingBuffer& RB1,
            RingBuffer& RB2,
            int total_samples,
            int sampleRate) {

        static int log_counter = 0;

        double max_envelope_in_frame = 0.0;
        double max_output_in_frame = 0.0;

        int clipped_samples = 0;

        for (int i = 0; i < total_samples; ++i) {

            if (RB1.available() == 0) {
                break;
            }

            // =====================================================
            // A. PCM INPUT
            // =====================================================
            double pcm_input = RB1.pop();


            // =====================================================
            // B. DC BLOCKER
            // =====================================================
            double dc_out =
                    pcm_input
                    - dc_last_in
                    + (0.995 * dc_last_out);

            dc_last_in = pcm_input;
            dc_last_out = dc_out;


            // =====================================================
            // C. ENVELOPE TRACKER
            // =====================================================
            double abs_val = std::abs(dc_out);

            if (abs_val > envelope) {

                envelope =
                        attack * abs_val
                        + ((1.0 - attack) * envelope);

            } else {

                envelope =
                        ((1.0 - release) * abs_val)
                        + (release * envelope);
            }


            if (envelope > max_envelope_in_frame) {
                max_envelope_in_frame = envelope;
            }


            // =====================================================
            // D. NOISE GATE
            // =====================================================
            double gate_gain;

            if (envelope >= noise_threshold) {

                gate_gain = 1.0;

            } else {

                double ratio =
                        envelope / noise_threshold;

                gate_gain =
                        ratio
                        * ratio
                        * ratio;
            }


            // =====================================================
            // E. DRY SIGNAL
            // =====================================================
            double dry_signal =
                    dc_out * gate_gain;


            // =====================================================
            // F. PITCH SHIFT (ARDILLA)
            // =====================================================
            double pitch_shifted =
                    dry_signal * pitch_factor;


            if (pitch_shifted > 1.0) {
                pitch_shifted = 1.0;
            }

            if (pitch_shifted < -1.0) {
                pitch_shifted = -1.0;
            }


            // =====================================================
            // G. OUTPUT MIX
            // =====================================================
            double mixed =
                    pitch_shifted;


            // =====================================================
            // H. SAFE GAIN
            // =====================================================
            double gained =
                    mixed * 1.08;


            // =====================================================
            // I. SOFT CLIPPER
            // =====================================================
            double output =
                    tanh(gained);


            if (std::abs(output) >= 0.99) {
                clipped_samples++;
            }


            if (std::abs(output) > max_output_in_frame) {
                max_output_in_frame =
                        std::abs(output);
            }


            // =====================================================
            // J. OUTPUT
            // =====================================================
            RB2.push(output);
        }


        // =====================================================
        // TELEMETRY
        // =====================================================
        log_counter++;

        if (log_counter >= 100) {

            LOGI("========== DSP REPORT ==========");
            LOGI("ENV MAX: %.4f", max_envelope_in_frame);
            LOGI("OUTPUT MAX: %.4f", max_output_in_frame);

            if (clipped_samples > 0) {

                LOGI(
                        "CLIPPED SAMPLES: %d",
                        clipped_samples);

            } else {

                LOGI("OUTPUT HEALTHY");
            }

            LOGI("================================");

            log_counter = 0;
        }
    }
};



// =========================================================
// GLOBAL STATE
// =========================================================

// 1 segundo @48khz
RingBuffer RB1(48000);
RingBuffer RB2(48000);

DSPEngine dspEngine;


// =========================================================
// JNI
// =========================================================
extern "C"
JNIEXPORT void JNICALL
Java_com_example_ghox_1app_RobotVoiceProcessor_processPitchShiftNative(
        JNIEnv* env,
        jobject,
        jobject buffer,
        jint numBands,
        jint numFrames,
        jfloat pitchFactor) {

    jshort* raw =
            static_cast<jshort*>(
                    env->GetDirectBufferAddress(buffer));

    if (!raw) {
        return;
    }


    int total_samples =
            numFrames * numBands;


    // =====================================================
    // INPUT
    // =====================================================
    for (int i = 0; i < total_samples; ++i) {

        double normalized =
                static_cast<double>(raw[i])
                / 32768.0;

        RB1.push(normalized);
    }


    // =====================================================
    // DSP @ 48khz
    // =====================================================
    dspEngine.process(
            RB1,
            RB2,
            total_samples,
            48000);


    // =====================================================
    // OUTPUT
    // =====================================================
    for (int i = 0; i < total_samples; ++i) {

        if (RB2.available() > 0) {

            double processed =
                    RB2.pop();

            raw[i] =
                    static_cast<jshort>(
                            processed * 32767.0);

        } else {

            raw[i] = 0;
        }
    }
}