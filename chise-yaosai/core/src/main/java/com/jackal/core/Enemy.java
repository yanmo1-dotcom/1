package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * Enemy —— 敌人基类。
 * <p>
 * 所有敌人共享：位置、血量、移动速度、朝向、AI 状态机（FSM）、被击中扣血、接触伤害。
 * 子类（{@link Infantry}/{@link Tank}/{@link Turret}）实现 {@link #think(float, Vector2)}
 * 决策逻辑与 {@link #render(ShapeRenderer)} 外观。
 *
 * <h3>有限状态机（FSM）</h3>
 * 用 {@link AIState} 枚举表示敌人当前行为状态。基类维护状态字段与切换，
 * 子类在 {@link #think(float, Vector2)} 中根据环境（玩家距离、视线、血量）
 * 决定状态迁移并执行对应行为。这是最轻量的 FSM 实现：状态是数据，行为是方法，
 * 无需独立 State 类，适合敌人这种行为相对简单的实体。
 * <pre>
 *   典型状态迁移（以 Tank 为例）：
 *   PATROL ──发现玩家──→ ATTACK ──玩家脱离──→ PATROL
 *   任意 ──血量≤0──→ DEAD（终态，待清理）
 * </pre>
 *
 * <h3>接触伤害</h3>
 * 敌人与吉普车矩形重叠时对玩家造成固定伤害，由 {@link #getContactDamage()} 声明。
 * GameWorld 每帧检测，为避免持续接触每帧扣血，GameWorld 负责给玩家加受击无敌帧。
 *
 * @author Jackal Dev Team
 */
public abstract class Enemy {

    /** AI 状态枚举（FSM 的状态集） */
    public enum AIState {
        /** 巡逻/待机（沿路径走或原地守候） */
        PATROL,
        /** 追击/攻击玩家 */
        ATTACK,
        /** 已死亡，等待清理 */
        DEAD
    }

    /** 中心位置（世界坐标） */
    public final Vector2 position = new Vector2();

    /** 当前血量（>0 存活） */
    protected int hp;

    /** 最大血量（用于 HUD 与判断） */
    protected final int maxHp;

    /** 移动速度（像素/秒）。Turret 为 0（固定） */
    protected final float moveSpeed;

    /** 当前 AI 状态 */
    protected AIState state = AIState.PATROL;

    /** 朝向角度（弧度），0=朝右(+x)。用于绘制朝向与开火方向 */
    protected float facing = 0f;

    /** 半径（像素），用于绘制大小与碰撞包围盒 */
    protected final float radius;

    /** 接触伤害：撞到玩家时单次扣血量。GameWorld 配合无敌帧避免连续扣血 */
    protected final float contactDamage;

    /** 是否已标记死亡（hp≤0）。GameWorld 据此清理 */
    private boolean dead = false;

    /** 地图碰撞器引用（可选，由 GameWorld 注入；用于避障查询） */
    protected MapCollider collider = null;

    /** 受击后无敌帧计时（秒）。>0 期间不再被同一发子弹重复扣血，也用于受击闪烁 */
    protected float hitInvuln = 0f;

    /**
     * 构造敌人。
     *
     * @param x              初始 X
     * @param y              初始 Y
     * @param hp             初始血量
     * @param moveSpeed      移动速度（像素/秒）
     * @param radius         碰撞/绘制半径
     * @param contactDamage  接触玩家伤害
     */
    protected Enemy(float x, float y, int hp, float moveSpeed, float radius, float contactDamage) {
        this.position.set(x, y);
        this.hp = hp;
        this.maxHp = hp;
        this.moveSpeed = moveSpeed;
        this.radius = radius;
        this.contactDamage = contactDamage;
    }

    /**
     * 每帧更新：推进受击无敌帧 → 调用子类决策 → 移动积分。
     * <p>
     * 模板方法模式：基类控制流程，子类填充 {@link #think}。
     *
     * @param dt     帧时间（秒）
     * @param target 玩家位置（吉普车中心）
     */
    public void update(float dt, Vector2 target) {
        // 受击无敌帧倒计时
        if (hitInvuln > 0f) hitInvuln -= dt;

        // 死亡态不再思考与移动
        if (state == AIState.DEAD) return;

        // 子类决策：根据玩家位置切换状态、决定移动/开火
        think(dt, target);
    }

    /**
     * 子类实现的 AI 决策。在此读取玩家位置，切换 {@link #state}，
     * 并直接修改 {@link #position}（移动）或请求开火。
     *
     * @param dt     帧时间（秒）
     * @param target 玩家位置
     */
    protected abstract void think(float dt, Vector2 target);

    /**
     * 受到伤害。返回是否真的扣血（受无敌帧保护时返回 false）。
     * <p>
     * 调用方（GameWorld）按子弹类型决定是否可命中（如机枪打步兵一击必杀）。
     *
     * @param amount 伤害值
     * @return true 表示本次扣血生效（hp 减少，可能死亡）；false 表示因无敌帧未生效
     */
    public boolean takeDamage(int amount) {
        if (state == AIState.DEAD) return false;
        if (hitInvuln > 0f) return false;
        hp -= amount;
        hitInvuln = 0.1f; // 100ms 无敌帧，防同一发子弹多段命中
        if (hp <= 0) {
            hp = 0;
            state = AIState.DEAD;
            dead = true;
        }
        return true;
    }

    /** @return 是否已死亡（待清理） */
    public boolean isDead() {
        return dead;
    }

    /** @return 当前血量 */
    public int getHp() {
        return hp;
    }

    /** @return 最大血量 */
    public int getMaxHp() {
        return maxHp;
    }

    /** @return 接触伤害值 */
    public float getContactDamage() {
        return contactDamage;
    }

    /** @return 当前 AI 状态 */
    public AIState getState() {
        return state;
    }

    /**
     * 注入地图碰撞器（GameWorld 在生成敌人时调用）。
     * <p>
     * 子类可用它做避障查询（如步兵射线探测前方墙体）。不需要避障的敌人可不注入。
     */
    public void setCollider(MapCollider collider) {
        this.collider = collider;
    }

    /**
     * 获取该敌人的世界包围盒（中心点 ± radius 的正方形）。
     * 复用缓存 rect，调用方用完即可，勿跨帧持有。
     */
    public Rectangle getBounds(Rectangle out) {
        return out.set(position.x - radius, position.y - radius, radius * 2f, radius * 2f);
    }

    /**
     * 渲染敌人。子类实现具体外观。
     * <p>
     * 调用前 shapes 须已 begin(Filled) 并设置投影矩阵。
     */
    public abstract void render(ShapeRenderer shapes);

    /**
     * 渲染血条（受击后显示在敌人上方，便于观察剩余血量）。
     * <p>
     * 仅 maxHp>1 的敌人显示（步兵一击死，无需血条）。
     *
     * @param shapes 已 begin(Filled) 的 ShapeRenderer
     */
    protected void renderHpBar(ShapeRenderer shapes, float barWidth) {
        if (maxHp <= 1) return; // 步兵一击死，不显示
        // 背景槽
        shapes.setColor(Color.DARK_GRAY);
        shapes.rect(position.x - barWidth * 0.5f, position.y + radius + 4f, barWidth, 3f);
        // 血量填充（绿→红渐变简化为绿）
        float ratio = (float) hp / maxHp;
        shapes.setColor(ratio > 0.5f ? Color.GREEN : (ratio > 0.25f ? Color.ORANGE : Color.RED));
        shapes.rect(position.x - barWidth * 0.5f, position.y + radius + 4f, barWidth * ratio, 3f);
    }
}
