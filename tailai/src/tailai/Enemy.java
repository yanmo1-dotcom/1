package tailai;

import java.awt.Rectangle;
import java.util.Random;

/**
 * 敌人：史莱姆（地面跳跃追击）与僵尸（夜间缓慢逼近、伤害更高）。
 * 速度参数与玩家一样下调，节奏更接近原版。
 */
public class Enemy {

    public enum Type {
        SLIME(25, 8, 30, 22),
        ZOMBIE(45, 14, 26, 44),
        EYE_OF_CTHULHU(1000, 20, 96, 72), // Boss：克苏鲁之眼
        SKELETRON(800, 25, 80, 80),       // Boss：骷髅王（头+双手）
        EATER_OF_WORLDS(1200, 18, 40, 40), // Boss：世界吞噬者（蠕虫）
        MUMMY(60, 18, 26, 44),        // 沙漠木乃伊
        ICE_SLIME(35, 10, 32, 24),    // 雪原冰史莱姆
        JUNGLE_SLIME(30, 12, 28, 20), // 丛林史莱姆
        DEVOURER(40, 16, 30, 22),     // 腐化吞噬者小怪
        DEMON_EYE(30, 12, 24, 20),    // 恶魔眼（飞行冲撞，夜晚出现）
        DEMON(80, 20, 36, 40),        // 地狱恶魔（飞行，射火球）
        WALL_OF_FLESH(4000, 30, 200, 160), // 血肉墙Boss（困难模式门）
        GOBLIN_WARRIOR(70, 16, 24, 40),    // 哥布林战士（近战）
        GOBLIN_ARCHER(55, 14, 22, 38),     // 哥布林弓箭手（远程）
        PIRATE_DECKHAND(90, 20, 26, 44),   // 海盗水手（近战）
        PIRATE_GUNNER(70, 18, 24, 42);     // 海盗枪手（远程）

        public final int maxHp, damage, w, h;

        Type(int maxHp, int damage, int w, int h) {
            this.maxHp = maxHp;
            this.damage = damage;
            this.w = w;
            this.h = h;
        }
    }

    public float x, y;
    public float vx, vy;
    public int w, h;
    public Type type;
    public int hp, maxHp;
    public int damage;
    public boolean onGround;
    public float hitFlash;      // 受击闪白剩余时间
    public float attackCooldown;
    public float aiTimer;
    public float knockVel;      // 横向击退冲量
    public boolean alive = true;
    /** 最后攻击者槽位（主机权威；0=主机本地，>0=客户端）。 */
    public int lastAttackerSlot = 0;

    // ---- 联机同步字段（客户端渲染用） ----
    public int netId = -1;      // 主机分配的敌人 ID（-1 表示未联网/本地）
    public boolean networked;   // 客户端模式：由主机 ENEMY_SYNC 驱动，不做本地 AI
    public float targetX, targetY; // 插值目标位置

    // ---- Boss 飞行状态 ----
    public float dashTimer;
    public float dashVx, dashVy;

    // ---- 恶魔火球 ----
    public boolean fireballRequested = false;
    // ---- 血肉墙激光 ----
    public boolean laserRequested = false;
    public float laserAngle = 0;

    // ---- 骷髅王手部状态 ----
    public float hand1OffsetX = -50, hand1OffsetY = 20;
    public float hand2OffsetX = 50, hand2OffsetY = 20;
    public float handAttackTimer = 2f;
    public int handAttacking = 0; // 0=无, 1=左手, 2=右手
    public float handAttackProgress = 0;
    /** 白天暴走标志（GamePanel 设置）。 */
    public boolean enraged = false;

    // ---- 世界吞噬者（蠕虫）节段 ----
    public static final int EATER_SEGMENTS = 12;
    public final float[] segX = new float[EATER_SEGMENTS];
    public final float[] segY = new float[EATER_SEGMENTS];

    private final Random rnd = new Random();

    public Enemy(float x, float y) {
        this(x, y, Type.SLIME);
    }

    public Enemy(float x, float y, Type type) {
        this.type = type;
        this.w = type.w;
        this.h = type.h;
        this.maxHp = type.maxHp;
        this.damage = type.damage;
        this.hp = maxHp;
        this.x = x;
        this.y = y;
        this.aiTimer = rnd.nextFloat() * 2f;
        // 初始化蠕虫节段
        for (int i = 0; i < EATER_SEGMENTS; i++) {
            segX[i] = x - i * 28;
            segY[i] = y;
        }
    }

