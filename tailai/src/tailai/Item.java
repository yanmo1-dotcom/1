package tailai;

import java.awt.Color;

/**
 * 背包物品。只定义身份与展示信息，方块映射关系在 World 中维护（避免枚举循环初始化）。
 */
public enum Item {

    GRASS(0, "草皮方块", new Color(96, 170, 64), true),
    DIRT(1, "泥土", new Color(134, 96, 67), true),
    STONE(2, "石头", new Color(125, 125, 133), true),
    COPPER(3, "铜矿石", new Color(206, 127, 50), true),
    IRON(4, "铁矿石", new Color(178, 150, 170), true),
    WOOD(5, "木头", new Color(118, 84, 46), true),
    LEAF(6, "树叶", new Color(62, 142, 57), true),
    GEL_BLOCK(7, "凝胶块", new Color(80, 205, 140), true),
    GEL(8, "史莱姆凝胶", new Color(120, 225, 180), false),
    TORCH(9, "火把", new Color(255, 180, 70), true),
    WOODEN_SWORD(10, "木剑", new Color(150, 105, 58), false, 12),
    COPPER_SWORD(11, "铜剑", new Color(196, 122, 72), false, 16),
    IRON_SWORD(12, "铁剑", new Color(180, 148, 160), false, 20),
    LIFE_CRYSTAL(13, "生命水晶", new Color(255, 70, 110), false),
    WORKBENCH(14, "工作台", new Color(150, 105, 58), true),
    ROTTEN_MEAT(15, "腐肉", new Color(150, 90, 70), false),
    HEART(16, "红心", new Color(255, 60, 60), false),
    // 生物群系方块物品
    SAND(17, "沙子", new Color(222, 200, 140), true),
    SNOW(18, "雪块", new Color(235, 240, 248), true),
    ICE(19, "冰块", new Color(150, 205, 240), true),
    CACTUS(20, "仙人掌", new Color(70, 140, 60), true),
    JUNGLE_GRASS(21, "丛林草皮", new Color(60, 150, 66), true),
    // 远程武器与弹药
    WOOD_BOW(22, "木弓", new Color(150, 108, 55), false, 8, true),
    IRON_BOW(23, "铁弓", new Color(150, 150, 160), false, 13, true),
    ARROW(24, "箭矢", new Color(222, 215, 185), false),
    SUSPICIOUS_EYE(25, "可疑眼球", new Color(190, 40, 60), false),
    // 护甲（头盔 / 胸甲 / 护腿，防御值）—— 直接用 7 参数构造，damage=0, ranged=false
    COPPER_HELMET(26, "铜头盔", new Color(206, 127, 50), false, 0, false, 2),
    COPPER_CHESTPLATE(27, "铜胸甲", new Color(206, 127, 50), false, 0, false, 3),
    COPPER_LEGGINGS(28, "铜护腿", new Color(206, 127, 50), false, 0, false, 2),
    IRON_HELMET(29, "铁头盔", new Color(178, 150, 170), false, 0, false, 3),
    IRON_CHESTPLATE(30, "铁胸甲", new Color(178, 150, 170), false, 0, false, 4),
    IRON_LEGGINGS(31, "铁护腿", new Color(178, 150, 170), false, 0, false, 3),
    // 饰品（配饰栏，提供被动效果）
    HERMES_BOOTS(32, "赫尔墨斯靴", new Color(200, 80, 80), false, 0, false, 0, AccEffect.SPEED),
    CLOUD_IN_BOTTLE(33, "云朵瓶", new Color(220, 230, 255), false, 0, false, 0, AccEffect.DOUBLE_JUMP),
    LUCKY_HORSESHOE(34, "幸运马蹄铁", new Color(220, 180, 80), false, 0, false, 0, AccEffect.DEFENSE),
    REGEN_BAND(35, "再生手环", new Color(120, 220, 120), false, 0, false, 0, AccEffect.REGEN),
    MECHANICAL_SKULL(36, "机械骷髅", new Color(180, 180, 190), false),
    WORM_FOOD(37, "蠕虫诱饵", new Color(120, 60, 140), false),
    POTION_HEALTH(38, "生命药水", new Color(220, 60, 80), false),
    POTION_THORNS(39, "荆棘药水", new Color(100, 180, 80), false),
    BOMB(40, "炸弹", new Color(60, 60, 70), false),
    FISH(41, "鱼", new Color(120, 180, 220), false),
    FISHING_ROD(42, "钓鱼竿", new Color(150, 110, 60), false),
    FURNACE(43, "熔炉", new Color(120, 110, 100), true),
    ANVIL(44, "铁砧", new Color(90, 90, 100), true),
    COPPER_BAR(45, "铜锭", new Color(220, 140, 60), false),
    IRON_BAR(46, "铁锭", new Color(190, 170, 180), false),
    GOLD(47, "金矿石", new Color(255, 210, 60), true),
    GOLD_BAR(48, "金锭", new Color(255, 220, 80), false),
    GOLD_SWORD(49, "金剑", new Color(255, 215, 0), false, 26),
    GOLD_HELMET(50, "金头盔", new Color(255, 215, 0), false, 0, false, 3),
    GOLD_CHESTPLATE(51, "金胸甲", new Color(255, 215, 0), false, 0, false, 4),
    GOLD_LEGGINGS(52, "金护腿", new Color(255, 215, 0), false, 0, false, 3),
    // ---- 工具：镐（挖矿石/方块） ----
    COPPER_PICKAXE(53, "铜镐", new Color(220, 140, 60), ToolType.PICKAXE, 1),
    IRON_PICKAXE(54, "铁镐", new Color(190, 170, 180), ToolType.PICKAXE, 2),
    GOLD_PICKAXE(55, "金镐", new Color(255, 215, 0), ToolType.PICKAXE, 3),
    // ---- 工具：斧（砍树） ----
    COPPER_AXE(56, "铜斧", new Color(220, 140, 60), ToolType.AXE, 1),
    IRON_AXE(57, "铁斧", new Color(190, 170, 180), ToolType.AXE, 2),
    GOLD_AXE(58, "金斧", new Color(255, 215, 0), ToolType.AXE, 3),
    // ---- 地狱物品 ----
    ASH(59, "灰烬", new Color(80, 70, 65), true),
    HELLSTONE(60, "地狱石", new Color(200, 60, 40), true),
    OBSIDIAN(61, "黑曜石", new Color(30, 25, 35), true),
    HELLSTONE_BAR(62, "狱石锭", new Color(220, 80, 50), false),
    HELLSTONE_SWORD(63, "狱石剑", new Color(220, 60, 30), false, 38),
    HELLSTONE_PICKAXE(64, "狱石镐", new Color(220, 60, 30), ToolType.PICKAXE, 4),
    HELLSTONE_HELMET(65, "狱石头盔", new Color(200, 50, 30), false, 0, false, 5),
    HELLSTONE_CHESTPLATE(66, "狱石胸甲", new Color(200, 50, 30), false, 0, false, 7),
    HELLSTONE_LEGGINGS(67, "狱石护腿", new Color(200, 50, 30), false, 0, false, 5),
    // ---- 困难模式 ----
    WALL_SPAWNER(68, "血肉娃娃", new Color(180, 40, 60), false),
    COBALT(69, "钴矿石", new Color(60, 150, 200), true),
    COBALT_BAR(70, "钴锭", new Color(80, 180, 220), false),
    MYTHRIL(71, "秘银矿石", new Color(180, 140, 220), true),
    MYTHRIL_BAR(72, "秘银锭", new Color(200, 160, 240), false),
    COBALT_SWORD(73, "钴剑", new Color(60, 160, 210), false, 50),
    MYTHRIL_SWORD(74, "秘银剑", new Color(190, 150, 230), false, 65),
    // ---- 魔法武器与魔力物品 ----
    FIRE_STAFF(75, "火花法杖", new Color(255, 120, 40), 22, 10),
    MAGIC_DAGGER(76, "魔法飞刀", new Color(180, 200, 255), 15, 5),
    MANA_CRYSTAL(77, "魔力水晶", new Color(100, 180, 255), false),
    POTION_MANA(78, "魔力药水", new Color(80, 140, 255), false),
    // ---- 世界吞噬者相关 ----
    DEMONITE_ORE(80, "魔金矿石", new Color(120, 50, 160), true),
    DEMONITE_BAR(81, "魔金锭", new Color(150, 70, 190), false),
    DEMONITE_SWORD(82, "魔光剑", new Color(170, 80, 210), false, 45),
    SHADOW_SCALE(83, "暗影鳞片", new Color(80, 40, 100), false),
    // ---- 更多饰品 ----
    OBSIDIAN_SHIELD(84, "黑曜石护盾", new Color(60, 60, 80), false, 0, false, 4, AccEffect.DEFENSE),
    WARRIOR_EMBLEM(85, "战士徽章", new Color(200, 60, 60), false, 0, false, 0, AccEffect.NONE),
    RANGER_EMBLEM(86, "游侠徽章", new Color(60, 180, 80), false, 0, false, 0, AccEffect.NONE),
    SORCERER_EMBLEM(87, "术士徽章", new Color(80, 120, 220), false, 0, false, 0, AccEffect.NONE),
    // ---- 更多药水 ----
    POTION_IRONSKIN(88, "铁皮药水", new Color(150, 150, 170), false),
    POTION_SWIFTNESS(89, "敏捷药水", new Color(220, 200, 80), false),
    POTION_RAGE(90, "怒气药水", new Color(220, 80, 60), false),
    POTION_NIGHTVISION(91, "夜视药水", new Color(100, 200, 100), false),
    // ---- 哥布林入侵 ----
    GOBLIN_STANDARD(92, "哥布林战旗", new Color(100, 150, 80), false),
    // ---- 机械Boss（困难模式） ----
    MECHANICAL_WORM(93, "机械蠕虫", new Color(150, 50, 50), false),
    HALLOWED_BAR(94, "神圣锭", new Color(240, 220, 150), false),
    SOUL_OF_SIGHT(95, "视野之魂", new Color(150, 200, 255), false),
    HALLOWED_SWORD(96, "神圣剑", new Color(255, 240, 180), false, 80),
    // ---- 召唤武器 ----
    SLIME_STAFF(97, "史莱姆法杖", new Color(100, 200, 120), false, 12),
    // ---- 坐骑 ----
    SLIME_MOUNT(98, "史莱姆坐骑", new Color(120, 200, 255), false),
    // ---- 海盗入侵 ----
    PIRATE_MAP(99, "海盗地图", new Color(200, 160, 80), false);

