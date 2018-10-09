#include "SuperpoweredExample.h"
#include <SuperpoweredSimple.h>
#include <SuperpoweredDecoder.h>
#include <SuperpoweredCPU.h>
#include <jni.h>
#include <stdio.h>
#include <android/log.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>
#include <malloc.h>


#define  LOG_TAG    "QPLAYER_NATIVE"
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        LOGD("JNI_OnLoad: ERROR");
        return -1;
    }

    LOGD("JNI_OnLoad: INIT");
    
    // Get jclass with env->FindClass.
    // Register methods with env->RegisterNatives.

    return JNI_VERSION_1_6;
}


static void playerEventCallbackA(void *clientData, SuperpoweredAdvancedAudioPlayerEvent event, void * __unused value) {
    
    if (event == SuperpoweredAdvancedAudioPlayerEvent_LoadSuccess) {

        ((SuperpoweredExample *)clientData)->onPrepared();
    };
    
    if (event == SuperpoweredAdvancedAudioPlayerEvent_EOF) {

        ((SuperpoweredExample *)clientData)->onCompletion();
    }

    if (event == SuperpoweredAdvancedAudioPlayerEvent_LoadError) {

        ((SuperpoweredExample *)clientData)->onError();
    }
}

static bool audioProcessing(void *clientdata, short int *audioIO, int numberOfSamples, int __unused samplerate) { 
    return ((SuperpoweredExample *)clientdata)->process(audioIO, (unsigned int)numberOfSamples);
}

SuperpoweredExample::SuperpoweredExample(unsigned int samplerate, 
                                         unsigned int buffersize) : activeFx(0), 
                                         volA(1.0f * headroom) {
                                                            
    LOGD("SuperpoweredExample(): INIT");

    stereoBuffer = (float *)memalign(16, (buffersize + 16) * sizeof(float) * 2);

    playerA = new SuperpoweredAdvancedAudioPlayer(&playerA, playerEventCallbackA, samplerate, 0);
    
    playerA->syncMode = SuperpoweredAdvancedAudioPlayerSyncMode_TempoAndBeat;

    roll = new SuperpoweredRoll(samplerate);
    filter = new SuperpoweredFilter(SuperpoweredFilter_Resonant_Lowpass, samplerate);
    flanger = new SuperpoweredFlanger(samplerate);

    audioSystem = new SuperpoweredAndroidAudioIO(samplerate, 
                                                 buffersize, 
                                                 false, 
                                                 true, 
                                                 audioProcessing, 
                                                 this, 
                                                 -1, 
                                                 SL_ANDROID_STREAM_MEDIA, 
                                                 buffersize * 2);                                                                                        
}

SuperpoweredExample::~SuperpoweredExample() {

    delete audioSystem;
    delete playerA;
    delete waveform;
    delete roll;
    delete filter;
    delete flanger;
    
    free(stereoBuffer);
}

void SuperpoweredExample::onPlayPause(bool play) {

    LOGD("onPlayPause()");
    
    if (!play) {
        playerA->pause();
    } else {
        playerA->play(true);
    };
    SuperpoweredCPU::setSustainedPerformanceMode(play); // <-- Important to prevent audio dropouts.
}

void SuperpoweredExample::onFxSelect(int value) { 
    __android_log_print(ANDROID_LOG_VERBOSE, "SuperpoweredExample", "FXSEL %i", value);
    activeFx = (unsigned char)value;
}

void SuperpoweredExample::onFxOff() {
    filter->enable(false);
    roll->enable(false);
    flanger->enable(false);
}

#define MINFREQ 60.0f
#define MAXFREQ 20000.0f

static inline float floatToFrequency(float value) {
    if (value > 0.97f) return MAXFREQ;
    if (value < 0.03f) return MINFREQ;
    value = powf(10.0f, (value + ((0.4f - fabsf(value - 0.4f)) * 0.3f)) * log10f(MAXFREQ - MINFREQ)) + MINFREQ;
    return value < MAXFREQ ? value : MAXFREQ;
}

void SuperpoweredExample::onFxValue(int ivalue) {

    float value = float(ivalue) * 0.01f;
    switch (activeFx) {
        case 1:
            filter->setResonantParameters(floatToFrequency(1.0f - value), 0.2f);
            filter->enable(true);
            flanger->enable(false);
            roll->enable(false);
            break;
        case 2:
            if (value > 0.8f) roll->beats = 0.0625f;
            else if (value > 0.6f) roll->beats = 0.125f;
            else if (value > 0.4f) roll->beats = 0.25f;
            else if (value > 0.2f) roll->beats = 0.5f;
            else roll->beats = 1.0f;
            roll->enable(true);
            filter->enable(false);
            flanger->enable(false);
            break;
        default:
            flanger->setWet(value);
            flanger->enable(true);
            filter->enable(false);
            roll->enable(false);
    };
}

