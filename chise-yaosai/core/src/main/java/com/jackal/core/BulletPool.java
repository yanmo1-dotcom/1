package com.jackal.core;

import com.badlogic.gdx.utils.Pool;

/**
 * BulletPool —— 弹射物对象池。
 * <p>
 * 继承 LibGDX 的 {@link Pool}，复用 {@link Bullet} 实例，避免高频开火时反复 new/GC。
 *
 * <h3>对象池工作原理</h3>
 * <pre>
 *   obtain()：优先从空闲栈取一个对象；若栈空则调用 newObject() 新建一个。
 *   free(obj)：把对象重置（reset()）后压回空闲栈，供下次 obtain 复用。
 * </pre>
 * 这样活跃子弹数稳定时，几乎不再产生新分配，GC 压力趋近于零。
 *
 * <h3>容量与扩容</h3>
 * 初始预分配 {@link #initialCapacity} 个对象；超出后 Pool 会自动新建，
 * 但为防止失控（如忘记回收），设了 {@link #max} 上限。达到上限时 free 会被丢弃，
 * 因此务必保证子弹的 update 在结束（边界/落地/寿命）时调用 free。
 *
 * <h3>使用约定</h3>
 * <ul>
 *   <li>obtain 后必须设置初始状态（initLine/initGrenade），再加入活跃列表。</li>
 *   <li>update 返回 true（已结束）时，调用方只需从活跃列表移除，free 已在 update 内完成。</li>
 *   <li>游戏退出时调用 clear() 释放池中对象（Bullet 本身无 native 资源，可省略，但保持习惯）。</li>
 * </ul>
 *
 * @author Jackal Dev Team
 */
public class BulletPool extends Pool<Bullet> {

    /** 初始预分配数量。机枪高频开火时活跃子弹约 20~40 个，预分配 32 足够覆盖常态 */
    private final int initialCapacity;

    /**
     * 构造对象池。
     *
     * @param initialCapacity 初始预分配数量（首次 obtain 前填充这么多空对象备用）
     * @param max             池最大容量，超出后 free 的对象被丢弃；0 表示无上限
     */
    public BulletPool(int initialCapacity, int max) {
        super(initialCapacity, max);
        this.initialCapacity = initialCapacity;
        // 预填充：提前 new 出若干空对象放入空闲栈，减少运行中首次扩容的小停顿
        for (int i = 0; i < initialCapacity; i++) {
            free(newObject());
        }
    }

    /**
     * 池为空时调用，创建新的 Bullet 实例。
     * <p>
     * 注意：这里创建的对象处于 reset 后的初始态，obtain 的调用方仍需调用
     * {@code initLine/initGrenade} 填入实际发射参数。
     */
    @Override
    protected Bullet newObject() {
        Bullet b = new Bullet();
        b.reset();
        return b;
    }

    /**
     * 回收子弹前重置其状态，防止下一轮复用时携带脏数据（如旧速度、旧位置）。
     * <p>
     * 覆写父类方法以显式调用 {@link Bullet#reset()}，保证字段全部归零。
     */
    @Override
    public void free(Bullet object) {
        object.reset();
        super.free(object);
    }

    /** @return 初始预分配容量（调试/监控用） */
    public int getInitialCapacity() {
        return initialCapacity;
    }
}
