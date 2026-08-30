package tailai;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * 物品图标绘制：用代码为每类物品绘制有特征的像素图标（16x16 基准，自动缩放）。
 * 不依赖外部图片文件，调用 draw(g, item, x, y, size) 即可。
 */
public final class ItemIcon {
    private ItemIcon() {}

    /** 绘制物品图标到指定区域（x,y 为左上角，size 为边长）。 */
    public static void draw(Graphics2D g, Item item, int x, int y, int size) {
        int s = size;
        switch (item) {
            // ---- 武器：剑 ----
            case WOODEN_SWORD: drawSword(g, x, y, s, new Color(150, 105, 58), new Color(100, 70, 40)); break;
            case COPPER_SWORD: drawSword(g, x, y, s, new Color(220, 140, 60), new Color(120, 80, 40)); break;
            case IRON_SWORD: drawSword(g, x, y, s, new Color(200, 190, 200), new Color(100, 100, 110)); break;
            case GOLD_SWORD: drawSword(g, x, y, s, new Color(255, 215, 0), new Color(180, 140, 20)); break;
            // ---- 工具：镐 ----
            case COPPER_PICKAXE: drawPickaxe(g, x, y, s, new Color(220, 140, 60)); break;
            case IRON_PICKAXE: drawPickaxe(g, x, y, s, new Color(190, 170, 180)); break;
            case GOLD_PICKAXE: drawPickaxe(g, x, y, s, new Color(255, 215, 0)); break;
            // ---- 工具：斧 ----
            case COPPER_AXE: drawAxe(g, x, y, s, new Color(220, 140, 60)); break;
            case IRON_AXE: drawAxe(g, x, y, s, new Color(190, 170, 180)); break;
            case GOLD_AXE: drawAxe(g, x, y, s, new Color(255, 215, 0)); break;
            // ---- 狱石工具/武器 ----
            case HELLSTONE_SWORD: drawSword(g, x, y, s, new Color(220, 60, 30), new Color(120, 40, 20)); break;
            case HELLSTONE_PICKAXE: drawPickaxe(g, x, y, s, new Color(220, 60, 30)); break;
            case HELLSTONE_BAR: drawBar(g, x, y, s, new Color(220, 80, 50)); break;
            // ---- 困难模式物品 ----
            case WALL_SPAWNER: drawVoodooDoll(g, x, y, s); break;
            case COBALT: drawBlock(g, x, y, s, new Color(60, 150, 200)); break;
            case COBALT_BAR: drawBar(g, x, y, s, new Color(80, 180, 220)); break;
            case COBALT_SWORD: drawSword(g, x, y, s, new Color(60, 160, 210), new Color(40, 80, 120)); break;
            case MYTHRIL: drawBlock(g, x, y, s, new Color(180, 140, 220)); break;
            case MYTHRIL_BAR: drawBar(g, x, y, s, new Color(200, 160, 240)); break;
            case MYTHRIL_SWORD: drawSword(g, x, y, s, new Color(190, 150, 230), new Color(100, 60, 140)); break;
            // ---- 魔法武器 ----
            case FIRE_STAFF: drawStaff(g, x, y, s, new Color(255, 120, 40)); break;
            case MAGIC_DAGGER: drawMagicDagger(g, x, y, s); break;
            case MANA_CRYSTAL: drawManaCrystal(g, x, y, s); break;
            case POTION_MANA: drawPotion(g, x, y, s, new Color(80, 140, 255)); break;
            // ---- 世界吞噬者相关 ----
            case WORM_FOOD: drawWormFood(g, x, y, s); break;
            case DEMONITE_ORE: drawOre(g, x, y, s, new Color(120, 50, 160)); break;
            case DEMONITE_BAR: drawBar(g, x, y, s, new Color(150, 70, 190)); break;
            case DEMONITE_SWORD: drawSword(g, x, y, s, new Color(170, 80, 210), new Color(80, 30, 110)); break;
            case SHADOW_SCALE: drawShadowScale(g, x, y, s); break;
            // ---- 新饰品 ----
            case OBSIDIAN_SHIELD: drawShield(g, x, y, s, new Color(50, 50, 70)); break;
            case WARRIOR_EMBLEM: drawEmblem(g, x, y, s, new Color(200, 60, 60)); break;
            case RANGER_EMBLEM: drawEmblem(g, x, y, s, new Color(60, 180, 80)); break;
            case SORCERER_EMBLEM: drawEmblem(g, x, y, s, new Color(80, 120, 220)); break;
            // ---- 新药水 ----
            case POTION_IRONSKIN: drawPotion(g, x, y, s, new Color(150, 150, 170)); break;
            case POTION_SWIFTNESS: drawPotion(g, x, y, s, new Color(220, 200, 80)); break;
            case POTION_RAGE: drawPotion(g, x, y, s, new Color(220, 80, 60)); break;
            case POTION_NIGHTVISION: drawPotion(g, x, y, s, new Color(100, 200, 100)); break;
            // ---- 哥布林入侵 ----
            case GOBLIN_STANDARD: drawGoblinStandard(g, x, y, s); break;
            // ---- 机械Boss ----
            case MECHANICAL_WORM: drawMechanicalWorm(g, x, y, s); break;
            case HALLOWED_BAR: drawBar(g, x, y, s, new Color(240, 220, 150)); break;
            case SOUL_OF_SIGHT: drawSoul(g, x, y, s, new Color(150, 200, 255)); break;
            case HALLOWED_SWORD: drawSword(g, x, y, s, new Color(255, 240, 180), new Color(200, 180, 100)); break;
            // ---- 召唤武器 ----
            case SLIME_STAFF: drawSlimeStaff(g, x, y, s); break;
            // ---- 坐骑 ----
            case SLIME_MOUNT: drawSlimeMount(g, x, y, s); break;
            // ---- 海盗入侵 ----
            case PIRATE_MAP: drawPirateMap(g, x, y, s); break;
            // ---- 武器：弓 ----
            case WOOD_BOW: drawBow(g, x, y, s, new Color(150, 108, 55)); break;
            case IRON_BOW: drawBow(g, x, y, s, new Color(160, 160, 170)); break;
            case ARROW: drawArrow(g, x, y, s); break;
            // ---- 护甲 ----
            case COPPER_HELMET: drawHelmet(g, x, y, s, new Color(206, 127, 50)); break;
            case COPPER_CHESTPLATE: drawChestplate(g, x, y, s, new Color(206, 127, 50)); break;
            case COPPER_LEGGINGS: drawLeggings(g, x, y, s, new Color(206, 127, 50)); break;
            case IRON_HELMET: drawHelmet(g, x, y, s, new Color(178, 150, 170)); break;
            case IRON_CHESTPLATE: drawChestplate(g, x, y, s, new Color(178, 150, 170)); break;
            case IRON_LEGGINGS: drawLeggings(g, x, y, s, new Color(178, 150, 170)); break;
            case GOLD_HELMET: drawHelmet(g, x, y, s, new Color(255, 215, 0)); break;
            case GOLD_CHESTPLATE: drawChestplate(g, x, y, s, new Color(255, 215, 0)); break;
            case GOLD_LEGGINGS: drawLeggings(g, x, y, s, new Color(255, 215, 0)); break;
            // ---- 药水 ----
            case POTION_HEALTH: drawPotion(g, x, y, s, new Color(220, 50, 70)); break;
            case POTION_THORNS: drawPotion(g, x, y, s, new Color(100, 180, 80)); break;
            // ---- 饰品 ----
            case HERMES_BOOTS: drawBoots(g, x, y, s, new Color(200, 80, 80)); break;
            case CLOUD_IN_BOTTLE: drawCloudBottle(g, x, y, s); break;
            case LUCKY_HORSESHOE: drawHorseshoe(g, x, y, s); break;
            case REGEN_BAND: drawBand(g, x, y, s); break;
            // ---- 材料 ----
            case GEL: drawGel(g, x, y, s); break;
            case ROTTEN_MEAT: drawRottenMeat(g, x, y, s); break;
            case HEART: drawHeart(g, x, y, s); break;
            case LIFE_CRYSTAL: drawLifeCrystal(g, x, y, s); break;
            case FISH: drawFish(g, x, y, s); break;
            case COPPER_BAR: drawBar(g, x, y, s, new Color(220, 140, 60)); break;
            case IRON_BAR: drawBar(g, x, y, s, new Color(190, 170, 180)); break;
            case GOLD_BAR: drawBar(g, x, y, s, new Color(255, 215, 0)); break;
            // ---- 工具/制作站 ----
            case FISHING_ROD: drawFishingRod(g, x, y, s); break;
            case WORKBENCH: drawWorkbench(g, x, y, s); break;
            case FURNACE: drawFurnace(g, x, y, s); break;
            case ANVIL: drawAnvil(g, x, y, s); break;
            case TORCH: drawTorch(g, x, y, s); break;
            case BOMB: drawBomb(g, x, y, s); break;
            case SUSPICIOUS_EYE: drawSuspiciousEye(g, x, y, s); break;
            case MECHANICAL_SKULL: drawMechanicalSkull(g, x, y, s); break;
            // ---- 方块类：带纹理的方块 ----
            default: drawBlock(g, x, y, s, item.color); break;
        }
    }

