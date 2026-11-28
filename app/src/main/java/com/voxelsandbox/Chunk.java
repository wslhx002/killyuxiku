package com.voxelsandbox;

/**
 * 区块：持有不透明与透明两个网格，以及重建/上传脏标记。
 */
public class Chunk {
    public final int cx, cy, cz;
    public final Mesh opaque;
    public final Mesh transparent;
    public volatile boolean needsRebuild = false;
    public volatile boolean built = false; // CPU 网格数据是否就绪

    public Chunk(int cx, int cy, int cz) {
        this.cx = cx; this.cy = cy; this.cz = cz;
        opaque = new Mesh(4 * 1024);
        transparent = new Mesh(1024);
    }

    public void dispose() {
        opaque.dispose();
        transparent.dispose();
    }
}
