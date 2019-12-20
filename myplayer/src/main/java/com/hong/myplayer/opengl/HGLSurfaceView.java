package com.hong.myplayer.opengl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class HGLSurfaceView extends GLSurfaceView{

    private HRender render;

    public HGLSurfaceView(Context context) {
        this(context, null);
    }

    public HGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        render = new HRender(context);
        setRenderer(render);
        //脏模式，调用一次requestRender渲染一次。    GLSurfaceView.RENDERMODE_CONTINUOUSLY则是一直渲染，能达到60帧/秒(相当于16ms一帧)，比较耗费资源
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        render.setOnRenderListener(new HRender.OnRenderListener() {
            @Override
            public void onRender() {
                requestRender();
            }
        });
    }

    public void setYUVData(int width, int height, byte[] y, byte[] u, byte[] v)
    {
        if(render != null)
        {
            //把YUV数据设置进Render里然后调用requestRender进行渲染
            render.setYUVRenderData(width, height, y, u, v);
            requestRender();//触发onDrawFrame方法
        }
    }

    public HRender getRender() {
        return render;
    }
}
