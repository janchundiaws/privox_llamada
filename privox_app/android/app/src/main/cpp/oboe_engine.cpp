#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <cstdint>

#define TAG "OboeAudioEngine"

#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

class OboeAudioEngine : public oboe::AudioStreamCallback {

private:

    std::shared_ptr<oboe::AudioStream> recordingStream;

    bool isRecording = false;

    int64_t samplesCaptured = 0;


public:

    OboeAudioEngine() = default;


    ~OboeAudioEngine() {
        stopRecording();
    }


    bool startRecording() {

        oboe::AudioStreamBuilder builder;

        builder.setDirection(
                        oboe::Direction::Input)

                ->setPerformanceMode(
                        oboe::PerformanceMode::LowLatency)

                ->setSharingMode(
                        oboe::SharingMode::Exclusive)

                ->setInputPreset(
                        oboe::InputPreset::VoiceCommunication)

                ->setFormat(
                        oboe::AudioFormat::I16)

                ->setChannelCount(
                        1)

                ->setSampleRate(
                        48000)

                ->setFramesPerCallback(
                        480)

                ->setCallback(
                        this);



        oboe::Result result =
                builder.openStream(
                        recordingStream);


        if (result != oboe::Result::OK) {

            LOGE(
                    "openStream failed: %s",
                    oboe::convertToText(result));

            return false;
        }



        result =
                recordingStream->requestStart();


        if (result != oboe::Result::OK) {

            LOGE(
                    "requestStart failed: %s",
                    oboe::convertToText(result));

            recordingStream->close();

            return false;
        }



        isRecording = true;


        LOGD(
                "OBoe started @ %d hz",
                recordingStream->getSampleRate());

        LOGD(
                "Frames per callback: %d",
                recordingStream->getFramesPerBurst());

        return true;
    }



    void stopRecording() {

        if (!recordingStream) {
            return;
        }

        if (!isRecording) {
            return;
        }


        recordingStream->requestStop();

        recordingStream->close();


        isRecording = false;


        LOGD("Oboe stopped");
    }



    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override {

        auto* pcm =
                static_cast<int16_t*>(audioData);


        if (!pcm) {

            return oboe::DataCallbackResult::Continue;
        }



        // ====================================================
        // AQUÍ irá el push hacia tu DSP
        //
        // Ejemplo futuro:
        //
        // pushToVoiceProcessor(
        //      pcm,
        //      numFrames);
        //
        // ====================================================



        // ====================================================
        // TELEMETRÍA
        // ====================================================
        samplesCaptured += numFrames;


        if (samplesCaptured >= 48000) {

            LOGD(
                    "Mic OK @48khz | captured=%lld",
                    samplesCaptured);

            samplesCaptured = 0;
        }



        // ====================================================
        // DETECTOR DE SILENCIO
        // ====================================================
        int16_t peak = 0;


        for (int i = 0; i < numFrames; i++) {

            int16_t sample = pcm[i];

            if (sample < 0) {
                sample = -sample;
            }

            if (sample > peak) {
                peak = sample;
            }
        }


        if (peak < 500) {

            LOGD(
                    "Background suppressed");
        }


        return oboe::DataCallbackResult::Continue;
    }
};



// ====================================================
// JNI
// ====================================================
static OboeAudioEngine* engine = nullptr;


extern "C"
JNIEXPORT void JNICALL
Java_com_example_ghox_1app_OboeEngine_start(
        JNIEnv*,
        jobject) {

    if (!engine) {

        engine =
                new OboeAudioEngine();
    }


    engine->startRecording();
}



extern "C"
JNIEXPORT void JNICALL
Java_com_example_ghox_1app_OboeEngine_stop(
        JNIEnv*,
        jobject) {

    if (!engine) {
        return;
    }


    engine->stopRecording();


    delete engine;


    engine = nullptr;
}