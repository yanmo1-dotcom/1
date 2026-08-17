package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Bullet —— 统一弹射物实体（机枪子弹 / 手雷 / 火箭弹共用此类）。
 * <p>
 * 通过 {@link #type} 区分弹道行为与外观。采用对象池（{@link BulletPool}）复用，
 * 避免高频开火时反复 new 造成 GC 卡顿。
 *
 * <h3>三种弹道模型推导</h3>
 *
 * <h4>1. 机枪（直线匀速）</h4>
 * 子弹不受重力，沿初始方向匀速直线运动：
 * <pre>
 *   pos += vel * dt          （vel 为常向量，方向 = 炮塔瞄准角）
 * </pre>
 *
 * <h4>2. 手雷（抛物线）</h4>
 * 在 2D 俯视/斜视角下，手雷的"抛物线"用一个独立的"高度 z"模拟腾空效果，
 * 水平方向（x,y）仍匀速飞行，z 方向受重力做竖直上抛运动：
 * <pre>
 *   水平：pos.xy += vel.xy * dt
 *   竖直（腾空高度）：
 *     z(t) = v0z * t - 0.5 * g * t²        —— 标准竖直上抛公式
 *     其中 v0z 为初始竖直速度，g 为重力加速度，t 为自发射起的累计时间。
 *   当 z(t) 再次回落到 0 时，手雷落地，触发 AOE 爆炸并回收。
 *
 *   推导：由 z(t)=0（除 t=0 外）解得落地时间 t_land = 2 * v0z / g。
 *   故射程 = |vel.xy| * t_land。调 v0z 可控制滞空时间与射程。
 * </pre>
 * 渲染时用 z 给手雷一个"影子 + 抬升"的视觉：本体按 (x, y+z) 绘制，影子留在 (x, y)。
 *
 * <h4>3. 火箭弹（直线穿透）</h4>
 * 与机枪同为直线匀速，但不命中即不消失（穿透），仅受边界/最大寿命限制：
 * <pre>
 *   pos += vel * dt
 *   life -= dt；life ≤ 0 时回收
 * </pre>
 *
 * <h3>边界回收</h3>
 * 任何弹射物飞出世界边界时必须调用 {@code pool.free(this)} 回收到池，
 * 否则会无限累积导致内存泄漏。回收由 {@link #update(float, BulletPool)} 内部判定，
 * 返回 true 表示已被回收（调用方应从活跃列表移除）。
 *
 * @author Jackal Dev Team
 */
public class Bullet {

    /** 弹射物类型，决定弹道与外观 */
    public enum Type {
        /** 机枪子弹：直线、低伤害、高频 */
        MACHINE_GUN,
        /** 手雷：抛物线、落地 AOE、中伤害 */
        GRENADE,
        /** 火箭弹：直线穿透、高伤害 */
        ROCKET,
        /** Boss 扇形子弹：直线、中伤害（阶段1） */
        BOSS_BULLET,
        /** Boss 追踪导弹：直线追踪玩家、高伤害（阶段2） */
        BOSS_MISSILE,
        /** Boss 激光段：直线高速、持续伤害（阶段3，由 LaserBeam 主管，此处备用） */
        BOSS_LASER
    }

    // ============== 运行时状态 ==============

    /** 当前弹射物类型 */
    public Type type = Type.MACHINE_GUN;

    /** 世界坐标位置（像素）。手雷的水平位置 */
    public final Vector2 position = new Vector2();

    /** 水平速度向量（像素/秒）。对直线弹种即完整速度；手雷仅水平分量 */
    public final Vector2 velocity = new Vector2();

    /** 伤害值（命中敌人时扣除的血量） */
    public float damage = 10f;

    /** 半径（像素），用于绘制大小与碰撞判定 */
    public float radius = 3f;

    // ===== 渲染用静态颜色常量（避免每帧 new Color 产生 GC）=====
    /** 手雷影子色（半透明黑） */
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 0.35f);
    /** Boss 导弹拖尾色 */
    private static final Color MISSILE_TRAIL_COLOR = new Color(1f, 0.6f, 0.8f, 0.6f);

    /** 剩余寿命（秒）。≤0 时回收。直线弹种用大值，手雷用落地判定为主 */
    public float life = 5f;

    // —— 手雷专属 ——
    /** 手雷腾空高度 z（像素）。仅 GRENADE 使用，>0 表示在空中 */
    public float z = 0f;

    /** 手雷初始竖直速度 v0z（像素/秒）。决定滞空时间 */
    public float vz = 0f;

    /** 重力加速度 g（像素/秒²）。仅 GRENADE 使用 */
    public float gravity = 0f;

    /** 手雷自发射累计时间 t（秒），用于 z(t) 计算 */
    public float airTime = 0f;

    /** 追踪导弹的目标位置（世界坐标）。仅 BOSS_MISSILE 使用，由 GameWorld 每帧更新 */
    public final Vector2 target = new Vector2();

    /** 追踪导弹转向角速度（弧度/秒）。越大跟得越紧 */
    public static final float MISSILE_TURN_RATE = 3.0f;

    /** 是否存活（在活跃列表中）。false 表示已被回收或未激活 */
    public boolean active = false;

    // ============== 池接口 ==============

    /**
     * 重置为初始默认状态，供 Pool.obtain() 取出时复用前清理脏数据。
     * 由 {@link BulletPool#newObject()} 与 {@link BulletPool#free(Bullet)} 调用。
     */
    public void reset() {
        position.setZero();
        velocity.setZero();
        target.setZero();
        z = 0f;
        vz = 0f;
        gravity = 0f;
        airTime = 0f;
        life = 5f;
        damage = 10f;
        radius = 3f;
        active = false;
    }

    // ============== 初始化（开火时由状态类调用） ==============

    /**
     * 初始化直线弹种（机枪/火箭）。
     *
     * @param type     弹种
     * @param startX   发射点 X
     * @param startY   发射点 Y
     * @param angleRad 速度方向角（弧度）
     * @param speed    速度大小（像素/秒）
     * @param damage   伤害
     * @param life     寿命（秒）
     * @param radius   半径
     */
    public void initLine(Type type, float startX, float startY, float angleRad,
                         float speed, float damage, float life, float radius) {
        this.type = type;
        this.position.set(startX, startY);
        this.velocity.set(MathUtils.cos(angleRad) * speed, MathUtils.sin(angleRad) * speed);
        this.damage = damage;
        this.life = life;
        this.radius = radius;
        this.z = 0f;
        this.vz = 0f;
        this.gravity = 0f;
        this.airTime = 0f;
        this.active = true;
    }

    /**
     * 初始化手雷（抛物线弹道）。
     *
     * @param startX    发射点 X
     * @param startY    发射点 Y
     * @param angleRad  水平方向角（弧度）
     * @param speed     水平速度（像素/秒）
     * @param damage    伤害
     * @param v0z       初始竖直速度（像素/秒），越大滞空越久射程越远
     * @param g         重力加速度（像素/秒²），越大落得越快
     * @param radius    半径
     */
    public void initGrenade(float startX, float startY, float angleRad, float speed,
                            float damage, float v0z, float g, float radius) {
        this.type = Type.GRENADE;
        this.position.set(startX, startY);
        this.velocity.set(MathUtils.cos(angleRad) * speed, MathUtils.sin(angleRad) * speed);
        this.damage = damage;
        this.life = 10f; // 手雷以落地判定为主，寿命给宽裕上限防异常
        this.radius = radius;
        this.z = 0f;
        this.vz = v0z;
        this.gravity = g;
        this.airTime = 0f;
        this.active = true;
    }

    // ============== 更新 ==============

    /**
     * 每帧推进弹道，并判定边界/落地/寿命，必要时回收到池。
     *
     * @param dt   帧时间（秒）
     * @param pool 对象池引用，用于回收
     * @return true 表示本弹射物已结束（被回收），调用方应从活跃列表移除
     */
    public boolean update(float dt, BulletPool pool) {
        // —— 追踪导弹：每帧转向目标，调整速度方向（大小不变）——
        // 仅 BOSS_MISSILE 执行。导弹速度向量 velocity 朝目标方向逐帧旋转 MISSILE_TURN_RATE*dt 弧度。
        if (type == Type.BOSS_MISSILE) {
            float desiredAng = MathUtils.atan2(target.y - position.y, target.x - position.x);
            float curAng = MathUtils.atan2(velocity.y, velocity.x);
            // 角度差归一化到 (-π,π]
            float diff = desiredAng - curAng;
            while (diff <= -MathUtils.PI) diff += MathUtils.PI2;
            while (diff > MathUtils.PI) diff -= MathUtils.PI2;
            // 本帧最多转 MISSILE_TURN_RATE*dt 弧度，方向取最短弧
            float maxTurn = MISSILE_TURN_RATE * dt;
            float turn = Math.abs(diff) < maxTurn ? diff : Math.signum(diff) * maxTurn;
            float newAng = curAng + turn;
            float speed = velocity.len();
            velocity.set(MathUtils.cos(newAng) * speed, MathUtils.sin(newAng) * speed);
        }

        // 水平位置积分（所有弹种通用）
        position.x += velocity.x * dt;
        position.y += velocity.y * dt;

        if (type == Type.GRENADE) {
            // —— 手雷抛物线：更新腾空高度 z ——
            airTime += dt;
            // z(t) = v0z * t - 0.5 * g * t²
            z = vz * airTime - 0.5f * gravity * airTime * airTime;

            // 落地判定：z 回落到 0 以下表示落地，触发 AOE 后回收
            if (z <= 0f && airTime > 0f) {
                z = 0f;
                // 落地爆炸音效（通过 AudioManager 单例播放）
                if (AudioManager.get() != null) AudioManager.get().playSfx("grenade");
                // TODO 第 5 步接入敌人后，在此对落点周围敌人造成 AOE 伤害
                pool.free(this);
                return true;
            }
        } else {
            // —— 直线弹种：寿命倒计时 ——
            life -= dt;
            if (life <= 0f) {
                pool.free(this);
                return true;
            }
        }

        // —— 边界回收：飞出世界范围则回收 ——
        // 边界由调用方通过 setBounds 设定；此处用静态字段存储当前世界边界。
        if (position.x < BOUNDS_X || position.x > BOUNDS_X + BOUNDS_W
                || position.y < BOUNDS_Y || position.y > BOUNDS_Y + BOUNDS_H) {
            pool.free(this);
            return true;
        }
        return false;
    }

    // ============== 渲染 ==============

    /**
     * 用 ShapeRenderer 绘制弹射物。调用前外部须已 begin 并设置投影矩阵。
     * <p>
     * 手雷会额外绘制地面影子，增强抛物线的空间感。
     *
     * @param shapes 外部管理的 ShapeRenderer
     * @param filled 是否用 Filled 模式（车身 pass 会先 Filled 再 Line，子弹只用 Filled）
     */
    public void render(ShapeRenderer shapes, boolean filled) {
        switch (type) {
            case MACHINE_GUN:
                // 机枪子弹：小黄色圆点
                shapes.setColor(Color.YELLOW);
                shapes.circle(position.x, position.y, radius);
                break;
            case GRENADE:
                // 手雷本体：按腾空高度抬升绘制（绿色圆）
                shapes.setColor(Color.LIME);
                shapes.circle(position.x, position.y + z, radius);
                // 影子：留在地面，随高度变淡变小（半透明深色）
                if (filled && z > 0f) {
                    shapes.setColor(SHADOW_COLOR);
                    shapes.circle(position.x, position.y, radius * 0.8f);
                }
                break;
            case ROCKET:
                // 火箭弹：橙红色稍大圆 + 朝向拖尾线
                shapes.setColor(Color.ORANGE);
                shapes.circle(position.x, position.y, radius);
                break;
            case BOSS_BULLET:
                // Boss 扇形子弹：紫红色圆点（与玩家黄色区分）
                shapes.setColor(Color.PURPLE);
                shapes.circle(position.x, position.y, radius);
                break;
            case BOSS_MISSILE:
                // Boss 追踪导弹：亮粉色稍大圆 + 拖尾线
                shapes.setColor(Color.PINK);
                shapes.circle(position.x, position.y, radius);
                shapes.setColor(MISSILE_TRAIL_COLOR);
                shapes.circle(position.x - velocity.x * 0.02f,
                        position.y - velocity.y * 0.02f, radius * 0.7f);
                break;
            case BOSS_LASER:
                // 激光段：亮青色小圆（主体由 LaserBeam 绘制，此处为弹射物形态备用）
                shapes.setColor(Color.CYAN);
                shapes.circle(position.x, position.y, radius);
                break;
        }
    }

    // ============== 世界边界（静态，由游戏主类设置） ==============

    /** 当前世界边界 X（左下角） */
    public static float BOUNDS_X = 0f;
    /** 当前世界边界 Y（左下角） */
    public static float BOUNDS_Y = 0f;
    /** 当前世界边界宽 */
    public static float BOUNDS_W = 512f;
    /** 当前世界边界高 */
    public static float BOUNDS_H = 480f;

    /**
     * 设置弹射物边界检测用的世界范围（超出即回收）。
     * 由游戏主类在 create/resize 时调用。给一定余量，避免子弹刚出屏幕边缘就消失显得突兀。
     *
     * @param x      边界左下角 X
     * @param y      边界左下角 Y
     * @param w      边界宽
     * @param h      边界高
     */
    public static void setBounds(float x, float y, float w, float h) {
        BOUNDS_X = x;
        BOUNDS_Y = y;
        BOUNDS_W = w;
        BOUNDS_H = h;
    }
}
