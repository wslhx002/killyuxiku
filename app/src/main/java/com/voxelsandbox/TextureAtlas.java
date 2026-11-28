package com.voxelsandbox;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.util.Random;

/**
 * 程序化生成 4x4 纹理图集（每格 16x16 像素，原创像素画）。
 * 不依赖任何外部图片资源。
 */
public class TextureAtlas {
    public static final int TILES_PER_ROW = 4;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = TILE_PX * TILES_PER_ROW; // 64

    private static final float HALF_TEXEL = 0.5f / ATLAS_PX;

    public int textureId = 0;

    /** 返回 tile 的 GL 纹理坐标 [u0,v0,u1,v1]，已做半像素内缩避免 bleeding */
    public float[] uvForTile(int tileIndex) {
        int col = tileIndex % TILES_PER_ROW;
        int row = tileIndex / TILES_PER_ROW;
        float u0 = (float) col / TILES_PER_ROW + HALF_TEXEL;
        float u1 = (float) (col + 1) / TILES_PER_ROW - HALF_TEXEL;
        // Bitmap 原点在左上，GL 原点在左下 → 翻转 V
        float v1 = 1f - (float) row / TILES_PER_ROW - HALF_TEXEL;
        float v0 = 1f - (float) (row + 1) / TILES_PER_ROW + HALF_TEXEL;
        return new float[]{u0, v0, u1, v1};
    }

