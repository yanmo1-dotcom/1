package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Tank —— 坦克。
 * <p>
 * 沿预设路径点（waypoint）循环巡逻；发现玩家进入检测范围后转入 ATTACK，
 * 转向玩家并按冷却开火。血量 3，需手雷/火箭弹击杀（机枪扣血但不致死除非多击）。
 *
 * <h3>巡逻逻辑</h3>
 * 维护一组路径点 {@link #waypoints} 和当前目标索引 {@link #wpIndex}。
 * 每帧朝当前目标点移动；到达阈值内则切到下一个点（循环）：
 * <pre>
 *   if dist(pos, waypoints[wpIndex]) < ARRIVE_RADIUS:
 *     wpIndex = (wpIndex + 1) % waypoints.length
 *   move toward waypoints[wpIndex]
 * </pre>
 *
 * <h3>FSM 迁移</h3>
 * <pre>
 *   PATROL ──玩家进入 DETECT_RANGE──→ ATTACK
 *   ATTACK ──玩家脱离 LOSE_RANGE（>DETECT）──→ PATROL   // 滞后区间防抖
 *   任意 ──hp≤0──→ DEAD
 * </pre>
 * 检测范围(280) < 脱离范围(360) 形成滞回（hysteresis），避免玩家在边界来回横跳导致状态抖动。
 *
 * <h3>开火</h3>
 * ATTACK 状态下若 {@link #fireCooldown} 归零，调用 {@link #fireCallback} 朝玩家直线开火。
 * 回调由 GameWorld 注入，把敌人子弹加入独立池。
 *
 * @author Jackal Dev Team
 */
public class Tank extends Enemy {

    /** 坦克血量：3 */
    private static final int HP = 3;
    /** 巡逻速度（像素/秒） */
    private static final float SPEED = 50f;
    /** 追击/转向速度（像素/秒，略慢于巡逻以体现"重型转身"） */
    private static final float CHASE_SPEED = 45f;
    /** 半径 */
    private static final float RADIUS = 12f;
    /** 接触伤害 */
    private static final float CONTACT_DAMAGE = 1f;
    /** 发现玩家的距离（像素）。进入此范围 PATROL→ATTACK */
    private static final float DETECT_RANGE = 280f;
    /** 脱离玩家的距离（像素）。超出此范围 ATTACK→PATROL。>DETECT 形成滞回防抖 */
    private static final float LOSE_RANGE = 360f;
    /** 到达路径点的判定半径（像素） */
    private static final float ARRIVE_RADIUS = 8f;
    /** 开火冷却（秒） */
    private static final float FIRE_COOLDOWN = 1.8f;
    /** 开火距离（像素）。超过此距离不浪费弹药 */
    private static final float FIRE_RANGE = 320f;

    /** 巡逻路径点数组（世界坐标，至少 2 个点形成往返） */
    private final Vector2[] waypoints;
    /** 当前目标路径点索引 */
    private int wpIndex = 0;
    /** 开火冷却剩余时间（秒） */
    private float fireCooldown = 0f;
    /** 开火回调（由 GameWorld 注入） */
    private EnemyFireCallback fireCallback;

    /**
     * 构造坦克。
     *
     * @param startX    初始 X（通常 = waypoints[0].x）
     * @param startY    初始 Y
     * @param waypoints 巡逻路径点（至少 2 个）
     */
    public Tank(float startX, float startY, Vector2[] waypoints) {
        super(startX, startY, HP, SPEED, RADIUS, CONTACT_DAMAGE);
        this.waypoints = waypoints;
        this.state = AIState.PATROL;
    }

    /** 设置开火回调（GameWorld 注入） */
    public void setFireCallback(EnemyFireCallback cb) {
        this.fireCallback = cb;
    }

    @Override
    protected void think(float dt, Vector2 target) {
        // 冷却推进
        if (fireCooldown > 0f) fireCooldown -= dt;

        float dist = Vector2.dst(position.x, position.y, target.x, target.y);

        // —— 状态迁移（滞回防抖）——
        if (state == AIState.PATROL && dist < DETECT_RANGE) {
            state = AIState.ATTACK;
        } else if (state == AIState.ATTACK && dist > LOSE_RANGE) {
            state = AIState.PATROL;
        }

        if (state == AIState.ATTACK) {
            // —— 攻击：转向玩家 + 开火 ——
            float dx = target.x - position.x;
            float dy = target.y - position.y;
            facing = MathUtils.atan2(dy, dx);
            // 缓慢靠近但不贴脸，保持在中距开火
            if (dist > 120f) {
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                position.x += (dx / len) * CHASE_SPEED * dt;
                position.y += (dy / len) * CHASE_SPEED * dt;
            }
            // 在射程内且冷却好则开火
            if (dist < FIRE_RANGE && fireCooldown <= 0f && fireCallback != null) {
                // 炮口位置
                float mx = position.x + MathUtils.cos(facing) * (RADIUS + 4f);
                float my = position.y + MathUtils.sin(facing) * (RADIUS + 4f);
                fireCallback.fire(mx, my, facing, this);
                fireCooldown = FIRE_COOLDOWN;
            }
        } else {
            // —— 巡逻：朝当前路径点移动 ——
            Vector2 wp = waypoints[wpIndex];
            float dx = wp.x - position.x;
            float dy = wp.y - position.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < ARRIVE_RADIUS) {
                // 到达，切下一个点（循环）
                wpIndex = (wpIndex + 1) % waypoints.length;
            } else if (d > 0.001f) {
                facing = MathUtils.atan2(dy, dx);
                position.x += (dx / d) * moveSpeed * dt;
                position.y += (dy / d) * moveSpeed * dt;
            }
        }
    }

    @Override
    public void render(ShapeRenderer shapes) {
        // 受击闪烁
        boolean flashing = hitInvuln > 0f && (((int) (hitInvuln * 50)) % 2 == 0);
        // 车身：深灰色矩形（以 facing 旋转）。用四点近似旋转矩形——这里用圆+炮管线简化
        shapes.setColor(flashing ? Color.WHITE : Color.DARK_GRAY);
        shapes.circle(position.x, position.y, radius);
        // 炮管：朝 facing 方向的粗线
        shapes.setColor(flashing ? Color.WHITE : Color.LIGHT_GRAY);
        shapes.line(position.x, position.y,
                position.x + MathUtils.cos(facing) * radius * 1.8f,
                position.y + MathUtils.sin(facing) * radius * 1.8f);
        // 血条
        renderHpBar(shapes, radius * 2f);
    }
}
