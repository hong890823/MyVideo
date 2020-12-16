package com.hong.myplayer.opengl;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

//加载raw中Shader着色器的相关工具类

public class HShaderUtil {

    public static String readRawTxt(Context context, int rawId) {
        InputStream inputStream = context.getResources().openRawResource(rawId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuffer sb = new StringBuffer();
        String line;
        try
        {
            while((line = reader.readLine()) != null)
            {
                sb.append(line).append("\n");
            }
            reader.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static int loadShader(int shaderType, String source)
    {
        int shader = GLES20.glCreateShader(shaderType);//创建Shader
        if(shader != 0)
        {
            GLES20.glShaderSource(shader, source);//把我们raw中的着色器编译进刚创建的shader中
            GLES20.glCompileShader(shader);//编译shader
            int[] compile = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compile, 0);
            if(compile[0] != GLES20.GL_TRUE)
            {
                Log.d("TTT", "shader compile error");
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    /**
     * @param vertexSource raw中的顶点着色器文本
     * @param fragmentSource raw中的片元着色器文本
     * */
    public static int createProgram(String vertexSource, String fragmentSource)
    {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if(vertexShader == 0)
        {
            return 0;
        }
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if(fragmentShader == 0)
        {
            return 0;
        }
        int program = GLES20.glCreateProgram();//创建OpenGL的主程序
        if(program != 0)
        {
            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);//连接GLES20到program上
            int[] linsStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linsStatus, 0);
            if(linsStatus[0] != GLES20.GL_TRUE)
            {
                Log.d("TTT", "link program error");
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return  program;

    }

}
