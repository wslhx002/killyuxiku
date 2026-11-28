package com.voxelsandbox;

/**
 * 体素射线检测（固定步长 DDA），返回命中方块与前一个空气方块（用于放置）。
 */
public class Raycaster {
    public static class Hit {
        public int x, y, z;     // 命中的方块
        public int nx, ny, nz;  // 命中面前方的空气方块坐标（放置位置）
    }

    public static Hit cast(World world, float ox, float oy, float oz,
                           float dx, float dy, float dz, float maxDist) {
        float step = 0.05f;
        int bx = (int) Math.floor(ox);
        int by = (int) Math.floor(oy);
        int bz = (int) Math.floor(oz);
        int px = bx, py = by, pz = bz;
        for (float d = 0; d <= maxDist; d += step) {
            if (Block.isSolid(world.getBlock(bx, by, bz))) {
                Hit h = new Hit();
                h.x = bx; h.y = by; h.z = bz;
                h.nx = px; h.ny = py; h.nz = pz;
                return h;
            }
            px = bx; py = by; pz = bz;
            ox += dx * step; oy += dy * step; oz += dz * step;
            bx = (int) Math.floor(ox);
            by = (int) Math.floor(oy);
            bz = (int) Math.floor(oz);
        }
        return null;
    }
}
