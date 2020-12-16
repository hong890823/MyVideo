#include "HVideo.h"

HVideo::HVideo(HPlaystatus *playstatus, HCallJava *callJava) {
    this->playstatus = playstatus;
    this->callJava = callJava;
    queue = new HQueue(playstatus);
    pthread_mutex_init(&codecMutex,NULL);
}

void * playVideo(void *data){

    HVideo *video = static_cast<HVideo *>(data);

    while(video->playstatus != NULL && !video->playstatus->exit)
    {

        if(video->playstatus->seek)
        {
            av_usleep(1000 * 100);
            continue;
        }

        if(video->playstatus->pause){
            av_usleep(1000 * 100);
            continue;
        }

        if(video->queue->getQueueSize() == 0)
        {
            if(!video->playstatus->load)
            {
                video->playstatus->load = true;
                video->callJava->onCallLoad(CHILD_THREAD, true);
            }
            av_usleep(1000 * 100);
            continue;
        } else{
            if(video->playstatus->load)
            {
                video->playstatus->load = false;
                video->callJava->onCallLoad(CHILD_THREAD, false);
            }
        }
        AVPacket *avPacket = av_packet_alloc();
        if(video->queue->getAvpacket(avPacket) != 0)
        {
            av_packet_free(&avPacket);
            av_free(avPacket);
            avPacket = NULL;
            continue;
        }
        //拿出avPacket之后就可以进行是硬解码还是软解码的判断了
        if(video->codectype == CODEC_MEDIACODEC)//硬解,不设计avFrame
        {
            if(av_bsf_send_packet(video->abs_ctx,avPacket)!=0){
                av_packet_free(&avPacket);
                av_free(avPacket);
                avPacket = NULL;
                continue;
            }
            while(av_bsf_receive_packet(video->abs_ctx,avPacket)==0){
                //音视频同步（音频不变，视频跟随音频的方式）
                double diff = video->getFrameDiffTime(NULL,avPacket);
                LOGE("diff is %f", diff);
                av_usleep(static_cast<unsigned int>(video->getDelayTime(diff) * 1000000));

                video->callJava->onCallDecodeAVPacket(avPacket->size,avPacket->data);

                av_packet_free(&avPacket);
                av_free(avPacket);
                continue;
            }
            avPacket = NULL;
        }
        else if(video->codectype == CODEC_YUV)//软解
        {
            pthread_mutex_lock(&video->codecMutex);//seek线程锁
            if(avcodec_send_packet(video->avCodecContext, avPacket) != 0)
            {
                av_packet_free(&avPacket);
                av_free(avPacket);
                avPacket = NULL;
                pthread_mutex_unlock(&video->codecMutex);
                continue;
            }
            AVFrame *avFrame = av_frame_alloc();
            if(avcodec_receive_frame(video->avCodecContext, avFrame) != 0)
            {
                av_frame_free(&avFrame);
                av_free(avFrame);
                avFrame = NULL;
                av_packet_free(&avPacket);
                av_free(avPacket);
                avPacket = NULL;
                pthread_mutex_unlock(&video->codecMutex);
                continue;
            }
            LOGE("子线程解码一个AVframe成功");

            if(avFrame->format == AV_PIX_FMT_YUV420P)
            {
                LOGE("当前视频是YUV420P格式");

                //音视频同步（音频不变，视频跟随音频的方式）
                double diff = video->getFrameDiffTime(avFrame,NULL);
                LOGE("diff is %f", diff);
                av_usleep(static_cast<unsigned int>(video->getDelayTime(diff) * 1000000));

                video->callJava->onCallRenderYUV(
                        video->avCodecContext->width,
                        video->avCodecContext->height,
                        avFrame->data[0],
                        avFrame->data[1],
                        avFrame->data[2]);
            } else{
                LOGE("当前视频不是YUV420P格式");

                //音视频同步（因为pFrameYUV420P和avFrame的pts是一样的，所以这里也可以用avFrame）
                double diff = video->getFrameDiffTime(avFrame,NULL);
                LOGE("diff is %f", diff);
                av_usleep(static_cast<unsigned int>(video->getDelayTime(diff) * 1000000));

                AVFrame *pFrameYUV420P = av_frame_alloc();
                int num = av_image_get_buffer_size(
                        AV_PIX_FMT_YUV420P,
                        video->avCodecContext->width,
                        video->avCodecContext->height,
                        1);
                uint8_t *buffer = static_cast<uint8_t *>(av_malloc(num * sizeof(uint8_t)));
                //填充数组数据
                av_image_fill_arrays(
                        pFrameYUV420P->data,
                        pFrameYUV420P->linesize,
                        buffer,
                        AV_PIX_FMT_YUV420P,
                        video->avCodecContext->width,
                        video->avCodecContext->height,
                        1);
                SwsContext *sws_ctx = sws_getContext(
                        video->avCodecContext->width,
                        video->avCodecContext->height,
                        video->avCodecContext->pix_fmt,//转换之前的像素格式
                        video->avCodecContext->width,
                        video->avCodecContext->height,
                        AV_PIX_FMT_YUV420P,//转换之后的像素格式
                        SWS_BICUBIC, NULL, NULL, NULL);

                if(!sws_ctx)
                {
                    av_frame_free(&pFrameYUV420P);
                    av_free(pFrameYUV420P);
                    av_free(buffer);
                    pthread_mutex_unlock(&video->codecMutex);
                    continue;
                }
                //实际转换YUV420P
                sws_scale(
                        sws_ctx,
                        reinterpret_cast<const uint8_t *const *>(avFrame->data),
                        avFrame->linesize,
                        0,
                        avFrame->height,
                        pFrameYUV420P->data,
                        pFrameYUV420P->linesize);
                //渲染
                video->callJava->onCallRenderYUV(
                        video->avCodecContext->width,
                        video->avCodecContext->height,
                        pFrameYUV420P->data[0],
                        pFrameYUV420P->data[1],
                        pFrameYUV420P->data[2]);

                    av_frame_free(&pFrameYUV420P);
                    av_free(pFrameYUV420P);
                    av_free(buffer);
                    sws_freeContext(sws_ctx);
                }
                av_frame_free(&avFrame);
                av_free(avFrame);
                avFrame = NULL;
                av_packet_free(&avPacket);
                av_free(avPacket);
                avPacket = NULL;
                pthread_mutex_unlock(&video->codecMutex);
            }
        }
//    pthread_exit(&video->thread_play);
        return 0;
}

