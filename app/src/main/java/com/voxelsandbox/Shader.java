package com.voxelsandbox;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 着色器程序封装。顶点属性：aPos(3) aUv(2) aLight(1)，stride=6 floats。
 */
public class Shader {
    private static final String TAG = "Shader";

    public final int program;
    public final int aPos, aUv, aLight, uMVP, uTex, uLightTint, uCamPos, uFogColor, uFogParam;

    private static final String VS =
        "attribute vec3 aPos;\n" +
        "attribute vec2 aUv;\n" +
        "attribute float aLight;\n" +
        "uniform mat4 uMVP;\n" +
        "uniform vec3 uCamPos;\n" +
        "varying vec2 vUv;\n" +
        "varying float vLight;\n" +
        "varying float vFog;\n" +
        "uniform vec2 uFogParam;\n" + // x=start, y=end
        "void main() {\n" +
        "    vUv = aUv;\n" +
        "    vLight = aLight;\n" +
        "    float d = length(aPos - uCamPos);\n" +
        "    vFog = clamp((d - uFogParam.x) / max(uFogParam.y - uFogParam.x, 0.001), 0.0, 1.0);\n" +
        "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
        "}\n";

    private static final String FS =
        "precision mediump float;\n" +
        "varying vec2 vUv;\n" +
        "varying float vLight;\n" +
        "varying float vFog;\n" +
        "uniform sampler2D uTex;\n" +
        "uniform vec3 uLightTint;\n" +
        "uniform vec3 uFogColor;\n" +
        "void main() {\n" +
        "    vec4 c = texture2D(uTex, vUv);\n" +
        "    if (c.a < 0.02) discard;\n" +
        "    vec3 col = c.rgb * vLight * uLightTint;\n" +
        "    col = mix(col, uFogColor, vFog);\n" +
        "    gl_FragColor = vec4(col, c.a);\n" +
        "}\n";

    public Shader() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VS);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FS);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            Log.e(TAG, "link error: " + GLES20.glGetProgramInfoLog(program));
        }
        aPos   = GLES20.glGetAttribLocation(program, "aPos");
        aUv    = GLES20.glGetAttribLocation(program, "aUv");
        aLight = GLES20.glGetAttribLocation(program, "aLight");
        uMVP   = GLES20.glGetUniformLocation(program, "uMVP");
        uTex   = GLES20.glGetUniformLocation(program, "uTex");
        uLightTint = GLES20.glGetUniformLocation(program, "uLightTint");
        uCamPos = GLES20.glGetUniformLocation(program, "uCamPos");
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor");
        uFogParam = GLES20.glGetUniformLocation(program, "uFogParam");
        // 默认值，避免未设置时渲染为黑色
        GLES20.glUseProgram(program);
        GLES20.glUniform3f(uLightTint, 1f, 1f, 1f);
        GLES20.glUniform3f(uFogColor, 0.60f, 0.78f, 0.95f);
        GLES20.glUniform2f(uFogParam, 55f, 130f);
        GLES20.glUseProgram(0);
    }

    private static int compile(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "compile error: " + GLES20.glGetShaderInfoLog(sh));
        }
        return sh;
    }

    public void use() { GLES20.glUseProgram(program); }

    /** 静态帮助：把 float[] 上传为一个 VBO 并返回 buffer id */
    public static int createVBO(float[] data) {
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0]);
        FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(data).position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.length * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        return ids[0];
    }
}
