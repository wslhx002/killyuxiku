package com.voxelsandbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** 玩家改动过的方块（key = idx），存档时只持久化这些差异 */
    private final Map<Long, Byte> edits = new HashMap<>();
    /** 每次放置/破坏递增，用于判断是否需要自动存档 */
    private volatile int editVersion = 0;

    // 6 个面的 4 顶点偏移 (x,y,z) —— 顺序对应 UV 左上/右上/右下/左下
    private static final float[][][] FACE_VERT = {
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}}, // +X
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}, // -X
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}}, // +Y
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, // -Y
            {{1, 0, 1}, {0, 0, 1}, {0, 1, 1}, {1, 1, 1}}, // +Z
            {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}, // -Z
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
        long key = idx(x, y, z);
        blocks[(int) key] = (byte) id;
        edits.put(key, (byte) id);
        editVersion++;
        markDirty(x, y, z);
    }

    public int getEditVersion() { return editVersion; }

    /** 当前所有玩家改动的副本（可在 GL 线程暂停后安全读取/写入存档） */
    public Map<Long, Byte> copyEdits() { return new HashMap<>(edits); }

    /** 应用存档中的方块差异（在地形生成后、网格构建前调用） */
    public void applyEdits(Map<Long, Byte> saved) {
        if (saved == null || saved.isEmpty()) return;
        for (Map.Entry<Long, Byte> e : saved.entrySet()) {
            int k = e.getKey().intValue();
            if (k < 0 || k >= blocks.length) continue;
            blocks[k] = e.getValue();
            edits.put(e.getKey(), e.getValue());
        }
        // 载入的改动视为已保存的基线
        editVersion = 0;
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
        int[][] heights = new int[SX][SZ];
        for (int x = 0; x < SX; x++) {
            for (int z = 0; z < SZ; z++) {
                float n = noise.fbm(x * 0.018f, z * 0.018f, 4, 2.0f, 0.5f);
                float mountain = Math.max(0, noise.fbm(x * 0.006f + 100, z * 0.006f + 100, 3, 2.0f, 0.5f));
                int h = (int) (base + n * 10 + mountain * mountain * 28);
                if (h < 2) h = 2;
                if (h >= SY) h = SY - 1;
                heights[x][z] = h;
                for (int y = 0; y <= h; y++) {
                    int id;
                    if (y == h) {
                        if (h <= SEA_LEVEL + 1) id = Block.SAND;
                        else if (h >= SNOW_LINE) id = Block.SNOW;
                        else id = Block.GRASS;
                    } else if (y >= h - 3) {
                        id = (h <= SEA_LEVEL + 1) ? Block.SAND : Block.DIRT;
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
        generateCaves(r, heights);
    }

    /**
     * 地下洞穴：从均匀分布的起点挖掘「蠕虫隧道」，并在部分起点处挖 2x2 竖井通向地表。
     * 所有挖掘都保证距该列地表至少 4 格，避免破坏海床或形成悬空水。
     */
    private void generateCaves(Random r, int[][] heights) {
        List<int[]> cells = new ArrayList<>(64);
        for (int gx = 0; gx < 8; gx++)
            for (int gz = 0; gz < 8; gz++) cells.add(new int[]{gx, gz});
        Collections.shuffle(cells, r);

        int worms = Math.min(44, cells.size());
        int entrances = 0;
        for (int i = 0; i < worms; i++) {
            int[] cell = cells.get(i);
            int x0 = cell[0] * 12 + 2 + r.nextInt(9);
            int z0 = cell[1] * 12 + 2 + r.nextInt(9);
            if (x0 >= SX - 2 || z0 >= SZ - 2) continue;
            int h = heights[x0][z0];
            if (h < 10) continue;
            int y0 = 4 + r.nextInt(Math.max(1, h - 9));
            if (y0 > h - 6) y0 = h - 6;
            if (y0 < 4) y0 = 4;

            // 地表竖井入口：只开在陆地上，且避开树干
            if (entrances < 14 && h > SEA_LEVEL + 2 && getBlock(x0, h + 1, z0) != Block.WOOD) {
                for (int y = Math.max(2, y0 - 1); y <= h; y++)
                    for (int dx = 0; dx < 2; dx++)
                        for (int dz = 0; dz < 2; dz++)
                            setRaw(x0 + dx, y, z0 + dz, Block.AIR);
                entrances++;
            }
            carveWorm(r, x0 + 0.5f, y0 + 0.5f, z0 + 0.5f, heights);
        }
    }

    private void carveWorm(Random r, float fx, float fy, float fz, int[][] heights) {
        float dx = r.nextFloat() - 0.5f, dy = (r.nextFloat() - 0.5f) * 0.5f, dz = r.nextFloat() - 0.5f;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) { dx = 0; dy = 0; dz = 1; len = 1; }
        dx /= len; dy /= len; dz /= len;

        int steps = 60 + r.nextInt(80);
        for (int s = 0; s < steps; s++) {
            carveSphere(fx, fy, fz, 1.1f + r.nextFloat() * 0.6f, heights);
            dx += (r.nextFloat() - 0.5f) * 0.35f;
            dy += (r.nextFloat() - 0.5f) * 0.18f;
            dz += (r.nextFloat() - 0.5f) * 0.35f;
            len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-4f) { dx = 0; dy = 0; dz = 1; len = 1; }
            dx /= len; dy /= len; dz /= len;
            fx += dx; fy += dy * 0.7f; fz += dz;

            if (fx < 3 || fx > SX - 4 || fz < 3 || fz > SZ - 4) {
                dx = -dx; dz = -dz;
                fx = Math.max(3, Math.min(SX - 4, fx));
                fz = Math.max(3, Math.min(SZ - 4, fz));
            }
            int ix = Math.max(0, Math.min(SX - 1, (int) fx));
            int iz = Math.max(0, Math.min(SZ - 1, (int) fz));
            int hh = heights[ix][iz];
            if (hh < 8 || fy > hh - 4) {
                dy = -dy;
                if (hh >= 8) fy = Math.min(hh - 4, fy);
            }
            if (fy < 3) { dy = Math.abs(dy); fy = 3; }
        }
    }

    private void carveSphere(float cx, float cy, float cz, float rad, int[][] heights) {
        int x0 = (int) Math.floor(cx - rad), x1 = (int) Math.ceil(cx + rad);
        int y0 = (int) Math.floor(cy - rad), y1 = (int) Math.ceil(cy + rad);
        int z0 = (int) Math.floor(cz - rad), z1 = (int) Math.ceil(cz + rad);
        float r2 = rad * rad;
        for (int x = Math.max(1, x0); x <= Math.min(SX - 2, x1); x++)
            for (int z = Math.max(1, z0); z <= Math.min(SZ - 2, z1); z++) {
                int hh = heights[x][z];
                if (hh < 8) continue;
                int ymax = hh - 4;
                for (int y = Math.max(3, y0); y <= Math.min(ymax, y1); y++) {
                    float ddx = x + 0.5f - cx, ddy = y + 0.5f - cy, ddz = z + 0.5f - cz;
                    if (ddx * ddx + ddy * ddy + ddz * ddz <= r2) {
                        setRaw(x, y, z, Block.AIR);
                    }
                }
            }
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
        // 角顺序：左上,右上,右下,左下
        float[][] cuv = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
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
