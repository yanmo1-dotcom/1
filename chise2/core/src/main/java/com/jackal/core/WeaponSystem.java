package com.jackal.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Array;

/**
 * WeaponSystem —— 武器系统（状态模式的上下文 Context）。
 * <p>
 * 职责：
 * <ul>
 *   <li>持有当前武器状态 {@link WeaponState}，把开火行为委托给它</li>
 *   <li>管理全局冷却计时（两发之间的最小间隔由当前状态的 cooldown 决定）</li>
 *   <li>按空格键循环切换武器：机枪 → 手雷 → 火箭弹 → 机枪 …</li>
 *   <li>维护活跃子弹列表，每帧推进并回收已结束的子弹</li>
 *   <li>提供渲染入口与当前武器名（供 HUD 显示）</li>
 * </ul>
 *
 * <h3>冷却机制</h3>
 * <pre>
 *   每帧：cooldownTimer -= dt
 *   开火条件：鼠标左键按下 且 cooldownTimer ≤ 0
 *   开火后：cooldownTimer = current.getCooldown()（重置冷却）
 * </pre>
 * 这样按住左键即可持续按射速自动开火，松开则停火。
 *
 * <h3>切换武器</h3>
 * 用 {@link Input#isKeyJustPressed} 监听空格，避免长按时一帧切多次。
 *
 * @author Jackal Dev Team
 */
public class WeaponSystem {

    /** 武器状态循环表：切换时按下标取下一个 */
    private static final WeaponState[] STATES = new WeaponState[]{
            new MachineGunState(),
            new GrenadeState(),
            new RocketState()
    };

    /** 当前状态在 STATES 中的下标 */
    private int currentIndex = 0;

    /** 已解锁的最高等级下标（0=机枪,1=手雷,2=火箭弹）。
     *  空格切换只能在 [0, unlockedLevel] 范围内循环；upgrade() 提升此值。 */
    private int unlockedLevel = 0;

    /** 当前武器状态 */
    private WeaponState current = STATES[0];

    /** 当前冷却剩余时间（秒）。≤0 表示可开火 */
    private float cooldownTimer = 0f;

    /** 弹射物对象池 */
    private final BulletPool pool;

    /** 活跃子弹列表。用 Array 而非 ArrayList，LibGDX 原生集合避免装箱、迭代开销低 */
    private final Array<Bullet> activeBullets = new Array<>(false, 64);

    /** 发射点偏移：从车心沿瞄准方向前移若干像素，模拟"炮口"位置 */
    private static final float MUZZLE_OFFSET = 16f;

    /**
     * 构造武器系统。
     *
     * @param pool 弹射物对象池（由外部统一创建并 dispose）
     */
    public WeaponSystem(BulletPool pool) {
        this.pool = pool;
    }

    /**
     * 每帧更新：处理切换/开火输入、推进冷却、更新所有活跃子弹。
     *
     * @param dt        帧时间（秒）
     * @param originX   发射点 X（车心）
     * @param originY   发射点 Y（车心）
     * @param angleRad  瞄准角度（弧度）
     */
    public void update(float dt, float originX, float originY, float angleRad) {
        // —— 1. 切换武器（空格）：只在已解锁范围内循环 ——
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            currentIndex = (currentIndex + 1) % (unlockedLevel + 1);
            current = STATES[currentIndex];
            // 切换时清空冷却，允许立即用新武器开火
            cooldownTimer = 0f;
            Gdx.app.log("Weapon", "切换武器 → " + current.getName()
                    + "（冷却 " + current.getCooldown() + "s，伤害 " + current.getDamage() + "）");
        }

        // —— 2. 冷却推进 ——
        if (cooldownTimer > 0f) {
            cooldownTimer -= dt;
        }

