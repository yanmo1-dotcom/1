package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * BossArmoredVehicle —— 巨型装甲车 Boss。
 * <p>
 * 血量 50，占据约屏幕 1/3 宽度。登场后从右侧驶入，在战斗区域左右缓慢移动，
 * 根据血量进入三个阶段，每阶段攻击模式不同。击败后触发胜利结算。
 *
 * <h3>阶段 FSM（BossPhase 枚举）</h3>
 * <pre>
 *   ENTERING  —— 从右侧驶入到战斗位置，期间不开火
 *        ↓ 到达
 *   PHASE_1   —— hp > 30：每 2s 发射 3 发扇形子弹
 *        ↓ hp ≤ 30
 *   PHASE_2   —— hp 15~30：召唤 2 步兵 + 加速移动 + 每 1.5s 发射追踪导弹
 *        ↓ hp < 15
 *   PHASE_3   —— hp < 15：全屏震动 + 每 1s 激光扫射 + 自身旋转冲撞
 *        ↓ hp ≤ 0
 *   DEFEATED  —— 击败，等待胜利结算
 * </pre>
 * 阶段切换由 {@link #checkPhaseTransition()} 在 takeDamage 后检测，
 * 切换时重置对应攻击冷却、播放音效、触发一次性特效（如召唤步兵）。
 *
 * <h3>攻击节奏</h3>
 * 每阶段有独立的攻击冷却计时器，归零后触发一次攻击并重置。
 * 这样攻击频率与帧率无关，只由冷却时间决定。
 *
 * <h3>子弹/激光/召唤的解耦</h3>
 * Boss 不直接持有子弹池/敌人列表，而是通过回调：
 * <ul>
 *   <li>{@link #fireCallback}（EnemyFireCallback）：发射 Boss 子弹/导弹</li>
 *   <li>{@link #summonCallback}（Runnable）：阶段2召唤步兵</li>
 *   <li>激光由本类自持 {@link LaserBeam}，GameWorld 负责其命中与渲染调度</li>
 * </ul>
 *
 * @author Jackal Dev Team
 */
public class BossArmoredVehicle {

    /** Boss 阶段枚举（FSM 状态集） */
    public enum BossPhase {
        /** 从右侧驶入中 */
        ENTERING,
        /** 阶段1：hp > 30，扇形子弹 */
        PHASE_1,
        /** 阶段2：hp 15~30，召唤+追踪导弹 */
        PHASE_2,
        /** 阶段3：hp < 15，激光+冲撞 */
        PHASE_3,
        /** 已击败 */
        DEFEATED
    }

    // ===== 基础属性 =====

    /** 最大血量 */
    public static final int MAX_HP = 50;
    /** Boss 半宽（像素）。约屏幕 1/3 宽 → 半宽 ≈ 512/6 ≈ 85 */
    public static final float HALF_WIDTH = 85f;
    /** Boss 半高（像素） */
    public static final float HALF_HEIGHT = 45f;
    /** 接触伤害（阶段3冲撞时更高） */
    private static final float CONTACT_DAMAGE = 1.5f;

    // ===== 渲染用静态颜色常量（避免每帧 new Color 产生 GC）=====
    /** Boss 车身深红色 */
    private static final Color BODY_COLOR = new Color(0.5f, 0.1f, 0.1f, 1f);

    // ===== 阶段参数 =====

    /** 阶段1 扇形子弹冷却（秒） */
    private static final float P1_FIRE_COOLDOWN = 2.0f;
    /** 阶段1 子弹速度（像素/秒）。GameWorld 回调按类型设置，此处仅文档化默认值 */
    public static final float P1_BULLET_SPEED = 260f;
    /** 阶段1 扇形张角（弧度，总角度） */
    private static final float P1_FAN_ANGLE = MathUtils.PI / 4f;
    /** 阶段1 每次发射子弹数 */
    private static final int P1_BULLET_COUNT = 3;

    /** 阶段2 导弹冷却（秒） */
    private static final float P2_FIRE_COOLDOWN = 1.5f;
    /** 阶段2 导弹速度（像素/秒）。GameWorld 回调按类型设置 */
    public static final float P2_MISSILE_SPEED = 200f;
    /** 阶段2 移动速度倍率（相对基础速度） */
    private static final float P2_SPEED_MULT = 1.6f;

    /** 阶段3 激光冷却（秒）。每 1s 启动一次新激光扫射 */
    private static final float P3_LASER_COOLDOWN = 1.0f;
    /** 阶段3 激光单次持续（秒） */
    private static final float P3_LASER_DURATION = 0.8f;
    /** 阶段3 激光 DPS */
    private static final float P3_LASER_DPS = 1f;
    /** 阶段3 移动速度倍率（冲撞，更快） */
    private static final float P3_SPEED_MULT = 2.2f;

    /** 基础水平移动速度（像素/秒）。阶段1用此值，阶段2/3 乘倍率 */
    private static final float BASE_MOVE_SPEED = 50f;

    // ===== 运行时状态 =====

    /** 中心位置（世界坐标） */
    public final Vector2 position = new Vector2();
    /** 当前血量 */
    private int hp = MAX_HP;
    /** 当前阶段 */
    private BossPhase phase = BossPhase.ENTERING;
    /** 是否已击败 */
    private boolean defeated = false;
    /** 受击无敌帧（秒），防止同一发子弹多段命中 */
    private float hitInvuln = 0f;

    /** 水平移动方向 +1（向右）/ -1（向左） */
    private int moveDir = -1;
    /** 战斗区域左边界（像素） */
    private float battleLeft = 0f;
    /** 战斗区域右边界（像素） */
    private float battleRight = 0f;
    /** 登场目标 X（驶入到此点开始战斗） */
    private float enterTargetX = 0f;

    /** 当前阶段攻击冷却剩余（秒） */
    private float attackCooldown = 0f;
    /** 阶段2 是否已召唤过步兵（避免重复召唤） */
    private boolean p2Summoned = false;
    /** 阶段3 自身旋转角（弧度），用于冲撞旋转特效 */
    private float spinAngle = 0f;

    /** 开火回调（发射 Boss 子弹/导弹，带类型） */
    private BossFireCallback fireCallback;
    /** 召唤回调（阶段2召唤 2 步兵） */
    private Runnable summonCallback;
    /** 阶段3 屏幕震动回调（GameWorld 实现，施加相机偏移） */
    private Runnable shakeCallback;

    /** Boss 自持激光（阶段3使用） */
    public final LaserBeam laser = new LaserBeam();

    /**
     * 构造 Boss。
     *
     * @param startX       初始 X（通常在屏幕右侧外）
     * @param startY       初始 Y（战斗高度）
     * @param enterTargetX 驶入目标 X（战斗中心）
     * @param battleLeft   战斗区域左边界
     * @param battleRight  战斗区域右边界
     */
    public BossArmoredVehicle(float startX, float startY, float enterTargetX,
                              float battleLeft, float battleRight) {
        this.position.set(startX, startY);
        this.enterTargetX = enterTargetX;
        this.battleLeft = battleLeft;
        this.battleRight = battleRight;
        this.phase = BossPhase.ENTERING;
    }

    /** 设置开火回调 */
    public void setFireCallback(BossFireCallback cb) { this.fireCallback = cb; }
    /** 设置召唤回调 */
    public void setSummonCallback(Runnable r) { this.summonCallback = r; }
    /** 设置震动回调 */
    public void setShakeCallback(Runnable r) { this.shakeCallback = r; }

    /** @return 当前血量 */
    public int getHp() { return hp; }
    /** @return 最大血量 */
    public int getMaxHp() { return MAX_HP; }
    /** @return 当前阶段 */
    public BossPhase getPhase() { return phase; }
    /** @return 是否已击败 */
    public boolean isDefeated() { return defeated; }
    /** @return 是否处于战斗中（非登场非击败） */
    public boolean isActive() {
        return phase == BossPhase.PHASE_1 || phase == BossPhase.PHASE_2
                || phase == BossPhase.PHASE_3;
    }

    /**
     * 受击扣血。Boss 不受无敌帧限制（每发都扣），但 hitInvuln 用于受击闪烁。
     *
     * @param amount 伤害值
     * @return true 表示扣血生效
     */
    public boolean takeDamage(int amount) {
        if (defeated) return false;
        hp -= amount;
        hitInvuln = 0.05f;
        if (hp <= 0) {
            hp = 0;
            phase = BossPhase.DEFEATED;
            defeated = true;
            if (AudioManager.get() != null) AudioManager.get().playSfx("gameover");
            com.badlogic.gdx.Gdx.app.log("Boss", "Boss 已击败！");
        } else {
            checkPhaseTransition();
        }
        return true;
    }

    /**
     * 检测阶段切换。在 takeDamage 后调用。
     * <p>
     * 切换时重置攻击冷却并触发一次性特效。
     */
    private void checkPhaseTransition() {
        BossPhase newPhase;
        if (hp > 30) newPhase = BossPhase.PHASE_1;
        else if (hp > 15) newPhase = BossPhase.PHASE_2;
        else newPhase = BossPhase.PHASE_3;

        if (newPhase != phase && phase != BossPhase.ENTERING) {
            phase = newPhase;
            attackCooldown = 0f; // 切换时允许立即攻击
            if (AudioManager.get() != null) AudioManager.get().playSfx("enemy_die");
            com.badlogic.gdx.Gdx.app.log("Boss", "进入阶段 → " + phase);

            // 阶段2首次进入：召唤 2 步兵
            if (phase == BossPhase.PHASE_2 && !p2Summoned && summonCallback != null) {
                p2Summoned = true;
                summonCallback.run();
                com.badlogic.gdx.Gdx.app.log("Boss", "召唤 2 步兵");
            }
        }
    }

    /**
     * 每帧更新。
     *
     * @param dt       帧时间（秒）
     * @param playerPos 玩家位置（用于导弹追踪与激光方向）
     */
    public void update(float dt, Vector2 playerPos) {
        if (hitInvuln > 0f) hitInvuln -= dt;

        if (phase == BossPhase.DEFEATED) return;

        if (phase == BossPhase.ENTERING) {
            // —— 登场：向左驶入到 enterTargetX ——
            position.x -= BASE_MOVE_SPEED * 1.5f * dt;
            if (position.x <= enterTargetX) {
                position.x = enterTargetX;
                phase = BossPhase.PHASE_1;
                attackCooldown = 0f;
                com.badlogic.gdx.Gdx.app.log("Boss", "登场完成，进入阶段 1");
            }
            return;
        }

        // —— 左右移动 ——
        float speed = BASE_MOVE_SPEED;
        if (phase == BossPhase.PHASE_2) speed *= P2_SPEED_MULT;
        else if (phase == BossPhase.PHASE_3) speed *= P3_SPEED_MULT;

        position.x += moveDir * speed * dt;
        if (position.x <= battleLeft + HALF_WIDTH) {
            position.x = battleLeft + HALF_WIDTH;
            moveDir = 1;
        } else if (position.x >= battleRight - HALF_WIDTH) {
            position.x = battleRight - HALF_WIDTH;
            moveDir = -1;
        }

        // 阶段3自身旋转（冲撞特效）
        if (phase == BossPhase.PHASE_3) {
            spinAngle += dt * 6f; // 旋转速度
            // 触发震动
            if (shakeCallback != null) shakeCallback.run();
        }

        // —— 攻击冷却推进 ——
        if (attackCooldown > 0f) attackCooldown -= dt;

        // —— 按阶段攻击（ENTERING/DEFEATED 不攻击）——
        switch (phase) {
            case PHASE_1: updatePhase1(dt, playerPos); break;
            case PHASE_2: updatePhase2(dt, playerPos); break;
            case PHASE_3: updatePhase3(dt, playerPos); break;
            case ENTERING:
            case DEFEATED:
            default:
                break;
        }
    }

    /** 阶段1：每 P1_FIRE_COOLDOWN 秒发射 3 发扇形子弹朝玩家方向 */
    private void updatePhase1(float dt, Vector2 playerPos) {
        if (attackCooldown > 0f || fireCallback == null) return;
        float baseAng = MathUtils.atan2(playerPos.y - position.y, playerPos.x - position.x);
        // 扇形：以 baseAng 为中心，均匀分布在 [-P1_FAN_ANGLE/2, +P1_FAN_ANGLE/2]
        for (int i = 0; i < P1_BULLET_COUNT; i++) {
            float t = i / (float) (P1_BULLET_COUNT - 1);
            float ang = baseAng - P1_FAN_ANGLE * 0.5f + P1_FAN_ANGLE * t;
            fireCallback.fire(position.x, position.y, ang, Bullet.Type.BOSS_BULLET);
        }
        attackCooldown = P1_FIRE_COOLDOWN;
        if (AudioManager.get() != null) AudioManager.get().playSfx("mg");
    }

    /** 阶段2：每 P2_FIRE_COOLDOWN 秒发射 1 发追踪导弹 */
    private void updatePhase2(float dt, Vector2 playerPos) {
        if (attackCooldown > 0f || fireCallback == null) return;
        float ang = MathUtils.atan2(playerPos.y - position.y, playerPos.x - position.x);
        fireCallback.fire(position.x, position.y, ang, Bullet.Type.BOSS_MISSILE);
        attackCooldown = P2_FIRE_COOLDOWN;
        if (AudioManager.get() != null) AudioManager.get().playSfx("rocket");
    }

    /** 阶段3：每 P3_LASER_COOLDOWN 秒启动一次激光扫射 */
    private void updatePhase3(float dt, Vector2 playerPos) {
        // 激光更新由 GameWorld 调度（需渲染与命中判定），这里只负责启动
        if (attackCooldown <= 0f && !laser.active) {
            float toPlayer = MathUtils.atan2(playerPos.y - position.y, playerPos.x - position.x);
            // 扫射范围：以朝玩家方向为中心 ±30°
            laser.start(position.x, position.y, toPlayer - 0.5f,
                    toPlayer - 0.5f, toPlayer + 0.5f, P3_LASER_DURATION);
            laser.dps = P3_LASER_DPS;
            attackCooldown = P3_LASER_COOLDOWN;
            if (AudioManager.get() != null) AudioManager.get().playSfx("rocket");
        }
    }

    /**
     * 获取 Boss 包围盒（用于玩家子弹命中与接触判定）。
     * Boss 是巨型矩形，用半宽半高的 AABB。
     */
    public Rectangle getBounds(Rectangle out) {
        return out.set(position.x - HALF_WIDTH, position.y - HALF_HEIGHT,
                HALF_WIDTH * 2f, HALF_HEIGHT * 2f);
    }

    /** @return 接触伤害值 */
    public float getContactDamage() {
        return phase == BossPhase.PHASE_3 ? CONTACT_DAMAGE * 2f : CONTACT_DAMAGE;
    }

    /** @return 受击无敌帧剩余（秒，用于闪烁） */
    public float getHitInvuln() { return hitInvuln; }

    /**
     * 渲染 Boss。
     * <p>
     * 调用前 shapes 须已 begin(Filled) 并设置投影矩阵。
     *
     * @param shapes 外部 ShapeRenderer
     */
    public void render(ShapeRenderer shapes) {
        if (phase == BossPhase.DEFEATED) return;
        boolean flashing = hitInvuln > 0f && (((int) (hitInvuln * 50)) % 2 == 0);
        // 车身：深红色巨型矩形（阶段3加旋转）
        shapes.setColor(flashing ? Color.WHITE : BODY_COLOR);
        float rot = phase == BossPhase.PHASE_3 ? spinAngle * MathUtils.radDeg : 0f;
        shapes.rect(position.x - HALF_WIDTH, position.y - HALF_HEIGHT,
                HALF_WIDTH, HALF_HEIGHT,
                HALF_WIDTH * 2f, HALF_HEIGHT * 2f, 1f, 1f, rot);
        // 装甲条纹：内部亮色矩形
        shapes.setColor(flashing ? Color.WHITE : Color.DARK_GRAY);
        shapes.rect(position.x - HALF_WIDTH * 0.6f, position.y - HALF_HEIGHT * 0.5f,
                HALF_WIDTH * 0.6f, HALF_HEIGHT * 0.5f,
                HALF_WIDTH * 1.2f, HALF_HEIGHT, 1f, 1f, rot);
        // 炮管：朝玩家方向的粗线（阶段1/2）
        // 由 GameWorld 渲染激光；此处不画激光
    }
}