void SuperpoweredExample::loadTrack(const char *path) {
    playerA->open(path, NULL);
}

void SuperpoweredExample::onSetTempo(float factor, bool mastertempo) {
    playerA->setTempo(factor, mastertempo);
}

void SuperpoweredExample::onSetPosition(double ms, bool andStop, bool synchronisedStart) {
    playerA->setPosition(ms, andStop, synchronisedStart);
}

double SuperpoweredExample::getPositionMs() {
    return playerA->positionMs;
}

double SuperpoweredExample::getDurationMs() {
    return playerA->durationMs;
}

bool SuperpoweredExample::process(short int *output, unsigned int numberOfSamples) {
    
    double masterBpm = playerA->currentBpm;
    bool silence = !playerA->process(stereoBuffer, false, numberOfSamples, volA, masterBpm, playerA->msElapsedSinceLastBeat);
    
    roll->bpm = flanger->bpm = (float)masterBpm; // Syncing fx is one line.

    if (roll->process(silence ? NULL : stereoBuffer, stereoBuffer, numberOfSamples) && silence) silence = false;
    if (!silence) {
        filter->process(stereoBuffer, stereoBuffer, numberOfSamples);
        flanger->process(stereoBuffer, stereoBuffer, numberOfSamples);
    };

    // The stereoBuffer is ready now, let's put the finished audio into the requested buffers.
    if (!silence) SuperpoweredFloatToShortInt(stereoBuffer, output, numberOfSamples);
    return !silence;
}

//
// Calls back to JAVA
//

SuperpoweredExample *example = NULL;
JavaVM *jvm;

jclass jClassRef;
jobject javaObjectRef;

void SuperpoweredExample::onPrepared() {
    LOGD("onPrepared()");
    
    JNIEnv *env;
    jint getEnvStat = jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->AttachCurrentThread(&env, NULL);
    }

    jmethodID jmethodID = env->GetMethodID(jClassRef, "onPrepared",   "()V");
    env->CallVoidMethod(javaObjectRef, jmethodID);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->DetachCurrentThread();
    }
}

void SuperpoweredExample::onCompletion() {
    LOGD("onCompletion()");

    JNIEnv *env;
    jint getEnvStat = jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->AttachCurrentThread(&env, NULL);
    }

    jmethodID jmethodID = env->GetMethodID(jClassRef, "onCompletion",   "()V");
    env->CallVoidMethod(javaObjectRef, jmethodID);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->DetachCurrentThread();
    }
}

void SuperpoweredExample::onError() {
    LOGD("onError()");

    JNIEnv *env;
    jint getEnvStat = jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->AttachCurrentThread(&env, NULL);
    }

    jmethodID jmethodID = env->GetMethodID(jClassRef, "onError",   "()V");
    env->CallVoidMethod(javaObjectRef, jmethodID);

    if (getEnvStat == JNI_EDETACHED) {
        jvm->DetachCurrentThread();
    }
}

//
// JNI Calls from JAVA
//

extern "C" JNIEXPORT 
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_SuperpoweredNative(JNIEnv * __unused jniEnv, 
                                                                                   jobject  __unused obj, 
                                                                                   jint     samplerate, 
                                                                                   jint     buffersize) {
    example = new SuperpoweredExample((unsigned int)samplerate, (unsigned int)buffersize);
    
    // for calling back to java we need to cache some references
    
    jint rs = jniEnv->GetJavaVM(&jvm);     
    jclass clazz = jniEnv->GetObjectClass(obj);
    jClassRef = (jclass)jniEnv->NewGlobalRef(clazz);
    javaObjectRef = jniEnv->NewGlobalRef(obj);
}

extern "C" JNIEXPORT
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_destroyNative(JNIEnv * __unused javaEnvironment,
                                                                              jobject  __unused obj) {
    javaEnvironment->DeleteGlobalRef(jClassRef);
    javaEnvironment->DeleteGlobalRef(javaObjectRef);

    // delete example;
}

extern "C" JNIEXPORT 
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_onPlayPause(JNIEnv * __unused javaEnvironment, 
                                                                            jobject  __unused obj, 
                                                                            jboolean play) { 
    example->onPlayPause(play);
}