    public void build() {
        Bitmap bmp = Bitmap.createBitmap(ATLAS_PX, ATLAS_PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setAntiAlias(false);
        p.setFilterBitmap(false);

        drawTile(c, p, 0, 0, grassTop());      // 0 grass top
        drawTile(c, p, 1, 0, grassSide());     // 1 grass side
        drawTile(c, p, 2, 0, dirt());          // 2 dirt
        drawTile(c, p, 3, 0, stone());         // 3 stone
        drawTile(c, p, 0, 1, woodTop());       // 4 wood top
        drawTile(c, p, 1, 1, leaves());        // 5 leaves
        drawTile(c, p, 2, 1, sand());          // 6 sand
        drawTile(c, p, 3, 1, water());         // 7 water
        drawTile(c, p, 0, 2, plank());         // 8 plank
        drawTile(c, p, 1, 2, cobble());        // 9 cobble
        drawTile(c, p, 2, 2, brick());         // 10 brick
        drawTile(c, p, 3, 2, glass());         // 11 glass
        drawTile(c, p, 0, 3, snowSide());      // 12 snow side
        drawTile(c, p, 1, 3, woodSide());      // 13 wood side
        drawTile(c, p, 2, 3, stone());         // 14 spare
        drawTile(c, p, 3, 3, stone());         // 15 spare

        upload(bmp);
        bmp.recycle();
    }

    private void drawTile(Canvas c, Paint p, int col, int row, int[][] px) {
        int ox = col * TILE_PX;
        int oy = row * TILE_PX;
        for (int y = 0; y < TILE_PX; y++) {
            for (int x = 0; x < TILE_PX; x++) {
                p.setColor(px[y][x]);
                c.drawRect(ox + x, oy + y, ox + x + 1, oy + y + 1, p);
            }
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

    // ---- 各 tile 的像素数据（16x16 ARGB）----

    private int[][] grassTop() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(7);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(3);
                int base = (n == 0) ? 0xFF5fae3a : (n == 1) ? 0xFF6cc243 : 0xFF559e32;
                g[y][x] = base;
            }
        return g;
    }

    private int[][] grassSide() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(11);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(3);
                int c;
                if (y < 4) {
                    c = (n == 0) ? 0xFF5fae3a : (n == 1) ? 0xFF6cc243 : 0xFF559e32;
                } else {
                    c = (n == 0) ? 0xFF8a5a2b : (n == 1) ? 0xFF7a4e25 : 0xFF96632f;
                }
                g[y][x] = c;
            }
        return g;
    }

    private int[][] dirt() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(3);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(4);
                g[y][x] = (n == 0) ? 0xFF8a5a2b : (n == 1) ? 0xFF7a4e25
                        : (n == 2) ? 0xFF96632f : 0xFF6f4520;
            }
        return g;
    }

    private int[][] stone() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(5);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(4);
                g[y][x] = (n == 0) ? 0xFF8a8a8a : (n == 1) ? 0xFF7d7d7d
                        : (n == 2) ? 0xFF979797 : 0xFF707070;
            }
        return g;
    }

    private int[][] woodTop() {
        int[][] g = new int[TILE_PX][TILE_PX];
        int cx = 8, cy = 8;
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                int ring = ((int) d) % 3;
                int c = (ring == 0) ? 0xFF9a6b3f : (ring == 1) ? 0xFF875a32 : 0xFF734a28;
                g[y][x] = c;
            }
        return g;
    }

    private int[][] leaves() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(13);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(4);
                g[y][x] = (n == 0) ? 0xFF2f7d31 : (n == 1) ? 0xFF3a9140
                        : (n == 2) ? 0xFF276628 : 0xFF45a34a;
            }
        return g;
    }

    private int[][] sand() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(17);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(3);
                g[y][x] = (n == 0) ? 0xFFe6d79a : (n == 1) ? 0xFFdccb86 : 0xFFf0e3ab;
            }
        return g;
    }

    private int[][] water() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(19);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(3);
                int a = 150 + r.nextInt(40);
                g[y][x] = Color.argb(a, 50 + n * 6, 110 + n * 10, 220 + n * 8);
            }
        return g;
    }

    private int[][] plank() {
        int[][] g = new int[TILE_PX][TILE_PX];
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int band = (y / 4) % 2;
                int c = (band == 0) ? 0xFFb5864a : 0xFFa07339;
                if (y % 4 == 0) c = 0xFF7a5526;
                g[y][x] = c;
            }
        return g;
    }

    private int[][] cobble() {
        int[][] g = stone();
        Random r = new Random(23);
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(TILE_PX), y = r.nextInt(TILE_PX);
            int w = 2 + r.nextInt(3), h = 2 + r.nextInt(3);
            int shade = 0xFF6a6a6a + r.nextInt(0x202020);
            for (int yy = 0; yy < h; yy++)
                for (int xx = 0; xx < w; xx++)
                    if (x + xx < TILE_PX && y + yy < TILE_PX) g[y + yy][x + xx] = shade;
        }
        return g;
    }

    private int[][] brick() {
        int[][] g = new int[TILE_PX][TILE_PX];
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int row = y / 4;
                int offset = (row % 2) * 4;
                boolean mortar = (y % 4 == 0) || ((x + offset) % 8 == 0);
                g[y][x] = mortar ? 0xFFb8b0a4 : 0xFFa14a3a;
            }
        return g;
    }

    private int[][] glass() {
        int[][] g = new int[TILE_PX][TILE_PX];
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                boolean border = (x == 0 || y == 0 || x == 15 || y == 15);
                g[y][x] = border ? 0xFFcfd8dc : Color.argb(40, 200, 230, 240);
            }
        return g;
    }

    private int[][] snowSide() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(29);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(3);
                int c;
                if (y < 4) {
                    c = (n == 0) ? 0xFFf4f8fb : (n == 1) ? 0xFFe8eef5 : 0xFFffffff;
                } else {
                    c = (n == 0) ? 0xFF8a5a2b : (n == 1) ? 0xFF7a4e25 : 0xFF96632f;
                }
                g[y][x] = c;
            }
        return g;
    }

    private int[][] woodSide() {
        int[][] g = new int[TILE_PX][TILE_PX];
        Random r = new Random(31);
        for (int y = 0; y < TILE_PX; y++)
            for (int x = 0; x < TILE_PX; x++) {
                int n = r.nextInt(3);
                int base = (n == 0) ? 0xFF6e4a28 : (n == 1) ? 0xFF7d5530 : 0xFF5e3f22;
                // 竖向纹理
                if ((x % 5) == 0) base = 0xFF4f341b;
                g[y][x] = base;
            }
        return g;
    }
}