    // ================= 武器 =================
    private static void drawPickaxe(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 镐头（横向）
        g.setColor(c);
        g.fillRect(x + 2*u, y + 3*u, 12*u, 3*u);
        // 镐尖（两端尖）
        int[] lx = {x + 2*u, x + 0*u, x + 2*u};
        int[] ly = {y + 3*u, y + 4*u, y + 6*u};
        g.fillPolygon(lx, ly, 3);
        int[] rx = {x + 14*u, x + 16*u, x + 14*u};
        int[] ry = {y + 3*u, y + 4*u, y + 6*u};
        g.fillPolygon(rx, ry, 3);
        // 镐身高光
        g.setColor(lighten(c, 30));
        g.fillRect(x + 3*u, y + 3*u, 10*u, u);
        // 手柄
        g.setColor(new Color(120, 80, 40));
        g.fillRect(x + 7*u, y + 5*u, 2*u, 9*u);
        // 手柄末端
        g.setColor(new Color(100, 65, 30));
        g.fillOval(x + 6*u, y + 13*u, 4*u, 3*u);
    }

    private static void drawAxe(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 斧头（扇形）
        g.setColor(c);
        int[] ax = {x + 5*u, x + 12*u, x + 14*u, x + 13*u, x + 10*u, x + 5*u};
        int[] ay = {y + 3*u, y + 2*u, y + 5*u, y + 9*u, y + 10*u, y + 8*u};
        g.fillPolygon(ax, ay, 6);
        // 斧头高光
        g.setColor(lighten(c, 30));
        int[] hx = {x + 6*u, x + 11*u, x + 12*u, x + 9*u};
        int[] hy = {y + 4*u, y + 3*u, y + 5*u, y + 6*u};
        g.fillPolygon(hx, hy, 4);
        // 手柄
        g.setColor(new Color(120, 80, 40));
        g.fillRect(x + 6*u, y + 8*u, 2*u, 7*u);
        // 手柄末端
        g.setColor(new Color(100, 65, 30));
        g.fillOval(x + 5*u, y + 14*u, 4*u, 2*u);
    }

