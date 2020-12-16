attribute vec4 av_Position;//顶点坐标系
attribute vec2 af_Position;//纹理坐标系
varying vec2 v_texPosition;//连接顶点着色器和片元着色器，该属性可以在片元着色器中使用
void main() {
    v_texPosition = af_Position;
    gl_Position = av_Position;
}

/*
vec4：x,y,z,w
attribute： 表示属性，attribute只能在顶点着色器中使用
*/