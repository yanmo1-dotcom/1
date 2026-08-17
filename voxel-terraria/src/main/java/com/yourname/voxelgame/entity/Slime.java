package com.yourname.voxelgame.entity;

import com.yourname.voxelgame.world.BlockAccess;

import static org.lwjgl.opengl.GL11.*;

/**
 * 史莱姆：绿色 AABB + 两只白色眼睛。
 * AI：每 1-2 秒随机跳跃，水平朝玩家缓慢移动（速度 1.5）。
 * 尺寸：0.8 × 0.8 × 0.8。
 */
public class Slime extends Enemy {

    public static final float W = 0.8f, H = 0.8f, D = 0.8f;
    private static final float SPEED = 1.5f;
    private static final float JUMP_SPEED = 6.0f;
    private static final int MAX_HP = 20;

    private float jumpTimer = 0f;     // 距下次跳跃的倒计时
    private float nextJumpDelay = 1.5f;

    private static final float[] COLOR_BODY = {0.3f, 0.8f, 0.3f};
    private static final float[] COLOR_HIT  = {1.0f, 0.3f, 0.3f};

    public Slime(float x, float y, float z) {
        super(x, y, z, MAX_HP);
        // 随机化首次跳跃延迟（0~1s 内）：用 hashCode 做伪随机，避免所有史莱姆同步跳
        nextJumpDelay = 0.5f + (float) ((System.identityHashCode(this) & 0xFF) / 255.0);
        jumpTimer = nextJumpDelay;
    }

    @Override public float width() { return W; }
    @Override public float height() { return H; }
    @Override public float depth() { return D; }

    @Override
    protected float[] stateColor() {
        return hitFlash > 0 ? COLOR_HIT : COLOR_BODY;
    }

    @Override
    public void updateAI(BlockAccess world, Player player, float dt) {
        // 朝玩家水平移动
        float dx = player.getX() - x;
        float dir = dx > 0 ? 1f : -1f;
        // 仅在地面时主动水平加速（空中保持惯性）
        if (onGround) {
            vx = dir * SPEED;
        }

        // 跳跃计时
        jumpTimer -= dt;
        if (jumpTimer <= 0 && onGround) {
            vy = JUMP_SPEED;
            onGround = false;
            // 下一次延迟 1-2s
            nextJumpDelay = 1.0f + (float) ((System.identityHashCode(this) >> 8 & 0xFF) / 255.0);
            jumpTimer = nextJumpDelay;
        }

        stepPhysics(world, dt);
    }

    /** 史莱姆渲染：AABB 线框 + 两只白眼睛（在面向玩家的面上）。 */
    @Override
    public void render() {
        super.render(); // 线框
        // 眼睛：两个白色小点，画在面向玩家的一侧（+z 面，与玩家视角一致）
        float eyeY = y + 0.15f;
        float eyeZ = z + D * 0.5f + 0.01f;
        glColor3f(1f, 1f, 1f);
        glPointSize(4f);
        glBegin(GL_POINTS);
        glVertex3f(x - 0.15f, eyeY, eyeZ);
        glVertex3f(x + 0.15f, eyeY, eyeZ);
        glEnd();
    }
}
