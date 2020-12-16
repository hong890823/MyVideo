#include "HFFmpeg.h"

HFFmpeg::HFFmpeg(HPlaystatus *playstatus, HCallJava *callJava, const char *url) {
    this->playstatus = playstatus;
    this->callJava = callJava;
    this->url = url;
    exit = false;
    pthread_mutex_init(&init_mutex, NULL);
    pthread_mutex_init(&seek_mutex, NULL);
}

void *decodeFFmpeg(void *data)
{
    HFFmpeg *wlFFmpeg = (HFFmpeg *) data;
    wlFFmpeg->decodeFFmpegThread();
//    pthread_exit(&wlFFmpeg->decodeThread);
    return 0;
}

void HFFmpeg::parpared() {
    pthread_create(&decodeThread, NULL, decodeFFmpeg, this);
}

int avformat_callback(void *ctx)
{
    HFFmpeg *fFmpeg = (HFFmpeg *) ctx;
    if(fFmpeg->playstatus->exit)
    {
        return AVERROR_EOF;
    }
    return 0;
}


void HFFmpeg::decodeFFmpegThread() {

    pthread_mutex_lock(&init_mutex);
    av_register_all();
    avformat_network_init();
    pFormatCtx = avformat_alloc_context();

    pFormatCtx->interrupt_callback.callback = avformat_callback;
    pFormatCtx->interrupt_callback.opaque = this;

    if(avformat_open_input(&pFormatCtx, url, NULL, NULL) != 0)
    {
        if(LOG_DEBUG)
        {
            LOGE("can not open url :%s", url);
        }
        callJava->onCallError(CHILD_THREAD, 1001, const_cast<char *>("can not open url"));
        exit = true;
        pthread_mutex_unlock(&init_mutex);
        return;
    }
    if(avformat_find_stream_info(pFormatCtx, NULL) < 0)
    {
        if(LOG_DEBUG)
        {
            LOGE("can not find streams from %s", url);
        }
        callJava->onCallError(CHILD_THREAD, 1002,
                              const_cast<char *>("can not find streams from url"));
        exit = true;
        pthread_mutex_unlock(&init_mutex);
        return;
    }
    //?如果一个视频中有多种音频流（中文英之类的），这里需要优化出不同的音频流对象
    for(int i = 0; i < pFormatCtx->nb_streams; i++)
    {
        if(pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO)//得到音频流
        {
            if(audio == NULL)
            {
                audio = new HAudio(playstatus, pFormatCtx->streams[i]->codecpar->sample_rate, callJava);
                audio->streamIndex = i;
                audio->codecpar = pFormatCtx->streams[i]->codecpar;
                audio->duration = static_cast<int>(pFormatCtx->duration / AV_TIME_BASE);
                audio->time_base = pFormatCtx->streams[i]->time_base;
                duration = audio->duration;
            }
        }
        else if(pFormatCtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO)//得到视频流
        {
            if(video == NULL)
            {
                video = new HVideo(playstatus, callJava);
                video->streamIndex = i;
                video->codecpar = pFormatCtx->streams[i]->codecpar;
                video->time_base = pFormatCtx->streams[i]->time_base;

                int num = pFormatCtx->streams[i]->avg_frame_rate.num;
                int den = pFormatCtx->streams[i]->avg_frame_rate.den;
                if(num != 0 && den != 0)
                {
                    int fps = num / den;//[25 / 1]
                    video->defaultDelayTime = 1.0 / fps;
                }

            }
        }

    }

    if(audio != NULL)
    {
        getCodecContext(audio->codecpar, &audio->avCodecContext);
    }
    if(video != NULL)
    {
        getCodecContext(video->codecpar, &video->avCodecContext);
    }

    if(callJava != NULL)
    {
        if(playstatus != NULL && !playstatus->exit)
        {
            callJava->onCallParpared(CHILD_THREAD);
        } else{
            exit = true;
        }
    }
    pthread_mutex_unlock(&init_mutex);
}

