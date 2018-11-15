//
// Created by yangw on 2018-5-14.
//

#ifndef MYVIDEO_HVIDEO_H
#define MYVIDEO_HVIDEO_H


#include "HQueue.h"
#include "HCallJava.h"
#include "HAudio.h"

extern "C"
{
#include <libavcodec/avcodec.h>
#include <libavutil/time.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
};

class HVideo {

public:
    int streamIndex = -1;
    AVCodecContext *avCodecContext = NULL;
    AVCodecParameters *codecpar = NULL;
    HQueue *queue = NULL;
    HPlaystatus *playstatus = NULL;
    HCallJava *callJava = NULL;
    AVRational time_base;

    pthread_t thread_play;

    //音视频同步
    HAudio *audio = NULL;
    double clock = 0;
    double delayTime = 0;
    double defaultDelayTime = 0.04;

    //seek过程对avCodecContext的线程锁
    pthread_mutex_t codecMutex;
public:
    HVideo(HPlaystatus *playstatus, HCallJava *wlCallJava);
    ~HVideo();

    void play();
    void release();

    double getFrameDiffTime(AVFrame *avframe);
    double getDelayTime(double diff);

};


#endif //MYVIDEO_HVIDEO_H
