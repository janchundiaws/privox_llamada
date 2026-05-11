#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>

#define TAG "OboeAudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

class OboeAudioEngine : public oboe::AudioStreamCallback {
private:
    std::shared_ptr<oboe::AudioStream> recordingStream;
    bool isRecording = false;

public:
    OboeAudioEngine() {}

    ~OboeAudioEngine() {
        stopRecording();
    }

    bool startRecording() {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
                ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
                ->setSharingMode(oboe::SharingMode::Exclusive)
                ->setFormat(oboe::AudioFormat::I16)
                ->setChannelCount(1) // Mono microphone capture
                ->setSampleRate(48000)
                ->setCallback(this);

        oboe::Result result = builder.openStream(recordingStream);
        if (result != oboe::Result::OK) {
            LOGE("Failed to open recording stream. Error: %s", oboe::convertToText(result));
            return false;
        }

        result = recordingStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Failed to start recording stream. Error: %s", oboe::convertToText(result));
            recordingStream->close();
            return false;
        }

        isRecording = true;
        LOGD("Oboe recording stream started successfully.");
        return true;
    }

    void stopRecording() {
        if (recordingStream && isRecording) {
            recordingStream->requestStop();
            recordingStream->close();
            isRecording = false;
            LOGD("Oboe recording stream stopped.");
        }
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        // Aquí es donde interceptamos los bytes crudos del micrófono en baja latencia.
        // Aplicaremos el STFT (Transformada de Fourier) aquí antes de enviarlo a WebRTC.
        
        int16_t *intData = static_cast<int16_t *>(audioData);
        
        // Placeholder: Copia silenciosa o procesamiento crudo
        // En el futuro inyectaremos esto al CustomAudioSource de WebRTC.

        return oboe::DataCallbackResult::Continue;
    }
};

// --- JNI Bridge ---
static OboeAudioEngine* engine = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_example_ghox_1app_OboeEngine_start(JNIEnv* env, jobject /* this */) {
    if (!engine) {
        engine = new OboeAudioEngine();
    }
    engine->startRecording();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_ghox_1app_OboeEngine_stop(JNIEnv* env, jobject /* this */) {
    if (engine) {
        engine->stopRecording();
        delete engine;
        engine = nullptr;
    }
}
