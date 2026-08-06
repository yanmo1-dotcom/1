package com.yourname.voxelgame.entity;

import com.yourname.voxelgame.world.BlockAccess;

import static org.lwjgl.opengl.GL11.*;

/**
 * 实体基类：位置/速度/AABB + 重力 + 分轴碰撞 + 受伤闪烁。
 * 子类提供尺寸、状态色、AI/输入逻辑。中心点为 position（y 含身高一半）。
 */
public abstract class Entity {

    // 重力（子类可覆盖）
    protected static final float GRAVITY = -9.8f;
    protected static final float TERMINAL_VEL = -20.0f;

    protected float x, y, z;
    protected float vx, vy, vz;
    protected boolean onGround = false;

    protected int hitFlash = 0;   // 受伤/碰撞高亮剩余帧
    protected float invuln = 0f;  // 无敌时间（秒）

    protected Entity(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
    }

    // —— 子类实现 ——
    public abstract float width();
    public abstract float height();
    public abstract float depth();
    /** 返回 [r,g,b] 状态色。 */
    protected abstract float[] stateColor();

    // —— 公共 ——
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getVx() { return vx; }
    public float getVy() { return vy; }
    public float getVz() { return vz; }
    public boolean isOnGround() { return onGround; }

    public void setPos(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        this.vx = 0; this.vy = 0; this.vz = 0;
    }

    public void setVx(float vx) { this.vx = vx; }
    public void setVy(float vy) { this.vy = vy; }

    /** 施加击退（水平方向 + 一点上抛）。 */
    public void knockback(float dirX, float speed, float lift) {
        this.vx = dirX * speed;
        if (lift > 0 && onGround) { this.vy = lift; onGround = false; }
        else if (lift > 0) { this.vy = Math.max(this.vy, lift * 0.5f); }
    }

    /** 受伤：返回是否真的扣血（无敌帧内忽略）。 */
    public boolean hurt(int damage, float invulnSeconds) {
        if (invuln > 0) return false;
        invuln = invulnSeconds;
        hitFlash = (int) Math.max(1, invulnSeconds * 60);
        return applyDamage(damage);
    }

    /** 子类实现扣血与死亡判定。返回是否死亡。 */
    protected abstract boolean applyDamage(int damage);
    public abstract boolean isDead();

    /**
     * 物理 + 分轴碰撞步进。子类先改 vx/vy/vz 与 onGround（输入/AI），再调用此方法应用位移。
     * 内部完成：重力、X→Y→Z 分轴碰撞解析、地面检测、闪烁/无敌倒计时。
     */
    protected void stepPhysics(BlockAccess world, float dt) {
        float w = width(), h = height(), d = depth();

        // 重力
        vy += GRAVITY * dt;
        if (vy < TERMINAL_VEL) vy = TERMINAL_VEL;

        float halfW = w * 0.5f, halfH = h * 0.5f, halfD = d * 0.5f;

        // ---- X 轴 ----
        float nx = x + vx * dt;
        if (!CollisionUtil.intersectsAt(world, nx, y, z, w, h, d)) {
            x = nx;
        } else {
            vx = 0;
        }

        // ---- Y 轴 ----
        float ny = y + vy * dt;
        if (!CollisionUtil.intersectsAt(world, x, ny, z, w, h, d)) {
            y = ny;
            onGround = false;
        } else {
            if (vy < 0) {
                // 落地：脚底对齐下方方块顶面
                float foot = ny - halfH;
                int by = (int) Math.floor(foot - 1e-4f);
                if (by < 0) by = 0;
                y = (by + 1) + halfH;
                onGround = true;
            } else {
                // 顶到上面方块：头贴方块底
                float head = ny + halfH;
                int by = (int) Math.floor(head);
                if (by < 0) by = 0;
                y = by - halfH;
            }
            vy = 0;
        }

        // ---- Z 轴 ----
        float nz = z + vz * dt;
        if (!CollisionUtil.intersectsAt(world, x, y, nz, w, h, d)) {
            z = nz;
        } else {
            vz = 0;
        }

        // 地面检测
        onGround = CollisionUtil.isOnGround(world, x, z, y - halfH, w, d);

        if (hitFlash > 0) hitFlash--;
        if (invuln > 0) invuln -= dt;
    }

    /** 渲染 AABB 12 边线框，颜色由 stateColor 决定。 */
    public void render() {
        float[] c = stateColor();
        glColor3f(c[0], c[1], c[2]);
        float halfW = width() * 0.5f, halfH = height() * 0.5f, halfD = depth() * 0.5f;
        float x0 = x - halfW, x1 = x + halfW;
        float y0 = y - halfH, y1 = y + halfH;
        float z0 = z - halfD, z1 = z + halfD;
        glBegin(GL_LINES);
        edge(x0,y0,z0, x1,y0,z0); edge(x1,y0,z0, x1,y0,z1);
        edge(x1,y0,z1, x0,y0,z1); edge(x0,y0,z1, x0,y0,z0);
        edge(x0,y1,z0, x1,y1,z0); edge(x1,y1,z0, x1,y1,z1);
        edge(x1,y1,z1, x0,y1,z1); edge(x0,y1,z1, x0,y1,z0);
        edge(x0,y0,z0, x0,y1,z0); edge(x1,y0,z0, x1,y1,z0);
        edge(x1,y0,z1, x1,y1,z1); edge(x0,y0,z1, x0,y1,z1);
        glEnd();
    }

    protected static void edge(float ax, float ay, float az, float bx, float by, float bz) {
        glVertex3f(ax, ay, az);
        glVertex3f(bx, by, bz);
    }

    /** 是否与某方块 AABB 重叠（用于放置取消）。 */
    public boolean overlapsBlock(int bx, int by, int bz) {
        float halfW = width() * 0.5f, halfH = height() * 0.5f, halfD = depth() * 0.5f;
        return !(bx + 1 <= x - halfW || bx >= x + halfW
              || by + 1 <= y - halfH || by >= y + halfH
              || bz + 1 <= z - halfD || bz >= z + halfD);
    }

    /** 与另一实体 AABB 是否相交（用于近战/接触伤害）。 */
    public boolean intersectsEntity(Entity other) {
        float aHW = width() * 0.5f, aHH = height() * 0.5f, aHD = depth() * 0.5f;
        float bHW = other.width() * 0.5f, bHH = other.height() * 0.5f, bHD = other.depth() * 0.5f;
        return Math.abs(x - other.x) < aHW + bHW
            && Math.abs(y - other.y) < aHH + bHH
            && Math.abs(z - other.z) < aHD + bHD;
    }
}
