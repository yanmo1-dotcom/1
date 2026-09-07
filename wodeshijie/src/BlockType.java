import java.awt.Color;

/**
 * 方块类型枚举
 * 定义游戏中所有方块的属性：颜色、是否固体、硬度等
 */
public enum BlockType {
    AIR(0, "空气", new Color(0, 0, 0, 0), false, false, 0),
    GRASS(1, "草方块", new Color(76, 153, 0), true, false, 0.6),
    DIRT(2, "泥土", new Color(134, 96, 67), true, false, 0.5),
    STONE(3, "石头", new Color(128, 128, 128), true, false, 1.5),
    WOOD(4, "木头", new Color(102, 76, 51), true, false, 1.0),
    LEAVES(5, "树叶", new Color(34, 139, 34), true, true, 0.2),
    SAND(6, "沙子", new Color(238, 214, 175), true, false, 0.5),
    WATER(7, "水", new Color(64, 164, 223, 180), false, true, -1),
    PLANKS(8, "木板", new Color(180, 140, 90), true, false, 0.8),
    COAL_ORE(9, "煤矿石", new Color(60, 60, 60), true, false, 2.0),
    IRON_ORE(10, "铁矿石", new Color(180, 140, 110), true, false, 2.5),
    DIAMOND_ORE(11, "钻石矿石", new Color(100, 220, 220), true, false, 3.0),
    GLASS(12, "玻璃", new Color(200, 230, 240, 160), true, true, 0.3),
    BRICK(13, "砖块", new Color(150, 60, 50), true, false, 1.2),
    BEDROCK(14, "基岩", new Color(40, 40, 40), true, false, -1),
    SNOW(15, "雪块", new Color(240, 240, 250), true, false, 0.4),
    TORCH(16, "火把", new Color(255, 200, 50), false, true, 0.1);

    private final int id;
    private final String name;
    private final Color color;
    private final boolean solid;      // 是否有碰撞
    private final boolean transparent; // 是否透明（渲染时不遮挡后方）
    private final double hardness;    // 硬度，-1表示不可破坏

    BlockType(int id, String name, Color color, boolean solid, boolean transparent, double hardness) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.solid = solid;
        this.transparent = transparent;
        this.hardness = hardness;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Color getColor() { return color; }
    public boolean isSolid() { return solid; }
    public boolean isTransparent() { return transparent; }
    public double getHardness() { return hardness; }
    public boolean isBreakable() { return hardness >= 0; }

    /** 根据ID获取方块类型 */
    public static BlockType fromId(int id) {
        for (BlockType type : values()) {
            if (type.id == id) return type;
        }
        return AIR;
    }
}
