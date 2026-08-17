package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Turret —— 固定炮台。
 * <p>
 * 不移动（moveSpeed=0）。持续扫描前方扇形视野（90°锥），玩家进入锥内且距离在射程内时，
 * 按"预判命中点"开火。血量 2。
 *
 * <h3>扇形视野检测</h3>
 * 锥以 {@link #facing} 为中心轴，半张角 {@link #HALF_FOV}（45°，整锥 90°）。
 * 玩家在锥内的条件：
 * <pre>
 *   1) 距离 ≤ FIRE_RANGE
 *   2) 朝向玩家的角度差 |Δangle| ≤ HALF_FOV
 * </pre>
 * 角度差用归一化到 (-π,π] 的差值判断，避免跨越 ±π 时误判。
 *
 * <h3>预判开火推导（第 7 步升级为精确线性预测）</h3>
 * 设炮台位置 P，玩家位置 T、速度 V，子弹速度 S，飞行时间 t。
 * t 秒后玩家在 T + V·t，子弹需满足 |T + V·t − P| = S·t。令 D = T − P：
 * <pre>
 *   |D + V·t|² = (S·t)²
 *   |D|² + 2(D·V)t + |V|²t² = S²t²
 *   (S² − |V|²) t² − 2(D·V) t − |D|² = 0      —— 一元二次方程
 * </pre>
 * 取 a = S²−|V|², b = −2(D·V), c = −|D|²。
 * - 若 a≈0（玩家速度≈子弹速度）：退化为一次方程 t = |D|² / (2 D·V)。
 * - 否则求判别式 Δ = b² − 4ac，取最小正实根。
 * - 若无正实根（玩家比子弹快且远离，追不上）：退化为朝当前位置开火。
 * 这是精确解，优于旧版 t≈|D|/S 的近似（旧版忽略玩家移动对距离的二阶影响）。
 * 对匀速直线移动的吉普车近乎必中；玩家急转弯仍可骗过。
 *
 * <h3>扫描行为</h3>
 * 未发现玩家时 facing 以恒定角速度旋转（来回摆动），模拟"巡逻扫描"。
 * 发现玩家后 facing 锁定预判角开火。
 *
 * @author Jackal Dev Team
 */
public class Turret extends Enemy {

    /** 炮台血量：2 */
    private static final int HP = 2;
    /** 固定不动 */
    private static final float SPEED = 0f;
    /** 半径 */
    private static final float RADIUS = 11f;
    /** 接触伤害（玩家撞上来） */
    private static final float CONTACT_DAMAGE = 0.5f;
    /** 射程（像素） */
    private static final float FIRE_RANGE = 300f;
    /** 视野半张角（弧度）。45° → 整锥 90° */
    private static final float HALF_FOV = MathUtils.PI / 4f;
    /** 开火冷却（秒） */
    private static final float FIRE_COOLDOWN = 1.4f;
    /** 扫描时 facing 旋转角速度（弧度/秒） */
    private static final float SCAN_ANGULAR_SPEED = 1.2f;
    /** 扫描摆动范围（弧度，相对中心轴 baseFacing） */
    private static final float SCAN_SWING = MathUtils.PI * 0.6f;

    /** 开火冷却剩余 */
    private float fireCooldown = 0f;
    /** 扫描中心轴（弧度）。构造时设定，炮台围绕此轴左右摆动扫描 */
    private final float baseFacing;
    /** 扫描方向 +1/-1，到摆动端点反向 */
    private int scanDir = 1;
    /** 开火回调 */
    private EnemyFireCallback fireCallback;
    /** 玩家速度（由 GameWorld 每帧注入，用于预判） */
    private final Vector2 targetVelocity = new Vector2();

    /**
     * 构造炮台。
     *
     * @param x            固定位置 X
     * @param y            固定位置 Y
     * @param baseFacing   扫描中心轴角（弧度）
     */
    public Turret(float x, float y, float baseFacing) {
        super(x, y, HP, SPEED, RADIUS, CONTACT_DAMAGE);
        this.baseFacing = baseFacing;
        this.facing = baseFacing;
        this.state = AIState.PATROL;
    }

    /** 设置开火回调（GameWorld 注入） */
    public void setFireCallback(EnemyFireCallback cb) {
        this.fireCallback = cb;
    }

    /**
     * 注入玩家速度向量（每帧调用），用于预判开火。
     * <p>
     * 由于基类 {@link #think(float, Vector2)} 只传位置，预判所需速度通过此 setter 注入。
     */
    public void setTargetVelocity(Vector2 v) {
        targetVelocity.set(v);
    }

