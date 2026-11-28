package com.voxelsandbox;

/**
 * 昼夜循环：维护 0..1 的时间进度，提供太阳/月亮方向、天空色与地形光照色调。
 * 时间 t：0=日出，0.25=正午，0.5=日落，0.75=午夜。
 */
public final class SkyCycle {
    /** 一个完整昼夜的真实秒数 */
    public static final float CYCLE_SECONDS = 180f;

    private float t = 0.28f; // 从上午开始
    /** 暴露给 HUD 读取（渲染线程写，UI 线程读） */
    public volatile float dayTime = t;

    public void update(float dt) {
        t += dt / CYCLE_SECONDS;
        t -= (float) Math.floor(t); // 归一化到 [0,1)
        dayTime = t;
    }

    /** 太阳方向单位向量：t=0 东地平线 → 0.25 天顶 → 0.5 西地平线 → 0.75 脚下 */
    public float[] sunDirection() {
        double a = t * 2.0 * Math.PI;
        float x = (float) Math.cos(a);
        float y = (float) Math.sin(a);
        float z = 0.18f;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    public float[] moonDirection() {
        float[] s = sunDirection();
        return new float[]{-s[0], -s[1], -s[2]};
    }

    /** 太阳高度角，范围 [-1,1]，>0 为白天 */
    public float sunElevation() {
        return (float) Math.sin(t * 2.0 * Math.PI);
    }

    public boolean isDay() {
        return sunElevation() > 0.02f;
    }

    // 天空色关键帧（t, r, g, b），按 t 循环插值
    private static final float[][] SKY_STOPS = {
            {0.00f, 0.96f, 0.58f, 0.46f}, // 日出：暖橙粉
            {0.12f, 0.60f, 0.80f, 0.92f}, // 清晨
            {0.25f, 0.44f, 0.72f, 0.96f}, // 正午：蓝
            {0.40f, 0.55f, 0.80f, 0.93f}, // 下午
            {0.50f, 0.97f, 0.47f, 0.33f}, // 日落：橙红
            {0.57f, 0.55f, 0.30f, 0.48f}, // 暮光：紫
            {0.66f, 0.14f, 0.10f, 0.30f}, // 入夜
            {0.80f, 0.02f, 0.03f, 0.10f}, // 深夜：藏青
            {0.92f, 0.10f, 0.08f, 0.22f}, // 黎明前
    };

    public float[] skyColor() {
        float[] c = cyclicLerp(SKY_STOPS, t);
        return new float[]{c[0], c[1], c[2], 1f};
    }

    /** 地形整体光照色调（RGB 乘数）：白天白，晨昏暖，夜晚冷暗 */
    public float[] lightTint() {
        float e = sunElevation();
        float[] horizon = {1.0f, 0.72f, 0.50f}; // 地平线处的暖色
        float[] white = {1.0f, 1.0f, 1.0f};
        float[] night = {0.22f, 0.26f, 0.42f};
        if (e >= 0) {
            return lerp3(horizon, white, smoothstep(0f, 0.45f, e));
        } else {
            return lerp3(horizon, night, smoothstep(0f, 0.45f, -e));
        }
    }

    private static float[] cyclicLerp(float[][] stops, float t) {
        int n = stops.length;
        for (int i = 0; i < n; i++) {
            float t0 = stops[i][0];
            float t1 = (i + 1 < n) ? stops[i + 1][0] : 1f;
            if (t >= t0 && t < t1) {
                float k = (t1 > t0) ? (t - t0) / (t1 - t0) : 0f;
                float[] a = stops[i];
                float[] b = (i + 1 < n) ? stops[i + 1] : stops[0];
                return new float[]{
                        a[1] + (b[1] - a[1]) * k,
                        a[2] + (b[2] - a[2]) * k,
                        a[3] + (b[3] - a[3]) * k
                };
            }
        }
        return new float[]{stops[0][1], stops[0][2], stops[0][3]};
    }

    private static float[] lerp3(float[] a, float[] b, float k) {
        return new float[]{
                a[0] + (b[0] - a[0]) * k,
                a[1] + (b[1] - a[1]) * k,
                a[2] + (b[2] - a[2]) * k
        };
    }

    private static float smoothstep(float e0, float e1, float x) {
        if (x <= e0) return 0f;
        if (x >= e1) return 1f;
        float t = (x - e0) / (e1 - e0);
        return t * t * (3f - 2f * t);
    }
}