    /** 饰品被动效果类型。 */
    public enum AccEffect {
        NONE, SPEED, DOUBLE_JUMP, DEFENSE, REGEN
    }
    /** 工具类型。 */
    public enum ToolType { NONE, PICKAXE, AXE }

    public final int id;
    public final String name;
    /** 物品图标主色。 */
    public final Color color;
    /** 是否可作为方块放置。 */
    public final boolean placeable;
    /** 武器伤害；0 表示非武器。 */
    public final int damage;
    /** 是否远程武器（弓）：左键射箭而非近战。 */
    public final boolean ranged;
    /** 护甲防御值；0 表示非护甲。 */
    public final int defense;
    /** 饰品效果；NONE 表示非饰品。 */
    public final AccEffect accEffect;
    /** 工具类型；NONE 表示非工具。 */
    public final ToolType toolType;
    /** 工具等级：0=无，1=铜，2=铁，3=金。镐决定能挖什么矿，斧和镐都影响速度。 */
    public final int toolLevel;
    /** 魔力消耗；>0 表示是魔法武器。 */
    public final int manaCost;

    Item(int id, String name, Color color, boolean placeable) {
        this(id, name, color, placeable, 0, false, 0, AccEffect.NONE, ToolType.NONE, 0, 0);
    }

    Item(int id, String name, Color color, boolean placeable, int damage) {
        this(id, name, color, placeable, damage, false, 0, AccEffect.NONE, ToolType.NONE, 0, 0);
    }

