package tailai;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家：物理（重力/跳跃/分轴碰撞）、生命、热键栏、武器装备、近战攻击、挖掘状态。
 * 手感参数参照原版泰拉瑞亚：移动更沉稳、跳跃高度有限、空中控制弱。
 */
public class Player {

    public static final int W = 20;
    public static final int H = 38;
    public static final int SLOTS = 10;
    public static final int INV_SLOTS = 20; // 主背包格数
    public static final int MAX_HP_LIMIT = 400; // 生命水晶可提升的上限

    // ---- 手感参数（原版向） ----
    public static final float MOVE_ACCEL = 1200f;      // 地面加速度 px/s^2
    public static final float AIR_ACCEL = 850f;        // 空中加速度（更灵活，接近原版）
    public static final float MAX_SPEED = 155f;        // 最大水平速度 px/s（约 9.7 格/s）
    public static final float GROUND_FRICTION = 7.5f;  // 地面摩擦系数
    public static final float AIR_FRICTION = 1.5f;     // 空中摩擦（更低，空中更易保持速度）
    public static final float GRAVITY = 820f;
    public static final float JUMP_VELOCITY = -360f;   // 跳跃初速 → 高度约 4.8 格
    public static final float MAX_FALL = 600f;
    public static final float CUT_JUMP_THRESHOLD = -60f; // 松开跳跃低于该速度时提前终止上升
    // ---- 手感优化：土狼时间 + 跳跃缓冲 ----
    public static final float COYOTE_TIME = 0.10f;       // 离开平台后仍可跳跃的时间窗口
    public static final float JUMP_BUFFER = 0.12f;       // 落地前按跳，落地后自动跳的缓冲
    public float coyoteTimer;   // 土狼时间计时器
    public float jumpBufferTimer; // 跳跃缓冲计时器

    public float x, y;
    public float vx, vy;
    public boolean onGround;
    public int facing = 1; // 1 右，-1 左

    public int hp;
    public int maxHp = 100;
    public int coins = 0;          // 金币数量（经济系统）
    public int mana = 100;         // 当前魔力
    public int maxMana = 100;      // 最大魔力
    public float manaRegenTimer;   // 魔力回复计时
    public static final int MAX_MANA_LIMIT = 400;
    public float invulnTimer;      // 受伤无敌帧
    public float attackCooldown;   // 挥剑冷却
    public float swingTimer;       // 挥剑动画剩余时间
    public float swingDuration;    // 挥剑动画总时长（用于计算进度）

    public int mineX = -1, mineY = -1; // 正在挖掘的格子，-1 表示未在挖
    public float mineProgress;         // 0..1

    // ---- 钓鱼状态 ----
    public int fishingState = 0;   // 0 未钓 1 等待咬钩 2 鱼咬钩了
    public float fishTimer;        // 等待/咬钩计时
    public float bobberX, bobberY; // 浮标世界坐标
    public float bobberDip;        // 浮标下沉量（咬钩动画）

    public float spawnX, spawnY;

    public final ItemStack[] hotbar = new ItemStack[SLOTS];
    public final ItemStack[] inventory = new ItemStack[INV_SLOTS];
    public int selected = 0;
    /** 当前装备的武器。 */
    public Item weapon = Item.WOODEN_SWORD;
    /** 当前装备武器的修饰语（重铸获得）。 */
    public ItemStack.Modifier weaponModifier = null;
    /** 护甲栏：0=头盔，1=胸甲，2=护腿。 */
    public final Item[] armor = new Item[3];
    /** 饰品栏：最多 4 个配饰。 */
    public final Item[] accessories = new Item[4];
    /** 二段跳是否可用（云朵瓶），落地重置。 */
    private boolean canDoubleJump = true;
    /** 再生手环回血计时。 */
    private float regenTimer = 0;
    /** 药水 sickness 冷却（秒），期间不能喝药。 */
    public float potionCooldown = 0;
    /** 荆棘效果剩余时间（秒）。 */
    public float thornsTimer = 0;
    /** 铁皮药水：+8防御。 */
    public float ironskinTimer = 0;
    /** 敏捷药水：+25%速度。 */
    public float swiftnessTimer = 0;
    /** 怒气药水：+15%伤害。 */
    public float rageTimer = 0;
    /** 夜视药水：夜间视野增强。 */
    public float nightVisionTimer = 0;
    public static final float POTION_DURATION = 300f;
    // ---- 坐骑 ----
    public Item mount = null;
    public float mountBounceTimer;

