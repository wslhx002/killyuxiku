package com.voxelsandbox;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 单个网格：位置+UV+光照（每顶点 6 个 float），VBO 存储。
 */
public class Mesh {
    public static final int FLOATS_PER_VERTEX = 6; // x y z u v light

    private FloatBuffer cpuBuffer;   // 构建阶段使用
    private int vertexCount = 0;
    private int vbo = 0;
    private boolean uploaded = false;
    private boolean dirty = false;

    public Mesh(int initialFloats) {
        cpuBuffer = ByteBuffer.allocateDirect(initialFloats * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public void addVertex(float x, float y, float z, float u, float v, float light) {
        ensureCapacity(vertexCount * FLOATS_PER_VERTEX + FLOATS_PER_VERTEX);
        int pos = vertexCount * FLOATS_PER_VERTEX;
        cpuBuffer.put(pos, x);
        cpuBuffer.put(pos + 1, y);
        cpuBuffer.put(pos + 2, z);
        cpuBuffer.put(pos + 3, u);
        cpuBuffer.put(pos + 4, v);
        cpuBuffer.put(pos + 5, light);
        vertexCount++;
        dirty = true;
    }

    private void ensureCapacity(int needed) {
        if (needed <= cpuBuffer.capacity()) return;
        int newCap = Math.max(cpuBuffer.capacity() * 2, needed);
        FloatBuffer nb = ByteBuffer.allocateDirect(newCap * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        cpuBuffer.position(0);
        nb.put(cpuBuffer);
        cpuBuffer = nb;
    }

    public boolean isEmpty() { return vertexCount == 0; }

    /** 在 GL 线程调用：上传/重传数据到 VBO */
    public void uploadIfDirty() {
        if (!dirty) return;
        if (vbo == 0) {
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            vbo = ids[0];
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        cpuBuffer.position(0);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexCount * FLOATS_PER_VERTEX * 4,
                cpuBuffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        dirty = false;
        uploaded = true;
    }

    public void draw(Shader s) {
        if (vertexCount == 0) return;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        int stride = FLOATS_PER_VERTEX * 4;
        GLES20.glEnableVertexAttribArray(s.aPos);
        GLES20.glVertexAttribPointer(s.aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(s.aUv);
        GLES20.glVertexAttribPointer(s.aUv, 2, GLES20.GL_FLOAT, false, stride, 12);
        GLES20.glEnableVertexAttribArray(s.aLight);
        GLES20.glVertexAttribPointer(s.aLight, 1, GLES20.GL_FLOAT, false, stride, 20);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void clear() {
        vertexCount = 0;
        dirty = true;
    }

    public int vertexCount() { return vertexCount; }

    public void dispose() {
        if (vbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vbo}, 0);
            vbo = 0;
        }
        uploaded = false;
    }
}
