package com.voxelsandbox;

/**
 * 昼夜循环。时间 t ∈ [0,1) 对应 24 小时：
 * 0.00=午夜, 0.25=日出(6点), 0.50=正午(12点), 0.75=日落(18点)。
 */
public final class SkyCycle {
    /** 一个完整昼夜的真实秒数 */
    public static final float CYCLE_SECONDS = 480f;

    private float t = 0.30f; // 从上午开始
    /** 渲染线程写，UI 线程读 */
    public volatile float dayTime = t;

    public void update(float dt) {
        t += dt / CYCLE_SECONDS;
        t -= (float) Math.floor(t);
        dayTime = t;
    }

    /** t 转 24 小时制时刻，返回 [小时, 分钟] */
    public int[] clock() {
        int minutes = (int) (t * 24f * 60f);
        return new int[]{(minutes / 60) % 24, minutes % 60};
    }

    /** 太阳方向单位向量：6点东地平线 → 12点天顶 → 18点西地平线 */
    public float[] sunDirection() {
        double a = (t - 0.25) * 2.0 * Math.PI;
        float x = (float) Math.cos(a);
        float y = (float) Math.sin(a);
        float z = 0.22f;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    public float[] moonDirection() {
        float[] s = sunDirection();
        return new float[]{-s[0], -s[1], -s[2]};
    }

    /** 太阳高度角 [-1,1]，>0 为白天 */
    public float sunElevation() {
        return (float) Math.sin((t - 0.25) * 2.0 * Math.PI);
    }

    public boolean isDay() {
        return sunElevation() > 0.02f;
    }

    // 天空色关键帧（t, r, g, b）
    private static final float[][] SKY_STOPS = {
            {0.00f, 0.03f, 0.04f, 0.11f}, // 午夜
            {0.18f, 0.09f, 0.10f, 0.22f}, // 黎明前
            {0.24f, 0.72f, 0.52f, 0.44f}, // 日出暖色
            {0.30f, 0.52f, 0.74f, 0.95f}, // 清晨
            {0.50f, 0.40f, 0.70f, 0.98f}, // 正午湛蓝
            {0.70f, 0.50f, 0.75f, 0.96f}, // 午后
            {0.765f, 0.90f, 0.56f, 0.40f}, // 日落
            {0.81f, 0.38f, 0.28f, 0.46f}, // 暮光紫
            {0.88f, 0.07f, 0.07f, 0.18f}, // 入夜
    };

    public float[] skyColor() {
        float[] c = cyclicLerp(SKY_STOPS, t);
        return new float[]{c[0], c[1], c[2], 1f};
    }

    /** 地形光照色调（RGB 乘数）：白天接近纯白，晨昏偏暖，夜晚偏冷但仍可辨认 */
    public float[] lightTint() {
        float e = sunElevation();
        float[] warm = {1.00f, 0.84f, 0.70f};
        float[] white = {1.00f, 1.00f, 1.00f};
        float[] night = {0.42f, 0.47f, 0.64f};
        if (e >= 0) {
            return lerp3(warm, white, smoothstep(0f, 0.30f, e));
        }
        return lerp3(warm, night, smoothstep(0f, 0.25f, -e));
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