    @Override
    protected void think(float dt, Vector2 target) {
        if (fireCooldown > 0f) fireCooldown -= dt;

        float dx = target.x - position.x;
        float dy = target.y - position.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float angleToPlayer = MathUtils.atan2(dy, dx);

        // —— 视野判定：距离 + 角度差 ——
        boolean inSight = dist <= FIRE_RANGE
                && Math.abs(angleDiff(angleToPlayer, facing)) <= HALF_FOV;

        if (inSight) {
            // —— 发现玩家：线性预测开火（第 7 步升级）——
            state = AIState.ATTACK;
            facing = (float) computeAimAngle(target);
            if (fireCooldown <= 0f && fireCallback != null) {
                float mx = position.x + MathUtils.cos(facing) * (RADIUS + 4f);
                float my = position.y + MathUtils.sin(facing) * (RADIUS + 4f);
                fireCallback.fire(mx, my, facing, this);
                fireCooldown = FIRE_COOLDOWN;
            }
        } else {
            // —— 扫描：facing 围绕 baseFacing 摆动 ——
            state = AIState.PATROL;
            facing += scanDir * SCAN_ANGULAR_SPEED * dt;
            float offset = angleDiff(facing, baseFacing);
            if (offset > SCAN_SWING) {
                scanDir = -1;
            } else if (offset < -SCAN_SWING) {
                scanDir = 1;
            }
        }
    }

    /**
     * 精确线性预测：求解子弹与玩家移动的相遇点，返回应瞄准的角度。
     * <p>
     * 推导见类注释。返回的角度满足：沿此角以 S 速发射的子弹，会在 t 秒后命中
     * 以恒速 V 移动的玩家。若无解（追不上），退化为朝玩家当前位置开火。
     *
     * @param target 玩家当前位置
     * @return 应瞄准的角度（弧度）
     */
    private double computeAimAngle(Vector2 target) {
        float bulletSpeed = GameWorld.ENEMY_BULLET_SPEED;
        // D = T - P（炮台指向玩家的向量）
        double dx = target.x - position.x;
        double dy = target.y - position.y;
        double vx = targetVelocity.x;
        double vy = targetVelocity.y;

        double distSq = dx * dx + dy * dy;          // |D|²
        double dDotV = dx * vx + dy * vy;            // D·V
        double vSq = vx * vx + vy * vy;              // |V|²
        double sSq = (double) bulletSpeed * bulletSpeed; // S²

        // 求解 (S² − |V|²) t² − 2(D·V) t − |D|² = 0
        double a = sSq - vSq;
        double b = -2.0 * dDotV;
        double c = -distSq;

        double t = -1; // 无效标记
        final double EPS = 1e-6;
        if (Math.abs(a) < EPS) {
            // a≈0：退化为一次方程 b·t + c = 0 → t = -c/b
            if (Math.abs(b) > EPS) {
                double t0 = -c / b;
                if (t0 > 0) t = t0;
            }
        } else {
            double disc = b * b - 4.0 * a * c;
            if (disc >= 0.0) {
                double sq = Math.sqrt(disc);
                double t1 = (-b + sq) / (2.0 * a);
                double t2 = (-b - sq) / (2.0 * a);
                // 取最小正根
                if (t1 > 0 && t2 > 0) t = Math.min(t1, t2);
                else if (t1 > 0) t = t1;
                else if (t2 > 0) t = t2;
            }
        }

        // 预测命中点；无解时退化为朝当前位置
        double predX, predY;
        if (t > 0) {
            predX = target.x + vx * t;
            predY = target.y + vy * t;
        } else {
            predX = target.x;
            predY = target.y;
        }
        return MathUtils.atan2((float) (predY - position.y), (float) (predX - position.x));
    }

    /**
     * 角度差归一化到 (-π, π]。
     *
     * @return target - current 的最短弧差
     */
    private static float angleDiff(float target, float current) {
        float d = target - current;
        while (d <= -MathUtils.PI) d += MathUtils.PI2;
        while (d > MathUtils.PI) d -= MathUtils.PI2;
        return d;
    }

    @Override
    public String getTypeName() { return "Turret"; }

    @Override
    public void render(ShapeRenderer shapes) {
        boolean flashing = hitInvuln > 0f && (((int) (hitInvuln * 50)) % 2 == 0);
        // 底座：深灰圆（固定）
        shapes.setColor(Color.GRAY);
        shapes.circle(position.x, position.y, radius);
        // 炮管：朝 facing 方向
        shapes.setColor(flashing ? Color.WHITE : Color.RED);
        shapes.line(position.x, position.y,
                position.x + MathUtils.cos(facing) * radius * 1.8f,
                position.y + MathUtils.sin(facing) * radius * 1.8f);
        // 扇形视野可视化（PATROL 时显示半透明锥，便于玩家观察安全区）
        if (state == AIState.PATROL) {
            drawFovCone(shapes);
        }
        // 血条
        renderHpBar(shapes, radius * 2f);
    }

    /**
     * 绘制扇形视野锥（90°），用于玩家观察炮台扫描范围。
     * <p>
     * 用线段近似锥的弧边：从 facing-HALF_FOV 到 facing+HALF_FOV 采样若干条放射线。
     * 仅 PATROL 状态显示（ATTACK 时锁定玩家，无需提示）。
     */
    private void drawFovCone(ShapeRenderer shapes) {
        shapes.setColor(new Color(1f, 0.3f, 0.3f, 0.25f));
        int segs = 8;
        for (int i = 0; i <= segs; i++) {
            float a = facing - HALF_FOV + (2f * HALF_FOV) * (i / (float) segs);
            shapes.line(position.x, position.y,
                    position.x + MathUtils.cos(a) * FIRE_RANGE * 0.5f,
                    position.y + MathUtils.sin(a) * FIRE_RANGE * 0.5f);
        }
    }
}