void HVideo::play() {
    if(playstatus!=NULL && !playstatus->exit){
        pthread_create(&thread_play, NULL, playVideo, this);
    }
}

void HVideo::release() {
    if(queue!=NULL){
        queue->noticeQueue();
    }
    pthread_join(thread_play,NULL);

    if(queue != NULL){
        delete(queue);
        queue = NULL;
    }
    if(abs_ctx != NULL){
        av_bsf_free(&abs_ctx);
        abs_ctx = NULL;
    }
    if(avCodecContext != NULL){
        pthread_mutex_lock(&codecMutex);
        avcodec_close(avCodecContext);
        avcodec_free_context(&avCodecContext);
        avCodecContext = NULL;
        pthread_mutex_unlock(&codecMutex);
    }

    if(playstatus != NULL){
        playstatus = NULL;
    }
    if(callJava != NULL){
        callJava = NULL;
    }

}

//获取音视频PTS差值
double HVideo::getFrameDiffTime(AVFrame *avFrame,AVPacket *avPacket) {
    double pts = 0;
    if(avFrame!=NULL){
        pts = av_frame_get_best_effort_timestamp(avFrame);
    }
    if(avPacket!=NULL){
        pts = avPacket->pts;
    }
    if(AV_NOPTS_VALUE==pts){//pts不是每帧都有的
        pts = 0;
    }
    pts *= av_q2d(time_base);//通过时间基转换成秒
    if(pts>0){ //大于0才可以进行累加
        clock = pts;
    }
    double diff = 1.0;
    if(audio!=NULL) diff = audio->clock-clock;
    return diff;
}

double HVideo::getDelayTime(double diff) {
    //微小差值
    if (diff > 0.003) {
        delayTime = delayTime * 2 / 3;
        if (delayTime < defaultDelayTime / 2) {
            delayTime = defaultDelayTime * 2 / 3;
        } else if (delayTime > defaultDelayTime * 2) {
            delayTime = defaultDelayTime * 2;
        }
    } else if (diff < -0.003) {
        delayTime = delayTime * 3 / 2;
        if (delayTime < defaultDelayTime / 2) {
            delayTime = defaultDelayTime * 2 / 3;
        } else if (delayTime > defaultDelayTime * 2) {
            delayTime = defaultDelayTime * 2;
        }
    } else if (diff == 0.003) {

    }
    //中等差值
    if (diff >= 0.5) {
        delayTime = 0;
    } else if (diff <= -0.5) {
        delayTime = defaultDelayTime * 2;
    }
    //差值太大的时候需要让delayTIme回归到初始状态
    if (fabs(diff) >= 10) {
        delayTime = defaultDelayTime;
    }
    return delayTime;
}

HVideo::~HVideo() {
    pthread_mutex_destroy(&codecMutex);
}