    private static void drawSword(Graphics2D g, int x, int y, int s, Color blade, Color hilt) {
        int u = s / 16;
        // 剑身（斜向）
        g.setColor(blade);
        int[] bx = {x + 4*u, x + 6*u, x + 13*u, x + 11*u};
        int[] by = {y + 12*u, y + 10*u, y + 3*u, y + 5*u};
        g.fillPolygon(bx, by, 4);
        // 剑身高光
        g.setColor(lighten(blade, 40));
        int[] hx = {x + 5*u, x + 6*u, x + 12*u, x + 11*u};
        int[] hy = {y + 11*u, y + 10*u, y + 4*u, y + 5*u};
        g.fillPolygon(hx, hy, 4);
        // 护手
        g.setColor(hilt);
        g.fillRect(x + 3*u, y + 11*u, 6*u, 2*u);
        // 剑柄
        g.fillRect(x + 2*u, y + 12*u, 3*u, 3*u);
        // 剑首
        g.setColor(lighten(hilt, 30));
        g.fillOval(x + 1*u, y + 13*u, 3*u, 3*u);
    }

    private static void drawBow(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        g.setColor(c);
        // 弓身（弧形）
        for (int i = 0; i < 12; i++) {
            double ang = -Math.PI/2 + (i / 11.0) * Math.PI;
            int px = x + 8*u + (int)(Math.cos(ang) * 6*u);
            int py = y + 8*u + (int)(Math.sin(ang) * 6*u);
            g.fillRect(px - u, py - u, 2*u, 2*u);
        }
        // 弓弦
        g.setColor(new Color(230, 220, 200));
        g.drawLine(x + 8*u, y + 2*u, x + 8*u, y + 14*u);
        // 箭
        g.setColor(new Color(150, 105, 58));
        g.fillRect(x + 7*u, y + 5*u, u, 7*u);
        g.setColor(new Color(200, 200, 210));
        int[] ax = {x + 6*u, x + 8*u, x + 10*u};
        int[] ay = {y + 6*u, y + 4*u, y + 6*u};
        g.fillPolygon(ax, ay, 3);
    }

    private static void drawArrow(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 箭杆
        g.setColor(new Color(150, 105, 58));
        g.fillRect(x + 4*u, y + 7*u, 8*u, 2*u);
        // 箭头
        g.setColor(new Color(200, 200, 210));
        int[] ax = {x + 12*u, x + 15*u, x + 12*u};
        int[] ay = {y + 5*u, y + 8*u, y + 11*u};
        g.fillPolygon(ax, ay, 3);
        // 箭羽
        g.setColor(new Color(220, 60, 60));
        g.fillRect(x + 2*u, y + 6*u, 2*u, 4*u);
    }

    // ================= 护甲 =================
    private static void drawHelmet(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        g.setColor(c);
        // 头盔主体（弧形顶部）
        g.fillRect(x + 3*u, y + 4*u, 10*u, 9*u);
        g.fillRect(x + 4*u, y + 3*u, 8*u, u);
        g.fillRect(x + 5*u, y + 2*u, 6*u, u);
        // 眼缝
        g.setColor(new Color(30, 30, 40));
        g.fillRect(x + 4*u, y + 8*u, 8*u, 2*u);
        // 高光
        g.setColor(lighten(c, 30));
        g.fillRect(x + 4*u, y + 4*u, 3*u, 3*u);
    }

    private static void drawChestplate(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        g.setColor(c);
        // 胸甲主体
        int[] px = {x + 3*u, x + 13*u, x + 12*u, x + 11*u, x + 5*u, x + 4*u};
        int[] py = {y + 3*u, y + 3*u, y + 13*u, y + 14*u, y + 14*u, y + 13*u};
        g.fillPolygon(px, py, 6);
        // 领口
        g.setColor(new Color(40, 40, 50));
        g.fillRect(x + 6*u, y + 3*u, 4*u, 2*u);
        // 高光
        g.setColor(lighten(c, 25));
        g.fillRect(x + 4*u, y + 5*u, 3*u, 5*u);
    }

    private static void drawLeggings(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        g.setColor(c);
        // 护腿（两条腿）
        g.fillRect(x + 3*u, y + 3*u, 4*u, 11*u);
        g.fillRect(x + 9*u, y + 3*u, 4*u, 11*u);
        // 腰带
        g.fillRect(x + 3*u, y + 3*u, 10*u, 2*u);
        // 高光
        g.setColor(lighten(c, 25));
        g.fillRect(x + 4*u, y + 6*u, 2*u, 4*u);
        g.fillRect(x + 10*u, y + 6*u, 2*u, 4*u);
    }

    // ================= 药水 =================
    private static void drawPotion(Graphics2D g, int x, int y, int s, Color liquid) {
        int u = s / 16;
        // 瓶身
        g.setColor(new Color(180, 220, 240, 200));
        int[] bx = {x + 5*u, x + 11*u, x + 12*u, x + 11*u, x + 5*u, x + 4*u};
        int[] by = {y + 6*u, y + 6*u, y + 8*u, y + 14*u, y + 14*u, y + 8*u};
        g.fillPolygon(bx, by, 6);
        // 液体
        g.setColor(liquid);
        g.fillRect(x + 5*u, y + 9*u, 6*u, 4*u);
        // 瓶颈
        g.setColor(new Color(160, 200, 220, 200));
        g.fillRect(x + 6*u, y + 3*u, 4*u, 4*u);
        // 软木塞
        g.setColor(new Color(150, 100, 50));
        g.fillRect(x + 6*u, y + 2*u, 4*u, 2*u);
        // 高光
        g.setColor(new Color(255, 255, 255, 120));
        g.fillRect(x + 5*u, y + 7*u, u, 4*u);
    }

