package com.jackal.core;

import com.badlogic.gdx.utils.Array;

/**
 * RocketState —— 火箭弹状态。
 * <p>
 * 直线穿透弹道：不因命中敌人而消失（穿透），仅由边界或寿命回收。
 * 高伤害、中频。
 * <p>
 * 参数（按需求）：冷却 0.8 秒。
 *
 * @author Jackal Dev Team
 */
public class RocketState implements WeaponState {

    /** 冷却时间（秒）。0.8 秒/发，介于机枪与手雷之间 */
    private static final float COOLDOWN = 0.8f;

    /** 单发伤害。火箭弹威力大，单发高伤 */
    private static final float DAMAGE = 60f;

    /** 飞行速度（像素/秒）。比机枪子弹略慢，便于观察弹道 */
    private static final float BULLET_SPEED = 480f;

    /** 寿命（秒）。穿透弹种靠寿命兜底，防止飞出边界前一直存在 */
    private static final float BULLET_LIFE = 2.0f;

    /** 半径（像素）。火箭弹稍大，视觉醒目 */
    private static final float BULLET_RADIUS = 4.5f;

    @Override
    public void fire(float originX, float originY, float angleRad,
                     BulletPool pool, Array<Bullet> activeBullets) {
        Bullet b = pool.obtain();
        b.initLine(Bullet.Type.ROCKET, originX, originY, angleRad,
                BULLET_SPEED, DAMAGE, BULLET_LIFE, BULLET_RADIUS);
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
        return "火箭弹";
    }

    @Override
    public Bullet.Type getBulletType() {
        return Bullet.Type.ROCKET;
    }
}
