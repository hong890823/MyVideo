package com.hong.myvideo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.hong.myplayer.listener.HOnCompleteListener;
import com.hong.myplayer.listener.HOnErrorListener;
import com.hong.myplayer.listener.HOnLoadListener;
import com.hong.myplayer.listener.HOnParparedListener;
import com.hong.myplayer.listener.HOnPauseResumeListener;
import com.hong.myplayer.listener.HOnTimeInfoListener;
import com.hong.myplayer.log.MyLog;
import com.hong.myplayer.opengl.HGLSurfaceView;
import com.hong.myplayer.player.HPlayer;
import com.ywl5320.myplayer.HTimeInfoBean;

public class MainActivity extends AppCompatActivity {
    private HPlayer wlPlayer;
    private TextView tvTime;

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
                MyLog.d("准备好了，可以开始播放声音了");
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

    }

    public void begin(View view) {
//        String path = Environment.getExternalStorageDirectory()+"/test.mp4";
        //夜神模拟器路径
        String path = "mnt/shared/Other/欧文.mp4";
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
                HTimeInfoBean wlTimeInfoBean = (HTimeInfoBean) msg.obj;
                tvTime.setText(com.ywl5320.myplayer.util.HTimeUtil.secdsToDateFormat(wlTimeInfoBean.getTotalTime(), wlTimeInfoBean.getTotalTime())
                + "/" + com.ywl5320.myplayer.util.HTimeUtil.secdsToDateFormat(wlTimeInfoBean.getCurrentTime(), wlTimeInfoBean.getTotalTime()));
            }
        }
    };

    public void stop(View view) {
        wlPlayer.stop();
    }

    public void seek(View view) {
        wlPlayer.seek(100);
    }

    public void next(View view) {
        //wlPlayer.playNext("/mnt/shared/Other/testvideo/楚乔传第一集.mp4");
    }
}