extern "C" JNIEXPORT JNICALL
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_loadTrack(JNIEnv * __unused javaEnvironment,
                                                                          jobject  __unused obj,
                                                                          jstring  javapath) {
    LOGD("loadTrack():");
    
    const char *path = javaEnvironment->GetStringUTFChars(javapath, JNI_FALSE);
    
    example->loadTrack(path);

    javaEnvironment->ReleaseStringUTFChars(javapath, path);
}

extern "C" JNIEXPORT JNICALL
jbyteArray Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_analyzeData(JNIEnv * __unused javaEnvironment,
                                                                                  jobject  __unused obj,
                                                                                  jstring  javapath) {
    LOGD("analyzeData():");

    jboolean isCopy;

    const char *path = javaEnvironment->GetStringUTFChars(javapath, JNI_FALSE);

    // Open the input file.
    SuperpoweredDecoder *decoder = new SuperpoweredDecoder();
    const char *openError = decoder->open(path, false, 0, 0);
    if (openError) {
        printf("Open error: %s\n", openError);
        delete decoder;
        return 0;
    };

    // Create the analyzer.
    SuperpoweredOfflineAnalyzer *analyzer = new SuperpoweredOfflineAnalyzer(decoder->samplerate, 0, decoder->durationSeconds);

    // Create a buffer for the 16-bit integer samples coming from the decoder.
    short int *intBuffer = (short int *)malloc(decoder->samplesPerFrame * 2 * sizeof(short int) + 32768);
    // Create a buffer for the 32-bit floating point samples required by the effect.
    float *floatBuffer = (float *)malloc(decoder->samplesPerFrame * 2 * sizeof(float) + 32768);

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
            LOGV("analyzeData(): progress: %i", progress);
            fflush(stdout);
        }
    };

    // Get the result.
    unsigned char *averageWaveform = NULL, *lowWaveform = NULL, *midWaveform = NULL, *highWaveform = NULL, *peakWaveform = NULL, *notes = NULL;
    int waveformSize, overviewSize, keyIndex;
    char *overviewWaveform = NULL;
    float loudpartsAverageDecibel, peakDecibel, bpm, averageDecibel, beatgridStartMs = 0;
    analyzer->getresults(&averageWaveform, &peakWaveform, &lowWaveform, &midWaveform, &highWaveform, &notes, &waveformSize, &overviewWaveform, &overviewSize, &averageDecibel, &loudpartsAverageDecibel, &peakDecibel, &bpm, &beatgridStartMs, &keyIndex);

    // Cleanup.

    // Do something with the result.
    LOGD("\rBpm is %f, average loudness is %f db, peak volume is %f db.\n", bpm, loudpartsAverageDecibel, peakDecibel);
    LOGD("waveforSize: %d", waveformSize);
    LOGD("overviewSize: %d", overviewSize);


    jbyteArray ret = javaEnvironment->NewByteArray(overviewSize);

    javaEnvironment->SetByteArrayRegion(ret, 0, overviewSize, (jbyte*) overviewWaveform);

    javaEnvironment->ReleaseStringUTFChars(javapath, path);

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
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_onFxSelect(JNIEnv * __unused javaEnvironment, 
                                                                               jobject __unused obj, 
                                                                               jint value) {
    example->onFxSelect(value);
}

extern "C" JNIEXPORT
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_onSetTempo(JNIEnv * __unused javaEnvironment,
                                                                               jobject __unused obj,
                                                                               jfloat value,
                                                                               jboolean masterTempo) {
    example->onSetTempo(value, masterTempo);
}

extern "C" JNIEXPORT
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_onSetPosition(JNIEnv *  __unused javaEnvironment,
                                                                               jobject  __unused obj,
                                                                               jdouble  position,
                                                                               jboolean andStop,
                                                                               jboolean synchStart) {
    example->onSetPosition(position, andStop, synchStart);
}

extern "C" JNIEXPORT
double Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_getPositionMs(JNIEnv * __unused javaEnvironment,
                                                                                jobject  __unused obj) {
    return example->getPositionMs();
}

extern "C" JNIEXPORT
double Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_getDurationMs(JNIEnv * __unused javaEnvironment,
                                                                                jobject  __unused obj) {
    return example->getDurationMs();
}

extern "C" JNIEXPORT 
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_onFxOff(JNIEnv * __unused javaEnvironment, 
                                                                        jobject  __unused obj) { 
    example->onFxOff();
}

extern "C" JNIEXPORT 
void Java_org_qstuff_qplayer_player_MediaPlayerSuperpoweredImpl_onFxValue(JNIEnv * __unused javaEnvironment,
                                                                          jobject  __unused obj, 
                                                                          jint     value) {
    example->onFxValue(value);
}