void HFFmpeg::start() {

    //if(audio == NULL)
    //{
     //   return;
   // }
   // if(video == NULL)
   // {
   //     return;
   // }
    video->audio = audio;



    const char* codecName = ((const AVCodec*)video->avCodecContext->codec)->name;
    supportMediacodec = callJava->onCallIsSupportVideo(codecName);
    //如果要纯软解才放开
    supportMediacodec = false;

    if(supportMediacodec)
    {
        LOGE("当前设备支持硬解码当前视频");
        if(strcasecmp(codecName,"h264")==0){
            bsFilter = av_bsf_get_by_name("h264_mp4toannexb");
        }else if(strcasecmp(codecName,"h265")==0){
            bsFilter = av_bsf_get_by_name("hevc_mp4toannexb");
        }
        if(bsFilter==NULL){
            goto end;
        }
        if(av_bsf_alloc(bsFilter, &video->abs_ctx) != 0){
            supportMediacodec = false;
            goto end;
        }
        if(avcodec_parameters_copy(video->abs_ctx->par_in, video->codecpar) < 0){
            supportMediacodec = false;
            av_bsf_free(&video->abs_ctx);
            video->abs_ctx = NULL;
            goto end;
        }
        if(av_bsf_init(video->abs_ctx) != 0){
            supportMediacodec = false;
            av_bsf_free(&video->abs_ctx);
            video->abs_ctx = NULL;
            goto end;
        }
        video->abs_ctx->time_base_in = video->time_base;
    }
    end:

    if(supportMediacodec)
    {
        video->codectype = CODEC_MEDIACODEC;
        video->callJava->onCallInitMediaCodec(
                codecName,
                video->avCodecContext->width,
                video->avCodecContext->height,
                video->avCodecContext->extradata_size,
                video->avCodecContext->extradata_size,
                video->avCodecContext->extradata,
                video->avCodecContext->extradata
        );
    }

    if(audio!=NULL)audio->play();
    if(video!=NULL)video->play();
    while(playstatus != NULL && !playstatus->exit)
    {
        if(playstatus->seek)
        {
            av_usleep(1000 * 100);
            continue;
        }
        //直播的话缓存区要小一些；如果缓存区不设置的话seek之后视频播放很可能会停止，因为没有缓存区这里很可能已经全部解析完了
        if(audio!=NULL && audio->queue->getQueueSize() > 40)
        {
            av_usleep(1000 * 100);
            continue;
        }
        AVPacket *avPacket = av_packet_alloc();

        if(av_read_frame(pFormatCtx, avPacket) == 0)
        {

            if(audio!=NULL && avPacket->stream_index == audio->streamIndex)
            {
                audio->queue->putAvpacket(avPacket);
            }
            else if(video!=NULL && avPacket->stream_index == video->streamIndex)
            {
                video->queue->putAvpacket(avPacket);
            }
            else{
                av_packet_free(&avPacket);
                av_free(avPacket);
            }
        } else{
            av_packet_free(&avPacket);
            av_free(avPacket);
            while(playstatus != NULL && !playstatus->exit)
            {
                //读取缓存区
                if(audio!=NULL && audio->queue->getQueueSize() > 0)
                {
                    av_usleep(1000 * 100);
                    continue;
                } else{
                    if(!playstatus->seek){
                        av_usleep(1000*500);//睡眠500毫秒足够把最后一帧播放出来
                        playstatus->exit = true;
                    }
                    break;
                }
            }
            break;
        }
    }
    if(callJava != NULL)
    {
        callJava->onCallComplete(CHILD_THREAD);
    }
    exit = true;

}

void HFFmpeg::pause() {
    //视频暂停
    if(playstatus!=NULL){
        playstatus->pause = true;
    }
    //音频暂停
    if(audio != NULL)
    {
        audio->pause();
    }
}

void HFFmpeg::resume() {
    if(playstatus!=NULL){
        playstatus->pause = false;
    }

    if(audio != NULL)
    {
        audio->resume();
    }
}

