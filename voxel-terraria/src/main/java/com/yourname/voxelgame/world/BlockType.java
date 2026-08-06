package com.yourname.voxelgame.world;

/**
 * 方块类型定义。ID 0=空气，1..N 为实体方块。
 * 颜色为 (r,g,b) 三元组，用于网格着色与快捷栏 UI。
 */
public enum BlockType {

    AIR     (0,  0.0f, 0.0f, 0.0f, false, 0),
    GRASS   (1,  0.4f, 0.7f, 0.3f, true, 0),
    DIRT    (2,  0.5f, 0.4f, 0.3f, true, 0),
    STONE   (3,  0.5f, 0.5f, 0.5f, true, 0),
    WOOD    (4, 0.75f, 0.5f, 0.2f, true, 0),
    BEDROCK (5, 0.15f, 0.15f, 0.15f, true, 0),
    COAL    (6, 0.1f, 0.1f, 0.1f, true, 0),
    IRON    (7, 0.7f, 0.5f, 0.4f, true, 0),
    GOLD    (8, 0.9f, 0.75f, 0.2f, true, 0),
    TORCH   (9, 1.0f, 0.7f, 0.2f, false, 12); // 火把：透明 + 点光源 12

    public final int id;
    public final float r, g, b;
    public final boolean opaque;   // 是否遮挡光线
    public final int lightEmission;// 自身发光等级（0 不发光）

    BlockType(int id, float r, float g, float b, boolean opaque, int lightEmission) {
        this.id = id;
        this.r = r; this.g = g; this.b = b;
        this.opaque = opaque;
        this.lightEmission = lightEmission;
    }

    /** 快捷栏可放置方块数（1-9 键）。 */
    public static final int PLACEABLE_COUNT = 5;

    public static BlockType byId(int id) {
        for (BlockType t : values()) if (t.id == id) return t;
        return AIR;
    }

    /** 快捷栏第 i 格对应的方块类型。 */
    public static BlockType hotbarSlot(int slot) {
        if (slot == 0) return GRASS;
        if (slot == 1) return DIRT;
        if (slot == 2) return STONE;
        if (slot == 3) return WOOD;
        if (slot == 4) return TORCH;
        return GRASS;
    }
}