    Item(int id, String name, Color color, boolean placeable, int damage, boolean ranged) {
        this(id, name, color, placeable, damage, ranged, 0, AccEffect.NONE, ToolType.NONE, 0, 0);
    }

    Item(int id, String name, Color color, boolean placeable, int damage, boolean ranged, int defense) {
        this(id, name, color, placeable, damage, ranged, defense, AccEffect.NONE, ToolType.NONE, 0, 0);
    }

    Item(int id, String name, Color color, boolean placeable, int damage, boolean ranged, int defense, AccEffect acc) {
        this(id, name, color, placeable, damage, ranged, defense, acc, ToolType.NONE, 0, 0);
    }

    /** 工具构造函数。 */
    Item(int id, String name, Color color, ToolType tool, int level) {
        this(id, name, color, false, 0, false, 0, AccEffect.NONE, tool, level, 0);
    }

    /** 魔法武器构造函数。 */
    Item(int id, String name, Color color, int damage, int manaCost) {
        this(id, name, color, false, damage, false, 0, AccEffect.NONE, ToolType.NONE, 0, manaCost);
    }

    Item(int id, String name, Color color, boolean placeable, int damage, boolean ranged, int defense,
         AccEffect acc, ToolType tool, int level, int manaCost) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.placeable = placeable;
        this.damage = damage;
        this.ranged = ranged;
        this.defense = defense;
        this.accEffect = acc;
        this.toolType = tool;
        this.toolLevel = level;
        this.manaCost = manaCost;
    }

    public boolean isWeapon() {
        return damage > 0;
    }

    public boolean isMagic() {
        return manaCost > 0;
    }

    public boolean isPickaxe() {
        return toolType == ToolType.PICKAXE;
    }

    public boolean isAxe() {
        return toolType == ToolType.AXE;
    }

    public boolean isTool() {
        return toolType != ToolType.NONE;
    }

    public boolean isBow() {
        return ranged;
    }

    public boolean isArmor() {
        return defense > 0;
    }

    public boolean isAccessory() {
        return accEffect != AccEffect.NONE;
    }

    public boolean isPotion() {
        return this == POTION_HEALTH || this == POTION_THORNS;
    }

    /** 护甲槽位：0=头盔，1=胸甲，2=护腿。非护甲返回 -1。 */
    public int armorSlot() {
        if (!isArmor()) {
            return -1;
        }
        // 铜套 26-28，铁套 29-31，金套 50-52，狱石套 65-67
        if (id >= 65) return (id - 65) % 3;
        if (id >= 50) return (id - 50) % 3;
        return (id - 26) % 3;
    }

    private static final Item[] BY_ID = values();

    public static Item byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }
}
