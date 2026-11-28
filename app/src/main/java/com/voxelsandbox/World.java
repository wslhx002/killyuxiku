package com.voxelsandbox;

import java.util.Random;

/**
 * 体素世界：方块存储、程序化地形生成、区块网格化。
 *
 * 坐标系：x/z 水平，y 向上；世界以 (0,0,0) 为一角。
 */
public class World {
    public static final int SX = 96;
    public static final int SY = 48;
    public static final int SZ = 96;
    public static final int CHUNK = 16;
    public static final int CX = SX / CHUNK; // 6
    public static final int CY = SY / CHUNK; // 3
    public static final int CZ = SZ / CHUNK; // 6

    public static final int SEA_LEVEL = 18;
    public static final int SNOW_LINE = 34;

    private final byte[] blocks; // [y*SZ*SX + z*SX + x]
    private final Chunk[][][] chunks;
    private final TextureAtlas atlas;
    private final Noise noise;
    private final long seed;

    // 6 个面的 4 顶点偏移 (x,y,z) —— 绕序保证从外侧看为逆时针(CCW)，可开启背面剔除
    private static final float[][][] FACE_VERT = {
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}}, // +X
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}, // -X
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}}, // +Y
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, // -Y
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}, // +Z
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}}, // -Z
    };
    private static final float[] FACE_LIGHT = {0.82f, 0.82f, 1.0f, 0.55f, 0.72f, 0.72f};
    private static final int[][] FACE_NEI = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    public World(TextureAtlas atlas, long seed) {
        this.atlas = atlas;
        this.seed = seed;
        this.noise = new Noise(seed);
        this.blocks = new byte[SX * SY * SZ];
        this.chunks = new Chunk[CX][CY][CZ];
        for (int cx = 0; cx < CX; cx++)
            for (int cy = 0; cy < CY; cy++)
                for (int cz = 0; cz < CZ; cz++)
                    chunks[cx][cy][cz] = new Chunk(cx, cy, cz);
        generateTerrain();
    }

    private int idx(int x, int y, int z) {
        return (y * SZ + z) * SX + x;
    }

    public int getBlock(int x, int y, int z) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return Block.AIR;
        return blocks[idx(x, y, z)] & 0xFF;
    }

    public void setBlock(int x, int y, int z, int id) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return;
        blocks[idx(x, y, z)] = (byte) id;
        markDirty(x, y, z);
    }

    private void markDirty(int x, int y, int z) {
        int cx = x / CHUNK, cy = y / CHUNK, cz = z / CHUNK;
        markChunk(cx, cy, cz);
        // 边界处相邻区块也要重建
        if (x % CHUNK == 0) markChunk(cx - 1, cy, cz);
        if (x % CHUNK == CHUNK - 1) markChunk(cx + 1, cy, cz);
        if (y % CHUNK == 0) markChunk(cx, cy - 1, cz);
        if (y % CHUNK == CHUNK - 1) markChunk(cx, cy + 1, cz);
        if (z % CHUNK == 0) markChunk(cx, cy, cz - 1);
        if (z % CHUNK == CHUNK - 1) markChunk(cx, cy, cz + 1);
    }

    private void markChunk(int cx, int cy, int cz) {
        if (cx < 0 || cx >= CX || cy < 0 || cy >= CY || cz < 0 || cz >= CZ) return;
        chunks[cx][cy][cz].needsRebuild = true;
    }

    public Chunk[][][] getChunks() { return chunks; }

    // ---- 地形生成 ----
    private void generateTerrain() {
        Random r = new Random(seed ^ 0x9E3779B9L);
        float base = SEA_LEVEL + 2;
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                float n = noise.fbm(x * 0.020f, z * 0.020f, 4, 2.0f, 0.5f);
                float mountain = Math.max(0, noise.fbm(x * 0.006f + 100, z * 0.006f + 100, 3, 2.0f, 0.5f));
                int h = (int) (base + n * 7 + mountain * mountain * 20);
                if (h < 2) h = 2;
                if (h >= SY) h = SY - 1;
                for (int y = 0; y <= h; y++) {
                    int id;
                    if (y == h) {
                        if (h <= SEA_LEVEL + 1) id = Block.SAND;
                        else if (h >= SNOW_LINE) id = Block.SNOW;
                        else id = Block.GRASS;
                    } else if (y >= h - 3) {
                        id = (h <= SEA_LEVEL + 2) ? Block.SAND : Block.DIRT;
                    } else {
                        id = Block.STONE;
                    }
                    blocks[idx(x, y, z)] = (byte) id;
                }
                // 水填充
                for (int y = h + 1; y <= SEA_LEVEL; y++) {
                    blocks[idx(x, y, z)] = (byte) Block.WATER;
                }
                // 种树
                if (h > SEA_LEVEL + 1 && h < SNOW_LINE - 2 && r.nextInt(100) < 7) {
                    placeTree(x, h + 1, z, r);
                }
            }
        }
        flattenSpawnArea();
    }

    private void placeTree(int x, int y, int z, Random r) {
        int trunk = 4 + r.nextInt(3);
        for (int i = 0; i < trunk; i++) {
            setRaw(x, y + i, z, Block.WOOD);
        }
        int top = y + trunk;
        for (int dy = -2; dy <= 1; dy++) {
            int r2 = (dy <= -1) ? 2 : 1;
            for (int dx = -r2; dx <= r2; dx++)
                for (int dz = -r2; dz <= r2; dz++) {
                    if (dx == 0 && dz == 0 && dy <= 0) continue;
                    if (Math.abs(dx) == r2 && Math.abs(dz) == r2 && r.nextInt(3) == 0) continue;
                    setRaw(x + dx, top + dy, z + dz, Block.LEAVES);
                }
        }
    }

    private void setRaw(int x, int y, int z, int id) {
        if (x < 0 || x >= SX || y < 0 || y >= SY || z < 0 || z >= SZ) return;
        blocks[idx(x, y, z)] = (byte) id;
    }

    /** 把出生点周围整平为一小块草甸，保证开屏视野开阔、不卡在山体里 */
    private void flattenSpawnArea() {
        int cx = SX / 2, cz = SZ / 2, r = 5;
        // 取区域内平均地表高度（忽略水面），夹在安全区间
        int sum = 0, cnt = 0;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                int h = getSurfaceHeight(cx + dx, cz + dz);
                if (h > SEA_LEVEL) { sum += h; cnt++; }
            }
        int h0 = cnt > 0 ? sum / cnt : SEA_LEVEL + 4;
        h0 = Math.max(SEA_LEVEL + 2, Math.min(SNOW_LINE - 8, h0));
        for (int dx = -r - 3; dx <= r + 3; dx++)
            for (int dz = -r - 3; dz <= r + 3; dz++) {
                int x = cx + dx, z = cz + dz;
                if (x < 0 || x >= SX || z < 0 || z >= SZ) continue;
                // 清理上方（含树木/水面）
                for (int y = h0 + 1; y < SY; y++) setRaw(x, y, z, Block.AIR);
                // 重塑下方
                for (int y = 0; y <= h0; y++) {
                    int id;
                    if (y == h0) id = Block.GRASS;
                    else if (y >= h0 - 3) id = Block.DIRT;
                    else id = Block.STONE;
                    setRaw(x, y, z, id);
                }
            }
        spawnX = cx; spawnY = h0 + 1; spawnZ = cz;
    }

    public int[] findSpawn() { return new int[]{spawnX, spawnY, spawnZ}; }

    private int spawnX, spawnY, spawnZ;

    public int getSurfaceHeight(int x, int z) {
        if (x < 0) x = 0; if (x >= SX) x = SX - 1;
        if (z < 0) z = 0; if (z >= SZ) z = SZ - 1;
        for (int y = SY - 1; y >= 0; y--) {
            int b = blocks[idx(x, y, z)] & 0xFF;
            if (b != Block.AIR && b != Block.WATER) return y;
        }
        return 0;
    }

    // ---- 网格化 ----

    /** 在当前（CPU）线程重建指定区块的顶点数据 */
    public void rebuildChunkMesh(Chunk c) {
        c.opaque.clear();
        c.transparent.clear();
        int x0 = c.cx * CHUNK, y0 = c.cy * CHUNK, z0 = c.cz * CHUNK;
        for (int y = y0; y < y0 + CHUNK; y++) {
            for (int z = z0; z < z0 + CHUNK; z++) {
                for (int x = x0; x < x0 + CHUNK; x++) {
                    int self = getBlock(x, y, z);
                    if (self == Block.AIR) continue;
                    for (int f = 0; f < 6; f++) {
                        int nx = x + FACE_NEI[f][0];
                        int ny = y + FACE_NEI[f][1];
                        int nz = z + FACE_NEI[f][2];
                        int nei = getBlock(nx, ny, nz);
                        if (!shouldRenderFace(self, nei)) continue;
                        int tile = tileForFace(self, f);
                        float[] uv = atlas.uvForTile(tile);
                        Mesh m = Block.isOpaque(self) ? c.opaque : c.transparent;
                        addFace(m, x, y, z, f, uv, FACE_LIGHT[f]);
                    }
                }
            }
        }
        c.needsRebuild = false;
        c.built = true;
    }

    private boolean shouldRenderFace(int self, int nei) {
        return Block.isTransparent(nei) && nei != self;
    }

    private int tileForFace(int id, int face) {
        switch (face) {
            case 2: return Block.topTile(id);
            case 3: return Block.bottomTile(id);
            default: return Block.sideTile(id);
        }
    }

    private void addFace(Mesh m, int x, int y, int z, int face, float[] uv, float light) {
        float[][] v = FACE_VERT[face];
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        // 角顺序：左上,右上,右下,左下（v0 为贴图顶部）
        float[][] cuv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        // 三角形 0,1,2
        for (int t : new int[]{0, 1, 2}) {
            m.addVertex(x + v[t][0], y + v[t][1], z + v[t][2], cuv[t][0], cuv[t][1], light);
        }
        // 三角形 0,2,3
        for (int t : new int[]{0, 2, 3}) {
            m.addVertex(x + v[t][0], y + v[t][1], z + v[t][2], cuv[t][0], cuv[t][1], light);
        }
    }

    /** 后台线程：构建全部区块网格（逐块，让出时间片） */
    public void rebuildAllMeshes() {
        for (int cx = 0; cx < CX; cx++)
            for (int cy = 0; cy < CY; cy++)
                for (int cz = 0; cz < CZ; cz++) {
                    rebuildChunkMesh(chunks[cx][cy][cz]);
                }
    }
}