        // —— 3. 开火（鼠标左键） ——
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && cooldownTimer <= 0f) {
            // 炮口位置：车心沿瞄准方向前移 MUZZLE_OFFSET，避免子弹从车体内冒出
            float mx = originX + (float) Math.cos(angleRad) * MUZZLE_OFFSET;
            float my = originY + (float) Math.sin(angleRad) * MUZZLE_OFFSET;
            current.fire(mx, my, angleRad, pool, activeBullets);
            cooldownTimer = current.getCooldown();
            // —— 开火音效：按当前武器类型选 SFX ——
            playFireSfx();
        }

        // —— 4. 更新所有活跃子弹，回收已结束的 ——
        // 倒序遍历，便于边遍历边删除（removeIndex O(1) 且不跳元素）
        for (int i = activeBullets.size - 1; i >= 0; i--) {
            Bullet b = activeBullets.get(i);
            if (b.update(dt, pool)) {
                // update 返回 true 表示已被回收（边界/落地/寿命），从列表移除
                activeBullets.removeIndex(i);
            }
        }
    }

    /**
     * 渲染所有活跃子弹。
     * <p>
     * 调用前外部须已 shapes.begin 并设置投影矩阵。本方法只在 Filled 模式下绘制。
     *
     * @param shapes 外部 ShapeRenderer
     */
    public void render(ShapeRenderer shapes) {
        for (int i = 0; i < activeBullets.size; i++) {
            activeBullets.get(i).render(shapes, true);
        }
    }

    /** @return 当前武器显示名称（HUD 用） */
    public String getCurrentWeaponName() {
        return current.getName();
    }

    /** @return 当前武器冷却时间（秒） */
    public float getCurrentCooldown() {
        return current.getCooldown();
    }

    /** @return 冷却剩余比例 [0,1]，0=可开火，1=刚开火。供 HUD 绘制冷却条 */
    public float getCooldownRatio() {
        if (current.getCooldown() <= 0f) return 0f;
        float r = cooldownTimer / current.getCooldown();
        return r < 0f ? 0f : (r > 1f ? 1f : r);
    }

    /** @return 活跃子弹数量（调试/监控用） */
    public int getActiveBulletCount() {
        return activeBullets.size;
    }

    /** @return 对象池（供外部 dispose 时清理） */
    public BulletPool getPool() {
        return pool;
    }

    /**
     * 暴露活跃子弹列表的内部引用，供 {@link GameWorld} 做子弹撞墙回收。
     * <p>
     * 调用方可直接遍历并移除/回收元素，但需倒序操作以避免索引错乱。
     * 不返回副本以避免每帧分配。
     */
    public com.badlogic.gdx.utils.Array<Bullet> getActiveBulletsInternal() {
        return activeBullets;
    }

    /**
     * 武器升级：解锁等级 +1 并切到新解锁的武器。
     * <p>
     * 由战俘营全部救出时触发。循环升级：机枪→手雷→火箭弹→（到顶后保持火箭弹）。
     * 升级后空格切换范围自动扩展到新解锁等级。
     *
     * @return true 表示本次确实升了一级；false 表示已在最高级（火箭弹）无法再升
     */
    public boolean upgrade() {
        if (unlockedLevel >= STATES.length - 1) {
            Gdx.app.log("Weapon", "武器已满级（" + current.getName() + "），无法再升级");
            return false;
        }
        unlockedLevel++;
        currentIndex = unlockedLevel;
        current = STATES[currentIndex];
        cooldownTimer = 0f;
        Gdx.app.log("Weapon", "武器升级 → " + current.getName()
                + "（解锁等级 " + unlockedLevel + "）");
        return true;
    }

    /** @return 已解锁的最高等级下标（0=机枪,1=手雷,2=火箭弹） */
    public int getUnlockedLevel() {
        return unlockedLevel;
    }

    /**
     * 根据当前武器类型播放对应开火音效。
     * <p>
     * 机枪→mg，手雷→grenade，火箭弹→rocket。AudioManager 内部节流（0.04s），
     * 故按住左键连射机枪时不会叠成爆音。
     */
    private void playFireSfx() {
        AudioManager am = AudioManager.get();
        if (am == null) return; // 音频未初始化（降级）
        switch (current.getBulletType()) {
            case MACHINE_GUN: am.playSfx("mg"); break;
            case GRENADE:     am.playSfx("grenade"); break;
            case ROCKET:      am.playSfx("rocket"); break;
        }
    }
}
