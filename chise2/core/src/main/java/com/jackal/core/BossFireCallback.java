package com.jackal.core;

/**
 * BossFireCallback —— Boss 专用开火回调（带子弹类型）。
 * <p>
 * 与 {@link EnemyFireCallback} 区别：Boss 需要发射不同类型弹药
 * （BOSS_BULLET 扇形弹 / BOSS_MISSILE 追踪导弹），
 * GameWorld 按类型设置不同的速度/伤害/追踪目标。
 *
 * @param originX   发射点 X
 * @param originY   发射点 Y
 * @param angleRad  速度方向角（弧度）
 * @param bulletType 子弹类型（BOSS_BULLET 或 BOSS_MISSILE）
 *
 * @author Jackal Dev Team
 */
@FunctionalInterface
public interface BossFireCallback {
    void fire(float originX, float originY, float angleRad, Bullet.Type bulletType);
}
