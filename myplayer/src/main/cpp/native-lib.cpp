#include <jni.h>
#include <string>
#include "HFFmpeg.h"
#include "HPlaystatus.h"

extern "C"
{
#include <libavformat/avformat.h>
}


_JavaVM *javaVM = NULL;
HCallJava *callJava = NULL;
HFFmpeg *fFmpeg = NULL;
HPlaystatus *playstatus = NULL;

bool nexit = true;
pthread_t thread_start;


extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    jint result = -1;
    javaVM = vm;
    JNIEnv *env;
    if(vm->GetEnv((void **) &env, JNI_VERSION_1_4) != JNI_OK)
    {

        return result;
    }
    return JNI_VERSION_1_4;

}

extern "C"
JNIEXPORT void JNICALL
Java_com_hong_myplayer_player_HPlayer_n_1parpared(JNIEnv *env, jobject instance,
                                                      jstring source_) {
    const char *source = env->GetStringUTFChars(source_, 0);

    if(fFmpeg == NULL)
    {
        if(callJava == NULL)
        {
            callJava = new HCallJava(javaVM, env, &instance);
        }
        callJava->onCallLoad(MAIN_THREAD, true);
        playstatus = new HPlaystatus();
        fFmpeg = new HFFmpeg(playstatus, callJava, source);
        fFmpeg->parpared();
    }
}

void *startCallBack(void *data)
{
    HFFmpeg *fFmpeg = (HFFmpeg *) data;
    fFmpeg->start();
//    pthread_exit(&thread_start);
    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_hong_myplayer_player_HPlayer_n_1start(JNIEnv *env, jobject instance) {

    // TODO
    if(fFmpeg != NULL)
    {
        pthread_create(&thread_start, NULL, startCallBack, fFmpeg);
    }

}extern "C"
JNIEXPORT void JNICALL
Java_com_hong_myplayer_player_HPlayer_n_1pause(JNIEnv *env, jobject instance) {

    // TODO
    if(fFmpeg != NULL)
    {
        fFmpeg->pause();
    }

}extern "C"
JNIEXPORT void JNICALL
Java_com_hong_myplayer_player_HPlayer_n_1resume(JNIEnv *env, jobject instance) {

    // TODO
    if(fFmpeg != NULL)
    {
        fFmpeg->resume();
    }

}extern "C"
JNIEXPORT void JNICALL
Java_com_hong_myplayer_player_HPlayer_n_1stop(JNIEnv *env, jobject instance) {
    if(!nexit)
        return;

    jclass clz = env->GetObjectClass(instance);
    jmethodID jmid_next = env->GetMethodID(clz, "onCallNext", "()V");

    nexit = false;
    if(fFmpeg != NULL)
    {
        fFmpeg->release();
        pthread_join(thread_start, NULL);
        delete(fFmpeg);
        fFmpeg = NULL;
        if(callJava != NULL)
        {
            delete(callJava);
            callJava = NULL;
        }
        if(playstatus != NULL)
        {
            delete(playstatus);
            playstatus = NULL;
        }
    }
    nexit = true;
    env->CallVoidMethod(instance, jmid_next);
}extern "C"
JNIEXPORT void JNICALL
Java_com_hong_myplayer_player_HPlayer_n_1seek(JNIEnv *env, jobject instance, jint secds) {

    // TODO
    if(fFmpeg != NULL)
    {
        fFmpeg->seek(secds);
    }

}