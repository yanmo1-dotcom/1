package com.jackal.core;

import com.badlogic.gdx.utils.Array;

/**
 * GrenadeState —— 手雷状态。
 * <p>
 * 抛物线弹道，落地后 AOE 伤害（AOE 命中逻辑在第 5 步敌人接入后补完）。
 * <p>
 * 参数（按需求）：冷却 1.2 秒。
 *
 * <h3>抛物线参数选择推导</h3>
 * 落地时间 t_land = 2 * v0z / g，水平射程 R = v_h * t_land。
 * 取 v0z = 320 px/s、g = 900 px/s²、水平速度 v_h = 260 px/s：
 * <pre>
 *   t_land = 2 * 320 / 900 ≈ 0.711 秒
 *   R = 260 * 0.711 ≈ 185 像素
 * </pre>
 * 即手雷约飞 0.7 秒、落点距发射点约 185 像素，手感适中、便于玩家预判落点。
 *
 * @author Jackal Dev Team
 */
public class GrenadeState implements WeaponState {

    /** 冷却时间（秒）。手雷威力大，冷却 1.2 秒限制频率 */
    private static final float COOLDOWN = 1.2f;

    /** 单发伤害（AOE 范围内每名敌人承受此伤害） */
    private static final float DAMAGE = 45f;

    /** 水平速度（像素/秒） */
    private static final float HORIZONTAL_SPEED = 260f;

    /** 初始竖直速度 v0z（像素/秒）。决定滞空时间与抛物线高度 */
    private static final float V0Z = 320f;

    /** 重力加速度 g（像素/秒²） */
    private static final float GRAVITY = 900f;

    /** 手雷半径（像素） */
    private static final float BULLET_RADIUS = 4f;

    @Override
    public void fire(float originX, float originY, float angleRad,
                     BulletPool pool, Array<Bullet> activeBullets) {
        Bullet b = pool.obtain();
        b.initGrenade(originX, originY, angleRad, HORIZONTAL_SPEED,
                DAMAGE, V0Z, GRAVITY, BULLET_RADIUS);
        activeBullets.add(b);
    }

    @Override
    public float getCooldown() {
        return COOLDOWN;
    }

    @Override
    public float getDamage() {
        return DAMAGE;
    }

    @Override
    public String getName() {
        return "手雷";
    }

    @Override
    public Bullet.Type getBulletType() {
        return Bullet.Type.GRENADE;
    }
}
