package com.voxelsandbox;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.util.Arrays;
import java.util.Random;

/**
 * 程序化生成 4x4 纹理图集（每格 16x16 像素，原创像素画）。
 *
 * 直接生成 int[] 像素后一次性建 Bitmap，避免 Canvas 逐像素绘制产生的
 * 空洞（透明像素会被着色器 discard，表现为“穿透噪点”）。
 */
public class TextureAtlas {
    public static final int TILES_PER_ROW = 4;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = TILE_PX * TILES_PER_ROW;

    private static final float HALF_TEXEL = 0.5f / ATLAS_PX;

    public int textureId = 0;

    /** 返回 tile 的 GL 纹理坐标 [u0,v0,u1,v1]，半像素内缩避免相邻格渗色。
     *  注意：Bitmap 第 0 行被上传到纹理 v=0 一侧，因此 v 不做翻转。 */
    public float[] uvForTile(int tileIndex) {
        int col = tileIndex % TILES_PER_ROW;
        int row = tileIndex / TILES_PER_ROW;
        float u0 = (float) col / TILES_PER_ROW + HALF_TEXEL;
        float u1 = (float) (col + 1) / TILES_PER_ROW - HALF_TEXEL;
        float v0 = (float) row / TILES_PER_ROW + HALF_TEXEL;
        float v1 = (float) (row + 1) / TILES_PER_ROW - HALF_TEXEL;
        return new float[]{u0, v0, u1, v1};
    }

    public void build() {
        int[] atlas = new int[ATLAS_PX * ATLAS_PX];
        // 兜底填充不透明灰色，任何未绘制区域也不会出现透明噪点
        Arrays.fill(atlas, 0xFF7F7F7F);

        put(atlas, 0, 0, grassTop());
        put(atlas, 1, 0, grassSide());
        put(atlas, 2, 0, dirt());
        put(atlas, 3, 0, stone());
        put(atlas, 0, 1, woodTop());
        put(atlas, 1, 1, leaves());
        put(atlas, 2, 1, sand());
        put(atlas, 3, 1, water());
        put(atlas, 0, 2, plank());
        put(atlas, 1, 2, cobble());
        put(atlas, 2, 2, brick());
        put(atlas, 3, 2, glass());
        put(atlas, 0, 3, snowSide());
        put(atlas, 1, 3, woodSide());
        put(atlas, 2, 3, glowStone());
        put(atlas, 3, 3, snowTop());

        Bitmap bmp = Bitmap.createBitmap(atlas, ATLAS_PX, ATLAS_PX, Bitmap.Config.ARGB_8888);
        upload(bmp);
        bmp.recycle();
    }

    private void put(int[] atlas, int col, int row, int[] tile) {
        for (int y = 0; y < TILE_PX; y++) {
            int dst = (row * TILE_PX + y) * ATLAS_PX + col * TILE_PX;
            System.arraycopy(tile, y * TILE_PX, atlas, dst, TILE_PX);
        }
    }

    private void upload(Bitmap bmp) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        textureId = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    // ---------- 工具 ----------
    private static int[] buf() { return new int[TILE_PX * TILE_PX]; }

    private static void set(int[] t, int x, int y, int c) { t[y * TILE_PX + x] = c; }

