package com.jackal.core;

/**
 * EnemyFireCallback —— 敌人开火回调。
 * <p>
 * 函数式接口。敌人（Tank/Turret）需要发射子弹时调用此回调，由 {@link GameWorld}
 * 统一从敌人子弹池取出 Bullet、初始化并加入敌人活跃子弹列表。
 * <p>
 * 这样敌人类不直接持有子弹池/列表，保持解耦，也保证敌人子弹与玩家子弹
 * 使用各自独立的对象池实例（互不干扰容量与回收）。
 *
 * @param originX  发射点 X
 * @param originY  发射点 Y
 * @param angleRad 速度方向角（弧度）
 * @param shooter  发射者引用（用于避免命中自己；当前简化未用，预留扩展）
 *
 * @author Jackal Dev Team
 */
@FunctionalInterface
public interface EnemyFireCallback {
    void fire(float originX, float originY, float angleRad, Enemy shooter);
}
