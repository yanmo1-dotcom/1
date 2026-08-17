package com.jackal.core;

/**
 * WeaponState —— 武器状态接口（状态模式）。
 * <p>
 * 每种武器（机枪/手雷/火箭弹）是一个独立状态类，封装各自的：
 * <ul>
 *   <li>射速冷却（cooldown）</li>
 *   <li>伤害值</li>
 *   <li>弹道特性（直线/抛物线/穿透）</li>
 *   <li>开火时如何从对象池取子弹并初始化</li>
 * </ul>
 * {@link WeaponSystem} 持有一个当前状态引用，把开火与更新委托给当前状态，
 * 切换武器只需替换状态对象，符合"开闭原则"。
 *
 * <h3>状态模式职责划分</h3>
 * <pre>
 *   WeaponSystem（上下文）：持有当前状态、全局冷却计时、活跃子弹列表、对象池
 *   WeaponState（状态）：   定义单次开火行为（取池中子弹 → 初始化 → 加入列表）
 * </pre>
 *
 * @author Jackal Dev Team
 */
public interface WeaponState {

    /**
     * 执行一次开火。
     * <p>
     * 由 {@link WeaponSystem#tryFire} 在冷却结束时调用。实现内应：
     * <ol>
     *   <li>从 {@code pool.obtain()} 取一个 Bullet</li>
     *   <li>调用 initLine/initGrenade 设置弹道参数</li>
     *   <li>把 Bullet 加入 {@code activeBullets} 列表</li>
     * </ol>
     *
     * @param originX       发射点 X（通常是炮口/车心）
     * @param originY       发射点 Y
     * @param angleRad      瞄准角度（弧度）
     * @param pool          弹射物对象池
     * @param activeBullets 活跃子弹列表（Array），新子弹加入其中
     */
    void fire(float originX, float originY, float angleRad,
              BulletPool pool, com.badlogic.gdx.utils.Array<Bullet> activeBullets);

    /** @return 该武器的开火冷却时间（秒），即两发之间的最小间隔 */
    float getCooldown();

    /** @return 该武器单发伤害 */
    float getDamage();

    /** @return 武器显示名称（用于 HUD） */
    String getName();

    /** @return 对应的弹射物类型，用于外观区分 */
    Bullet.Type getBulletType();
}