    // ================= 饰品 =================
    private static void drawBoots(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        g.setColor(c);
        // 靴筒
        g.fillRect(x + 4*u, y + 3*u, 5*u, 7*u);
        // 靴底
        g.fillRect(x + 3*u, y + 10*u, 8*u, 3*u);
        // 鞋尖
        g.fillRect(x + 8*u, y + 9*u, 4*u, 3*u);
        // 翅膀（赫尔墨斯靴特征）
        g.setColor(new Color(255, 230, 150));
        g.fillOval(x + 1*u, y + 5*u, 4*u, 3*u);
        g.fillOval(x + 0*u, y + 7*u, 3*u, 2*u);
        // 高光
        g.setColor(lighten(c, 30));
        g.fillRect(x + 5*u, y + 4*u, 2*u, 4*u);
    }

    private static void drawCloudBottle(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 瓶子
        g.setColor(new Color(200, 230, 255, 200));
        g.fillRect(x + 5*u, y + 6*u, 6*u, 7*u);
        g.fillRect(x + 6*u, y + 4*u, 4*u, 3*u);
        // 云
        g.setColor(Color.WHITE);
        g.fillOval(x + 4*u, y + 8*u, 4*u, 3*u);
        g.fillOval(x + 7*u, y + 7*u, 4*u, 4*u);
        g.fillOval(x + 9*u, y + 9*u, 3*u, 3*u);
        // 软木塞
        g.setColor(new Color(150, 100, 50));
        g.fillRect(x + 6*u, y + 3*u, 4*u, 2*u);
    }