    public Player(float spawnX, float spawnY) {
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.x = spawnX;
        this.y = spawnY;
        this.hp = maxHp;
        // 初始物资，方便直接开始建造
        hotbar[0] = new ItemStack(Item.COPPER_PICKAXE, 1);
        hotbar[1] = new ItemStack(Item.COPPER_AXE, 1);
        hotbar[2] = new ItemStack(Item.WOODEN_SWORD, 1);
        hotbar[3] = new ItemStack(Item.TORCH, 20);
        hotbar[4] = new ItemStack(Item.WORKBENCH, 1);
        hotbar[5] = new ItemStack(Item.DIRT, 30);
        hotbar[6] = new ItemStack(Item.WOOD, 10);
    }

    public Player() {
        this(0, 0);
    }

    /** 重置为初始状态（新开世界 / 新联机会话用）。 */
    public void clear() {
        for (int i = 0; i < SLOTS; i++) {
            hotbar[i] = null;
        }
        for (int i = 0; i < INV_SLOTS; i++) {
            inventory[i] = null;
        }
        hotbar[0] = new ItemStack(Item.COPPER_PICKAXE, 1);
        hotbar[1] = new ItemStack(Item.COPPER_AXE, 1);
        hotbar[2] = new ItemStack(Item.WOODEN_SWORD, 1);
        hotbar[3] = new ItemStack(Item.TORCH, 20);
        hotbar[4] = new ItemStack(Item.WORKBENCH, 1);
        hotbar[5] = new ItemStack(Item.DIRT, 30);
        hotbar[6] = new ItemStack(Item.WOOD, 10);
        selected = 0;
        weapon = Item.WOODEN_SWORD;
        armor[0] = null;
        armor[1] = null;
        armor[2] = null;
        for (int i = 0; i < accessories.length; i++) {
            accessories[i] = null;
        }
        canDoubleJump = true;
        regenTimer = 0;
        potionCooldown = 0;
        thornsTimer = 0;
        maxHp = 100;
        invulnTimer = 0;
        attackCooldown = 0;
        swingTimer = 0;
        mineX = -1;
        mineProgress = 0;
    }

    /** 统计热键栏与背包中某物品总数。 */
    public int countOf(Item it) {
        int sum = 0;
        for (ItemStack st : hotbar) {
            if (st != null && st.item == it) {
                sum += st.count;
            }
        }
        for (ItemStack st : inventory) {
            if (st != null && st.item == it) {
                sum += st.count;
            }
        }
        return sum;
    }

    /** 装备武器。 */
    public void equipWeapon(Item it) {
        if (it != null && it.isWeapon()) {
            weapon = it;
            weaponModifier = null;
        }
    }

    /** 装备武器（带修饰语）。 */
    public void equipWeapon(ItemStack stack) {
        if (stack != null && stack.item != null && stack.item.isWeapon()) {
            weapon = stack.item;
            weaponModifier = stack.modifier;
        }
    }

    /** 当前总防御值（头盔+胸甲+护腿+饰品）。 */
    public int defense() {
        int d = 0;
        for (Item a : armor) {
            if (a != null) {
                d += a.defense;
            }
        }
        if (hasAcc(Item.AccEffect.DEFENSE)) {
            d += 2;
        }
        // 套装防御加成
        int set = armorSet();
        if (set == 1) d += 2; // 铜套 +2
        if (set == 2) d += 3; // 铁套 +3
        if (set == 3) d += 4; // 金套 +4
        if (set == 4) d += 6; // 狱石套 +6
        if (ironskinTimer > 0) d += 8; // 铁皮药水 +8
        return d;
    }

