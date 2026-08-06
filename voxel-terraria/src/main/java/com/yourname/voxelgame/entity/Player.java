package com.yourname.voxelgame.entity;

import com.yourname.voxelgame.world.BlockAccess;
import com.yourname.voxelgame.inventory.Inventory;
import com.yourname.voxelgame.inventory.ItemRegistry;

import static org.lwjgl.opengl.GL11.*;

/**
 * 玩家实体：复用 Entity 物理，加 HP、移动输入、跳跃、攻击。
 */
public class Player extends Entity {

    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float DEPTH = 0.6f;

    private static final float MOVE_ACCEL = 15.0f;
    private static final float MAX_SPEED = 4.0f;
    private static final float FRICTION = 12.0f;
    private static final float JUMP_SPEED = 8.0f;

    // 战斗
    public static final int MAX_HP = 100;
    public static final int ATTACK_DAMAGE = 10;
    public static final float ATTACK_COOLDOWN = 0.4f; // 秒
    public static final float ATTACK_RANGE = 1.5f;
    public static final float ATTACK_ARC = (float) Math.PI * 0.7f; // 扇形张角
    public static final float KNOCKBACK_SPEED = 3.0f;

    private int hp = MAX_HP;
    private float attackCd = 0f;
    private float attackAnim = 0f; // 挥剑动画剩余秒
    private float facingX = 1f;    // 朝向（+1 右 / -1 左）

    // 状态色
    private static final float[] COLOR_STAND = {0.3f, 0.9f, 0.3f};
    private static final float[] COLOR_AIR   = {0.3f, 0.5f, 1.0f};
    private static final float[] COLOR_HIT  = {1.0f, 0.2f, 0.2f};

    private final float spawnX, spawnY, spawnZ;
    private Inventory inventory;

    public Player(float x, float y, float z) {
        super(x, y, z);
        this.spawnX = x; this.spawnY = y; this.spawnZ = z;
        this.inventory = new Inventory();
    }

    public Inventory inventory() { return inventory; }

    @Override public float width() { return WIDTH; }
    @Override public float height() { return HEIGHT; }
    @Override public float depth() { return DEPTH; }

    @Override
    protected float[] stateColor() {
        if (hitFlash > 0) return COLOR_HIT;
        return onGround ? COLOR_STAND : COLOR_AIR;
    }

    @Override
    protected boolean applyDamage(int damage) {
        hp -= damage;
        if (hp <= 0) { hp = 0; return true; }
        return false;
    }

    @Override
    public boolean isDead() { return hp <= 0; }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = Math.max(0, Math.min(MAX_HP, hp)); }
    public float getFacingX() { return facingX; }
    public float getAttackAnim() { return attackAnim; }
    public float getAttackRange() { return ATTACK_RANGE; }
    public float getAttackArc() { return ATTACK_ARC; }
    /** 当前攻击伤害 = 基础 5 + 手持剑加成。 */
    public int getAttackDamage() {
        ItemRegistry.ItemDef d = inventory.heldDef();
        return 5 + (d != null ? d.attackBonus : 0);
    }
    public float getKnockbackSpeed() { return KNOCKBACK_SPEED; }

    /** 重生到出生点。 */
    public void respawn() {
        setPos(spawnX, spawnY, spawnZ);
        hp = MAX_HP;
        invuln = 0f;
        hitFlash = 0;
    }

    /** 攻击：成功发起返回 true（冷却未到返回 false）。动画由调用方驱动显示。 */
    public boolean tryAttack() {
        if (attackCd > 0) return false;
        attackCd = ATTACK_COOLDOWN;
        attackAnim = 0.1f;
        return true;
    }

    /**
     * 更新玩家：输入 + 物理。
     * @param moveX -1/0/1
     * @param jump  是否跳
     */
    public void update(BlockAccess world, int moveX, boolean jump, boolean attack, float dt) {
        if (attackCd > 0) attackCd -= dt;
        if (attackAnim > 0) attackAnim -= dt;

        if (moveX != 0) {
            facingX = moveX > 0 ? 1f : -1f;
            vx += moveX * MOVE_ACCEL * dt;
            if (vx > MAX_SPEED) vx = MAX_SPEED;
            if (vx < -MAX_SPEED) vx = -MAX_SPEED;
        } else {
            if (vx > 0) { vx -= FRICTION * dt; if (vx < 0) vx = 0; }
            else if (vx < 0) { vx += FRICTION * dt; if (vx > 0) vx = 0; }
        }

        if (jump && onGround) {
            vy = JUMP_SPEED;
            onGround = false;
        }

        if (attack) tryAttack();

        stepPhysics(world, dt);
    }

    /** 手持工具渲染：在玩家右侧画简易线框。镐=斜线把手，剑=竖条。 */
    public void renderHeldItem() {
        ItemRegistry.ItemDef d = inventory.heldDef();
        if (d == null || d.toolType == ItemRegistry.ToolType.NONE) return;
        float hx = x + facingX * 0.6f;
        float hy = y;
        float hz = z + depth() * 0.5f + 0.02f;
        glColor3f(d.r, d.g, d.b);
        glBegin(GL_LINES);
        if (d.toolType == ItemRegistry.ToolType.PICKAXE) {
            // 把手 + 镐头
            glVertex3f(hx, hy - 0.4f, hz); glVertex3f(hx + facingX * 0.3f, hy + 0.3f, hz);
            glVertex3f(hx + facingX * 0.3f, hy + 0.3f, hz); glVertex3f(hx + facingX * 0.55f, hy + 0.45f, hz);
            glVertex3f(hx + facingX * 0.3f, hy + 0.3f, hz); glVertex3f(hx + facingX * 0.1f, hy + 0.5f, hz);
        } else {
            // 剑：竖条 + 横挡
            glVertex3f(hx, hy - 0.4f, hz); glVertex3f(hx, hy + 0.5f, hz);
            glVertex3f(hx - 0.15f, hy + 0.4f, hz); glVertex3f(hx + 0.15f, hy + 0.4f, hz);
        }
        glEnd();
        glColor3f(1f, 1f, 1f);
    }
}
