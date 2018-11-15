//
// Created by yangw on 2018-3-6.
//

#ifndef MYVIDEO_HQUEUE_H
#define MYVIDEO_HQUEUE_H

#include "queue"
#include "pthread.h"
#include "AndroidLog.h"
#include "HPlaystatus.h"

extern "C"
{
#include "libavcodec/avcodec.h"
};


class HQueue {

public:
    std::queue<AVPacket *> queuePacket;
    pthread_mutex_t mutexPacket;
    pthread_cond_t condPacket;
    HPlaystatus *playstatus = NULL;

public:

    HQueue(HPlaystatus *playstatus);
    ~HQueue();

    int putAvpacket(AVPacket *packet);
    int getAvpacket(AVPacket *packet);

    int getQueueSize();

    void clearAvpacket();




};


#endif //MYVIDEO_HQUEUE_H
