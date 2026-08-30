package tailai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * 友好 NPC：向导。在玩家附近自由走动，右键可对话并购买基础物品（物品交换，无货币）。
 * NPC 不会被敌人攻击，也不会攻击敌人。
 */
public class Npc {

    public static final int W = 20;
    public static final int H = 38;

    public final String name;
    public float x, y;
    public float vx, vy;
    public boolean onGround;
    public int facing = 1;
    /** 入住的房屋坐标（像素），-1 表示未入住。 */
    public float homeX = -1, homeY = -1;
    public boolean hasHome = false;
    private float walkTimer;
    private float idleTimer;

    public Npc(String name, float x, float y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    /** 入住指定房屋（像素坐标）。 */
    public void setHome(float hx, float hy) {
        this.homeX = hx;
        this.homeY = hy;
        this.hasHome = true;
        this.x = hx;
        this.y = hy;
    }

    public Rectangle bounds() {
        return new Rectangle((int) x, (int) y, W, H);
    }

    /** 简单 AI：在玩家附近随机走动，偶尔停下；不远离玩家超过 8 格。 */
    public void update(float dt, World world, Player p) {
        walkTimer -= dt;
        idleTimer -= dt;

        float refX = hasHome ? homeX : (p.x + Player.W / 2f);
        float cx = x + W / 2f;
        float dist = Math.abs(refX - cx);

        // 离参考点太远就朝家/玩家走
        if (dist > (hasHome ? 5 * World.TILE : 8 * World.TILE)) {
            facing = (refX > cx) ? 1 : -1;
            vx = facing * 60f;
        } else if (walkTimer > 0) {
            vx = facing * 45f;
        } else {
            vx = 0;
            if (idleTimer <= 0) {
                // 随机决定：继续走或停下
                if (Math.random() < 0.5f) {
                    facing = Math.random() < 0.5f ? -1 : 1;
                    walkTimer = 1f + (float) Math.random() * 2f;
                } else {
                    idleTimer = 1f + (float) Math.random() * 2f;
                }
            }
        }

        // 重力
        vy += 800f * dt;
        if (vy > 600f) {
            vy = 600f;
        }

        // 分轴碰撞（同玩家简化版）
        x += vx * dt;
        boolean hitWall = resolveCollisionX(world);
        y += vy * dt;
        onGround = false;
        resolveCollisionY(world);

        // 撞墙则反向（避免卡在墙边）
        if (hitWall && walkTimer > 0) {
            facing = -facing;
            walkTimer = 0.5f + (float) Math.random();
        }
    }

    private boolean resolveCollisionX(World world) {
        int left = (int) Math.floor(x / World.TILE);
        int right = (int) Math.floor((x + W - 1) / World.TILE);
        int top = (int) Math.floor(y / World.TILE);
        int bottom = (int) Math.floor((y + H - 1) / World.TILE);
        boolean hit = false;
        for (int gy = top; gy <= bottom; gy++) {
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, gy)) {
                    if (vx > 0) {
                        x = gx * World.TILE - W;
                    } else if (vx < 0) {
                        x = (gx + 1) * World.TILE;
                    }
                    vx = 0;
                    hit = true;
                }
            }
        }
        return hit;
    }

    private void resolveCollisionY(World world) {
        int left = (int) Math.floor(x / World.TILE);
        int right = (int) Math.floor((x + W - 1) / World.TILE);
        int top = (int) Math.floor(y / World.TILE);
        int bottom = (int) Math.floor((y + H - 1) / World.TILE);
        for (int gy = top; gy <= bottom; gy++) {
            for (int gx = left; gx <= right; gx++) {
                if (world.isSolid(gx, gy)) {
                    if (vy > 0) {
                        y = gy * World.TILE - H;
                        onGround = true;
                    } else if (vy < 0) {
                        y = (gy + 1) * World.TILE;
                    }
                    vy = 0;
                }
            }
        }
    }

    private Color shirtColor() {
        switch (name) {
            case "商人": return new Color(120, 80, 50);
            case "护士": return new Color(220, 220, 230);
            case "军火商": return new Color(80, 60, 40);
            case "爆破专家": return new Color(180, 60, 50);
            case "树妖": return new Color(80, 160, 80);
            case "渔夫": return new Color(60, 130, 170);
            default: return new Color(60, 100, 180);
        }
    }

    private Color hatColor() {
        switch (name) {
            case "商人": return new Color(80, 50, 30);
            case "护士": return new Color(240, 240, 250);
            case "军火商": return new Color(50, 40, 30);
            case "爆破专家": return new Color(120, 40, 30);
            case "树妖": return new Color(50, 120, 50);
            case "渔夫": return new Color(40, 90, 130);
            default: return new Color(90, 90, 100);
        }
    }

    /** 渲染：蓝色衣服的向导小人。 */
    public void draw(Graphics2D g, float camX, float camY) {
        int sx = (int) (x - camX);
        int sy = (int) (y - camY);
        // 身体（按职业区分衣服颜色）
        g.setColor(shirtColor());
        g.fillRect(sx + 2, sy + 14, W - 4, 16);
        // 头
        g.setColor(new Color(230, 200, 170));
        g.fillRect(sx + 4, sy + 2, W - 8, 12);
        // 帽子（按职业区分）
        g.setColor(hatColor());
        g.fillRect(sx + 3, sy, W - 6, 5);
        // 眼睛
        g.setColor(Color.BLACK);
        if (facing > 0) {
            g.fillRect(sx + 11, sy + 7, 2, 2);
            g.fillRect(sx + 15, sy + 7, 2, 2);
        } else {
            g.fillRect(sx + 3, sy + 7, 2, 2);
            g.fillRect(sx + 7, sy + 7, 2, 2);
        }
        // 腿
        g.setColor(new Color(50, 50, 70));
        g.fillRect(sx + 3, sy + 30, 6, 8);
        g.fillRect(sx + 11, sy + 30, 6, 8);
        // 名字
        g.setColor(new Color(255, 255, 255, 220));
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 11));
        int nw = g.getFontMetrics().stringWidth(name);
        g.drawString(name, sx + W / 2 - nw / 2, sy - 6);
    }
}