    /**
     * 当前装备的套装：0=无，1=铜套，2=铁套，3=金套，4=狱石套。
     */
    public int armorSet() {
        if (armor[0] == Item.COPPER_HELMET && armor[1] == Item.COPPER_CHESTPLATE
                && armor[2] == Item.COPPER_LEGGINGS) {
            return 1;
        }
        if (armor[0] == Item.IRON_HELMET && armor[1] == Item.IRON_CHESTPLATE
                && armor[2] == Item.IRON_LEGGINGS) {
            return 2;
        }
        if (armor[0] == Item.GOLD_HELMET && armor[1] == Item.GOLD_CHESTPLATE
                && armor[2] == Item.GOLD_LEGGINGS) {
            return 3;
        }
        if (armor[0] == Item.HELLSTONE_HELMET && armor[1] == Item.HELLSTONE_CHESTPLATE
                && armor[2] == Item.HELLSTONE_LEGGINGS) {
            return 4;
        }
        return 0;
    }

    /** 套装名称，用于 HUD 显示。 */
    public String armorSetName() {
        switch (armorSet()) {
            case 1: return "铜套装";
            case 2: return "铁套装";
            case 3: return "金套装";
            case 4: return "狱石套装";
            default: return "";
        }
    }

    /** 是否装备了指定效果的饰品。 */
    public boolean hasAcc(Item.AccEffect eff) {
        for (Item a : accessories) {
            if (a != null && a.accEffect == eff) {
                return true;
            }
        }
        return false;
    }

    /** 速度倍率：赫尔墨斯靴 1.35x，铁套 1.10x，金套 1.15x，狱石套 1.20x，可叠加。 */
    public float speedMul() {
        float m = 1.0f;
        if (hasAcc(Item.AccEffect.SPEED)) m *= 1.35f;
        if (armorSet() == 2) m *= 1.10f; // 铁套 +10%
        if (armorSet() == 3) m *= 1.15f; // 金套 +15%
        if (armorSet() == 4) m *= 1.20f; // 狱石套 +20%
        if (swiftnessTimer > 0) m *= 1.25f; // 敏捷药水 +25%
        if (mount != null) m *= 1.5f; // 坐骑 +50%速度
        return m;
    }

    /** 消耗魔力，魔力不足返回 false。 */
    public boolean consumeMana(int amount) {
        if (mana < amount) return false;
        mana -= amount;
        return true;
    }

    /**
     * 伤害倍率：怒气药水+15%，对应徽章+15%。
     * type: 0=近战, 1=远程, 2=魔法。
     */
    public float damageMul(int type) {
        float m = 1.0f;
        if (rageTimer > 0) m *= 1.15f;
        if (type == 0 && hasItemEquipped(Item.WARRIOR_EMBLEM)) m *= 1.15f;
        if (type == 1 && hasItemEquipped(Item.RANGER_EMBLEM)) m *= 1.15f;
        if (type == 2 && hasItemEquipped(Item.SORCERER_EMBLEM)) m *= 1.15f;
        return m;
    }

    /** 检查饰品栏中是否装备了指定物品。 */
    public boolean hasItemEquipped(Item item) {
        for (Item a : accessories) {
            if (a == item) return true;
        }
        return false;
    }

    /** 装备饰品到空槽；返回被替换的旧饰品（可能 null）。 */
    public Item equipAccessory(Item it) {
        if (it == null || !it.isAccessory()) {
            return null;
        }
        // 先找空槽
        for (int i = 0; i < accessories.length; i++) {
            if (accessories[i] == null) {
                accessories[i] = it;
                return null;
            }
        }
        // 满了替换第一个
        Item old = accessories[0];
        accessories[0] = it;
        return old;
    }

    /**
     * 装备护甲到对应槽位；旧护甲返回背包（若背包满则掉落到调用方处理，这里返回旧护甲）。
     * 返回被替换下的旧护甲（可能为 null）。
     */
    public Item equipArmor(Item it) {
        if (it == null || !it.isArmor()) {
            return null;
        }
        int slot = it.armorSlot();
        Item old = armor[slot];
        armor[slot] = it;
        return old;
    }

    /** 在给定世界重生；slot 用于多人时错开出生点。 */
    public void respawn(World world, int slot) {
        float spawnXT = world.width / 2f + slot * 2.5f;
        int gx = Math.max(1, Math.min(world.width - 2, (int) spawnXT));
        int gy = world.surfaceY;
        while (gy < world.height - 1 && world.get(gx, gy) == TileType.AIR) {
            gy++;
        }
        spawnX = gx * World.TILE;
        spawnY = gy * World.TILE - H;
        respawn();
    }

