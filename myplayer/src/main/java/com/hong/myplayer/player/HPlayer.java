package com.hong.myplayer.player;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.text.TextUtils;
import android.view.Surface;

import com.hong.myplayer.HTimeInfoBean;
import com.hong.myplayer.listener.HOnCompleteListener;
import com.hong.myplayer.listener.HOnErrorListener;
import com.hong.myplayer.listener.HOnLoadListener;
import com.hong.myplayer.listener.HOnParparedListener;
import com.hong.myplayer.listener.HOnPauseResumeListener;
import com.hong.myplayer.listener.HOnTimeInfoListener;
import com.hong.myplayer.log.MyLog;
import com.hong.myplayer.opengl.HGLSurfaceView;
import com.hong.myplayer.opengl.HRender;
import com.hong.myplayer.util.HVideoSupportUitl;

import java.nio.ByteBuffer;

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
    private int duration = 0;
    private HGLSurfaceView surfaceView;

    private MediaFormat mediaFormat;
    private MediaCodec mediaCodec;
    private Surface surface;
    private MediaCodec.BufferInfo info;

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
        surfaceView.getRender().setOnSurfaceCreateListener(new HRender.OnSurfaceCreateListener() {
            @Override
            public void onSurfaceCreate(Surface s) {
               if(surface==null) surface = s;
            }
        });
    }

    public int getDuration() {
        return duration;
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
        duration=0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                n_stop();
                releaseMediaCodec();
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
            duration = totalTime;
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
        surfaceView.getRender().setRenderType(HRender.RENDER_YUV);
        if(surfaceView!=null)surfaceView.setYUVData(width, height, y, u, v);
    }

    public boolean onCallIsSupportMediaCodec(String ffcodecname){
        return HVideoSupportUitl.isSupportCodec(ffcodecname);
    }

    /**
     * 初始化MediaCodec
     * @param codecName
     * @param width
     * @param height
     * @param csd_0
     * @param csd_1
     */
    public void initMediaCodec(String codecName, int width, int height, byte[] csd_0, byte[] csd_1)
    {
        if(surface != null)
        {
            try {
                surfaceView.getRender().setRenderType(HRender.RENDER_MEDIACODEC);

                String mime = HVideoSupportUitl.findVideoCodecName(codecName);
                mediaFormat = MediaFormat.createVideoFormat(mime, width, height);
                mediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height);
                mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csd_0));
                mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(csd_1));
                MyLog.d(mediaFormat.toString());
                mediaCodec = MediaCodec.createDecoderByType(mime);

                info = new MediaCodec.BufferInfo();
                mediaCodec.configure(mediaFormat, surface, null, 0);
                mediaCodec.start();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            if(wlOnErrorListener != null)
            {
                wlOnErrorListener.onError(2001, "surface is null");
            }
        }
    }

    public void decodeAVPacket(int dataSize, byte[] data)
    {
        if(surface != null && dataSize > 0 && data != null && mediaCodec!=null)
        {
            try{
                int intputBufferIndex = mediaCodec.dequeueInputBuffer(10);
                if(intputBufferIndex >= 0)
                {
                    ByteBuffer byteBuffer = mediaCodec.getInputBuffers()[intputBufferIndex];
                    byteBuffer.clear();
                    byteBuffer.put(data);
                    mediaCodec.queueInputBuffer(intputBufferIndex, 0, dataSize, 0, 0);
                }
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 10);
                while(outputBufferIndex >= 0)
                {
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                    outputBufferIndex = mediaCodec.dequeueOutputBuffer(info, 10);
                }
            }catch(Exception e){
                e.printStackTrace();
            }

        }
    }
    private void releaseMediaCodec(){
        if(mediaCodec != null){
            try{
                mediaCodec.flush();
                mediaCodec.stop();
                mediaCodec.release();
            }catch(Exception e){
                e.printStackTrace();
            }
            mediaCodec = null;
            mediaFormat = null;
            info = null;
        }

    }

    private native void n_parpared(String source);
    private native void n_start();
    private native void n_pause();
    private native void n_resume();
    private native void n_stop();
    private native void n_seek(int seconds);





}
