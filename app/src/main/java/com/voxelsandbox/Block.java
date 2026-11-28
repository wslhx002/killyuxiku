package com.voxelsandbox;

/**
 * 方块类型定义与纹理坐标映射。
 * 所有美术资源均为程序化生成（见 TextureAtlas），不依赖外部素材。
 */
public final class Block {
    public static final int AIR    = 0;
    public static final int GRASS  = 1;
    public static final int DIRT   = 2;
    public static final int STONE  = 3;
    public static final int WOOD   = 4;
    public static final int LEAVES = 5;
    public static final int SAND   = 6;
    public static final int WATER  = 7;
    public static final int PLANK  = 8;
    public static final int COBBLE = 9;
    public static final int BRICK  = 10;
    public static final int SNOW   = 11;
    public static final int GLASS  = 12;
    public static final int GLOW   = 13; // 发光石

    public static final int COUNT = 14;

    // 纹理图集中每个方块的 tile 索引（0-based）
    // face order: 0=top, 1=bottom, 2=side (for dirt/stone/sand etc. side=top)
    public static int topTile(int id) {
        switch (id) {
            case GRASS:  return 0;
            case SNOW:   return 15;
            case WOOD:   return 4;
            case LEAVES: return 5;
            case SAND:   return 6;
            case WATER:  return 7;
            case PLANK:  return 8;
            case COBBLE: return 9;
            case BRICK:  return 10;
            case GLASS:  return 11;
            case DIRT:   return 2;
            case STONE:  return 3;
            case GLOW:   return 14;
            default:     return 3;
        }
    }

    public static int bottomTile(int id) {
        switch (id) {
            case GRASS:  return 2; // dirt
            case SNOW:   return 2;
            case WOOD:   return 4;
            default:     return topTile(id);
        }
    }

    public static int sideTile(int id) {
        switch (id) {
            case GRASS:  return 1;  // grass side (top+bottom blended)
            case SNOW:   return 12; // snow side
            case WOOD:   return 13; // wood side bark
            default:     return topTile(id);
        }
    }

    public static boolean isSolid(int id) {
        return id != AIR && id != WATER;
    }

    public static boolean isOpaque(int id) {
        return id != AIR && id != WATER && id != GLASS && id != LEAVES;
    }

    public static boolean isTransparent(int id) {
        return id == AIR || id == WATER || id == GLASS || id == LEAVES;
    }

    /** 玩家可放置的方块循环选择列表 */
    public static final int[] PALETTE = {
        GRASS, DIRT, STONE, WOOD, LEAVES, SAND, PLANK, COBBLE, BRICK, SNOW, GLASS, GLOW
    };

    private Block() {}
}
