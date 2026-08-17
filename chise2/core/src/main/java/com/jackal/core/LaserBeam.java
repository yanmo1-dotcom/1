package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * LaserBeam —— Boss 阶段3 激光扫射（持续伤害区域）。
 * <p>
 * 与弹射物不同：激光是从 Boss 发出的一条射线，有持续时长与扫射角速度。
 * 每帧检测玩家矩形是否落入激光的矩形带（近似为以方向角旋转的细长矩形），
 * 若命中则对玩家造成持续伤害（受玩家无敌帧节流）。
 *
 * <h3>扫射机制</h3>
 * 激光以 {@link #angle} 为当前指向，每帧以 {@link #sweepSpeed} 旋转，
 * 在 [angleMin, angleMax] 范围内来回扫动：
 * <pre>
 *   angle += sweepDir * sweepSpeed * dt
 *   if angle > angleMax: sweepDir = -1
 *   if angle < angleMin: sweepDir = +1
 * </pre>
 *
 * <h3>命中判定</h3>
 * 把激光近似为从起点沿 angle 方向、长 {@link #length}、宽 {@link #width} 的矩形，
 * 用 OBB（定向包围盒）与玩家 AABB 做相交。简化做法：把玩家矩形逆旋转到激光本地坐标系
 * （激光本地坐标 = 起点为原点、x 轴沿激光方向），再用 AABB 相交判定。
 *
 * @author Jackal Dev Team
 */
public class LaserBeam {

    /** 激光起点（Boss 位置，每帧由 Boss 更新） */
    public final Vector2 origin = new Vector2();
    /** 当前指向角（弧度） */
    public float angle = 0f;
    /** 激光长度（像素）。覆盖整个屏幕宽足够 */
    public float length = 700f;
    /** 激光宽度（像素）。命中区域厚度 */
    public float width = 10f;
    /** 剩余持续时间（秒）。>0 期间持续生效 */
    public float duration = 0f;
    /** 扫射角速度（弧度/秒） */
    public float sweepSpeed = 1.5f;
    /** 扫射范围下限（弧度） */
    public float angleMin = 0f;
    /** 扫射范围上限（弧度） */
    public float angleMax = 0f;
    /** 当前扫射方向 +1/-1 */
    private int sweepDir = 1;
    /** 每秒对玩家造成的伤害（持续伤害，受玩家无敌帧节流实际每 ~1.2s 扣一次） */
    public float dps = 1f;
    /** 是否存活（duration>0） */
    public boolean active = false;

    /** 本地坐标玩家矩形缓存（避免每帧 new） */
    private final Rectangle localPlayer = new Rectangle();
    /** 本地坐标激光 AABB（原点起，length × width） */
    private final Rectangle localBeam = new Rectangle();

    /**
     * 启动一次激光扫射。
     *
     * @param originX   起点 X
     * @param originY   起点 Y
     * @param startAngle 起始角（弧度）
     * @param angleMin   扫射下限（弧度）
     * @param angleMax   扫射上限（弧度）
     * @param duration   持续时间（秒）
     */
    public void start(float originX, float originY, float startAngle,
                      float angleMin, float angleMax, float duration) {
        this.origin.set(originX, originY);
        this.angle = startAngle;
        this.angleMin = angleMin;
        this.angleMax = angleMax;
        this.duration = duration;
        this.sweepDir = 1;
        this.active = true;
    }

    /**
     * 每帧更新：推进扫射角度与持续时间，返回是否仍存活。
     *
     * @param dt 帧时间（秒）
     * @return true 表示激光仍活跃；false 表示已结束（调用方应移除）
     */
    public boolean update(float dt) {
        if (!active) return false;
        duration -= dt;
        if (duration <= 0f) {
            active = false;
            return false;
        }
        // 扫射
        angle += sweepDir * sweepSpeed * dt;
        if (angle > angleMax) {
            angle = angleMax;
            sweepDir = -1;
        } else if (angle < angleMin) {
            angle = angleMin;
            sweepDir = 1;
        }
        return true;
    }

    /**
     * 判定玩家矩形是否被激光命中，若命中则对玩家扣血。
     * <p>
     * 把玩家中心变换到激光本地坐标系（原点=激光起点，x 轴=激光方向），
     * 再用 AABB 相交判定激光带 [0,length]×[-width/2,width/2]。
     *
     * @param jeep 玩家吉普车
     * @return true 表示本帧命中并扣血
     */
    public boolean tryHit(Jeep jeep) {
        if (!active) return false;
        float cos = MathUtils.cos(-angle);
        float sin = MathUtils.sin(-angle);
        // 玩家中心相对激光起点
        float rx = jeep.getPosition().x - origin.x;
        float ry = jeep.getPosition().y - origin.y;
        // 逆旋转到激光本地坐标
        float lx = rx * cos - ry * sin;
        float ly = rx * sin + ry * cos;
        // 玩家近似为半边长 = max(HALF_WIDTH, HALF_HEIGHT) 的方块
        float half = Math.max(Jeep.HALF_WIDTH, Jeep.HALF_HEIGHT);
        localPlayer.set(lx - half, ly - half, half * 2f, half * 2f);
        localBeam.set(0f, -width * 0.5f, length, width);
        if (localPlayer.overlaps(localBeam)) {
            return jeep.takeDamage(dps);
        }
        return false;
    }

    /**
     * 渲染激光：沿 angle 方向画一条宽度 × 长度的亮青色矩形带。
     * <p>
     * 用 ShapeRenderer 的 rect(x,y,ox,oy,w,h,sx,sy,deg) 以起点为旋转中心绘制。
     * 调用前 shapes 须已 begin(Filled) 并设置投影矩阵。
     */
    public void render(ShapeRenderer shapes) {
        if (!active) return;
        // 外层半透明发光
        shapes.setColor(new Color(0.4f, 0.9f, 1f, 0.35f));
        shapes.rect(origin.x, origin.y - width, 0f, width,
                length, width * 2f, 1f, 1f, angle * MathUtils.radDeg);
        // 内层亮芯
        shapes.setColor(Color.CYAN);
        shapes.rect(origin.x, origin.y - width * 0.5f, 0f, width * 0.5f,
                length, width, 1f, 1f, angle * MathUtils.radDeg);
    }
}
