#include "SuperpoweredInterface.h"
#include <SuperpoweredSimple.h>
#include <SuperpoweredDecoder.h>
#include <SuperpoweredCPU.h>
#include <jni.h>
#include <cstdio>
#include <android/log.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>
#include <malloc.h>
#include <Superpowered.h>

#define  LOG_TAG    "QPLAYER_NATIVE"
#define  LOG_V(...)  __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define  LOG_D(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOG_D("JNI_OnLoad: ERROR");
        return -1;
    }

    LOG_D("JNI_OnLoad: INIT");
    
    // Get jclass with env->FindClass.
    // Register methods with env->RegisterNatives.

    return JNI_VERSION_1_6;
}


static void playerEventCallbackA(void *clientData, SuperpoweredAdvancedAudioPlayerEvent event, void * __unused value) {
    
    if (event == SuperpoweredAdvancedAudioPlayerEvent_LoadSuccess) {

        ((SuperpoweredInterface *)clientData)->onPrepared();
    };
    
    if (event == SuperpoweredAdvancedAudioPlayerEvent_EOF) {

        ((SuperpoweredInterface *)clientData)->onCompletion();
    }

    if (event == SuperpoweredAdvancedAudioPlayerEvent_LoadError) {

        ((SuperpoweredInterface *)clientData)->onError();
    }
}

static bool audioProcessing(void *clientdata, short int *audioIO, int numberOfSamples, int __unused samplerate) { 
    return ((SuperpoweredInterface *)clientdata)->process(audioIO, (unsigned int)numberOfSamples);
}

SuperpoweredInterface::SuperpoweredInterface(unsigned int samplerate,
                                         unsigned int buffersize) : activeFx(0), 
                                         volA(1.0f * headroom) {
                                                            
    LOG_D("SuperpoweredInterface(): INIT");

    SuperpoweredInitialize(
            "eXFxYlc3MDZKeEZIWGQ3ZmFhZDRmOTNiMzQyYzE4NzU4NmNjODk1Y2NlNTllNzgwYTIxOTB5bnJ1U3BTT3pPSXUzbGp2bHky",
            true, // enableAudioAnalysis (using SuperpoweredAnalyzer, SuperpoweredLiveAnalyzer, SuperpoweredWaveform or SuperpoweredBandpassFilterbank)
            false, // enableFFTAndFrequencyDomain (using SuperpoweredFrequencyDomain, SuperpoweredFFTComplex, SuperpoweredFFTReal or SuperpoweredPolarFFT)
            false, // enableAudioTimeStretching (using SuperpoweredTimeStretching)
            true, // enableAudioEffects (using any SuperpoweredFX class)
            true, // enableAudioPlayerAndDecoder (using SuperpoweredAdvancedAudioPlayer or SuperpoweredDecoder)
            false, // enableCryptographics (using Superpowered::RSAPublicKey, Superpowered::RSAPrivateKey, Superpowered::hasher or Superpowered::AES)
            false  // enableNetworking (using Superpowered::httpRequest)
    );


    stereoBuffer = (float *)memalign(16, (buffersize + 16) * sizeof(float) * 2);

    playerA = new SuperpoweredAdvancedAudioPlayer(&playerA, playerEventCallbackA, samplerate, 0);
    
    playerA->syncMode = SuperpoweredAdvancedAudioPlayerSyncMode_TempoAndBeat;

    filter = new SuperpoweredFilter(SuperpoweredFilter_Resonant_Lowpass, samplerate);
    equalizer = new Superpowered3BandEQ(samplerate);

    audioSystem = new SuperpoweredAndroidAudioIO(samplerate, 
                                                 buffersize, 
                                                 false, 
                                                 true, 
                                                 audioProcessing, 
                                                 this, 
                                                 -1, 
                                                 SL_ANDROID_STREAM_MEDIA);
}

SuperpoweredInterface::~SuperpoweredInterface() {

    delete audioSystem;
    delete playerA;
    delete waveform;
    delete filter;
    delete equalizer;

    free(stereoBuffer);
}

void SuperpoweredInterface::onPlayPause(bool play) {

    LOG_D("onPlayPause()");
    
    if (!play) {
        playerA->pause();
    } else {
        playerA->play(true);
    };
    SuperpoweredCPU::setSustainedPerformanceMode(play); // <-- Important to prevent audio dropouts.
}

#define MINFREQ 60.0f
#define MAXFREQ 20000.0f

static inline float floatToFrequency(float value) {
    if (value > 0.97f) return MAXFREQ;
    if (value < 0.03f) return MINFREQ;
    value = powf(10.0f, (value + ((0.4f - fabsf(value - 0.4f)) * 0.3f)) * log10f(MAXFREQ - MINFREQ)) + MINFREQ;
    return value < MAXFREQ ? value : MAXFREQ;
}

