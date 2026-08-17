package com.yourname.voxelgame.entity;

import com.yourname.voxelgame.world.BlockAccess;

/**
 * 敌怪基类：复用 Entity 物理，持有 HP，提供 AI 钩子。
 */
public abstract class Enemy extends Entity {

    protected int hp;
    protected int maxHp;

    protected Enemy(float x, float y, float z, int maxHp) {
        super(x, y, z);
        this.maxHp = maxHp;
        this.hp = maxHp;
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
    public int getMaxHp() { return maxHp; }

    /** 每帧 AI：子类改速度/跳，再调用 stepPhysics。 */
    public abstract void updateAI(BlockAccess world, Player player, float dt);
}
