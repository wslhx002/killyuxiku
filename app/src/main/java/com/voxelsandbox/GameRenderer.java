package com.voxelsandbox;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 主渲染器：负责 GL 资源、世界构建调度、玩家更新与绘制。
 */
public class GameRenderer implements GLSurfaceView.Renderer {

    private final InputState input;
    private Shader shader;
    private ColorShader colorShader;
    private TextureAtlas atlas;
    private World world;
    private Player player;
    private final Camera camera = new Camera();

    private FloatBuffer lineBuffer;
    private FloatBuffer bodyBuffer; // 太阳/月亮 billboard 顶点（6 顶点 * 3）
    private Raycaster.Hit currentHit;

    private final SkyCycle sky = new SkyCycle();

    private Runnable onFrame; // 每帧回调（用于驱动 HUD 重绘）
    public void setOnFrame(Runnable r) { onFrame = r; }

    private long lastNanos = 0;
    private volatile boolean meshesBuilding = true;
    private int frameCount = 0;

    /** 初始化若失败，保存原因供 HUD 显示（避免无提示直接闪退） */
    public volatile String initError = null;

    public GameRenderer(InputState input) {
        this.input = input;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1.0f); // 天空蓝
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        // 面向外的面按逆时针绕序生成，开启背面剔除：减少一半填充率，且不再看到地形内壁
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glFrontFace(GLES20.GL_CCW);
        try {
            shader = new Shader();
            colorShader = new ColorShader();

            // 纹理对象属于 GL 上下文，上下文丢失后必须重新上传
            if (atlas == null) atlas = new TextureAtlas();
            atlas.build();

            lineBuffer = ByteBuffer.allocateDirect(24 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            bodyBuffer = ByteBuffer.allocateDirect(6 * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();

            if (world == null) {
                world = new World(atlas, 1337L);
                int[] sp = world.findSpawn();
                player = new Player(world, sp[0] + 0.5f, sp[1] + 0.05f, sp[2] + 0.5f);
                player.yaw = 0.6f; player.pitch = -0.15f;
                meshesBuilding = true;
                markAllChunksForBuild();
            } else {
                // GL 上下文重建：顶点数据仍在 CPU，丢弃旧 VBO 后自动重传
                for (int cx = 0; cx < World.CX; cx++)
                    for (int cy = 0; cy < World.CY; cy++)
                        for (int cz = 0; cz < World.CZ; cz++) {
                            world.getChunks()[cx][cy][cz].opaque.dispose();
                            world.getChunks()[cx][cy][cz].transparent.dispose();
                        }
            }
            initError = null;
        } catch (Throwable t) {
            initError = t.toString();
            android.util.Log.e("VoxelCrash", "onSurfaceCreated failed", t);
        }
        lastNanos = System.nanoTime();
    }

    /** 首帧时把所有区块标记为待构建（在 GL 线程分帧完成，避免跨线程竞态） */
    private void markAllChunksForBuild() {
        for (int cx = 0; cx < World.CX; cx++)
            for (int cy = 0; cy < World.CY; cy++)
                for (int cz = 0; cz < World.CZ; cz++)
                    world.getChunks()[cx][cy][cz].needsRebuild = true;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        camera.updateProjection(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        frameCount++;

        if (world == null || player == null) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (onFrame != null) onFrame.run();
            return;
        }

        sky.update(dt);
        handleInput(dt);
        rebuildDirtyChunks();
        uploadChunkMeshes();

        float[] skyCol = sky.skyColor();
        GLES20.glClearColor(skyCol[0], skyCol[1], skyCol[2], skyCol[3]);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        player.applyTo(camera);
        camera.updateView();
        float[] mvp = camera.mvp();

        updateSelection(); // 在相机更新后做射线检测，保证用本帧视角
        drawWorld(mvp, skyCol);
        drawSkyBodies(mvp);
        drawSelection(mvp);

        if (onFrame != null) onFrame.run();
    }

    /** 供 HUD 读取的时间进度 [0,1) 与是否白天 */
    public float dayTime() { return sky.dayTime; }
    public boolean isDay() { return sky.isDay(); }

    private void handleInput(float dt) {
        // 视角
        player.yaw += input.consumeYaw();
        player.pitch += input.consumePitch();
        float lim = (float) (Math.PI / 2 - 0.01);
        if (player.pitch > lim) player.pitch = lim;
        if (player.pitch < -lim) player.pitch = -lim;

        // 飞行切换
        if (input.consumeFlyToggle()) {
            input.flying = !input.flying;
            player.vy = 0;
        }

        player.update(dt, input.moveX(), input.moveZ(), input.consumeJump(), input.flying);

        // 方块操作
        if (input.consumeBreak() && currentHit != null) {
            world.setBlock(currentHit.x, currentHit.y, currentHit.z, Block.AIR);
        }
        if (input.consumePlace() && currentHit != null) {
            int id = Block.PALETTE[input.selectedSlot % Block.PALETTE.length];
            if (!intersectsPlayer(currentHit.nx, currentHit.ny, currentHit.nz)) {
                world.setBlock(currentHit.nx, currentHit.ny, currentHit.nz, id);
            }
        }
    }

    private boolean intersectsPlayer(int bx, int by, int bz) {
        float hw = Player.WIDTH / 2f;
        float minX = player.x - hw, maxX = player.x + hw;
        float minY = player.y, maxY = player.y + Player.HEIGHT;
        float minZ = player.z - hw, maxZ = player.z + hw;
        return bx + 1 > minX && bx < maxX
                && by + 1 > minY && by < maxY
                && bz + 1 > minZ && bz < maxZ;
    }

    private void rebuildDirtyChunks() {
        int budget = 8; // 每帧最多重建 8 个区块，避免卡顿
        Chunk[][][] cs = world.getChunks();
        outer:
        for (int cx = 0; cx < World.CX; cx++)
            for (int cy = 0; cy < World.CY; cy++)
                for (int cz = 0; cz < World.CZ; cz++) {
                    Chunk c = cs[cx][cy][cz];
                    if (c.needsRebuild) {
                        world.rebuildChunkMesh(c);
                        if (--budget <= 0) break outer;
                    }
                }
    }

    private void uploadChunkMeshes() {
        Chunk[][][] cs = world.getChunks();
        for (int cx = 0; cx < World.CX; cx++)
            for (int cy = 0; cy < World.CY; cy++)
                for (int cz = 0; cz < World.CZ; cz++) {
                    Chunk c = cs[cx][cy][cz];
                    if (c.built) {
                        c.opaque.uploadIfDirty();
                        c.transparent.uploadIfDirty();
                    }
                }
    }

    private void updateSelection() {
        float[] f = camera.forward();
        currentHit = Raycaster.cast(world, camera.x, camera.y, camera.z,
                f[0], f[1], f[2], 6f);
    }

    private void drawWorld(float[] mvp, float[] skyCol) {
        shader.use();
        GLES20.glUniformMatrix4fv(shader.uMVP, 1, false, mvp, 0);
        float[] lt = sky.lightTint();
        GLES20.glUniform3f(shader.uLightTint, lt[0], lt[1], lt[2]);
        // 距离雾：颜色与天空一致，遮住有限世界的边缘并增强纵深
        GLES20.glUniform3f(shader.uCamPos, camera.x, camera.y, camera.z);
        GLES20.glUniform3f(shader.uFogColor, skyCol[0], skyCol[1], skyCol[2]);
        GLES20.glUniform2f(shader.uFogParam, 46f, 112f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlas.textureId);
        GLES20.glUniform1i(shader.uTex, 0);

        Chunk[][][] cs = world.getChunks();
        // 不透明：深度写入开启
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        for (int cx = 0; cx < World.CX; cx++)
            for (int cy = 0; cy < World.CY; cy++)
                for (int cz = 0; cz < World.CZ; cz++) {
                    Chunk c = cs[cx][cy][cz];
                    if (c.built) c.opaque.draw(shader);
                }

        // 透明：混合，关闭深度写入
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        for (int cx = 0; cx < World.CX; cx++)
            for (int cy = 0; cy < World.CY; cy++)
                for (int cz = 0; cz < World.CZ; cz++) {
                    Chunk c = cs[cx][cy][cz];
                    if (c.built) c.transparent.draw(shader);
                }
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /** 绘制太阳与月亮 billboard（始终面向相机，随昼夜位置移动） */
    private void drawSkyBodies(float[] mvp) {
        float[] f = camera.forward();
        float[] r = camera.right();
        // 相机上方向 = cross(right, forward)
        float ux = r[1] * f[2] - r[2] * f[1];
        float uy = r[2] * f[0] - r[0] * f[2];
        float uz = r[0] * f[1] - r[1] * f[0];
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (ul > 0) { ux /= ul; uy /= ul; uz /= ul; }

        float e = sky.sunElevation();
        if (e > -0.18f) {
            drawBody(mvp, sky.sunDirection(), 16f, new float[]{1f, 0.93f, 0.60f, 0.95f}, r, ux, uy, uz);
        }
        if (e < 0.18f) {
            drawBody(mvp, sky.moonDirection(), 12f, new float[]{0.82f, 0.86f, 1.0f, 0.92f}, r, ux, uy, uz);
        }
    }

    private void drawBody(float[] mvp, float[] dir, float size, float[] color,
                          float[] r, float ux, float uy, float uz) {
        final float dist = 260f;
        float cx = camera.x + dir[0] * dist;
        float cy = camera.y + dir[1] * dist;
        float cz = camera.z + dir[2] * dist;
        float hx = r[0] * size, hy = r[1] * size, hz = r[2] * size;
        float vx = ux * size, vy = uy * size, vz = uz * size;
        // 两个三角形组成一个面向相机的方形（MC 风格）
        float[] v = {
                cx - hx - vx, cy - hy - vy, cz - hz - vz,
                cx + hx - vx, cy + hy - vy, cz + hz - vz,
                cx + hx + vx, cy + hy + vy, cz + hz + vz,
                cx - hx - vx, cy - hy - vy, cz - hz - vz,
                cx + hx + vx, cy + hy + vy, cz + hz + vz,
                cx - hx + vx, cy - hy + vy, cz - hz + vz,
        };
        bodyBuffer.clear();
        bodyBuffer.put(v).position(0);

        colorShader.use();
        GLES20.glUniformMatrix4fv(colorShader.uMVP, 1, false, mvp, 0);
        GLES20.glUniform4f(colorShader.uColor, color[0], color[1], color[2], color[3]);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glEnableVertexAttribArray(colorShader.aPos);
        GLES20.glVertexAttribPointer(colorShader.aPos, 3, GLES20.GL_FLOAT, false, 0, bodyBuffer);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private void drawSelection(float[] mvp) {
        if (currentHit == null) return;
        float e = 0.004f; // 轻微膨胀避免 z-fighting
        float x = currentHit.x - e, y = currentHit.y - e, z = currentHit.z - e;
        float X = x + 1 + 2 * e, Y = y + 1 + 2 * e, Z = z + 1 + 2 * e;
        float[] v = {
                x,y,z, X,y,z,  X,y,z, X,y,Z,  X,y,Z, x,y,Z,  x,y,Z, x,y,z, // 底
                x,Y,z, X,Y,z,  X,Y,z, X,Y,Z,  X,Y,Z, x,Y,Z,  x,Y,Z, x,Y,z, // 顶
                x,y,z, x,Y,z,  X,y,z, X,Y,z,  X,y,Z, X,Y,Z,  x,y,Z, x,Y,Z  // 竖
        };
        lineBuffer.clear();
        lineBuffer.put(v).position(0);

        colorShader.use();
        GLES20.glUniformMatrix4fv(colorShader.uMVP, 1, false, mvp, 0);
        GLES20.glUniform4f(colorShader.uColor, 0f, 0f, 0f, 0.5f);
        // 只启用位置属性；禁用纹理/光照属性，避免驱动读取已解绑的 VBO
        GLES20.glDisableVertexAttribArray(shader.aUv);
        GLES20.glDisableVertexAttribArray(shader.aLight);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0); // 使用客户端内存绘制
        GLES20.glEnableVertexAttribArray(colorShader.aPos);
        GLES20.glVertexAttribPointer(colorShader.aPos, 3, GLES20.GL_FLOAT, false, 0, lineBuffer);
        GLES20.glLineWidth(2f);
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 24);
        GLES20.glDisableVertexAttribArray(colorShader.aPos);
    }
}