    /** 切换为联网渲染模式（不做本地 AI，只跟随目标位置插值）。 */
    public void networkMode() {
        networked = true;
        targetX = x;
        targetY = y;
    }

    public Rectangle bounds() {
        return new Rectangle((int) x, (int) y, w, h);
    }

    /** 受击：扣血 + 击退。 */
    public void hurt(int dmg, float kx, float ky) {
        if (!alive) {
            return;
        }
        hp -= dmg;
        hitFlash = 0.15f;
        knockVel = kx;
        vy = ky;
        if (hp <= 0) {
            alive = false;
        }
    }

    public void update(float dt, World world, Player p) {
        if (!alive) {
            return;
        }
        // 客户端渲染的联网敌人：只做位置插值，不做 AI
        if (networked) {
            float k = Math.min(1f, 8f * dt);
            x += (targetX - x) * k;
            y += (targetY - y) * k;
            if (hitFlash > 0) {
                hitFlash -= dt;
            }
            return;
        }
        // Boss：克苏鲁之眼飞行 AI（不落地）
        if (type == Type.EYE_OF_CTHULHU) {
            updateEyeAI(dt, world, p);
            return;
        }
        // Boss：骷髅王飞行 AI（头+双手）
        if (type == Type.SKELETRON) {
            updateSkeletronAI(dt, world, p);
            return;
        }
        // Boss：世界吞噬者（蠕虫）AI
        if (type == Type.EATER_OF_WORLDS) {
            updateEaterAI(dt, world, p);
            return;
        }
        // 恶魔眼：飞行冲撞敌人（不受重力，朝玩家加速飞行）
        if (type == Type.DEMON_EYE) {
            updateDemonEyeAI(dt, world, p);
            return;
        }
        // 地狱恶魔：飞行+射火球
        if (type == Type.DEMON) {
            updateDemonAI(dt, world, p);
            return;
        }
        // 血肉墙：横向缓慢移动，定期射激光
        if (type == Type.WALL_OF_FLESH) {
            updateWallAI(dt, world, p);
            return;
        }
        aiTimer -= dt;
        attackCooldown -= dt;
        if (hitFlash > 0) {
            hitFlash -= dt;
        }

        // ---- AI：决定跳跃时机与方向 ----
        float dx = (p.x + Player.W / 2f) - (x + w / 2f);
        float dy = (p.y + Player.H / 2f) - (y + h / 2f);
        float chaseRange = (type == Type.ZOMBIE || type == Type.MUMMY) ? 300f : 380f;
        if (aiTimer <= 0) {
            if (Math.abs(dx) < chaseRange && Math.abs(dy) < 120) {
                // 追击玩家
                if (onGround) {
                    if (type == Type.ZOMBIE || type == Type.MUMMY) {
                        vy = -(140 + rnd.nextInt(30));
                        vx = (dx > 0 ? 1 : -1) * (25 + rnd.nextInt(20));
                    } else {
                        vy = -(130 + rnd.nextInt(60));
                        vx = (dx > 0 ? 1 : -1) * (30 + rnd.nextInt(30));
                    }
                }
                aiTimer = 0.7f + rnd.nextFloat() * 0.7f;
            } else {
                // 原地闲逛
                if (onGround) {
                    if (type == Type.ZOMBIE || type == Type.MUMMY) {
                        vy = -(120 + rnd.nextInt(30));
                        vx = (rnd.nextBoolean() ? 1 : -1) * (15 + rnd.nextInt(20));
                    } else {
                        vy = -(110 + rnd.nextInt(50));
                        vx = (rnd.nextBoolean() ? 1 : -1) * (20 + rnd.nextInt(25));
                    }
                }
                aiTimer = 1.0f + rnd.nextFloat();
            }
        }

        // ---- 击退冲量衰减 ----
        vx += knockVel;
        knockVel *= (float) Math.pow(0.0001, dt);
        if (Math.abs(knockVel) < 2) {
            knockVel = 0;
        }

        // ---- 重力 ----
        vy += 850 * dt;
        if (vy > 620) {
            vy = 620;
        }

        // ---- 分轴碰撞 ----
        x += vx * dt;
        int top = (int) (y / World.TILE);
        int bottom = (int) ((y + h - 0.01f) / World.TILE);
        if (vx > 0) {
            int col = (int) ((x + w - 0.01f) / World.TILE);
            for (int gy = top; gy <= bottom; gy++) {
                if (world.isSolid(col, gy)) {
                    x = col * World.TILE - w - 0.01f;
                    vx = 0;
                    break;
                }
            }
        } else if (vx < 0) {
            int col = (int) (x / World.TILE);
            for (int gy = top; gy <= bottom; gy++) {
                if (world.isSolid(col, gy)) {
                    x = (col + 1) * World.TILE + 0.01f;
                    vx = 0;
                    break;
                }
            }
        }

        y += vy * dt;
        onGround = false;
        int left = (int) (x / World.TILE);
        int right = (int) ((x + w - 0.01f) / World.TILE);
        int rowT = (int) (y / World.TILE);
        int rowB = (int) ((y + h - 0.01f) / World.TILE);
        if (vy > 0) {
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, rowB)) {
                    y = rowB * World.TILE - h - 0.01f;
                    vy = 0;
                    onGround = true;
                    break;
                }
            }
        } else if (vy < 0) {
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, rowT)) {
                    y = (rowT + 1) * World.TILE + 0.01f;
                    vy = 0;
                    break;
                }
            }
        }

        // ---- 接触玩家伤害 ----
        if (attackCooldown <= 0 && bounds().intersects(p.bounds())) {
            if (p.hurt(damage, this)) {
                attackCooldown = 1.2f;
            }
        }

        // ---- 掉出世界 ----
        if (y > world.height * World.TILE + 300) {
            alive = false;
        }
    }

    /**
     * Boss 飞行 AI：缓慢环绕追踪玩家，周期直线冲刺；
     * 血量低于 50% 后狂暴（更快、更频繁冲刺）。不落地、不掉出世界、不吃击退。
     */
    private void updateEyeAI(float dt, World world, Player p) {
        aiTimer -= dt;
        if (hitFlash > 0) {
            hitFlash -= dt;
        }
        float pcx = p.x + Player.W / 2f;
        float pcy = p.y + Player.H / 2f;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float dx = pcx - cx;
        float dy = pcy - cy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 1f) {
            dist = 1f;
        }
        float nx = dx / dist, ny = dy / dist;
        boolean enraged = hp < maxHp * 0.5f;
        float speed = enraged ? 210f : 140f;
        if (aiTimer <= 0) {
            dashTimer = enraged ? 1.3f : 2.0f;
            dashVx = nx * (enraged ? 470f : 340f);
            dashVy = ny * (enraged ? 390f : 280f);
            aiTimer = enraged ? 1.3f : 2.2f;
        }
        if (dashTimer > 0) {
            dashTimer -= dt;
            vx = dashVx;
            vy = dashVy;
        } else {
            vx += (nx * speed - vx) * 2f * dt;
            vy += (ny * speed - vy) * 2f * dt;
        }
        x += vx * dt;
        y += vy * dt;
        // 世界边界反弹
        if (x < 0) {
            x = 0;
            vx = Math.abs(vx);
        }
        if (x + w > world.width * World.TILE) {
            x = world.width * World.TILE - w;
            vx = -Math.abs(vx);
        }
        if (y < 0) {
            y = 0;
            vy = Math.abs(vy);
        }
        if (y + h > world.height * World.TILE) {
            y = world.height * World.TILE - h;
            vy = -Math.abs(vy);
        }
        // 接触玩家伤害
        attackCooldown -= dt;
        if (attackCooldown <= 0 && bounds().intersects(p.bounds())) {
            if (p.hurt(damage, this)) {
                attackCooldown = 1.2f;
            }
        }
    }

    /**
     * 骷髅王 AI：头部飞行追踪玩家（保持在上方），双手周期性冲刺攻击；
     * 血量低于 30% 或白天时暴走（更快、手更频繁攻击）。不落地、不吃击退。
     */
    private void updateSkeletronAI(float dt, World world, Player p) {
        aiTimer -= dt;
        attackCooldown -= dt;
        if (hitFlash > 0) {
            hitFlash -= dt;
        }
        boolean enraged = hp < maxHp * 0.3f || this.enraged;
        float pcx = p.x + Player.W / 2f;
        float pcy = p.y + Player.H / 2f;
        float cx = x + w / 2f;
        float cy = y + h / 2f;

        // 头部：保持在玩家上方 140 像素，水平跟随
        float targetX = pcx - w / 2f;
        float targetY = pcy - 140 - h / 2f;
        float headSpeed = enraged ? 180f : 110f;
        vx += ((targetX - x) * 3f - vx) * Math.min(1f, 4f * dt);
        vy += ((targetY - y) * 3f - vy) * Math.min(1f, 4f * dt);
        // 限速
        float sp = (float) Math.hypot(vx, vy);
        if (sp > headSpeed) {
            vx = vx / sp * headSpeed;
            vy = vy / sp * headSpeed;
        }
        x += vx * dt;
        y += vy * dt;

        // 手部攻击逻辑
        handAttackTimer -= dt;
        if (handAttacking == 0 && handAttackTimer <= 0) {
            // 选择一只手发起冲刺
            handAttacking = (Math.random() < 0.5f) ? 1 : 2;
            handAttackProgress = 0;
            handAttackTimer = enraged ? 1.2f : 2.2f;
        }
        if (handAttacking != 0) {
            handAttackProgress += dt;
            float attackDur = 0.9f;
            float retractDur = 0.7f;
            float dx = pcx - cx;
            float dy = pcy - cy;
            float d = (float) Math.hypot(dx, dy);
            if (d < 1f) d = 1f;
            float nx = dx / d, ny = dy / d;
            float baseX = (handAttacking == 1) ? -50f : 50f;
            float baseY = 20f;
            if (handAttackProgress < attackDur) {
                // 冲刺阶段：手向玩家飞出
                float t = handAttackProgress / attackDur;
                float reach = 120f * (float) Math.sin(t * Math.PI);
                float ox = baseX + nx * reach;
                float oy = baseY + ny * reach;
                if (handAttacking == 1) { hand1OffsetX = ox; hand1OffsetY = oy; }
                else { hand2OffsetX = ox; hand2OffsetY = oy; }
            } else if (handAttackProgress < attackDur + retractDur) {
                // 收回阶段
                float t = (handAttackProgress - attackDur) / retractDur;
                float ox = baseX + (handAttacking == 1 ? hand1OffsetX - baseX : hand2OffsetX - baseX) * (1 - t);
                float oy = baseY + (handAttacking == 1 ? hand1OffsetY - baseY : hand2OffsetY - baseY) * (1 - t);
                if (handAttacking == 1) { hand1OffsetX = ox; hand1OffsetY = oy; }
                else { hand2OffsetX = ox; hand2OffsetY = oy; }
            } else {
                // 收回完成，复位
                if (handAttacking == 1) { hand1OffsetX = -50f; hand1OffsetY = 20f; }
                else { hand2OffsetX = 50f; hand2OffsetY = 20f; }
                handAttacking = 0;
            }
        }

        // 世界边界
        if (x < 0) x = 0;
        if (x + w > world.width * World.TILE) x = world.width * World.TILE - w;
        if (y < 0) y = 0;
        if (y + h > world.height * World.TILE) y = world.height * World.TILE - h;

        // 头部接触伤害
        int dmg = enraged ? (int)(damage * 1.5f) : damage;
        if (attackCooldown <= 0 && bounds().intersects(p.bounds())) {
            if (p.hurt(dmg, this)) {
                attackCooldown = 1.0f;
            }
        }
        // 手部接触伤害
        float h1x = cx + hand1OffsetX - 16, h1y = cy + hand1OffsetY - 16;
        float h2x = cx + hand2OffsetX - 16, h2y = cy + hand2OffsetY - 16;
        Rectangle h1b = new Rectangle((int)h1x, (int)h1y, 32, 32);
        Rectangle h2b = new Rectangle((int)h2x, (int)h2y, 32, 32);
        if (attackCooldown <= 0 && (h1b.intersects(p.bounds()) || h2b.intersects(p.bounds()))) {
            if (p.hurt((int)(dmg * 0.7f), this)) {
                attackCooldown = 1.0f;
            }
        }
    }

    /**
     * 世界吞噬者 AI：蠕虫头部追踪玩家（可穿墙），身体节段跟随；
     * 头部和所有节段接触玩家都造成伤害。不落地、不吃击退。
     */
    private void updateEaterAI(float dt, World world, Player p) {
        aiTimer -= dt;
        attackCooldown -= dt;
        if (hitFlash > 0) {
            hitFlash -= dt;
        }
        float pcx = p.x + Player.W / 2f;
        float pcy = p.y + Player.H / 2f;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float dx = pcx - cx;
        float dy = pcy - cy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 1f) dist = 1f;
        float nx = dx / dist, ny = dy / dist;
        // 头部追踪（可穿墙），速度随距离变化
        float speed = 170f + Math.min(80f, dist * 0.15f);
        vx += (nx * speed - vx) * 3f * dt;
        vy += (ny * speed - vy) * 3f * dt;
        float sp = (float) Math.hypot(vx, vy);
        if (sp > speed) {
            vx = vx / sp * speed;
            vy = vy / sp * speed;
        }
        x += vx * dt;
        y += vy * dt;
        // 世界边界
        if (x < 0) x = 0;
        if (x + w > world.width * World.TILE) x = world.width * World.TILE - w;
        if (y < 0) y = 0;
        if (y + h > world.height * World.TILE) y = world.height * World.TILE - h;

        // 身体节段跟随前一节
        segX[0] = x + w / 2f;
        segY[0] = y + h / 2f;
        float segDist = 26f;
        for (int i = 1; i < EATER_SEGMENTS; i++) {
            float sdx = segX[i - 1] - segX[i];
            float sdy = segY[i - 1] - segY[i];
            float sd = (float) Math.hypot(sdx, sdy);
            if (sd > segDist) {
                float ratio = (sd - segDist) / sd;
                segX[i] += sdx * ratio;
                segY[i] += sdy * ratio;
            }
        }

        // 头部接触伤害
        if (attackCooldown <= 0 && bounds().intersects(p.bounds())) {
            if (p.hurt(damage, this)) {
                attackCooldown = 1.0f;
            }
        }
        // 身体节段接触伤害
        if (attackCooldown <= 0) {
            for (int i = 2; i < EATER_SEGMENTS; i++) {
                Rectangle sb = new Rectangle((int) segX[i] - 16, (int) segY[i] - 16, 32, 32);
                if (sb.intersects(p.bounds())) {
                    if (p.hurt((int) (damage * 0.6f), this)) {
                        attackCooldown = 1.0f;
                    }
                    break;
                }
            }
        }
    }

    /**
     * 恶魔眼 AI：飞行敌人，不受重力影响，朝玩家方向加速飞行，撞墙反弹。
     * 速度随距离变化（远时快，近时慢），接触造成伤害。
     */
    private void updateDemonEyeAI(float dt, World world, Player p) {
        aiTimer -= dt;
        attackCooldown -= dt;
        if (hitFlash > 0) {
            hitFlash -= dt;
        }
        float pcx = p.x + Player.W / 2f;
        float pcy = p.y + Player.H / 2f;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float dx = pcx - cx;
        float dy = pcy - cy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 1f) {
            dist = 1f;
        }
        // 朝玩家方向加速
        float accel = 220f;
        vx += (dx / dist) * accel * dt;
        vy += (dy / dist) * accel * dt;
        // 最大速度（远时更快）
        float maxSp = dist > 200 ? 130f : 90f;
        float sp = (float) Math.hypot(vx, vy);
        if (sp > maxSp) {
            vx = vx / sp * maxSp;
            vy = vy / sp * maxSp;
        }
        // 击退冲量
        vx += knockVel;
        knockVel *= (float) Math.pow(0.0001, dt);
        if (Math.abs(knockVel) < 2) {
            knockVel = 0;
        }
        // 移动 + 撞墙反弹
        x += vx * dt;
        int top = (int) (y / World.TILE);
        int bottom = (int) ((y + h - 0.01f) / World.TILE);
        if (vx > 0) {
            int col = (int) ((x + w - 0.01f) / World.TILE);
            for (int gy = top; gy <= bottom; gy++) {
                if (world.isSolid(col, gy)) {
                    x = col * World.TILE - w - 0.01f;
                    vx = -vx * 0.5f;
                    break;
                }
            }
        } else if (vx < 0) {
            int col = (int) (x / World.TILE);
            for (int gy = top; gy <= bottom; gy++) {
                if (world.isSolid(col, gy)) {
                    x = (col + 1) * World.TILE + 0.01f;
                    vx = -vx * 0.5f;
                    break;
                }
            }
        }
        y += vy * dt;
        int left = (int) (x / World.TILE);
        int right = (int) ((x + w - 0.01f) / World.TILE);
        if (vy > 0) {
            int row = (int) ((y + h - 0.01f) / World.TILE);
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, row)) {
                    y = row * World.TILE - h - 0.01f;
                    vy = -vy * 0.5f;
                    break;
                }
            }
        } else if (vy < 0) {
            int row = (int) (y / World.TILE);
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, row)) {
                    y = (row + 1) * World.TILE + 0.01f;
                    vy = -vy * 0.5f;
                    break;
                }
            }
        }
        // 世界边界
        if (x < 0) { x = 0; vx = Math.abs(vx); }
        if (x + w > world.width * World.TILE) { x = world.width * World.TILE - w; vx = -Math.abs(vx); }
        // 接触伤害
        if (attackCooldown <= 0 && bounds().intersects(p.bounds())) {
            if (p.hurt(damage, this)) {
                attackCooldown = 1.0f;
            }
        }
        // 掉出世界
        if (y > world.height * World.TILE + 300) {
            alive = false;
        }
    }

    /** 地狱恶魔 AI：飞行保持距离，定期射火球。 */
    private void updateDemonAI(float dt, World world, Player p) {
        aiTimer -= dt;
        attackCooldown -= dt;
        if (hitFlash > 0) hitFlash -= dt;
        float pcx = p.x + Player.W / 2f;
        float pcy = p.y + Player.H / 2f;
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float dx = pcx - cx;
        float dy = pcy - cy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 1f) dist = 1f;
        // 保持 180 格距离：太近飞远，太远飞近
        float targetDist = 180f;
        float moveDir = (dist > targetDist) ? 1f : -0.6f;
        float accel = 180f;
        vx += (dx / dist) * accel * moveDir * dt;
        vy += (dy / dist) * accel * moveDir * dt;
        // 上下浮动
        vy += Math.sin(aiTimer * 3f) * 30f * dt;
        // 最大速度
        float maxSp = 100f;
        float sp = (float) Math.hypot(vx, vy);
        if (sp > maxSp) {
            vx = vx / sp * maxSp;
            vy = vy / sp * maxSp;
        }
        // 击退
        vx += knockVel;
        knockVel *= (float) Math.pow(0.0001, dt);
        if (Math.abs(knockVel) < 2) knockVel = 0;
        // 移动 + 撞墙
        x += vx * dt;
        y += vy * dt;
        // 边界
        if (x < 0) { x = 0; vx = Math.abs(vx); }
        if (x + w > world.width * World.TILE) { x = world.width * World.TILE - w; vx = -Math.abs(vx); }
        if (y < 0) { y = 0; vy = Math.abs(vy); }
        if (y + h > world.height * World.TILE) { y = world.height * World.TILE - h; vy = -Math.abs(vy); }
        // 射火球：每 2.5 秒，距离 < 400
        if (attackCooldown <= 0 && dist < 400f) {
            fireballRequested = true;
            attackCooldown = 2.5f;
        }
        // 接触伤害
        if (attackCooldown <= 0 && bounds().intersects(p.bounds())) {
            if (p.hurt(damage, this)) {
                attackCooldown = 1.0f;
            }
        }
    }

    /** 血肉墙 AI：横向缓慢推进，定期射激光。 */
    private void updateWallAI(float dt, World world, Player p) {
        aiTimer -= dt;
        attackCooldown -= dt;
        if (hitFlash > 0) hitFlash -= dt;
        // 横向移动：朝玩家方向缓慢推进
        float pcx = p.x + Player.W / 2f;
        float cx = x + w / 2f;
        float dir = (pcx > cx) ? 1f : -1f;
        vx = dir * 40f; // 缓慢推进
        x += vx * dt;
        // 上下浮动跟随玩家
        float pcy = p.y + Player.H / 2f;
        float cy = y + h / 2f;
        if (Math.abs(pcy - cy) > 30) {
            y += Math.signum(pcy - cy) * 25f * dt;
        }
        // 边界
        if (x < 0) { x = 0; vx = Math.abs(vx); }
        if (x + w > world.width * World.TILE) {
            x = world.width * World.TILE - w;
            vx = -Math.abs(vx);
        }
        // 射激光：每 2 秒
        if (attackCooldown <= 0) {
            laserRequested = true;
            laserAngle = (float) Math.atan2(pcy - cy, pcx - cx);
            attackCooldown = 2.0f;
        }
        // 接触伤害（高伤害）
        if (bounds().intersects(p.bounds())) {
            if (p.hurt(damage, this)) {
                attackCooldown = 0.8f;
            }
        }
    }
}
