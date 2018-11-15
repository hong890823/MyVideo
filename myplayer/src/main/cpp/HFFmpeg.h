//
// Created by yangw on 2018-2-28.
//

#ifndef MYVIDEO_HFFMPEG_H
#define MYVIDEO_HFFMPEG_H

#include "HCallJava.h"
#include "pthread.h"
#include "HAudio.h"
#include "HPlaystatus.h"
#include "HVideo.h"

extern "C"
{
#include "libavformat/avformat.h"
#include <libavutil/time.h>
};


class HFFmpeg {

public:
    HCallJava *callJava = NULL;
    const char* url = NULL;
    pthread_t decodeThread;
    AVFormatContext *pFormatCtx = NULL;
    HAudio *audio = NULL;
    HVideo *video = NULL;
    HPlaystatus *playstatus = NULL;
    pthread_mutex_t init_mutex;
    bool exit = false;
    int duration = 0;
    pthread_mutex_t seek_mutex;
public:
    HFFmpeg(HPlaystatus *playstatus, HCallJava *callJava, const char *url);
    ~HFFmpeg();

    void parpared();
    void decodeFFmpegThread();
    void start();

    void pause();

    void resume();

    void release();

    void seek(int64_t secds);

    int getCodecContext(AVCodecParameters *codecpar, AVCodecContext **avCodecContext);

};


#endif //MYVIDEO_HFFMPEG_H
