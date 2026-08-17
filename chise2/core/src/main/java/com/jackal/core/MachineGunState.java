package com.jackal.core;

import com.badlogic.gdx.utils.Array;

/**
 * MachineGunState —— 机枪状态。
 * <p>
 * 默认武器。高频、低伤害、直线匀速弹道。
 * <p>
 * 参数（按需求）：冷却 0.15 秒/发。
 *
 * @author Jackal Dev Team
 */
public class MachineGunState implements WeaponState {

    /** 冷却时间（秒）。0.15 秒/发 ≈ 6.67 发/秒 */
    private static final float COOLDOWN = 0.15f;

    /** 单发伤害 */
    private static final float DAMAGE = 8f;

    /** 子弹速度（像素/秒）。机枪子弹速度快，命中反馈即时 */
    private static final float BULLET_SPEED = 600f;

    /** 子弹寿命（秒）。直线弹种靠寿命+边界双重回收 */
    private static final float BULLET_LIFE = 1.2f;

    /** 子弹半径（像素） */
    private static final float BULLET_RADIUS = 2.5f;

    @Override
    public void fire(float originX, float originY, float angleRad,
                     BulletPool pool, Array<Bullet> activeBullets) {
        // 从池中取一个子弹并初始化为直线弹道
        Bullet b = pool.obtain();
        b.initLine(Bullet.Type.MACHINE_GUN, originX, originY, angleRad,
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
        return "机枪";
    }

    @Override
    public Bullet.Type getBulletType() {
        return Bullet.Type.MACHINE_GUN;
    }
}
