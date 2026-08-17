package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * Infantry —— 步兵。
 * <p>
 * 最弱敌人：血量 1，被机枪一击必杀。行为是追踪玩家 + 避障绕墙。
 * <p>
 * <h3>追踪 + 随机偏移</h3>
 * 纯追踪（直线朝玩家走）会让多个步兵叠在同一直线上，既不自然也不美观。
 * 给每个步兵一个固定的"偏移角" offsetAngle，实际目标点 = 玩家位置绕该角旋转一小段距离，
 * 使多个步兵从不同方向接近，自然分散：
 * <pre>
 *   target = player + rotate( (r,0), offsetAngle )   // r≈40px 偏移半径
 *   desired = normalize(target - position) * speed
 *   position += desired * dt
 * </pre>
 *
 * <h3>避障绕墙（射线探测 + 切线偏转）</h3>
 * 第 7 步新增。在按期望方向移动前，沿该方向向前探测 LOOK_AHEAD 像素处是否是墙：
 * <pre>
 *   probe = position + desired * LOOK_AHEAD
 *   if 墙(probe):
 *     把 desired 方向偏转一个固定角（先试左偏 +δ，仍撞墙再试右偏 −δ）
 *     偏转后沿墙切线方向滑动，自然绕过墙体
 * </pre>
 * 推导：墙的法向未知（只有碰撞层布尔信息），故不计算精确法向，
 * 而是用"角度采样"——以期望方向为基准，向两侧各试一个偏转角，
 * 取第一个不撞墙的方向作为本帧实际移动方向。这等价于沿墙切线滑动，
 * 视觉上表现为步兵遇到墙后顺墙绕行，而非顶在墙上卡死。
 *
 * <h3>FSM</h3>
 * PATROL（待机）→ 发现玩家 → ATTACK（追踪+避障）。进入后持续 ATTACK 不退回。
 *
 * @author Jackal Dev Team
 */
public class Infantry extends Enemy {

    /** 步兵血量：1（机枪一击必杀） */
    private static final int HP = 1;
    /** 移动速度（像素/秒）。比吉普车慢很多，玩家可甩开 */
    private static final float SPEED = 90f;
    /** 半径（像素） */
    private static final float RADIUS = 6f;
    /** 接触玩家伤害 */
    private static final float CONTACT_DAMAGE = 0.5f;
    /** 发现玩家的距离阈值（像素）。进入此范围开始追踪 */
    private static final float DETECT_RANGE = 280f;
    /** 多步兵分散用的偏移半径（像素）。目标点绕玩家旋转此距离 */
    private static final float OFFSET_RADIUS = 40f;

    /** 避障前方探测距离（像素）。约 1.5 个瓦片，够提前转向 */
    private static final float LOOK_AHEAD = 18f;
    /** 避障偏转角（弧度）。45°，遇墙时向侧前方切出 */
    private static final float AVOID_TURN = MathUtils.PI / 4f;
    /** 避障探测用的矩形半边长（像素） */
    private static final float PROBE_HALF = 4f;

    /** 本步兵的固定偏移角（弧度）。构造时随机，使多个步兵目标点不同 */
    private final float offsetAngle;

    /** 避障探测矩形复用缓存（避免每帧 new） */
    private final Rectangle probeBox = new Rectangle();

    /**
     * 构造步兵。
     *
     * @param x 初始 X
     * @param y 初始 Y
     */
    public Infantry(float x, float y) {
        super(x, y, HP, SPEED, RADIUS, CONTACT_DAMAGE);
        // 随机偏移角，范围 [0, 2π)
        this.offsetAngle = MathUtils.random(MathUtils.PI2);
        this.state = AIState.PATROL;
    }

    @Override
    protected void think(float dt, Vector2 target) {
        float dist = Vector2.dst(position.x, position.y, target.x, target.y);

        // 状态迁移：PATROL → ATTACK（一旦发现就持续追击）
        if (state == AIState.PATROL && dist < DETECT_RANGE) {
            state = AIState.ATTACK;
        }

        if (state != AIState.ATTACK) {
            return; // PATROL：原地不动
        }

        // —— 期望目标点：玩家 + 偏移半径旋转向量（多步兵分散）——
        float tx = target.x + MathUtils.cos(offsetAngle) * OFFSET_RADIUS;
        float ty = target.y + MathUtils.sin(offsetAngle) * OFFSET_RADIUS;

        // 期望方向（归一化）
        float dx = tx - position.x;
        float dy = ty - position.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        dx /= len;
        dy /= len;

        // —— 避障：沿期望方向探测前方，遇墙则偏转 ——
        float moveAngle = MathUtils.atan2(dy, dx);
        if (collider != null && blockedAhead(moveAngle)) {
            // 先试左偏（+AVOID_TURN），仍撞墙再试右偏（−AVOID_TURN）
            if (!blockedAhead(moveAngle + AVOID_TURN)) {
                moveAngle += AVOID_TURN;
            } else if (!blockedAhead(moveAngle - AVOID_TURN)) {
                moveAngle -= AVOID_TURN;
            } else {
                // 两侧都撞墙（如死胡同）：本帧不移动，等下一帧重新评估
                return;
            }
        }

        // 按避障后的方向移动
        facing = moveAngle;
        position.x += MathUtils.cos(moveAngle) * moveSpeed * dt;
        position.y += MathUtils.sin(moveAngle) * moveSpeed * dt;
    }

    /**
     * 沿指定角度方向探测前方 LOOK_AHEAD 处是否被墙阻挡。
     * <p>
     * 在探测点放一个小矩形（PROBE_HALF×2），用 collider.collides 判定是否与墙重叠。
     * 这是最简单的"射线-瓦片"采样：不计算精确交点，只查前方采样点是否实心。
     *
     * @param angle 探测方向（弧度）
     * @return true 表示前方有墙需要避让
     */
    private boolean blockedAhead(float angle) {
        float px = position.x + MathUtils.cos(angle) * LOOK_AHEAD;
        float py = position.y + MathUtils.sin(angle) * LOOK_AHEAD;
        probeBox.set(px - PROBE_HALF, py - PROBE_HALF, PROBE_HALF * 2f, PROBE_HALF * 2f);
        return collider.collides(probeBox);
    }

    @Override
    public void render(ShapeRenderer shapes) {
        // 受击无敌帧期间闪烁（白色）以提供击中反馈
        boolean flashing = hitInvuln > 0f && (((int) (hitInvuln * 50)) % 2 == 0);
        shapes.setColor(flashing ? Color.WHITE : Color.BROWN);
        // 步兵：小棕色圆
        shapes.circle(position.x, position.y, radius);
        // 朝向指示线（短）显示追踪方向
        shapes.setColor(Color.RED);
        shapes.line(position.x, position.y,
                position.x + MathUtils.cos(facing) * radius * 1.5f,
                position.y + MathUtils.sin(facing) * radius * 1.5f);
        // 步兵血量1，不画血条
    }
}

