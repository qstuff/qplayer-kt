#ifndef Header_SuperpoweredExample
#define Header_SuperpoweredExample

#include <math.h>
#include <pthread.h>

#include "SuperpoweredExample.h"
#include <SuperpoweredAdvancedAudioPlayer.h>
#include <SuperpoweredFilter.h>
#include <SuperpoweredRoll.h>
#include <SuperpoweredFlanger.h>
#include <SuperpoweredAnalyzer.h>
#include <AndroidIO/SuperpoweredAndroidAudioIO.h>

#define HEADROOM_DECIBEL 3.0f
static const float headroom = powf(10.0f, -HEADROOM_DECIBEL * 0.025f);

class SuperpoweredExample {
public:

    SuperpoweredExample(unsigned int samplerate, unsigned int buffersize);  
    ~SuperpoweredExample();
    
    bool process(short int *output, unsigned int numberOfSamples);
    
    void onPlayPause(bool play);
    void onFxSelect(int value);
    void onFxOff();
    void onFxValue(int value);
    void onSetTempo(float factor, bool mastertempo);
    void onSetPosition(double ms, bool andStop, bool synchronisedStart);
    void loadTrack(const char *path);
    void destroy();
    
    double getPositionMs();
    double getDurationMs();
    
    void onEQBand(unsigned int index, int gain);
    
    void onPrepared();
    void onCompletion();
    void onError();

private:
    SuperpoweredAndroidAudioIO *audioSystem;
    SuperpoweredAdvancedAudioPlayer *playerA;
    SuperpoweredWaveform *waveform;
    SuperpoweredRoll *roll;
    SuperpoweredFilter *filter;
    SuperpoweredFlanger *flanger;
    float *stereoBuffer;
    unsigned char activeFx;
    float volA;
};

#endif
