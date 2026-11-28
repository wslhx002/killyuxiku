package com.voxelsandbox;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.InputStream;

/**
 * 方块纹理图集：加载 assets/textures/*.png（16x16 原创像素贴图），
 * 拼成 4x4 图集后上传为 GL 纹理。
 */
public class TextureAtlas {
    private static final String TAG = "TextureAtlas";

    public static final int TILES_PER_ROW = 4;
    public static final int TILE_PX = 16;
    public static final int ATLAS_PX = TILE_PX * TILES_PER_ROW;

    private static final float HALF_TEXEL = 0.5f / ATLAS_PX;

    /** 与 Block 中的 tile 索引一一对应 */
    private static final String[] TILE_FILES = {
            "grass_top", "grass_side", "dirt", "stone",
            "wood_top", "leaves", "sand", "water",
            "plank", "cobble", "brick", "glass",
            "snow_side", "wood_side", "glowstone", "snow_top"
    };

    public int textureId = 0;

    /** 返回 tile 的 GL 纹理坐标 [u0,v0,u1,v1]，半像素内缩避免相邻格渗色。
     *  Bitmap 第 0 行对应纹理 v=0，因此 v 不做翻转。 */
    public float[] uvForTile(int tileIndex) {
        int col = tileIndex % TILES_PER_ROW;
        int row = tileIndex / TILES_PER_ROW;
        float u0 = (float) col / TILES_PER_ROW + HALF_TEXEL;
        float u1 = (float) (col + 1) / TILES_PER_ROW - HALF_TEXEL;
        float v0 = (float) row / TILES_PER_ROW + HALF_TEXEL;
        float v1 = (float) (row + 1) / TILES_PER_ROW - HALF_TEXEL;
        return new float[]{u0, v0, u1, v1};
    }

    public void build(Context ctx) {
        Bitmap atlas = Bitmap.createBitmap(ATLAS_PX, ATLAS_PX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(atlas);
        c.drawColor(0xFF7F7F7F); // 兜底：不留任何透明像素

        Paint p = new Paint();
        p.setFilterBitmap(false);
        p.setAntiAlias(false);
        p.setDither(false);

        AssetManager am = ctx.getAssets();
        for (int i = 0; i < TILE_FILES.length; i++) {
            int col = i % TILES_PER_ROW;
            int row = i / TILES_PER_ROW;
            Rect dst = new Rect(col * TILE_PX, row * TILE_PX,
                    col * TILE_PX + TILE_PX, row * TILE_PX + TILE_PX);
            Bitmap src = load(am, TILE_FILES[i]);
            if (src != null) {
                c.drawBitmap(src, null, dst, p);
                src.recycle();
            } else {
                p.setColor(0xFFFF00FF);
                c.drawRect(dst, p);
            }
        }
        upload(atlas);
        atlas.recycle();
    }

    private Bitmap load(AssetManager am, String name) {
        try (InputStream in = am.open("textures/" + name + ".png")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            if (b == null) return null;
            if (b.getWidth() != TILE_PX || b.getHeight() != TILE_PX) {
                Bitmap scaled = Bitmap.createScaledBitmap(b, TILE_PX, TILE_PX, false);
                if (scaled != b) b.recycle();
                return scaled;
            }
            return b;
        } catch (Throwable t) {
            Log.e(TAG, "failed to load texture: " + name, t);
            return null;
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
}
