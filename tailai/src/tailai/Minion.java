package tailai;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * 召唤仆从：跟随玩家，自动攻击附近敌人。
 * 丐版实现：史莱姆仆从，跳跃移动，接触敌人造成伤害。
 */
public class Minion {
    public float x, y;
    public float vx, vy;
    public int damage;
    public boolean alive = true;
    public float attackCooldown;
    public float jumpTimer;
    public final int ownerSlot; // 所属玩家槽位（单机固定0）

    public Minion(float x, float y, int damage) {
        this.x = x;
        this.y = y;
        this.damage = damage;
        this.ownerSlot = 0;
    }

    public void update(float dt, Player player, World world, java.util.List<Enemy> enemies) {
        if (!alive) return;
        if (attackCooldown > 0) attackCooldown -= dt;
        jumpTimer -= dt;

        // 找最近的敌人
        Enemy target = null;
        float minDist = 300f;
        for (Enemy e : enemies) {
            if (!e.alive) continue;
            float dx = e.x + e.w / 2f - x;
            float dy = e.y + e.h / 2f - y;
            float dist = (float) Math.hypot(dx, dy);
            if (dist < minDist) {
                minDist = dist;
                target = e;
            }
        }

        if (target != null) {
            // 攻击模式：朝敌人跳跃
            float dx = target.x + target.w / 2f - x;
            float dy = target.y + target.h / 2f - y;
            if (jumpTimer <= 0 && Math.abs(vx) < 50f) {
                vx = Math.signum(dx) * 180f;
                vy = -250f;
                jumpTimer = 0.6f;
            }
            // 接触伤害
            if (Math.abs(dx) < 30 && Math.abs(dy) < 30 && attackCooldown <= 0) {
                target.hurt(damage, Math.signum(dx) * 100, -50);
                attackCooldown = 0.8f;
            }
        } else {
            // 跟随模式：跟随玩家
            float dx = player.x + Player.W / 2f - x;
            float dy = player.y + Player.H / 2f - y - 40; // 在玩家上方跟随
            float dist = (float) Math.hypot(dx, dy);
            if (dist > 150f) {
                // 太远了，快速跟上
                vx = dx / dist * 200f;
                if (jumpTimer <= 0 && vy >= 0) {
                    vy = -200f;
                    jumpTimer = 0.5f;
                }
            } else if (dist > 60f) {
                // 中等距离，缓慢移动
                vx = dx / dist * 80f;
            } else {
                // 很近，原地小跳
                vx *= 0.9f;
                if (jumpTimer <= 0 && vy >= 0) {
                    vy = -120f;
                    jumpTimer = 0.8f;
                }
            }
        }

        // 物理
        vy += 600f * dt; // 重力
        x += vx * dt;
        y += vy * dt;
        vx *= 0.98f; // 空气阻力

        // 地面碰撞
        int footTile = (int) ((y + 20) / World.TILE);
        int myTile = (int) (x / World.TILE);
        if (footTile >= 0 && footTile < world.height && myTile >= 0 && myTile < world.width) {
            if (world.get(myTile, footTile) != TileType.AIR) {
                y = footTile * World.TILE - 20;
                vy = 0;
            }
        }

        // 防止掉出世界
        if (y > world.height * World.TILE) {
            x = player.x;
            y = player.y - 50;
            vy = 0;
        }
    }

    public void draw(Graphics2D g, Camera cam) {
        if (!alive) return;
        int sx = (int) (x - cam.x);
        int sy = (int) (y - cam.y);
        // 史莱姆身体（蓝色，区别于野生绿色史莱姆）
        g.setColor(new Color(80, 150, 220));
        g.fillOval(sx - 14, sy - 10, 28, 24);
        // 高光
        g.setColor(new Color(140, 200, 255, 200));
        g.fillOval(sx - 10, sy - 8, 12, 8);
        // 眼睛
        g.setColor(Color.WHITE);
        g.fillOval(sx - 6, sy - 2, 5, 5);
        g.fillOval(sx + 2, sy - 2, 5, 5);
        g.setColor(Color.BLACK);
        g.fillOval(sx - 4, sy - 1, 2, 2);
        g.fillOval(sx + 4, sy - 1, 2, 2);
        // 召唤光环
        g.setColor(new Color(100, 180, 255, 80));
        g.fillOval(sx - 18, sy - 14, 36, 32);
    }
}
