package com.voxelsandbox;

import java.util.Random;

/**
 * 轻量级二维值噪声 + 分形叠加，用于地形高度生成。
 * 纯原创实现，不依赖外部库。
 */
public final class Noise {
    private final int[] perm;

    public Noise(long seed) {
        perm = new int[512];
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private float lerp(float a, float b, float t) { return a + t * (b - a); }
    private float grad(int hash, float x, float y) {
        switch (hash & 3) {
            case 0: return  x + y;
            case 1: return -x + y;
            case 2: return  x - y;
            default: return -x - y;
        }
    }

    private float noise2(float x, float y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        float xf = x - (float) Math.floor(x);
        float yf = y - (float) Math.floor(y);
        float u = fade(xf);
        float v = fade(yf);
        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];
        return lerp(
            lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u),
            lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u),
            v
        );
    }

    /** 分形噪声，返回大致 [-1,1] */
    public float fbm(float x, float y, int octaves, float lacunarity, float gain) {
        float amp = 0.5f;
        float freq = 1f;
        float sum = 0f;
        float norm = 0f;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise2(x * freq, y * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }
}
