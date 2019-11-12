package com.hong.myvideo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.hong.myplayer.HTimeInfoBean;
import com.hong.myplayer.listener.HOnCompleteListener;
import com.hong.myplayer.listener.HOnErrorListener;
import com.hong.myplayer.listener.HOnLoadListener;
import com.hong.myplayer.listener.HOnParparedListener;
import com.hong.myplayer.listener.HOnPauseResumeListener;
import com.hong.myplayer.listener.HOnTimeInfoListener;
import com.hong.myplayer.log.MyLog;
import com.hong.myplayer.opengl.HGLSurfaceView;
import com.hong.myplayer.player.HPlayer;
import com.hong.myplayer.util.HTimeUtil;

public class MainActivity extends AppCompatActivity {
    private HPlayer wlPlayer;
    private TextView tvTime;
    private SeekBar seekBar;
    private int position;//seek的进度
    private boolean isSeeking;//是否正在seek

    private static final int REQUEST_EXTERNAL=1;
    private static String[] PERMISSIONS= {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
    };

    /**
     * Android6.0以上校验文件读写权限
     */
    public void verifyStoragePermissions(Activity activity) {
        int writePermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int readPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE);
        int internetPermission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.INTERNET);

        if (writePermission != PackageManager.PERMISSION_GRANTED || readPermission != PackageManager.PERMISSION_GRANTED
                || internetPermission != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(activity,PERMISSIONS,REQUEST_EXTERNAL);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        verifyStoragePermissions(this);
        tvTime = findViewById(R.id.tv_time);
        HGLSurfaceView surfaceView = findViewById(R.id.surface_view);
        wlPlayer = new HPlayer();
        wlPlayer.setSurfaceView(surfaceView);
        wlPlayer.setOnParparedListener(new HOnParparedListener() {
            @Override
            public void onParpared() {
                MyLog.d("准备好了，可以开始播放了");
                wlPlayer.start();
            }
        });
        wlPlayer.setOnLoadListener(new HOnLoadListener() {
            @Override
            public void onLoad(boolean load) {
                if(load)
                {
                    MyLog.d("加载中...");
                }
                else
                {
                    MyLog.d("播放中...");
                }
            }
        });

        wlPlayer.setOnPauseResumeListener(new HOnPauseResumeListener() {
            @Override
            public void onPause(boolean pause) {
                if(pause)
                {
                    MyLog.d("暂停中...");
                }
                else
                {
                    MyLog.d("播放中...");
                }
            }
        });

        wlPlayer.setOnTimeInfoListener(new HOnTimeInfoListener() {
            @Override
            public void onTimeInfo(HTimeInfoBean timeInfoBean) {
//                MyLog.d(timeInfoBean.toString());
                Message message = Message.obtain();
                message.what = 1;
                message.obj = timeInfoBean;
                handler.sendMessage(message);

            }
        });

        wlPlayer.setOnErrorListener(new HOnErrorListener() {
            @Override
            public void onError(int code, String msg) {
                MyLog.d("code:" + code + ", msg:" + msg);
            }
        });

        wlPlayer.setOnCompleteListener(new HOnCompleteListener() {
            @Override
            public void onComplete() {
                MyLog.d("播放完成了");
            }
        });

        seekBar = findViewById(R.id.video_seek_view);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int totalTime = wlPlayer.getDuration();
                position = totalTime*progress/100;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                wlPlayer.seek(position);
                isSeeking = false;
            }
        });
    }

    public void begin(View view) {
        String path = Environment.getExternalStorageDirectory()+"/屌丝男士.mov";
        //夜神模拟器路径
//        String path = "mnt/shared/Other/欧文.mp4";
//        path = "rtsp://184.72.239.149/vod/mp4://BigBuckBunny_175k.mov";
//        path = "rtsp://10.1.21.159:8554/channel=0";
//        path = "rtmp://live.hkstv.hk.lxdns.com/live/hks";

//        path = "rtsp://nps.bjzst.cn:7008/cam/realmonitor";
        path = "rtsp://10.1.26.189:554/screen111.sdp";
        path = "http://js.live-play.acgvideo.com/live-js/798500/live_36514186_3847856.flv?wsSecret=9ef2a98b2dab0ea2d0fd8e29285630d9&wsTime=1562665541&trid=10319457c58f48a5afb8f680e92f7f3a&order=1&sig=no";
        path = "http://baobab.kaiyanapp.com/api/v1/playUrl?vid=172434&resourceType=video&editionType=default&source=aliyun&playUrlType=url_oss";
        wlPlayer.setSource(path);
//        wlPlayer.setSource("http://mpge.5nd.com/2015/2015-11-26/69708/1.mp3");
//        wlPlayer.setSource("http://ngcdn004.cnr.cn/live/dszs/index12.m3u8");
        wlPlayer.parpared();
    }

    public void pause(View view) {
        wlPlayer.pause();
    }

    public void resume(View view) {
        wlPlayer.resume();
    }

    Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.what == 1)
            {
                HTimeInfoBean timeInfoBean = (HTimeInfoBean) msg.obj;
                tvTime.setText(HTimeUtil.secdsToDateFormat(timeInfoBean.getTotalTime(), timeInfoBean.getTotalTime())
                + "/" + HTimeUtil.secdsToDateFormat(timeInfoBean.getCurrentTime(), timeInfoBean.getTotalTime()));

                if(!isSeeking && timeInfoBean.getTotalTime()>0){
                    seekBar.setProgress(timeInfoBean.getCurrentTime()*100/timeInfoBean.getTotalTime());
                }

            }
        }
    };

    public void stop(View view) {
        wlPlayer.stop();
    }

    public void next(View view) {
        String path = Environment.getExternalStorageDirectory()+"/建国大业.mpg";
        wlPlayer.playNext(path);
    }
}
