package com.yourname.voxelgame.inventory;

/**
 * 物品注册表。方块物品 id 复用 BlockType.id（1..9），工具 id 从 100 起。
 * 工具分镐(pickaxe)与剑(sword)，各有木/石/铁等级，对应挖矿倍率与攻击加成。
 */
public final class ItemRegistry {

    // 工具 id 段
    public static final int WOOD_PICKAXE = 100;
    public static final int STONE_PICKAXE = 101;
    public static final int IRON_PICKAXE = 102;
    public static final int WOOD_SWORD = 110;
    public static final int STONE_SWORD = 111;
    public static final int IRON_SWORD = 112;
    public static final int GEL = 200; // 凝胶材料

    // 工具类型
    public enum ToolType { NONE, PICKAXE, SWORD }

    public static final class ItemDef {
        public final int id;
        public final String name;
        public final int maxStack;
        public final ToolType toolType;
        public final int tier;       // 0=木 1=石 2=铁
        public final float mineSpeed; // 挖矿倍率
        public final int attackBonus; // 攻击加成
        public final int maxDurability;
        public final float r, g, b;   // UI 颜色

        ItemDef(int id, String name, int maxStack, ToolType tt, int tier,
                float mineSpeed, int atk, int dur, float r, float g, float b) {
            this.id = id; this.name = name; this.maxStack = maxStack;
            this.toolType = tt; this.tier = tier;
            this.mineSpeed = mineSpeed; this.attackBonus = atk;
            this.maxDurability = dur; this.r = r; this.g = g; this.b = b;
        }
    }

    private static final ItemDef[] defs = new ItemDef[256];

    static {
        // 空气兜底（id=0）
        def(0, "Air", 0, 0f,0f,0f);
        // 方块物品（maxStack 64，非工具）。颜色复用 BlockType。
        def(1, "Grass", 64, 0.4f,0.7f,0.3f);
        def(2, "Dirt", 64, 0.5f,0.4f,0.3f);
        def(3, "Stone", 64, 0.5f,0.5f,0.5f);
        def(4, "Wood", 64, 0.75f,0.5f,0.2f);
        def(5, "Bedrock", 64, 0.15f,0.15f,0.15f);
        def(6, "Coal", 64, 0.1f,0.1f,0.1f);
        def(7, "Iron", 64, 0.7f,0.5f,0.4f);
        def(8, "Gold", 64, 0.9f,0.75f,0.2f);
        def(9, "Torch", 64, 1.0f,0.7f,0.2f);

        // 凝胶
        def(GEL, "Gel", 64, 0.3f,0.5f,1.0f);

        // 工具：镐（木2x/石4x/铁8x），剑（+5/+10/+20）
        defs[WOOD_PICKAXE] = new ItemDef(WOOD_PICKAXE, "Wood Pickaxe", 1, ToolType.PICKAXE, 0, 2f, 0, 60, 0.6f,0.4f,0.2f);
        defs[STONE_PICKAXE] = new ItemDef(STONE_PICKAXE, "Stone Pickaxe", 1, ToolType.PICKAXE, 1, 4f, 0, 130, 0.5f,0.5f,0.5f);
        defs[IRON_PICKAXE] = new ItemDef(IRON_PICKAXE, "Iron Pickaxe", 1, ToolType.PICKAXE, 2, 8f, 0, 250, 0.7f,0.5f,0.4f);
        defs[WOOD_SWORD] = new ItemDef(WOOD_SWORD, "Wood Sword", 1, ToolType.SWORD, 0, 1f, 5, 60, 0.6f,0.4f,0.2f);
        defs[STONE_SWORD] = new ItemDef(STONE_SWORD, "Stone Sword", 1, ToolType.SWORD, 1, 1f, 10, 130, 0.5f,0.5f,0.5f);
        defs[IRON_SWORD] = new ItemDef(IRON_SWORD, "Iron Sword", 1, ToolType.SWORD, 2, 1f, 20, 250, 0.7f,0.5f,0.4f);
    }

    private static void def(int id, String name, int maxStack, float r, float g, float b) {
        defs[id] = new ItemDef(id, name, maxStack, ToolType.NONE, 0, 1f, 0, 0, r, g, b);
    }

    public static ItemDef get(int id) {
        if (id < 0 || id >= defs.length || defs[id] == null) return defs[0]; // 兜底
        return defs[id];
    }

    public static boolean isTool(int id) { return get(id).toolType != ToolType.NONE; }
    public static String name(int id) { return get(id).name; }
    public static int maxStack(int id) { return get(id).maxStack; }
}
