package com.voxelsandbox;

import android.opengl.Matrix;

/**
 * 第一人称相机：维护视图/投影矩阵，并提供前向/右向向量用于输入与射线检测。
 */
public class Camera {
    public float x, y, z;       // 眼睛位置
    public float yaw, pitch;    // 弧度
    public float fov = 70f;
    public float near = 0.1f;
    public float far = 600f;

    private final float[] view = new float[16];
    private final float[] proj = new float[16];
    private final float[] mvp  = new float[16];

    public void updateProjection(int width, int height) {
        if (height == 0) height = 1;
        Matrix.perspectiveM(proj, 0, fov, (float) width / height, near, far);
    }

    public float[] forward() {
        float cp = (float) Math.cos(pitch);
        return new float[]{
                (float) (-Math.sin(yaw) * cp),
                (float) Math.sin(pitch),
                (float) (-Math.cos(yaw) * cp)
        };
    }

    public float[] right() {
        return new float[]{
                (float) Math.cos(yaw),
                0f,
                (float) (-Math.sin(yaw))
        };
    }

    public void updateView() {
        float[] f = forward();
        Matrix.setLookAtM(view, 0,
                x, y, z,
                x + f[0], y + f[1], z + f[2],
                0f, 1f, 0f);
    }

    public float[] mvp() {
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0);
        return mvp;
    }
}
