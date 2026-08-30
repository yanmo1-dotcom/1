package tailai;

import java.awt.Color;

/**
 * 方块类型。id 与 World 中 byte 数组存储值一致。
 * 注意：本类不引用 Item，避免枚举双向初始化问题；方块<->物品映射在 World 中维护。
 */
public enum TileType {

    AIR(0, "空气", null, 0.0f, false, false, 0),
    GRASS(1, "草皮", new Color(96, 170, 64), 0.35f, true, true, 0),
    DIRT(2, "泥土", new Color(134, 96, 67), 0.35f, true, true, 0),
    STONE(3, "石头", new Color(125, 125, 133), 0.6f, true, true, 0),
    COPPER(4, "铜矿石", new Color(206, 127, 50), 0.85f, true, true, 1),
    IRON(5, "铁矿石", new Color(178, 150, 170), 0.95f, true, true, 1),
    WOOD(6, "木头", new Color(118, 84, 46), 0.5f, false, true, 0),
    LEAF(7, "树叶", new Color(62, 142, 57), 0.2f, false, true, 0),
    GEL_BLOCK(8, "凝胶块", new Color(80, 205, 140), 0.3f, true, true, 0),
    TORCH(9, "火把", new Color(255, 180, 70), 0.1f, false, true, 0),
    WORKBENCH(10, "工作台", new Color(150, 105, 58), 0.6f, false, true, 0),
    HEART_CRYSTAL(11, "生命水晶", new Color(255, 70, 110), 0.4f, false, false, 0),
    // 生物群系方块：沙漠 / 雪原 / 丛林
    SAND(12, "沙子", new Color(222, 200, 140), 0.3f, true, true, 0),
    SNOW(13, "雪块", new Color(235, 240, 248), 0.3f, true, true, 0),
    ICE(14, "冰块", new Color(150, 205, 240), 0.45f, true, true, 0),
    CACTUS(15, "仙人掌", new Color(70, 140, 60), 0.4f, true, true, 0),
    JUNGLE_GRASS(16, "丛林草皮", new Color(60, 150, 66), 0.35f, true, true, 0),
    // 腐化之地方块
    CORRUPT_GRASS(17, "腐化草皮", new Color(100, 60, 120), 0.35f, true, true, 0),
    EBONSTONE(18, "黑檀石", new Color(70, 50, 90), 0.7f, true, true, 1),
    SHADOW_WOOD(19, "暗影木", new Color(80, 50, 100), 0.5f, false, true, 0),
    VILE_MUSHROOM(20, "毒蘑菇", new Color(140, 80, 160), 0.2f, false, true, 0),
    WATER(21, "水", new Color(70, 140, 220, 170), 0f, false, true, 0),
    FURNACE(22, "熔炉", new Color(120, 110, 100), 0.8f, false, true, 0),
    ANVIL(23, "铁砧", new Color(90, 90, 100), 0.9f, false, true, 0),
    GOLD(24, "金矿石", new Color(255, 210, 60), 1.1f, true, true, 2),
    // 地狱维度方块
    ASH(25, "灰烬", new Color(80, 70, 65), 0.4f, true, true, 0),
    HELLSTONE(26, "地狱石", new Color(200, 60, 40), 1.3f, true, true, 3),
    LAVA(27, "熔岩", new Color(255, 100, 20, 200), 0f, false, false, 0),
    OBSIDIAN(28, "黑曜石", new Color(30, 25, 35), 1.5f, true, true, 3),
    COBALT(29, "钴矿石", new Color(60, 150, 200), 1.2f, true, true, 4),
    MYTHRIL(30, "秘银矿石", new Color(180, 140, 220), 1.4f, true, true, 4);

    public final int id;
    public final String name;
    /** 主色；AIR 为 null（不绘制）。 */
    public final Color color;
    /** 用基础手段挖掘所需的秒数；0 表示不可挖。 */
    public final float mineSeconds;
    /** 是否阻挡实体移动。 */
    public final boolean solid;
    /** 是否允许玩家放置。 */
    public final boolean placeable;
    /** 挖掘所需最低镐等级：0=徒手可挖，1=铜镐，2=铁镐，3=金镐。 */
    public final int minPickaxe;

    TileType(int id, String name, Color color, float mineSeconds, boolean solid, boolean placeable, int minPickaxe) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.mineSeconds = mineSeconds;
        this.solid = solid;
        this.placeable = placeable;
        this.minPickaxe = minPickaxe;
    }

    private static final TileType[] BY_ID = values();

    public static TileType byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : AIR;
    }
}
