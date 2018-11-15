package com.hong.myplayer.player;

import android.text.TextUtils;

import com.hong.myplayer.listener.HOnCompleteListener;
import com.hong.myplayer.listener.HOnErrorListener;
import com.hong.myplayer.listener.HOnLoadListener;
import com.hong.myplayer.listener.HOnParparedListener;
import com.hong.myplayer.listener.HOnPauseResumeListener;
import com.hong.myplayer.listener.HOnTimeInfoListener;
import com.hong.myplayer.log.MyLog;
import com.hong.myplayer.opengl.HGLSurfaceView;
import com.ywl5320.myplayer.HTimeInfoBean;

/**
 * Created by yangw on 2018-2-28.
 */

public class HPlayer {

    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("avcodec-57");
        System.loadLibrary("avdevice-57");
        System.loadLibrary("avfilter-6");
        System.loadLibrary("avformat-57");
        System.loadLibrary("avutil-55");
        System.loadLibrary("postproc-54");
        System.loadLibrary("swresample-2");
        System.loadLibrary("swscale-4");
    }

    private static String source;//数据源
    private static HTimeInfoBean wlTimeInfoBean;
    private static boolean playNext = false;
    private HOnParparedListener wlOnParparedListener;
    private HOnLoadListener wlOnLoadListener;
    private HOnPauseResumeListener wlOnPauseResumeListener;
    private HOnTimeInfoListener wlOnTimeInfoListener;
    private HOnErrorListener wlOnErrorListener;
    private HOnCompleteListener wlOnCompleteListener;

    private HGLSurfaceView surfaceView;

    public HPlayer()
    {}

    /**
     * 设置数据源
     * @param source
     */
    public void setSource(String source)
    {
        this.source = source;
    }

    public void setSurfaceView(HGLSurfaceView surfaceView) {
        this.surfaceView = surfaceView;
    }

    /**
     * 设置准备接口回调
     * @param wlOnParparedListener
     */
    public void setOnParparedListener(HOnParparedListener wlOnParparedListener)
    {
        this.wlOnParparedListener = wlOnParparedListener;
    }

    public void setOnLoadListener(HOnLoadListener wlOnLoadListener) {
        this.wlOnLoadListener = wlOnLoadListener;
    }

    public void setOnPauseResumeListener(HOnPauseResumeListener wlOnPauseResumeListener) {
        this.wlOnPauseResumeListener = wlOnPauseResumeListener;
    }

    public void setOnTimeInfoListener(HOnTimeInfoListener wlOnTimeInfoListener) {
        this.wlOnTimeInfoListener = wlOnTimeInfoListener;
    }

    public void setOnErrorListener(HOnErrorListener wlOnErrorListener) {
        this.wlOnErrorListener = wlOnErrorListener;
    }

    public void setOnCompleteListener(HOnCompleteListener wlOnCompleteListener) {
        this.wlOnCompleteListener = wlOnCompleteListener;
    }

    public void parpared()
    {
        if(TextUtils.isEmpty(source))
        {
            MyLog.d("source not be empty");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                n_parpared(source);
            }
        }).start();

    }

    public void start()
    {
        if(TextUtils.isEmpty(source))
        {
            MyLog.d("source is empty");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                n_start();
            }
        }).start();
    }

    public void pause()
    {
        n_pause();
        if(wlOnPauseResumeListener != null)
        {
            wlOnPauseResumeListener.onPause(true);
        }
    }

    public void resume()
    {
        n_resume();
        if(wlOnPauseResumeListener != null)
        {
            wlOnPauseResumeListener.onPause(false);
        }
    }

    public void stop()
    {
        wlTimeInfoBean = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                n_stop();
            }
        }).start();
    }

    public void seek(int secds)
    {
        n_seek(secds);
    }

    public void playNext(String url)
    {
        source = url;
        playNext = true;
        stop();
    }





    /**
     * c++回调java的方法
     */
    public void onCallParpared()
    {
        if(wlOnParparedListener != null)
        {
            wlOnParparedListener.onParpared();
        }
    }

    public void onCallLoad(boolean load)
    {
        if(wlOnLoadListener != null)
        {
            wlOnLoadListener.onLoad(load);
        }
    }

    public void onCallTimeInfo(int currentTime, int totalTime)
    {
        if(wlOnTimeInfoListener != null)
        {
            if(wlTimeInfoBean == null)
            {
                wlTimeInfoBean = new HTimeInfoBean();
            }
            wlTimeInfoBean.setCurrentTime(currentTime);
            wlTimeInfoBean.setTotalTime(totalTime);
            wlOnTimeInfoListener.onTimeInfo(wlTimeInfoBean);
        }
    }

    public void onCallError(int code, String msg)
    {
        if(wlOnErrorListener != null)
        {
            stop();
            wlOnErrorListener.onError(code, msg);
        }
    }

    public void onCallComplete()
    {
        if(wlOnCompleteListener != null)
        {
            stop();
            wlOnCompleteListener.onComplete();
        }
    }

    public void onCallNext()
    {
        if(playNext)
        {
            playNext = false;
            parpared();
        }
    }

    public void onCallRenderYUV(int width,int height,byte[]y,byte[]u,byte[]v){
        MyLog.d("获取到视频的YUV数据");
        if(surfaceView!=null)surfaceView.setYUVData(width, height, y, u, v);
    }

    private native void n_parpared(String source);
    private native void n_start();
    private native void n_pause();
    private native void n_resume();
    private native void n_stop();
    private native void n_seek(int secds);





}
