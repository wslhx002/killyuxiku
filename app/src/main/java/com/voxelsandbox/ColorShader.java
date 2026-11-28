package com.voxelsandbox;

import android.opengl.GLES20;
import android.util.Log;

/**
 * 纯色着色器，用于绘制选中方块的线框高亮。
 */
public class ColorShader {
    private static final String TAG = "ColorShader";
    public final int program, aPos, uMVP, uColor;

    private static final String VS =
            "attribute vec3 aPos;\n" +
            "uniform mat4 uMVP;\n" +
            "void main(){ gl_Position = uMVP * vec4(aPos, 1.0); }\n";
    private static final String FS =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "void main(){ gl_FragColor = uColor; }\n";

    public ColorShader() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VS);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, FS);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) Log.e(TAG, "link: " + GLES20.glGetProgramInfoLog(program));
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        uMVP = GLES20.glGetUniformLocation(program, "uMVP");
        uColor = GLES20.glGetUniformLocation(program, "uColor");
    }

    private static int compile(int type, String src) {
        int sh = GLES20.glCreateShader(type);
        GLES20.glShaderSource(sh, src);
        GLES20.glCompileShader(sh);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(sh, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) Log.e(TAG, "compile: " + GLES20.glGetShaderInfoLog(sh));
        return sh;
    }

    public void use() { GLES20.glUseProgram(program); }
}