    public Rectangle bounds() {
        return new Rectangle((int) x, (int) y, W, H);
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public void respawn() {
        hp = maxHp;
        x = spawnX;
        y = spawnY;
        vx = vy = 0;
        invulnTimer = 1.5f;
        mineX = -1;
        mineProgress = 0;
    }

    /** 近战攻击范围（px），随武器提升。 */
    public float attackRange() {
        switch (weapon) {
            case COPPER_SWORD: return 7.0f * World.TILE;
            case IRON_SWORD: return 7.5f * World.TILE;
            default: return 6.5f * World.TILE;
        }
    }

    /** 被敌人攻击；返回是否实际受伤（有无敌帧时返回 false）。 */
    public boolean hurt(int dmg, Enemy src) {
        if (invulnTimer > 0 || hp <= 0) {
            return false;
        }
        float dir = (x + W / 2f < src.x + src.w / 2f) ? -1 : 1;
        return hurtAt(dmg, dir * 260, -200);
    }

    /** 受到伤害（含联机主机下发的 DAMAGE）；返回是否实际受伤。护甲减伤：实际伤害=max(1,伤害-防御)。 */
    public boolean hurtAt(int dmg, float kx, float ky) {
        if (invulnTimer > 0 || hp <= 0) {
            return false;
        }
        int real = Math.max(1, dmg - defense());
        hp = Math.max(0, hp - real);
        invulnTimer = 0.9f;
        vx = kx;
        vy = ky;
        SoundPlayer.play("hurt");
        return true;
    }

    /** 攻击目标敌人（挥剑），伤害与击退由当前武器决定。 */
    public void attack(Enemy e) {
        // 注意：swingTimer 和 attackCooldown 已在 GamePanel.swingAttack 中根据武器速度设置
        float playerCx = x + W / 2f;
        float enemyCx = e.x + e.w / 2f;
        facing = enemyCx >= playerCx ? 1 : -1;
        float dir = enemyCx >= playerCx ? 1 : -1;
        int dmg = weapon != null ? weapon.damage : 1;
        // 击退力度：根据武器伤害调整，高伤害武器击退更强
        float kb = 200 + dmg * 8f;
        e.hurt(dmg, dir * kb, -150 - dmg * 2f);
    }

    /** 把物品加入热键栏或背包（先叠到已有堆叠，再找空位）；背包满返回 false。 */
    public boolean addItem(Item it, int n) {
        for (ItemStack st : hotbar) {
            if (st != null && st.item == it) {
                st.count += n;
                return true;
            }
        }
        for (ItemStack st : inventory) {
            if (st != null && st.item == it) {
                st.count += n;
                return true;
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (hotbar[i] == null) {
                hotbar[i] = new ItemStack(it, n);
                return true;
            }
        }
        for (int i = 0; i < INV_SLOTS; i++) {
            if (inventory[i] == null) {
                inventory[i] = new ItemStack(it, n);
                return true;
            }
        }
        return false;
    }

    /** 从热键栏与背包扣除材料；不足返回 false。 */
    public boolean consume(Item it, int n) {
        for (int i = 0; i < SLOTS; i++) {
            if (hotbar[i] != null && hotbar[i].item == it) {
                int take = Math.min(n, hotbar[i].count);
                hotbar[i].count -= take;
                n -= take;
                if (hotbar[i].count <= 0) {
                    hotbar[i] = null;
                }
                if (n <= 0) {
                    return true;
                }
            }
        }
        for (int i = 0; i < INV_SLOTS; i++) {
            if (inventory[i] != null && inventory[i].item == it) {
                int take = Math.min(n, inventory[i].count);
                inventory[i].count -= take;
                n -= take;
                if (inventory[i].count <= 0) {
                    inventory[i] = null;
                }
                if (n <= 0) {
                    return true;
                }
            }
        }
        return n <= 0;
    }

    /** 检查热键栏与背包中某材料是否足够。 */
    public boolean hasEnough(Item it, int n) {
        return countOf(it) >= n;
    }

    /** 一键整理：合并同类堆叠，按类型排序，热键栏优先填充。 */
    public void sortInventory() {
        Map<Item, Integer> agg = new LinkedHashMap<>();
        for (ItemStack s : hotbar) {
            if (s != null) {
                agg.merge(s.item, s.count, Integer::sum);
            }
        }
        for (ItemStack s : inventory) {
            if (s != null) {
                agg.merge(s.item, s.count, Integer::sum);
            }
        }
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(agg.entrySet());
        // 按类型排序：武器 > 工具 > 饰品 > 护甲 > 药水 > 方块 > 材料
        sorted.sort((a, b) -> {
            int ta = sortPriority(a.getKey());
            int tb = sortPriority(b.getKey());
            if (ta != tb) return ta - tb;
            return a.getKey().id - b.getKey().id;
        });
        for (int i = 0; i < SLOTS; i++) {
            hotbar[i] = null;
        }
        for (int i = 0; i < INV_SLOTS; i++) {
            inventory[i] = null;
        }
        int hi = 0, ii = 0;
        for (Map.Entry<Item, Integer> e : sorted) {
            if (hi < SLOTS) {
                hotbar[hi++] = new ItemStack(e.getKey(), e.getValue());
            } else if (ii < INV_SLOTS) {
                inventory[ii++] = new ItemStack(e.getKey(), e.getValue());
            }
        }
        // 修正选中格
        if (selected >= SLOTS || hotbar[selected] == null) {
            selected = 0;
            for (int i = 0; i < SLOTS; i++) {
                if (hotbar[i] != null) { selected = i; break; }
            }
        }
    }

    private int sortPriority(Item item) {
        if (item.isWeapon()) return 0;
        if (item.toolType != Item.ToolType.NONE) return 1;
        if (item.accEffect != Item.AccEffect.NONE) return 2;
        if (item.defense > 0) return 3;
        if (item.name.contains("药水") || item == Item.HEART) return 4;
        if (item.placeable) return 5;
        return 6;
    }

    public void update(float dt, World world, InputHandler.Snapshot in) {
        // ---- 水平移动（加速度 + 摩擦，地面/空中区分） ----
        int dir = 0;
        if (in.key(java.awt.event.KeyEvent.VK_A) || in.key(java.awt.event.KeyEvent.VK_LEFT)) {
            dir -= 1;
        }
        if (in.key(java.awt.event.KeyEvent.VK_D) || in.key(java.awt.event.KeyEvent.VK_RIGHT)) {
            dir += 1;
        }
        float accel = (onGround ? MOVE_ACCEL : AIR_ACCEL) * speedMul();
        if (dir != 0) {
            vx += dir * accel * dt;
            if (onGround) {
                facing = dir;
            }
        }
        float friction = onGround ? GROUND_FRICTION : AIR_FRICTION;
        float f = Math.max(0, 1 - friction * dt);
        vx *= f;
        if (Math.abs(vx) < 1f) {
            vx = 0;
        }
        vx = clamp(vx, -MAX_SPEED * speedMul(), MAX_SPEED * speedMul());

        // ---- 跳跃（含土狼时间 + 跳跃缓冲 + 云朵瓶二段跳） ----
        boolean jumpHeld = in.key(java.awt.event.KeyEvent.VK_SPACE)
                || in.key(java.awt.event.KeyEvent.VK_W)
                || in.key(java.awt.event.KeyEvent.VK_UP);
        boolean jumpPressed = in.pressed(java.awt.event.KeyEvent.VK_SPACE)
                || in.pressed(java.awt.event.KeyEvent.VK_W)
                || in.pressed(java.awt.event.KeyEvent.VK_UP);

        // 跳跃缓冲：按下跳跃键后记录缓冲时间
        if (jumpPressed) {
            jumpBufferTimer = JUMP_BUFFER;
        }
        if (jumpBufferTimer > 0) {
            jumpBufferTimer -= dt;
        }

        // 土狼时间：在地面上时重置，离开地面后递减
        if (onGround) {
            coyoteTimer = COYOTE_TIME;
        } else if (coyoteTimer > 0) {
            coyoteTimer -= dt;
        }

        // 地面跳跃（含土狼时间和跳跃缓冲）
        boolean canGroundJump = (onGround || coyoteTimer > 0) && jumpBufferTimer > 0;
        if (canGroundJump) {
            vy = JUMP_VELOCITY * (mount != null ? 1.25f : 1f);
            onGround = false;
            coyoteTimer = 0;
            jumpBufferTimer = 0;
            canDoubleJump = true;
            SoundPlayer.play("jump");
        } else if (jumpPressed && !onGround && canDoubleJump && hasAcc(Item.AccEffect.DOUBLE_JUMP)) {
            vy = JUMP_VELOCITY * 0.9f * (mount != null ? 1.25f : 1f);
            canDoubleJump = false;
            SoundPlayer.play("jump");
        }
        // 松开跳跃则提前终止上升（短跳手感）
        if (!jumpHeld && vy < CUT_JUMP_THRESHOLD) {
            vy += 300 * dt;
        }

        // ---- 重力 ----
        vy += GRAVITY * dt;
        if (vy > MAX_FALL) {
            vy = MAX_FALL;
        }

        // ---- 分轴碰撞 ----
        x += vx * dt;
        resolveX(world);
        y += vy * dt;
        onGround = false;
        resolveY(world);
        if (onGround) {
            canDoubleJump = true;
        }

        // ---- 再生手环：每 3 秒回 1 血 ----
        if (hasAcc(Item.AccEffect.REGEN) && hp < maxHp && hp > 0) {
            regenTimer += dt;
            if (regenTimer >= 3f) {
                hp = Math.min(maxHp, hp + 1);
                regenTimer = 0;
            }
        } else {
            regenTimer = 0;
        }

        // ---- 计时器 ----
        if (invulnTimer > 0) {
            invulnTimer -= dt;
        }
        if (attackCooldown > 0) {
            attackCooldown -= dt;
        }
        if (swingTimer > 0) {
            swingTimer -= dt;
        }
        if (potionCooldown > 0) {
            potionCooldown -= dt;
        }
        if (thornsTimer > 0) {
            thornsTimer -= dt;
        }
        if (ironskinTimer > 0) ironskinTimer -= dt;
        if (swiftnessTimer > 0) swiftnessTimer -= dt;
        if (rageTimer > 0) rageTimer -= dt;
        if (nightVisionTimer > 0) nightVisionTimer -= dt;
        // ---- 魔力回复：每秒 3 点，移动时减半 ----
        manaRegenTimer += dt;
        if (manaRegenTimer >= 1f) {
            manaRegenTimer -= 1f;
            int regen = (Math.abs(vx) > 10f) ? 2 : 4;
            mana = Math.min(maxMana, mana + regen);
        }

        // ---- 掉落出世界 ----
        if (y > world.height * World.TILE + 200) {
            hp = 0;
        }
    }

    private void resolveX(World world) {
        int left = (int) (x / World.TILE);
        int right = (int) ((x + W - 0.01f) / World.TILE);
        int top = (int) (y / World.TILE);
        int bottom = (int) ((y + H - 0.01f) / World.TILE);
        if (vx > 0) {
            for (int gy = top; gy <= bottom; gy++) {
                if (world.isSolid(right, gy)) {
                    x = right * World.TILE - W - 0.001f;
                    vx = 0;
                    break;
                }
            }
        } else if (vx < 0) {
            for (int gy = top; gy <= bottom; gy++) {
                if (world.isSolid(left, gy)) {
                    x = (left + 1) * World.TILE + 0.001f;
                    vx = 0;
                    break;
                }
            }
        }
    }

    private void resolveY(World world) {
        int left = (int) (x / World.TILE);
        int right = (int) ((x + W - 0.01f) / World.TILE);
        int top = (int) (y / World.TILE);
        int bottom = (int) ((y + H - 0.01f) / World.TILE);
        if (vy > 0) {
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, bottom)) {
                    y = bottom * World.TILE - H - 0.001f;
                    vy = 0;
                    onGround = true;
                    break;
                }
            }
        } else if (vy < 0) {
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, top)) {
                    y = (top + 1) * World.TILE + 0.001f;
                    vy = 0;
                    break;
                }
            }
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
