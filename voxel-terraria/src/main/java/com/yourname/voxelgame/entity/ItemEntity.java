package com.yourname.voxelgame.entity;

import com.yourname.voxelgame.world.BlockAccess;

import static org.lwjgl.opengl.GL11.*;

/**
 * 掉落物实体：简化物理（重力 + 地面碰撞），玩家靠近 1 格自动拾取。
 * 用一个小球（GL_POINTS）渲染。拾取后 isDead() 返回 true 由管理器移除。
 */
public class ItemEntity extends Entity {

    public static final float W = 0.3f, H = 0.3f, D = 0.3f;
    private static final float PICKUP_DIST = 1.0f;

    private final String name;
    private boolean picked = false;
    private float bob = 0f; // 上下浮动相位

    private static final float[] COLOR = {0.3f, 0.5f, 1.0f}; // 蓝色凝胶

    public ItemEntity(String name, float x, float y, float z) {
        super(x, y, z);
        this.name = name;
    }

    public String getName() { return name; }

    @Override public float width() { return W; }
    @Override public float height() { return H; }
    @Override public float depth() { return D; }

    @Override
    protected float[] stateColor() { return COLOR; }

    @Override
    protected boolean applyDamage(int damage) { return false; }

    @Override
    public boolean isDead() { return picked; }

    public void update(BlockAccess world, Player player, float dt) {
        bob += dt;
        // 重力 + 物理（无水平输入）
        stepPhysics(world, dt);
        // 拾取检测
        float dx = player.getX() - x;
        float dy = player.getY() - y;
        float dz = player.getZ() - z;
        if (dx*dx + dy*dy + dz*dz <= PICKUP_DIST * PICKUP_DIST) {
            picked = true;
            System.out.println("Picked up " + name);
        }
    }

    @Override
    public void render() {
        float bobOffset = (float) Math.sin(bob * 3.0f) * 0.05f;
        glColor3f(COLOR[0], COLOR[1], COLOR[2]);
        glPointSize(8f);
        glBegin(GL_POINTS);
        glVertex3f(x, y + bobOffset, z);
        glEnd();
    }
}
