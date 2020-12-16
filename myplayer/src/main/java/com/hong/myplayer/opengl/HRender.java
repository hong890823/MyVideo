package com.hong.myplayer.opengl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.view.Surface;

import com.hong.myplayer.R;
import com.hong.myplayer.log.MyLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class HRender implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    public static final int RENDER_YUV = 1;
    public static final int RENDER_MEDIACODEC = 2;

    private Context context;

    /*
    * 一：顶点坐标系和Android坐标系不同
    * 它是以屏幕中心为原点，x轴右正，y轴上正
    *
    * 二：OpenGL的顶点数据
    * 我们自己定义的顶点数据又是怎么给到OpenGL的呢？请参考1，2，3
    *
    * 三：OpenGL只能画点，线，三角形，没有四边形多边形
    * 图形环绕方向必须一致
    * */
    private final float[] vertexData ={

            -1f, -1f,//左下
            1f, -1f,//右下
            -1f, 1f,//左上
            1f, 1f//右上

    };

    /*
     * 一：纹理坐标系和顶点坐标系不同，和Android屏幕的坐标系是相同的。
     * 需要把顶点坐标系的每个顶点映射到纹理坐标系对应的位置上，得到以下四个点
     * */
    private final float[] textureData ={
            0f,1f,//左下
            1f, 1f,//右下
            0f, 0f,//左上
            1f, 0f//右上
    };

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private int renderType = RENDER_YUV;

    //yuv
    private int program_yuv;//代表OpenGL的主程序
    private int avPosition_yuv;
    private int afPosition_yuv;

    private int sampler_y;
    private int sampler_u;
    private int sampler_v;
    private int[] textureId_yuv;

    private int width_yuv;
    private int height_yuv;
    private ByteBuffer y;
    private ByteBuffer u;
    private ByteBuffer v;

    //MediaCodec
    private int program_mediacodec;
    private int avPosition_mediacodec;
    private int afPosition_mediacodec;
    private int samplerOES_mediacodec;
    private int textureId_mediacodec;
    private SurfaceTexture surfaceTexture;
    private Surface surface;

    private OnSurfaceCreateListener onSurfaceCreateListener;
    private OnRenderListener onRenderListener;

    HRender(Context context)
    {
        this.context = context;
        /*
        * ByteBuffer声明的空间是在native层声明的空间，这样不会担心被GC回收掉
        * 为什么 * 4呢？因为是vertexData数组是float类型的，每个float占4个字节
        * 1，这里得到的vertexBuffer也就是我们把顶点坐标vertexData放到了buffer中了
        * */
        vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())//对齐方式和本地及其一样
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);//指向数据的起始位置

        textureBuffer = ByteBuffer.allocateDirect(textureData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureData);
        textureBuffer.position(0);
    }
    public void setRenderType(int renderType) {
        this.renderType = renderType;
    }

    public void setOnSurfaceCreateListener(OnSurfaceCreateListener onSurfaceCreateListener) {
        this.onSurfaceCreateListener = onSurfaceCreateListener;
    }

    public void setOnRenderListener(OnRenderListener onRenderListener) {
        this.onRenderListener = onRenderListener;
    }

    //surface创建的时候回掉
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        initRenderYUV();
        initRenderMediaCodec();
    }

    //surface改变的时候回调
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);//设置视频画面的宽高，横竖屏的时候会自动切换
    }

    //surface画帧
    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);//清屏颜色缓冲区
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);//用某种颜色清屏，然后整个屏幕就都是这种颜色了
        if(renderType == RENDER_YUV) {
            renderYUV();
        }
        else if(renderType == RENDER_MEDIACODEC){
            renderMediaCodec();
        }
        //不管数据是否符合条件都要画一遍矩形，否则会出现黑色闪屏
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    //硬解刷新界面的方法。SurfaceTexture有数据就会调动这个方法，surfacetexture绑定的surface给了mediacodec.
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        MyLog.d("onFrameAvailable");
        if(onRenderListener != null) {
            onRenderListener.onRender();
        }
    }

    private void initRenderYUV()
    {
        String vertexSource = HShaderUtil.readRawTxt(context, R.raw.vertex_shader);
        String fragmentSource = HShaderUtil.readRawTxt(context, R.raw.fragment_yuv);
        program_yuv = HShaderUtil.createProgram(vertexSource, fragmentSource);

        /*
        * 2，拿OpenGl脚本语言中gl_Position属性（该属性是OpenGL规定的，我们不可以随意更改）的指向av_Position（这个值是我们自己定义的）
        * 转换成了int值avPosition_yuv，接下来操作的就是avPosition_yuv，操作avPosition_yuv也就代表我们操作了OpenGL的gl_Position属性。
        * 通过啥转换的呢？通过program_yuv，也就是OpenGl连接过来的主程序（就这么理解吧）
        * */
        avPosition_yuv = GLES20.glGetAttribLocation(program_yuv, "av_Position");
        afPosition_yuv = GLES20.glGetAttribLocation(program_yuv, "af_Position");

        sampler_y = GLES20.glGetUniformLocation(program_yuv, "sampler_y");
        sampler_u = GLES20.glGetUniformLocation(program_yuv, "sampler_u");
        sampler_v = GLES20.glGetUniformLocation(program_yuv, "sampler_v");

        //创建纹理
        textureId_yuv = new int[3];
        GLES20.glGenTextures(3, textureId_yuv, 0);
        for(int i = 0; i < 3; i++)
        {
            //绑定纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[i]);
            //设置纹理的环绕模式
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);//s对应x，超出边界则重复
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);//t对应y，超出边界则重复
            //设置纹理的过滤方式，纹理像素以什么样的方式映射到坐标点
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        }
          //设置图片
