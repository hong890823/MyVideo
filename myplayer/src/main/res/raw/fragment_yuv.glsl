precision mediump float;
varying vec2 v_texPosition;
uniform sampler2D sampler_y;
uniform sampler2D sampler_u;
uniform sampler2D sampler_v;
void main() {
    float y,u,v;
    //r值可以替换成g，b，效果一样
    y = texture2D(sampler_y,v_texPosition).r;
    u = texture2D(sampler_u,v_texPosition).r- 0.5;
    v = texture2D(sampler_v,v_texPosition).r- 0.5;

    vec3 rgb;
    rgb.r = y + 1.403 * v;
    rgb.g = y - 0.344 * u - 0.714 * v;
    rgb.b = y + 1.770 * u;

    gl_FragColor = vec4(rgb,1);
}


/*
precision mediump float：片元着色器的精度，这里是中等
这里的vec4又变成了r,g,b,a了...
*/