void HFFmpeg::release() {

    if(LOG_DEBUG)
        LOGE("开始释放Ffmpeg");

    playstatus->exit = true;

    pthread_join(decodeThread, NULL);

    pthread_mutex_lock(&init_mutex);

    int sleepCount = 0;
    while (!exit)
    {
        if(sleepCount > 1000)
        {
            exit = true;
        }
        if(LOG_DEBUG)
        {
            LOGE("wait ffmpeg  exit %d", sleepCount);
        }
        sleepCount++;
        av_usleep(1000 * 10);//暂停10毫秒
    }

    if(LOG_DEBUG)
    {
        LOGE("释放 Audio");
    }

    if(audio != NULL)
    {
        audio->release();
        delete(audio);
        audio = NULL;
    }
    if(LOG_DEBUG)
    {
        LOGE("释放 video");
    }
    if(video != NULL)
    {
        video->release();
        delete(video);
        video = NULL;
    }
    if(LOG_DEBUG)
    {
        LOGE("释放 封装格式上下文");
    }
    if(pFormatCtx != NULL)
    {
        avformat_close_input(&pFormatCtx);
        avformat_free_context(pFormatCtx);
        pFormatCtx = NULL;
    }
    if(LOG_DEBUG)
    {
        LOGE("释放 callJava");
    }
    if(callJava != NULL)
    {
        callJava = NULL;
    }
    if(LOG_DEBUG)
    {
        LOGE("释放 playstatus");
    }
    if(playstatus != NULL)
    {
        playstatus = NULL;
    }
    pthread_mutex_unlock(&init_mutex);
}

HFFmpeg::~HFFmpeg() {
    pthread_mutex_destroy(&init_mutex);
    pthread_mutex_destroy(&seek_mutex);
}

void HFFmpeg::seek(int64_t secds) {

    if(duration <= 0)
    {
        return;
    }
    if(secds >= 0 && secds <= duration)
    {
        playstatus->seek = true;
        pthread_mutex_lock(&seek_mutex);
        int64_t rel = secds * AV_TIME_BASE;
        avformat_seek_file(pFormatCtx, -1, INT64_MIN, rel, INT64_MAX, 0);
        if(audio != NULL)
        {
            audio->queue->clearAvpacket();
            audio->clock = 0;
            audio->last_tiem = 0;
            pthread_mutex_lock(&audio->codecMutex);
            avcodec_flush_buffers(audio->avCodecContext);
            pthread_mutex_unlock(&audio->codecMutex);
        }

        if(video!=NULL){
            video->queue->clearAvpacket();
            video->clock=0;
            pthread_mutex_lock(&video->codecMutex);
            avcodec_flush_buffers(video->avCodecContext);
            pthread_mutex_unlock(&video->codecMutex);
        }
        pthread_mutex_unlock(&seek_mutex);
        playstatus->seek = false;
    }
}

int HFFmpeg::getCodecContext(AVCodecParameters *codecpar, AVCodecContext **avCodecContext) {
    AVCodec *dec = avcodec_find_decoder(codecpar->codec_id);
    if(!dec)
    {
        if(LOG_DEBUG)
        {
            LOGE("can not find decoder");
        }
        callJava->onCallError(CHILD_THREAD, 1003, const_cast<char *>("can not find decoder"));
        exit = true;
        pthread_mutex_unlock(&init_mutex);
        return -1;
    }

    *avCodecContext = avcodec_alloc_context3(dec);
    if(audio!=NULL && !audio->avCodecContext)
    {
        if(LOG_DEBUG)
        {
            LOGE("can not alloc new decodecctx");
        }
        callJava->onCallError(CHILD_THREAD, 1004,
                              const_cast<char *>("can not alloc new decodecctx"));
        exit = true;
        pthread_mutex_unlock(&init_mutex);
        return -1;
    }

    if(avcodec_parameters_to_context(*avCodecContext, codecpar) < 0)
    {
        if(LOG_DEBUG)
        {
            LOGE("can not fill decodecctx");
        }
        callJava->onCallError(CHILD_THREAD, 1005, const_cast<char *>("ccan not fill decodecctx"));
        exit = true;
        pthread_mutex_unlock(&init_mutex);
        return -1;
    }

    if(avcodec_open2(*avCodecContext, dec, 0) != 0)
    {
        if(LOG_DEBUG)
        {
            LOGE("cant not open audio strames");
        }
        callJava->onCallError(CHILD_THREAD, 1006, const_cast<char *>("cant not open audio strames"));
        exit = true;
        pthread_mutex_unlock(&init_mutex);
        return -1;
    }
    return 0;
}