// Low/mid/high gain.
// 1.0f is "flat",
// 2.0f is +6db.
// Kill is enabled under -40 db (0.01f).
// Limits: 0.0f and 8.0f.
void SuperpoweredInterface::onEqValuesSet(float band1, float band2, float band3) {

    equalizer->enable(true);
    equalizer->bands[0] = band1;
    equalizer->bands[1] = band2;
    equalizer->bands[2] = band3;
}

void SuperpoweredInterface::loadTrack(const char *path) {
    playerA->open(path, nullptr);
}

void SuperpoweredInterface::onSetTempo(float factor, bool mastertempo) {
    playerA->setTempo(factor, mastertempo);
}

void SuperpoweredInterface::onSetPosition(double ms, bool andStop, bool synchronisedStart) {
    playerA->setPosition(ms, andStop, synchronisedStart);
}

double SuperpoweredInterface::getPositionMs() {
    return playerA->positionMs;
}

unsigned int SuperpoweredInterface::getDurationMs() {
    return playerA->durationMs;
}

bool SuperpoweredInterface::process(short int *output, unsigned int numberOfSamples) {
    
    double masterBpm = playerA->currentBpm;
    bool silence = !playerA->process(stereoBuffer, false, numberOfSamples, volA, masterBpm, playerA->msElapsedSinceLastBeat);
    if (!silence) {
        filter->process(stereoBuffer, stereoBuffer, numberOfSamples);
        equalizer->process(stereoBuffer, stereoBuffer, numberOfSamples);
    };

    // The stereoBuffer is ready now, let's put the finished audio into the requested buffers.
    if (!silence) SuperpoweredFloatToShortInt(stereoBuffer, output, numberOfSamples);
    return !silence;
}

//
// Calls back to JAVA
//

SuperpoweredInterface *example = nullptr;
JavaVM *jvm;

jclass jClassRef;
jobject javaObjectRef;

void SuperpoweredInterface::onPrepared() {
    LOG_D("onPrepared()");
    
    JNIEnv *env;
    jint getEnvStat = jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->AttachCurrentThread(&env, nullptr);
    }

    jmethodID jmethodID = env->GetMethodID(jClassRef, "onPrepared", "()V");
    env->CallVoidMethod(javaObjectRef, jmethodID);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->DetachCurrentThread();
    }
}

void SuperpoweredInterface::onCompletion() {
    LOG_D("onCompletion()");

    JNIEnv *env;
    jint getEnvStat = jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->AttachCurrentThread(&env, nullptr);
    }

    jmethodID jmethodID = env->GetMethodID(jClassRef, "onCompletion",   "()V");
    env->CallVoidMethod(javaObjectRef, jmethodID);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->DetachCurrentThread();
    }
}

void SuperpoweredInterface::onError() {
    LOG_D("onError()");

    JNIEnv *env;
    jint getEnvStat = jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->AttachCurrentThread(&env, nullptr);
    }

    jmethodID jmethodID = env->GetMethodID(jClassRef, "onError",   "()V");
    env->CallVoidMethod(javaObjectRef, jmethodID);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->DetachCurrentThread();
    }
}

//
// JNI Calls from Java resp. Kotlin
//

extern "C" JNIEXPORT 
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_SuperpoweredNative(
        JNIEnv * __unused jniEnv,
        jobject  __unused obj,
        jint     samplerate,
        jint     buffersize) {

    example = new SuperpoweredInterface((unsigned int)samplerate, (unsigned int)buffersize);
    
    // for calling back to java we need to cache some references
    
    jint rs = jniEnv->GetJavaVM(&jvm);
    jclass clazz = jniEnv->GetObjectClass(obj);
    jClassRef = (jclass)jniEnv->NewGlobalRef(clazz);
    javaObjectRef = jniEnv->NewGlobalRef(obj);
}

extern "C" JNIEXPORT
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_destroyNative(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj) {

    javaEnvironment->DeleteGlobalRef(jClassRef);
    javaEnvironment->DeleteGlobalRef(javaObjectRef);

   // delete example;
}

extern "C" JNIEXPORT 
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_onPlayPause(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj,
        jboolean play) {

    example->onPlayPause(play);
}

extern "C" JNIEXPORT JNICALL
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_loadTrack(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj,
        jstring  javapath) {

    LOG_D("loadTrack():");
    
    const char *path = javaEnvironment->GetStringUTFChars(javapath, JNI_FALSE);
    
    example->loadTrack(path);

    javaEnvironment->ReleaseStringUTFChars(javapath, path);
}