    private static int mix(int a, int b, float k) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * k);
        int g = (int) (ag + (bg - ag) * k);
        int bl = (int) (ab + (bb - ab) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** 两色随机抖动填充，形成细腻的像素噪点 */
    private static int[] speckle(long seed, int c1, int c2, int ratio) {
        int[] t = buf();
        Random r = new Random(seed);
        for (int i = 0; i < t.length; i++) t[i] = (r.nextInt(100) < ratio) ? c2 : c1;
        return t;
    }

    // ---------- 各方块 ----------
    private int[] grassTop() {
        return speckle(7, 0xFF5DAE3C, 0xFF6EC24A, 45);
    }

    private int[] grassSide() {
        int[] t = speckle(11, 0xFF8A5A2B, 0xFF7A4E25, 40);
        Random r = new Random(12);
        for (int x = 0; x < TILE_PX; x++) {
            int depth = 3 + r.nextInt(3);
            for (int y = 0; y < depth; y++) set(t, x, y, (y == depth - 1) ? 0xFF4E8F31 : 0xFF5DAE3C);
        }
        return t;
    }

    private int[] dirt() {
        return speckle(3, 0xFF8A5A2B, 0xFF70441F, 42);
    }

    private int[] stone() {
        int[] t = speckle(5, 0xFF8A8A8A, 0xFF777777, 45);
        Random r = new Random(6);
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(TILE_PX - 3) + 1, y = r.nextInt(TILE_PX - 2) + 1;
            set(t, x, y, 0xFF6E6E6E);
            set(t, x + 1, y, 0xFF6E6E6E);
            set(t, x, y + 1, 0xFF6E6E6E);
        }
        return t;
    }

    private int[] woodTop() {
        int[] t = buf();
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.sqrt((x - 7.5) * (x - 7.5) + (y - 7.5) * (y - 7.5));
                int ring = ((int) d) % 3;
                set(t, x, y, ring == 0 ? 0xFF9A6B3F : ring == 1 ? 0xFF875A32 : 0xFF754B27);
            }
        return t;
    }

    private int[] woodSide() {
        int[] t = speckle(31, 0xFF6E4A28, 0xFF5E3F22, 45);
        for (int x = 0; x < TILE_PX; x++)
            if (x % 5 == 0)
                for (int y = 0; y < TILE_PX; y++) set(t, x, y, 0xFF4F341B);
        return t;
    }

    private int[] leaves() {
        int[] t = speckle(13, 0xFF2F7D31, 0xFF3B9140, 50);
        Random r = new Random(14);
        for (int i = 0; i < 26; i++) set(t, r.nextInt(TILE_PX), r.nextInt(TILE_PX), 0xFF1F5B21);
        return t;
    }

    private int[] sand() {
        return speckle(17, 0xFFE4D595, 0xFFF0E3AB, 40);
    }

    private int[] water() {
        int[] t = buf();
        Random r = new Random(19);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean wave = ((x + y) % 8 == 0) || ((x - y + 32) % 9 == 0);
                int rC = wave ? 74 : 56, gC = wave ? 140 : 118, bC = wave ? 236 : 216;
                t[y * TILE_PX + x] = (170 << 24) | (rC << 16) | (gC << 8) | bC;
                if (r.nextInt(12) == 0) t[y * TILE_PX + x] = (150 << 24) | (0x50 << 16) | (0xA0 << 8) | 0xE8;
            }
        return t;
    }

    private int[] plank() {
        int[] t = buf();
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int band = (y / 4) % 2;
                int c = band == 0 ? 0xFFB5864A : 0xFFA07339;
                if (y % 4 == 0) c = 0xFF7A5526;
                if (x % 8 == 0) c = mix(c, 0xFF6E4A22, 0.5f);
                set(t, x, y, c);
            }
        return t;
    }

    private int[] cobble() {
        int[] t = speckle(23, 0xFF8A8A8A, 0xFF6F6F6F, 45);
        Random r = new Random(24);
        for (int i = 0; i < 12; i++) {
            int x = r.nextInt(TILE_PX - 3) + 1, y = r.nextInt(TILE_PX - 3) + 1;
            int w = 2 + r.nextInt(3), h = 2 + r.nextInt(3);
            int shade = 0xFF636363 + (r.nextInt(0x28) << 16) + (r.nextInt(0x28) << 8) + r.nextInt(0x28);
            for (int yy = 0; yy < h; yy++)
                for (int xx = 0; xx < w; xx++)
                    if (x + xx < TILE_PX && y + yy < TILE_PX) set(t, x + xx, y + yy, shade);
        }
        return t;
    }

    private int[] brick() {
        int[] t = buf();
        Random r = new Random(27);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int row = y / 4;
                int offset = (row % 2) * 4;
                boolean mortar = (y % 4 == 3) || ((x + offset) % 8 == 0);
                int c = mortar ? 0xFFB8B0A4 : (r.nextInt(3) == 0 ? 0xFFA84E3C : 0xFFB05642);
                set(t, x, y, c);
            }
        return t;
    }

    private int[] glass() {
        int[] t = buf();
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean border = x == 0 || y == 0 || x == TILE_PX - 1 || y == TILE_PX - 1;
                boolean glint = (x == y && x > 2 && x < 7);
                if (border) set(t, x, y, 0xFFDCE6EA);
                else if (glint) set(t, x, y, (60 << 24) | 0xFFFFFF);
                else set(t, x, y, (34 << 24) | 0xC8E4F0);
            }
        return t;
    }

    private int[] snowTop() {
        return speckle(33, 0xFFF7FBFF, 0xFFE6EFF8, 45);
    }

    private int[] snowSide() {
        int[] t = speckle(29, 0xFF8A5A2B, 0xFF7A4E25, 40);
        Random r = new Random(30);
        for (int x = 0; x < TILE_PX; x++) {
            int depth = 4 + r.nextInt(2);
            for (int y = 0; y < depth; y++) set(t, x, y, y == depth - 1 ? 0xFFDDE8F2 : 0xFFF2F8FD);
        }
        return t;
    }

    private int[] glowStone() {
        int[] t = speckle(37, 0xFF3A2A18, 0xFF4A3420, 45);
        Random r = new Random(38);
        for (int i = 0; i < 18; i++) {
            int x = r.nextInt(TILE_PX), y = r.nextInt(TILE_PX);
            int s = 1 + r.nextInt(2);
            int col = r.nextInt(3) == 0 ? 0xFFFFE9A8 : 0xFFFFC857;
            for (int dy = 0; dy < s; dy++)
                for (int dx = 0; dx < s; dx++)
                    if (x + dx < TILE_PX && y + dy < TILE_PX) set(t, x + dx, y + dy, col);
        }
        return t;
    }
}
