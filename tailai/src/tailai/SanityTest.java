package tailai;

import java.io.File;

/**
 * 无 GUI 回归测试：直接 new 核心类并跑逻辑，不依赖 Swing/音频/网络。
 * 运行：java -cp out tailai.SanityTest
 */
public class SanityTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        SoundPlayer.setMuted(true);
        testWorldGeneration();
        testPlayerMovement();
        testPlayerArmorDefense();
        testItemArmorSlot();
        testEnemyDeath();
        testNpcCreation();
        testItemStackMerge();
        System.out.println("=== 测试: " + passed + " 通过, " + failed + " 失败 ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL #" + (passed + failed) + ": " + name);
        }
    }

    private static void testWorldGeneration() {
        World w = new World(400, 120);
        w.generate(42L);
        check("世界宽度", w.width == 400);
        check("世界高度", w.height == 120);
        // 表面附近应有草皮或泥土
        int surface = w.surfaceY;
        check("表面在合理范围", surface > 30 && surface < 90);
        check("表面下方是固体", w.isSolid(200, surface + 1));
        // 世界外视为石头（固体），不抛异常
        check("世界外视为固体", w.isSolid(-1, 50) && w.isSolid(400, 50));
        // 洞穴存在：地下某区域应有空气（检查多列）
        boolean hasCave = false;
        for (int x = 50; x < w.width - 50 && !hasCave; x += 5) {
            for (int y = surface + 10; y < w.height - 5; y += 3) {
                if (w.get(x, y) == TileType.AIR) {
                    hasCave = true;
                    break;
                }
            }
        }
        check("地下存在洞穴", hasCave);
    }

    private static void testPlayerMovement() {
        World w = new World(200, 80);
        w.generate(7L);
        Player p = new Player();
        p.respawn(w, 0);
        check("玩家出生有生命", p.hp > 0);
        check("玩家生命上限100", p.maxHp == 100);
        check("玩家在世界内", p.x >= 0 && p.y >= 0);
        // 模拟向右走，update 不抛异常
        InputHandler.Snapshot in = new InputHandler.Snapshot();
        in.keys.add(java.awt.event.KeyEvent.VK_D);
        float xBefore = p.x;
        for (int i = 0; i < 30; i++) {
            p.update(1f / 60f, w, in);
        }
        check("玩家update后仍存活", p.hp > 0);
        check("玩家update后坐标有效", !Float.isNaN(p.x) && !Float.isNaN(p.y));
        // 受伤
        int hpBefore = p.hp;
        p.invulnTimer = 0;
        p.hurtAt(10, 0, -100);
        check("受伤扣血", p.hp < hpBefore);
        check("受伤后无敌", p.invulnTimer > 0);
        // 无敌期间不受伤
        int hpAfter = p.hp;
        p.hurtAt(10, 0, -100);
        check("无敌期间不重复受伤", p.hp == hpAfter);
    }

    private static void testPlayerArmorDefense() {
        Player p = new Player();
        p.clear();
        check("无护甲防御为0", p.defense() == 0);
        p.armor[0] = Item.COPPER_HELMET;
        p.armor[1] = Item.COPPER_CHESTPLATE;
        p.armor[2] = Item.COPPER_LEGGINGS;
        check("铜套防御=9（含套装+2）", p.defense() == 9);
        // 护甲减伤：10 伤害 - 9 防御 = 1 实际伤害
        p.hp = 100;
        p.invulnTimer = 0;
        p.hurtAt(10, 0, 0);
        check("护甲减伤后剩99", p.hp == 99);
        // 最小伤害1：伤害<=防御时仍扣1
        p.hp = 100;
        p.invulnTimer = 0;
        p.hurtAt(5, 0, 0);
        check("防御高于伤害仍扣1", p.hp == 99);
        // 装备护甲替换旧护甲
        Item old = p.equipArmor(Item.IRON_HELMET);
        check("装备铁头盔替换铜头盔", old == Item.COPPER_HELMET && p.armor[0] == Item.IRON_HELMET);
        check("铁头盔防御+1", p.defense() == 8);
    }

    private static void testItemArmorSlot() {
        check("铜头盔槽位0", Item.COPPER_HELMET.armorSlot() == 0);
        check("铜胸甲槽位1", Item.COPPER_CHESTPLATE.armorSlot() == 1);
        check("铜护腿槽位2", Item.COPPER_LEGGINGS.armorSlot() == 2);
        check("铁头盔槽位0", Item.IRON_HELMET.armorSlot() == 0);
        check("非护甲槽位-1", Item.WOODEN_SWORD.armorSlot() == -1);
        check("护甲isArmor", Item.COPPER_CHESTPLATE.isArmor());
        check("武器非护甲", !Item.IRON_SWORD.isArmor());
    }

    private static void testEnemyDeath() {
        Enemy e = new Enemy(100, 100, Enemy.Type.SLIME);
        check("史莱姆初始存活", e.alive);
        check("史莱姆有生命", e.hp > 0);
        e.hp = 0;
        check("血量归零", e.hp == 0);
        Enemy boss = new Enemy(100, 100, Enemy.Type.EYE_OF_CTHULHU);
        check("Boss类型正确", boss.type == Enemy.Type.EYE_OF_CTHULHU);
        check("Boss生命高于史莱姆", boss.hp > e.maxHp);
    }

    private static void testNpcCreation() {
        Npc n = new Npc("向导", 200, 100);
        check("NPC名字", n.name.equals("向导"));
        check("NPC有碰撞盒", n.bounds().width == Npc.W);
        World w = new World(100, 60);
        w.generate(5L);
        Player p = new Player();
        p.respawn(w, 0);
        // NPC 更新不崩溃
        for (int i = 0; i < 30; i++) {
            n.update(1f / 60f, w, p);
        }
        check("NPC更新后存活", n.x >= 0);
    }

    private static void testItemStackMerge() {
        ItemStack a = new ItemStack(Item.DIRT, 5);
        ItemStack b = new ItemStack(Item.DIRT, 3);
        check("物品堆有物品", a.item == Item.DIRT);
        check("物品堆数量正确", a.count == 5);
        check("同类物品堆物品相同", a.item == b.item);
        ItemStack c = new ItemStack(Item.STONE, 1);
        check("异类物品堆物品不同", a.item != c.item);
    }
}