//        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(),R.drawable.yangmi);
//        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);
    }

    public void setYUVRenderData(int width, int height, byte[] y, byte[] u, byte[] v)
    {
        this.width_yuv = width;
        this.height_yuv = height;
        this.y = ByteBuffer.wrap(y);
        this.u = ByteBuffer.wrap(u);
        this.v = ByteBuffer.wrap(v);
    }

    private void renderYUV()
    {
        if(width_yuv > 0 && height_yuv > 0 && y != null && u != null && v != null)
        {
            GLES20.glUseProgram(program_yuv);

            /*
            * 3，操作avPosition_yuv吧，通过vertexBuffer和我们定义的顶点vertexData范围勾结上了
            * */
            GLES20.glEnableVertexAttribArray(avPosition_yuv);
            GLES20.glVertexAttribPointer(avPosition_yuv, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

            GLES20.glEnableVertexAttribArray(afPosition_yuv);
            GLES20.glVertexAttribPointer(afPosition_yuv, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);

//            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6);//两个三角形拼接成一个四边形
//            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);//三角形带画出四边形

            //设置YUV纹理（这里和YUV的具体方式的渲染模式有关）
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);//激活第一个纹理
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[0]);//绑定到textureId_yuv数组的第一个元素上
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width_yuv, height_yuv, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, y);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[1]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width_yuv / 2, height_yuv / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, u);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId_yuv[2]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width_yuv / 2, height_yuv / 2, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, v);

            //使用YUV纹理
            GLES20.glUniform1i(sampler_y, 0);//0对应GLES20.GL_TEXTURE0
            GLES20.glUniform1i(sampler_u, 1);
            GLES20.glUniform1i(sampler_v, 2);

            y.clear();
            u.clear();
            v.clear();
            y = null;
            u = null;
            v = null;
        }
    }

    private void initRenderMediaCodec()
    {
        String vertexSource = HShaderUtil.readRawTxt(context, R.raw.vertex_shader);
        String fragmentSource = HShaderUtil.readRawTxt(context, R.raw.fragment_mediacodec);
        program_mediacodec = HShaderUtil.createProgram(vertexSource, fragmentSource);

        avPosition_mediacodec = GLES20.glGetAttribLocation(program_mediacodec, "av_Position");
        afPosition_mediacodec = GLES20.glGetAttribLocation(program_mediacodec, "af_Position");
        samplerOES_mediacodec = GLES20.glGetUniformLocation(program_mediacodec, "sTexture");

        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        textureId_mediacodec = textureIds[0];

        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        surfaceTexture = new SurfaceTexture(textureId_mediacodec);
        surface = new Surface(surfaceTexture);
        surfaceTexture.setOnFrameAvailableListener(this);

        if(onSurfaceCreateListener != null){
            onSurfaceCreateListener.onSurfaceCreate(surface);
        }
    }

    private void renderMediaCodec()
    {
        surfaceTexture.updateTexImage();
        GLES20.glUseProgram(program_mediacodec);

        GLES20.glEnableVertexAttribArray(avPosition_mediacodec);
        GLES20.glVertexAttribPointer(avPosition_mediacodec, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer);

        GLES20.glEnableVertexAttribArray(afPosition_mediacodec);
        GLES20.glVertexAttribPointer(afPosition_mediacodec, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId_mediacodec);
        GLES20.glUniform1i(samplerOES_mediacodec, 0);
    }

    public interface OnSurfaceCreateListener
    {
        void onSurfaceCreate(Surface surface);
    }

    public interface OnRenderListener{
        void onRender();
    }

}