    private static void drawHorseshoe(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(220, 180, 80));
        // U 形马蹄铁
        g.fillRect(x + 3*u, y + 3*u, 3*u, 8*u);
        g.fillRect(x + 10*u, y + 3*u, 3*u, 8*u);
        g.fillRect(x + 3*u, y + 10*u, 10*u, 3*u);
        // 钉子
        g.setColor(new Color(180, 140, 50));
        g.fillOval(x + 4*u, y + 5*u, u, u);
        g.fillOval(x + 11*u, y + 5*u, u, u);
        g.fillOval(x + 6*u, y + 11*u, u, u);
        g.fillOval(x + 9*u, y + 11*u, u, u);
    }

    private static void drawBand(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 手环（环形）
        g.setColor(new Color(120, 220, 120));
        g.fillOval(x + 3*u, y + 5*u, 10*u, 7*u);
        g.setColor(new Color(40, 40, 50));
        g.fillOval(x + 5*u, y + 6*u, 6*u, 5*u);
        // 红心装饰
        g.setColor(new Color(255, 80, 80));
        g.fillOval(x + 7*u, y + 7*u, 2*u, 2*u);
    }

    // ================= 材料 =================
    private static void drawGel(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(120, 225, 180, 220));
        // 凝胶（椭圆+底部平）
        g.fillOval(x + 3*u, y + 4*u, 10*u, 9*u);
        g.fillRect(x + 3*u, y + 9*u, 10*u, 4*u);
        // 高光
        g.setColor(new Color(200, 255, 230, 180));
        g.fillOval(x + 5*u, y + 5*u, 3*u, 3*u);
    }

    private static void drawRottenMeat(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(150, 90, 70));
        // 不规则肉块
        int[] px = {x + 3*u, x + 8*u, x + 13*u, x + 12*u, x + 9*u, x + 4*u};
        int[] py = {y + 6*u, y + 3*u, y + 7*u, y + 12*u, y + 13*u, y + 10*u};
        g.fillPolygon(px, py, 6);
        // 骨头
        g.setColor(new Color(220, 210, 190));
        g.fillRect(x + 5*u, y + 8*u, 6*u, 2*u);
        g.fillOval(x + 4*u, y + 7*u, 3*u, 3*u);
        g.fillOval(x + 9*u, y + 7*u, 3*u, 3*u);
    }

    private static void drawHeart(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(255, 60, 80));
        // 心形（两个圆+三角）
        g.fillOval(x + 3*u, y + 4*u, 5*u, 5*u);
        g.fillOval(x + 8*u, y + 4*u, 5*u, 5*u);
        int[] px = {x + 3*u, x + 13*u, x + 8*u};
        int[] py = {y + 7*u, y + 7*u, y + 13*u};
        g.fillPolygon(px, py, 3);
        // 高光
        g.setColor(new Color(255, 150, 160));
        g.fillOval(x + 5*u, y + 5*u, 2*u, 2*u);
    }

    private static void drawLifeCrystal(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 水晶（菱形）
        g.setColor(new Color(255, 70, 110));
        int[] px = {x + 8*u, x + 13*u, x + 8*u, x + 3*u};
        int[] py = {y + 2*u, y + 8*u, y + 14*u, y + 8*u};
        g.fillPolygon(px, py, 4);
        // 内部高光
        g.setColor(new Color(255, 150, 180));
        int[] ix = {x + 8*u, x + 11*u, x + 8*u, x + 5*u};
        int[] iy = {y + 4*u, y + 8*u, y + 12*u, y + 8*u};
        g.fillPolygon(ix, iy, 4);
        // 中心
        g.setColor(new Color(255, 200, 220));
        g.fillOval(x + 7*u, y + 7*u, 2*u, 2*u);
    }

    private static void drawFish(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(120, 180, 220));
        // 鱼身（椭圆）
        g.fillOval(x + 2*u, y + 5*u, 9*u, 6*u);
        // 鱼尾
        int[] tx = {x + 10*u, x + 14*u, x + 14*u};
        int[] ty = {y + 8*u, y + 4*u, y + 12*u};
        g.fillPolygon(tx, ty, 3);
        // 鱼眼
        g.setColor(Color.WHITE);
        g.fillOval(x + 4*u, y + 6*u, 2*u, 2*u);
        g.setColor(Color.BLACK);
        g.fillOval(x + 5*u, y + 7*u, u, u);
        // 鱼鳞
        g.setColor(new Color(90, 150, 190));
        g.fillOval(x + 6*u, y + 7*u, u, u);
        g.fillOval(x + 8*u, y + 7*u, u, u);
    }

    private static void drawBar(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 金属锭（梯形+顶面）
        g.setColor(c);
        int[] px = {x + 3*u, x + 13*u, x + 12*u, x + 4*u};
        int[] py = {y + 5*u, y + 5*u, y + 12*u, y + 12*u};
        g.fillPolygon(px, py, 4);
        // 顶面高光
        g.setColor(lighten(c, 40));
        g.fillRect(x + 4*u, y + 5*u, 8*u, 2*u);
        // 底面阴影
        g.setColor(darken(c, 30));
        g.fillRect(x + 5*u, y + 10*u, 6*u, 2*u);
    }

    /** 矿石（石头背景+矿点）。 */
    private static void drawOre(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 石头背景
        g.setColor(new Color(90, 90, 100));
        g.fillRect(x + 2*u, y + 2*u, 12*u, 12*u);
        g.setColor(new Color(110, 110, 120));
        g.fillRect(x + 3*u, y + 3*u, 10*u, 10*u);
        // 矿点
        g.setColor(c);
        g.fillOval(x + 4*u, y + 5*u, 3*u, 3*u);
        g.fillOval(x + 9*u, y + 4*u, 3*u, 3*u);
        g.fillOval(x + 6*u, y + 9*u, 4*u, 3*u);
        g.setColor(lighten(c, 50));
        g.fillOval(x + 5*u, y + 6*u, u, u);
        g.fillOval(x + 10*u, y + 5*u, u, u);
    }

    /** 法杖。 */
    private static void drawStaff(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 杖身
        g.setColor(new Color(100, 70, 40));
        g.fillRect(x + 7*u, y + 4*u, 2*u, 10*u);
        // 杖头（宝石）
        g.setColor(c);
        g.fillOval(x + 5*u, y + 1*u, 6*u, 6*u);
        g.setColor(lighten(c, 50));
        g.fillOval(x + 6*u, y + 2*u, 3*u, 3*u);
        // 杖头装饰
        g.setColor(new Color(180, 150, 80));
        g.fillRect(x + 6*u, y + 6*u, 4*u, u);
    }

    /** 魔法飞刀。 */
    private static void drawMagicDagger(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 刀身（发光蓝色）
        g.setColor(new Color(180, 200, 255));
        int[] bx = {x + 8*u, x + 12*u, x + 8*u, x + 4*u};
        int[] by = {y + 2*u, y + 8*u, y + 14*u, y + 8*u};
        g.fillPolygon(bx, by, 4);
        // 高光
        g.setColor(new Color(220, 230, 255));
        g.fillRect(x + 7*u, y + 5*u, 2*u, 5*u);
        // 刀柄
        g.setColor(new Color(80, 60, 100));
        g.fillRect(x + 6*u, y + 12*u, 4*u, 3*u);
    }

    /** 魔力水晶（蓝色菱形）。 */
    private static void drawManaCrystal(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(80, 150, 255));
        int[] px = {x + 8*u, x + 13*u, x + 8*u, x + 3*u};
        int[] py = {y + 2*u, y + 8*u, y + 14*u, y + 8*u};
        g.fillPolygon(px, py, 4);
        g.setColor(new Color(140, 190, 255));
        int[] ix = {x + 8*u, x + 11*u, x + 8*u, x + 5*u};
        int[] iy = {y + 4*u, y + 8*u, y + 12*u, y + 8*u};
        g.fillPolygon(ix, iy, 4);
        g.setColor(new Color(200, 220, 255));
        g.fillOval(x + 7*u, y + 7*u, 2*u, 2*u);
    }

    /** 蠕虫诱饵（紫色腐肉团）。 */
    private static void drawWormFood(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(100, 40, 120));
        g.fillOval(x + 3*u, y + 4*u, 10*u, 8*u);
        g.setColor(new Color(140, 60, 160));
        g.fillOval(x + 4*u, y + 5*u, 8*u, 6*u);
        g.setColor(new Color(80, 20, 100));
        g.fillOval(x + 6*u, y + 6*u, 2*u, 2*u);
        g.fillOval(x + 9*u, y + 7*u, 2*u, 2*u);
    }

    /** 盾牌。 */
    private static void drawShield(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        g.setColor(c);
        int[] px = {x + 4*u, x + 12*u, x + 12*u, x + 8*u, x + 4*u};
        int[] py = {y + 3*u, y + 3*u, y + 9*u, y + 13*u, y + 9*u};
        g.fillPolygon(px, py, 5);
        g.setColor(lighten(c, 40));
        g.fillRect(x + 6*u, y + 5*u, 4*u, 5*u);
        g.setColor(new Color(200, 170, 60));
        g.fillOval(x + 7*u, y + 6*u, 2*u, 2*u);
    }

    /** 机械蠕虫（召唤物）。 */
    private static void drawMechanicalWorm(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 金属杆
        g.setColor(new Color(120, 120, 130));
        g.fillRect(x + 3*u, y + 7*u, 10*u, 3*u);
        // 机械关节
        g.setColor(new Color(80, 80, 90));
        g.fillOval(x + 2*u, y + 6*u, 4*u, 5*u);
        g.fillOval(x + 10*u, y + 6*u, 4*u, 5*u);
        // 红色核心
        g.setColor(new Color(200, 50, 50));
        g.fillOval(x + 6*u, y + 6*u, 4*u, 4*u);
        g.setColor(new Color(255, 100, 100));
        g.fillOval(x + 7*u, y + 7*u, 2*u, 2*u);
        // 金属高光
        g.setColor(new Color(180, 180, 190));
        g.fillRect(x + 4*u, y + 7*u, 8*u, u);
    }

    /** 海盗地图（羊皮纸+骷髅标记）。 */
    private static void drawPirateMap(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 羊皮纸
        g.setColor(new Color(220, 190, 130));
        g.fillRect(x + 2*u, y + 3*u, 12*u, 10*u);
        g.setColor(new Color(180, 150, 90));
        g.drawRect(x + 2*u, y + 3*u, 12*u, 10*u);
        // 卷边
        g.setColor(new Color(160, 130, 70));
        g.fillRect(x + 1*u, y + 3*u, u, 10*u);
        g.fillRect(x + 14*u, y + 3*u, u, 10*u);
        // 骷髅标记
        g.setColor(new Color(60, 40, 20));
        g.fillOval(x + 6*u, y + 5*u, 4*u, 4*u);
        g.setColor(new Color(220, 190, 130));
        g.fillOval(x + 7*u, y + 6*u, u, u);
        g.fillOval(x + 9*u, y + 6*u, u, u);
        // 交叉骨头
        g.setColor(new Color(60, 40, 20));
        g.drawLine(x + 5*u, y + 10*u, x + 11*u, y + 12*u);
        g.drawLine(x + 11*u, y + 10*u, x + 5*u, y + 12*u);
        // 虚线路径
        g.setColor(new Color(150, 100, 50));
        for (int i = 0; i < 4; i++) {
            g.fillRect(x + 3*u + i*3*u, y + 11*u, 2*u, u);
        }
    }

    /** 史莱姆坐骑（大蓝色史莱姆+鞍）。 */
    private static void drawSlimeMount(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 大史莱姆身体
        g.setColor(new Color(100, 180, 255));
        g.fillOval(x + 1*u, y + 4*u, 14*u, 11*u);
        g.setColor(new Color(150, 210, 255, 200));
        g.fillOval(x + 3*u, y + 5*u, 8*u, 5*u);
        // 眼睛
        g.setColor(Color.WHITE);
        g.fillOval(x + 4*u, y + 7*u, 3*u, 3*u);
        g.fillOval(x + 9*u, y + 7*u, 3*u, 3*u);
        g.setColor(Color.BLACK);
        g.fillOval(x + 5*u, y + 8*u, u, u);
        g.fillOval(x + 10*u, y + 8*u, u, u);
        // 鞍
        g.setColor(new Color(120, 70, 40));
        g.fillRect(x + 4*u, y + 3*u, 8*u, 3*u);
        g.setColor(new Color(180, 140, 80));
        g.fillRect(x + 5*u, y + 3*u, 6*u, u);
    }

    /** 史莱姆法杖（杖头有蓝色史莱姆）。 */
    private static void drawSlimeStaff(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 杖身
        g.setColor(new Color(100, 70, 40));
        g.fillRect(x + 7*u, y + 5*u, 2*u, 10*u);
        // 杖头（蓝色史莱姆）
        g.setColor(new Color(80, 150, 220));
        g.fillOval(x + 4*u, y + 1*u, 8*u, 7*u);
        g.setColor(new Color(140, 200, 255));
        g.fillOval(x + 5*u, y + 2*u, 5*u, 3*u);
        // 眼睛
        g.setColor(Color.WHITE);
        g.fillOval(x + 5*u, y + 4*u, 2*u, 2*u);
        g.fillOval(x + 9*u, y + 4*u, 2*u, 2*u);
        g.setColor(Color.BLACK);
        g.fillOval(x + 6*u, y + 5*u, u, u);
        g.fillOval(x + 10*u, y + 5*u, u, u);
        // 杖头装饰
        g.setColor(new Color(180, 150, 80));
        g.fillRect(x + 6*u, y + 7*u, 4*u, u);
    }

    /** 灵魂（发光球体）。 */
    private static void drawSoul(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 外发光
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 80));
        g.fillOval(x + 2*u, y + 2*u, 12*u, 12*u);
        // 主体
        g.setColor(c);
        g.fillOval(x + 4*u, y + 4*u, 8*u, 8*u);
        // 高光
        g.setColor(new Color(255, 255, 255, 200));
        g.fillOval(x + 5*u, y + 5*u, 3*u, 3*u);
        // 中心光点
        g.setColor(Color.WHITE);
        g.fillOval(x + 7*u, y + 7*u, 2*u, 2*u);
    }

    /** 哥布林战旗。 */
    private static void drawGoblinStandard(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 旗杆
        g.setColor(new Color(100, 70, 40));
        g.fillRect(x + 4*u, y + 1*u, 2*u, 14*u);
        // 旗帜（绿色，哥布林色）
        g.setColor(new Color(80, 140, 60));
        g.fillRect(x + 6*u, y + 2*u, 8*u, 7*u);
        // 旗帜缺口（燕尾形）
        g.setColor(new Color(10, 10, 20));
        int[] fx = {x + 12*u, x + 14*u, x + 14*u};
        int[] fy = {y + 5*u, y + 2*u, y + 9*u};
        g.fillPolygon(fx, fy, 3);
        // 旗帜上的骷髅图案
        g.setColor(new Color(220, 220, 210));
        g.fillOval(x + 8*u, y + 4*u, 3*u, 3*u);
        g.setColor(Color.BLACK);
        g.fillOval(x + 8*u, y + 5*u, u, u);
        g.fillOval(x + 10*u, y + 5*u, u, u);
    }

    /** 徽章（盾形+图案）。 */
    private static void drawEmblem(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 金色边框
        g.setColor(new Color(200, 170, 60));
        int[] px = {x + 3*u, x + 13*u, x + 13*u, x + 8*u, x + 3*u};
        int[] py = {y + 2*u, y + 2*u, y + 9*u, y + 14*u, y + 9*u};
        g.fillPolygon(px, py, 5);
        // 内部
        g.setColor(c);
        int[] ix = {x + 5*u, x + 11*u, x + 11*u, x + 8*u, x + 5*u};
        int[] iy = {y + 4*u, y + 4*u, y + 9*u, y + 12*u, y + 9*u};
        g.fillPolygon(ix, iy, 5);
        // 中心图案（剑/弓/杖的简化符号）
        g.setColor(Color.WHITE);
        g.fillRect(x + 7*u, y + 6*u, 2*u, 5*u);
        g.fillOval(x + 6*u, y + 5*u, 4*u, 2*u);
    }

    /** 暗影鳞片。 */
    private static void drawShadowScale(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(60, 30, 80));
        int[] px = {x + 8*u, x + 12*u, x + 10*u, x + 6*u, x + 4*u};
        int[] py = {y + 2*u, y + 7*u, y + 13*u, y + 13*u, y + 7*u};
        g.fillPolygon(px, py, 5);
        g.setColor(new Color(100, 50, 130));
        g.fillPolygon(new int[]{x + 8*u, x + 10*u, x + 8*u, x + 6*u},
                new int[]{y + 4*u, y + 8*u, y + 11*u, y + 8*u}, 4);
        g.setColor(new Color(140, 80, 170));
        g.fillOval(x + 7*u, y + 7*u, 2*u, 2*u);
    }

    /** 血肉娃娃（巫毒娃娃）。 */
    private static void drawVoodooDoll(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 身体
        g.setColor(new Color(180, 40, 60));
        g.fillOval(x + 5*u, y + 2*u, 6*u, 5*u); // 头
        g.fillRect(x + 4*u, y + 7*u, 8*u, 6*u); // 躯干
        // 手脚
        g.fillRect(x + 2*u, y + 8*u, 3*u, 2*u); // 左手
        g.fillRect(x + 11*u, y + 8*u, 3*u, 2*u); // 右手
        g.fillRect(x + 5*u, y + 13*u, 2*u, 3*u); // 左腿
        g.fillRect(x + 9*u, y + 13*u, 2*u, 3*u); // 右腿
        // 眼睛（X形）
        g.setColor(Color.BLACK);
        g.drawLine(x + 6*u, y + 4*u, x + 7*u, y + 5*u);
        g.drawLine(x + 7*u, y + 4*u, x + 6*u, y + 5*u);
        g.drawLine(x + 9*u, y + 4*u, x + 10*u, y + 5*u);
        g.drawLine(x + 10*u, y + 4*u, x + 9*u, y + 5*u);
        // 针
        g.setColor(new Color(100, 100, 110));
        g.fillRect(x + 7*u, y + 9*u, u, 4*u);
    }

    // ================= 工具/制作站 =================
    private static void drawFishingRod(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 钓竿（斜向）
        g.setColor(new Color(150, 110, 60));
        for (int i = 0; i < 10; i++) {
            g.fillRect(x + 2*u + i*u, y + 13*u - i*u, 2*u, 2*u);
        }
        // 鱼线
        g.setColor(new Color(200, 200, 210));
        g.drawLine(x + 12*u, y + 3*u, x + 14*u, y + 12*u);
        // 浮标
        g.setColor(new Color(255, 80, 80));
        g.fillOval(x + 13*u, y + 11*u, 2*u, 2*u);
    }

    private static void drawWorkbench(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 桌面
        g.setColor(new Color(150, 105, 58));
        g.fillRect(x + 2*u, y + 5*u, 12*u, 3*u);
        // 桌腿
        g.fillRect(x + 3*u, y + 8*u, 2*u, 6*u);
        g.fillRect(x + 11*u, y + 8*u, 2*u, 6*u);
        // 桌面纹理
        g.setColor(new Color(120, 80, 40));
        g.drawLine(x + 4*u, y + 6*u, x + 12*u, y + 6*u);
        g.drawLine(x + 4*u, y + 7*u, x + 12*u, y + 7*u);
        // 工具（锤子）
        g.setColor(new Color(100, 100, 110));
        g.fillRect(x + 6*u, y + 2*u, 4*u, 3*u);
        g.setColor(new Color(150, 105, 58));
        g.fillRect(x + 7*u, y + 4*u, 2*u, 2*u);
    }

    private static void drawFurnace(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 炉体
        g.setColor(new Color(110, 100, 90));
        g.fillRect(x + 2*u, y + 3*u, 12*u, 11*u);
        // 炉口（火光）
        g.setColor(new Color(60, 40, 30));
        g.fillRect(x + 5*u, y + 7*u, 6*u, 5*u);
        g.setColor(new Color(255, 140, 40));
        g.fillRect(x + 6*u, y + 8*u, 4*u, 3*u);
        g.setColor(new Color(255, 220, 80));
        g.fillRect(x + 7*u, y + 9*u, 2*u, u);
        // 顶部
        g.setColor(new Color(90, 80, 70));
        g.fillRect(x + 2*u, y + 3*u, 12*u, 2*u);
        // 烟囱
        g.fillRect(x + 10*u, y + 1*u, 3*u, 2*u);
    }

    private static void drawAnvil(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        g.setColor(new Color(90, 90, 100));
        // 铁砧顶部（宽）
        g.fillRect(x + 2*u, y + 4*u, 12*u, 3*u);
        // 铁砧尖（右）
        int[] px = {x + 10*u, x + 14*u, x + 10*u};
        int[] py = {y + 4*u, y + 5*u, y + 7*u};
        g.fillPolygon(px, py, 3);
        // 腰部
        g.fillRect(x + 5*u, y + 7*u, 6*u, 2*u);
        // 底座
        g.fillRect(x + 3*u, y + 9*u, 10*u, 4*u);
        // 高光
        g.setColor(new Color(140, 140, 150));
        g.fillRect(x + 3*u, y + 4*u, 8*u, u);
    }

    private static void drawTorch(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 木棍
        g.setColor(new Color(120, 80, 40));
        g.fillRect(x + 7*u, y + 7*u, 2*u, 7*u);
        // 火焰
        g.setColor(new Color(255, 140, 40));
        g.fillOval(x + 5*u, y + 3*u, 6*u, 5*u);
        g.setColor(new Color(255, 220, 80));
        g.fillOval(x + 6*u, y + 4*u, 4*u, 3*u);
        g.setColor(new Color(255, 255, 200));
        g.fillOval(x + 7*u, y + 5*u, 2*u, u);
    }

    private static void drawBomb(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 炸弹主体
        g.setColor(new Color(60, 60, 70));
        g.fillOval(x + 3*u, y + 5*u, 10*u, 9*u);
        // 高光
        g.setColor(new Color(100, 100, 110));
        g.fillOval(x + 5*u, y + 6*u, 3*u, 3*u);
        // 引线
        g.setColor(new Color(150, 100, 50));
        g.drawLine(x + 8*u, y + 5*u, x + 10*u, y + 2*u);
        // 火花
        g.setColor(new Color(255, 200, 60));
        g.fillOval(x + 9*u, y + 1*u, 3*u, 3*u);
    }

    private static void drawSuspiciousEye(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 眼球
        g.setColor(new Color(190, 50, 60));
        g.fillOval(x + 2*u, y + 4*u, 12*u, 8*u);
        // 眼白
        g.setColor(new Color(240, 230, 220));
        g.fillOval(x + 4*u, y + 5*u, 8*u, 6*u);
        // 瞳孔
        g.setColor(new Color(40, 10, 20));
        g.fillOval(x + 6*u, y + 6*u, 4*u, 4*u);
        // 血管
        g.setColor(new Color(180, 30, 40));
        g.drawLine(x + 3*u, y + 6*u, x + 5*u, y + 7*u);
        g.drawLine(x + 3*u, y + 9*u, x + 5*u, y + 8*u);
    }

    private static void drawMechanicalSkull(Graphics2D g, int x, int y, int s) {
        int u = s / 16;
        // 头骨
        g.setColor(new Color(180, 180, 190));
        g.fillOval(x + 3*u, y + 3*u, 10*u, 8*u);
        // 眼窝（红眼）
        g.setColor(new Color(40, 40, 50));
        g.fillOval(x + 5*u, y + 5*u, 2*u, 2*u);
        g.fillOval(x + 9*u, y + 5*u, 2*u, 2*u);
        g.setColor(new Color(255, 50, 50));
        g.fillOval(x + 5*u, y + 5*u, u, u);
        g.fillOval(x + 9*u, y + 5*u, u, u);
        // 牙齿
        g.setColor(new Color(220, 220, 230));
        for (int i = 0; i < 4; i++) {
            g.fillRect(x + 5*u + i*2*u, y + 10*u, u, 3*u);
        }
        // 机械部件
        g.setColor(new Color(120, 120, 130));
        g.fillRect(x + 7*u, y + 2*u, 2*u, 2*u);
    }

    // ================= 方块（带纹理） =================
    private static void drawBlock(Graphics2D g, int x, int y, int s, Color c) {
        int u = s / 16;
        // 主体
        g.setColor(c);
        g.fillRect(x, y, s, s);
        // 顶部高光
        g.setColor(lighten(c, 25));
        g.fillRect(x, y, s, 2*u);
        g.fillRect(x, y, 2*u, s);
        // 底部阴影
        g.setColor(darken(c, 25));
        g.fillRect(x, y + s - 2*u, s, 2*u);
        g.fillRect(x + s - 2*u, y, 2*u, s);
        // 纹理点
        g.setColor(darken(c, 15));
        g.fillOval(x + 4*u, y + 5*u, u, u);
        g.fillOval(x + 9*u, y + 8*u, u, u);
        g.fillOval(x + 6*u, y + 11*u, u, u);
    }

    // ================= 工具方法 =================
    private static Color lighten(Color c, int amt) {
        return new Color(
                Math.min(255, c.getRed() + amt),
                Math.min(255, c.getGreen() + amt),
                Math.min(255, c.getBlue() + amt));
    }

    private static Color darken(Color c, int amt) {
        return new Color(
                Math.max(0, c.getRed() - amt),
                Math.max(0, c.getGreen() - amt),
                Math.max(0, c.getBlue() - amt));
    }
}
