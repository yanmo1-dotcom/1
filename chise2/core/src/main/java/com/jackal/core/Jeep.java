package com.jackal.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Jeep —— 玩家吉普车控制器。
 * <p>
 * 《赤色要塞》的核心载具。本类实现：
 * <ul>
 *   <li>WASD 八方向移动，带加速度/摩擦力惯性</li>
 *   <li>鼠标控制炮塔朝向，与车身移动方向解耦（瞄准独立）</li>
 *   <li>用 ShapeRenderer 绘制车身 + 炮塔指示线（绿色方块占位符的升级版）</li>
 * </ul>
 *
 * <h3>物理模型推导</h3>
 * 设当前速度向量 v，输入方向向量 i（单位向量），最大速度 Vmax，加速度 a，摩擦系数 f。
 * <pre>
 *   每帧（dt 为帧时间）：
 *     1) 加速阶段：若玩家有输入，速度趋向输入方向
 *        v += i * a * dt          （冲量叠加）
 *        若 |v| > Vmax，则 v 缩放到 Vmax   （限速，保持方向）
 *
 *     2) 摩擦衰减：无输入或停止按键时，速度逐帧衰减
 *        v *= (1 - f * dt)        （指数衰减，f 越大停得越快）
 *        若 |v| < ε（极小值），直接归零，避免抖动
 *
 *     3) 位置积分：
 *        pos += v * dt
 * </pre>
 * 这种"加速度 + 指数摩擦"模型比直接赋值速度更有"惯性感"：
 * 松开按键后吉普车会滑行一小段，符合吉普车在崎岖地面的真实手感。
 *
 * <h3>手感调参指南</h3>
 * <ul>
 *   <li>{@link #MAX_SPEED}：吉普车极速（像素/秒）。越大移动越快。</li>
 *   <li>{@link #ACCELERATION}：加速度（像素/秒²）。越大起步越猛，越小越"重"。</li>
 *   <li>{@link #FRICTION}：摩擦系数（1/秒，无量纲）。越大松手后滑行越短，越小漂移感越强。</li>
 *   <li>{@link #VELOCITY_EPSILON}：速度归零阈值。过小会导致松手后微抖；过大则显得粘滞。</li>
 * </ul>
 *
 * @author Jackal Dev Team
 */
public class Jeep {

    // ============== 物理参数（调参区） ==============

    /** 最大速度（像素/秒）。FC 原作吉普车移速约 1.5 像素/帧≈90 像素/秒，这里略放大到 220 提升手感 */
    public static final float MAX_SPEED = 220f;

    /** 加速度（像素/秒²）。值越大，从静止到极速越快，操控越"灵敏" */
    public static final float ACCELERATION = 900f;

    /** 摩擦系数（1/秒）。每秒速度衰减比例的近似值；
     *  实际衰减为 v *= (1 - FRICTION * dt)，故 8.0 表示约 1/8 秒衰减殆尽。
     *  越大停车越干脆，越小滑行/漂移感越强。 */
    public static final float FRICTION = 8.0f;

    /** 速度归零阈值（像素/秒）。低于此值直接置零，防止浮点误差导致松手后微抖 */
    public static final float VELOCITY_EPSILON = 5f;

    // ============== 几何尺寸 ==============

    /** 车身半宽（像素）。ShapeRenderer 以中心点为基准绘制 */
    public static final float HALF_WIDTH = 14f;

    /** 车身半高（像素） */
    public static final float HALF_HEIGHT = 10f;

    /** 炮塔指示线长度（像素），从车心指向鼠标方向，直观反映瞄准角度 */
    public static final float TURRET_LINE_LENGTH = 30f;

    // ============== 运行时状态 ==============

    /** 车心世界坐标（像素） */
    private final Vector2 position = new Vector2();

    /** 当前速度向量（像素/秒）。含方向与大小 */
    private final Vector2 velocity = new Vector2();

    /** 车身朝向角度（弧度）。跟随移动方向平滑旋转，0 = 朝右(+x) */
    private float bodyAngle = 0f;

    /** 炮塔瞄准角度（弧度）。由鼠标位置决定，与车身朝向独立 */
    private float turretAngle = 0f;

    /** 渲染辅助：ShapeRenderer 由外部传入生命周期，避免本类管理 OpenGL 资源 */
    private final ShapeRenderer shapes;

    /** 武器系统：管理武器切换、冷却、开火与活跃子弹。由吉普车持有并驱动 */
    private WeaponSystem weaponSystem;

    // ============== 生命系统（第 5 步） ==============

    /** 最大血量。按需求初始 3 */
    public static final int MAX_HP = 3;

    /** 当前血量（>0 存活，≤0 死亡） */
    private int hp = MAX_HP;

    /** 受击无敌帧计时（秒）。>0 期间不再被敌人/子弹扣血，并闪烁渲染 */
    private float hurtInvuln = 0f;

    /** 是否已死亡 */
    private boolean dead = false;

    /**
     * 构造吉普车。
     *
     * @param startX 初始世界坐标 X（像素）
     * @param startY 初始世界坐标 Y（像素）
     * @param shapes 外部管理的 ShapeRenderer（用于绘制矢量图形）
     */
    public Jeep(float startX, float startY, ShapeRenderer shapes) {
        this.position.set(startX, startY);
        this.shapes = shapes;
        // 创建武器系统：对象池预分配 64，上限 512 防失控
        this.weaponSystem = new WeaponSystem(new BulletPool(64, 512));
    }

    /**
     * 每帧更新：读取输入 → 积分物理 → 更新朝向。
     * <p>
     * 必须在 render 之前调用，保证本帧位置为最新。
     *
     * @param dt 距上一帧的时间间隔（秒），用 Gdx.graphics.getDeltaTime() 获取
     */
    public void update(float dt) {
        // ---------- 1. 读取 WASD 输入，构造方向向量 ----------
        // 用 int 累加得到 -1/0/1 三态，天然支持两键同按抵消（如 A+D 同时按=不动）
        int ix = 0, iy = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) ix -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) ix += 1;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) iy -= 1;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) iy += 1;

        // ---------- 2. 加速度积分 ----------
        if (ix != 0 || iy != 0) {
            // 对角线移动若直接用 (1,1) 会导致实际速度 √2 倍于直线，需要归一化。
            // Vector2.set().nor() 完成归一化：得到单位方向向量。
            Vector2 dir = new Vector2(ix, iy).nor();

            // 冲量叠加：v += dir * a * dt
            velocity.x += dir.x * ACCELERATION * dt;
            velocity.y += dir.y * ACCELERATION * dt;

            // 限速：若速度超过 MAX_SPEED，按方向缩放到上限，保留朝向不"瞬移减速"
            if (velocity.len() > MAX_SPEED) {
                velocity.setLength(MAX_SPEED);
            }

            // 车身朝向平滑跟随移动方向。
            // 使用线性插值 lerpAngle 避免角度跨越 ±π 时出现"反转一圈"的突变。
            float moveAngle = velocity.angleRad();
            bodyAngle = lerpAngle(bodyAngle, moveAngle, Math.min(1f, 10f * dt));
        } else {
            // ---------- 3. 无输入：摩擦力衰减 ----------
            // 指数衰减：v *= (1 - f * dt)。dt 越大衰减越多，保证帧率无关。
            // 注意 f*dt 不应超过 1，否则速度反向；FRICTION=8 在 60fps 时 dt≈0.016，乘积≈0.13 安全。
            float damp = 1f - FRICTION * dt;
            if (damp < 0f) damp = 0f; // 极端低帧率保护
            velocity.scl(damp);

            // 速度过小直接归零，避免松手后因浮点残留持续微移抖动
            if (velocity.len() < VELOCITY_EPSILON) {
                velocity.setZero();
            }
        }

        // ---------- 4. 位置积分 ----------
        // pos += v * dt。这是欧拉积分，简单稳定，对吉普车这种低速载具足够。
        // 若日后做高速抛射物，可改用半隐式欧拉或 RK4 提升精度。
        position.x += velocity.x * dt;
        position.y += velocity.y * dt;

        // ---------- 5. 炮塔瞄准：由鼠标屏幕坐标反推世界坐标 ----------
        // Gdx.input.getX/Y 返回屏幕坐标（原点左上，y 向下）。
        // 通过相机 unproject 转成世界坐标（原点左下，y 向上），才能与吉普车坐标系一致。
        // 这里通过外部设置 turretAngle 实现（见 setTurretAngleFromMouse），
        // 也可在此直接传入相机；为解耦，本类不持有相机引用。

        // ---------- 6. 武器系统更新 ----------
        // 瞄准角度（turretAngle）由外部 aimAt 设置后，把开火委托给武器系统。
        // 武器系统读取鼠标左键（开火）与空格（切换），并推进所有活跃子弹。
        weaponSystem.update(dt, position.x, position.y, turretAngle);

        // ---------- 7. 受击无敌帧推进 ----------
        if (hurtInvuln > 0f) hurtInvuln -= dt;
    }

    // ============== 生命系统接口 ==============

    /**
     * 吉普车受击扣血。
     * <p>
     * 受无敌帧保护期间返回 false，避免持续接触伤害每帧扣血。
     * 死亡后不再扣血。扣血后进入 1.2 秒无敌帧。
     *
     * @param amount 伤害值
     * @return true 表示本次扣血生效
     */
    public boolean takeDamage(float amount) {
        if (dead || hurtInvuln > 0f) return false;
        hp -= (int) Math.ceil(amount);
        if (hp <= 0) {
            hp = 0;
            dead = true;
            // 死亡音效（仅在本帧首次进入死亡时播放一次）
            if (AudioManager.get() != null) AudioManager.get().playSfx("gameover");
        }
        hurtInvuln = 1.2f;
        com.badlogic.gdx.Gdx.app.log("Jeep", "受击 -" + amount + "，剩余血量 " + hp + (dead ? "（阵亡）" : ""));
        return true;
    }

    /** @return 当前血量 */
    public int getHp() {
        return hp;
    }

    /** @return 最大血量 */
    public int getMaxHp() {
        return MAX_HP;
    }

    /** @return 是否已死亡 */
    public boolean isDead() {
        return dead;
    }

    /** @return 受击无敌帧剩余时间（秒），>0 期间渲染闪烁 */
    public float getHurtInvuln() {
        return hurtInvuln;
    }

    /**
     * 重置吉普车状态用于关卡重启：回满血、清死亡与无敌帧。
     * <p>
     * 位置与速度由 GameWorld.restartLevel() 单独设置（需地图尺寸）。
     */
    public void resetForRestart() {
        hp = MAX_HP;
        dead = false;
        hurtInvuln = 0f;
    }

    /**
     * 根据鼠标屏幕坐标计算炮塔瞄准角度。
     * <p>
     * 由于本类不持有相机引用（保持解耦），由调用方完成 unproject 后传入鼠标世界坐标。
     *
     * @param mouseWorldX 鼠标在世界坐标系中的 X
     * @param mouseWorldY 鼠标在世界坐标系中的 Y
     */
    public void aimAt(float mouseWorldX, float mouseWorldY) {
        // atan2(dy, dx) 给出从车心指向鼠标的方位角（弧度），范围 (-π, π]
        turretAngle = MathUtils.atan2(mouseWorldY - position.y, mouseWorldX - position.x);
    }

    /**
     * 用 ShapeRenderer 绘制吉普车：车身矩形 + 炮塔指示线。
     * <p>
     * 调用前外部须已 shapes.begin(ShapeType.Filled/Line) 并设置投影矩阵。
     *
     * @param unusedBatch 保留参数（协议要求 render(SpriteBatch) 签名）；
     *                    矢量绘制用 ShapeRenderer，本参数暂不使用，后续替换纹理时改用 SpriteBatch
     */
    public void render(com.badlogic.gdx.graphics.g2d.SpriteBatch unusedBatch) {
        // —— 受击闪烁：无敌帧期间交替绿色/红色，提供受击反馈 ——
        boolean hurt = hurtInvuln > 0f && (((int) (hurtInvuln * 12)) % 2 == 0);
        Color bodyColor = hurt ? Color.RED : Color.GREEN;
        // 死亡时变灰
        if (dead) bodyColor = Color.GRAY;

        // —— 车身：旋转的填充矩形 ——
        // ShapeRenderer 的 rect(x,y,ox,oy,w,h,sx,sy,deg) 以 (ox,oy) 为旋转中心。
        // 这里让车身以车心为中心旋转到 bodyAngle（弧度转度，LibGDX 绘图用度）。
        shapes.setColor(bodyColor);
        shapes.rect(
                position.x - HALF_WIDTH,        // 矩形左下角 X
                position.y - HALF_HEIGHT,       // 矩形左下角 Y
                HALF_WIDTH, HALF_HEIGHT,        // 旋转中心相对矩形左下角的偏移（即矩形中心）
                HALF_WIDTH * 2, HALF_HEIGHT * 2,// 宽高
                1f, 1f,                         // 缩放
                bodyAngle * MathUtils.radDeg    // 旋转角度（度）
        );

        // —— 炮塔指示线：从车心指向瞄准方向 ——
        // 用亮黄色线条直观区分"瞄准"与"移动"两个独立朝向。
        shapes.setColor(Color.YELLOW);
        shapes.line(
                position.x, position.y,
                position.x + MathUtils.cos(turretAngle) * TURRET_LINE_LENGTH,
                position.y + MathUtils.sin(turretAngle) * TURRET_LINE_LENGTH
        );

        // —— 车心标记点：小白点，便于观察位置 ——
        shapes.setColor(Color.WHITE);
        shapes.circle(position.x, position.y, 2f);
    }

    // ============== 访问器 ==============

    /** @return 车心世界坐标（只读视图，勿直接修改） */
    public Vector2 getPosition() {
        return position;
    }

    /** @return 当前速度向量（像素/秒） */
    public Vector2 getVelocity() {
        return velocity;
    }

    /** @return 车身朝向（弧度） */
    public float getBodyAngle() {
        return bodyAngle;
    }

    /** @return 炮塔瞄准角（弧度） */
    public float getTurretAngle() {
        return turretAngle;
    }

    /** @return 武器系统引用（供外部渲染子弹、读取 HUD 信息） */
    public WeaponSystem getWeaponSystem() {
        return weaponSystem;
    }

    /**
     * 渲染所有活跃子弹。调用前外部须已 shapes.begin(Filled) 并设置投影矩阵。
     */
    public void renderWeapons() {
        weaponSystem.render(shapes);
    }

    /** 释放武器系统资源（对象池清理） */
    public void disposeWeapons() {
        weaponSystem.getPool().clear();
    }

    // ============== 工具方法 ==============

    /**
     * 角度线性插值（处理 ±π 跨越）。
     * <p>
     * 普通线性插值在角度从 +3.0 到 -3.0（实际只差 0.28 弧度）时会"绕远路"转一整圈。
     * 本方法先把差值归一化到 (-π, π]，再插值，保证总是走最短弧。
     *
     * @param current 当前角度（弧度）
     * @param target  目标角度（弧度）
     * @param t       插值因子 [0,1]，越大转向越快
     * @return 插值后的角度（弧度）
     */
    private static float lerpAngle(float current, float target, float t) {
        float diff = target - current;
        // 把差值卷绕到 (-π, π]
        while (diff <= -MathUtils.PI) diff += MathUtils.PI2;
        while (diff > MathUtils.PI) diff -= MathUtils.PI2;
        return current + diff * t;
    }
}