extern "C" JNIEXPORT JNICALL
jbyteArray Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_analyzeData(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj,
        jstring  javaPath) {

    LOG_D("analyzeData():");

    jboolean isCopy;

    const char *path = javaEnvironment->GetStringUTFChars(javaPath, JNI_FALSE);

    // Open the input file.
    auto *decoder = new SuperpoweredDecoder();
    const char *openError = decoder->open(path, false, 0, 0);
    if (openError) {
        LOG_D("analyzeData(): Open error: %s\n", openError);
        delete decoder;
        return 0;
    };

    // Create the analyzer.
    auto *analyzer = new SuperpoweredOfflineAnalyzer(decoder->samplerate, 0,
                                                     static_cast<int>(decoder->durationSeconds));

    // Create a buffer for the 16-bit integer samples coming from the decoder.
    auto *intBuffer = (short int *)malloc(decoder->samplesPerFrame * 2 * sizeof(short int) + 32768);
    // Create a buffer for the 32-bit floating point samples required by the effect.
    auto *floatBuffer = (float *)malloc(decoder->samplesPerFrame * 2 * sizeof(float) + 32768);

    // Processing.
    int progress = 0;
    while (true) {
        // Decode one frame. samplesDecoded will be overwritten with the actual decoded number of samples.
        unsigned int samplesDecoded = decoder->samplesPerFrame;
        if (decoder->decode(intBuffer, &samplesDecoded) == SUPERPOWEREDDECODER_ERROR) break;
        if (samplesDecoded < 1) break;

        // Convert the decoded PCM samples from 16-bit integer to 32-bit floating point.
        SuperpoweredShortIntToFloat(intBuffer, floatBuffer, samplesDecoded);

        // Submit samples to the analyzer.
        analyzer->process(floatBuffer, samplesDecoded);

        // Update the progress indicator.
        int p = int(((double)decoder->samplePosition / (double)decoder->durationSamples) * 100.0);
        if (progress != p) {
            progress = p;
            printf("\r%i%%", progress);
            LOG_V("analyzeData(): progress: %i", progress);
            fflush(stdout);
        }
    };

    // Get the result.
    unsigned char *averageWaveform = nullptr,
        *lowWaveform = nullptr,
        *midWaveform = nullptr,
        *highWaveform = nullptr,
        *peakWaveform = nullptr,
        *notes = nullptr;

    int waveformSize, overviewSize, keyIndex;
    char *overviewWaveform = nullptr;

    float loudpartsAverageDecibel, peakDecibel, bpm, averageDecibel, beatgridStartMs = 0;
    analyzer->getresults(&averageWaveform, &peakWaveform, &lowWaveform, &midWaveform, &highWaveform, &notes, &waveformSize, &overviewWaveform, &overviewSize, &averageDecibel, &loudpartsAverageDecibel, &peakDecibel, &bpm, &beatgridStartMs, &keyIndex);

    // Cleanup.

    // Do something with the result.
    LOG_D("\rBpm is %f, average loudness is %f db, peak volume is %f db.\n", bpm, loudpartsAverageDecibel, peakDecibel);
    LOG_D("waveformSize: %d", waveformSize);
    LOG_D("overviewSize: %d", overviewSize);

    jbyteArray ret = javaEnvironment->NewByteArray(overviewSize);

    javaEnvironment->SetByteArrayRegion(ret, 0, overviewSize, (jbyte*) overviewWaveform);

    javaEnvironment->ReleaseStringUTFChars(javaPath, path);

    delete decoder;
    delete analyzer;
    free(intBuffer);
    free(floatBuffer);
    
    // Done with the result, free memory.
    if (averageWaveform) free(averageWaveform);
    if (lowWaveform) free(lowWaveform);
    if (midWaveform) free(midWaveform);
    if (highWaveform) free(highWaveform);
    if (peakWaveform) free(peakWaveform);
    if (notes) free(notes);
    if (overviewWaveform) free(overviewWaveform);
    
    return ret;
}

extern "C" JNIEXPORT
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_onSetTempo(
        JNIEnv * __unused javaEnvironment,
        jobject __unused obj,
        jfloat value,
        jboolean masterTempo) {

    example->onSetTempo(value, masterTempo);
}

extern "C" JNIEXPORT
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_onSetPosition(
        JNIEnv *  __unused javaEnvironment,
        jobject  __unused obj,
        jdouble  position,
        jboolean andStop,
        jboolean synchStart) {

    example->onSetPosition(position, andStop, synchStart);
}

extern "C" JNIEXPORT
jlong Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_getPositionMs(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj) {

    return (jlong)(unsigned long long)example->getPositionMs();
}

extern "C" JNIEXPORT
jlong Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_getDurationMs(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj) {

    return (jlong)(unsigned long long)example->getDurationMs();
}

extern "C" JNIEXPORT
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_setEqValues(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj,
        jfloat   band1,
        jfloat   band2,
        jfloat   band3) {
}

// Prototype for upcoming JNI functions
extern "C" JNIEXPORT 
void Java_org_qstuff_qplayer_player_mediaservice_QDeqPlayerSuperpowered_onFxValue(
        JNIEnv * __unused javaEnvironment,
        jobject  __unused obj,
        jint     value) {
}
