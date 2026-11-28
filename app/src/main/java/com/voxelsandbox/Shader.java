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
    public final int aPos, aUv, aLight, uMVP, uTex;

    private static final String VS =
        "attribute vec3 aPos;\n" +
        "attribute vec2 aUv;\n" +
        "attribute float aLight;\n" +
        "uniform mat4 uMVP;\n" +
        "varying vec2 vUv;\n" +
        "varying float vLight;\n" +
        "void main() {\n" +
        "    vUv = aUv;\n" +
        "    vLight = aLight;\n" +
        "    gl_Position = uMVP * vec4(aPos, 1.0);\n" +
        "}\n";

    private static final String FS =
        "precision mediump float;\n" +
        "varying vec2 vUv;\n" +
        "varying float vLight;\n" +
        "uniform sampler2D uTex;\n" +
        "void main() {\n" +
        "    vec4 c = texture2D(uTex, vUv);\n" +
        "    if (c.a < 0.02) discard;\n" +
        "    gl_FragColor = vec4(c.rgb * vLight, c.a);\n" +
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
