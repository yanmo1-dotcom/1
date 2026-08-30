package tailai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 机械毁灭者（The Destroyer）：困难模式机械蠕虫Boss。
 * 比世界吞噬者更大、更硬，每段会定期发射激光。
 */
public class DestroyerBoss {
    public static final int SEGMENT_COUNT = 20;
    public static final int SEG_SIZE = 34;
    public static final float SEG_DIST = 26f;

    public float headX, headY;
    public float vx, vy;
    public int hp;
    public int maxHp;
    public boolean alive = true;
    public float hitFlash;
    public float attackCooldown;
    public float laserTimer; // 激光发射计时

    private final float[] segX = new float[SEGMENT_COUNT];
    private final float[] segY = new float[SEGMENT_COUNT];

    public DestroyerBoss(float x, float y) {
        this.headX = x;
        this.headY = y;
        this.hp = 8000;
        this.maxHp = 8000;
        this.laserTimer = 3f;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segX[i] = x - i * SEG_DIST;
            segY[i] = y;
        }
    }

    public void update(float dt, Player player, World world, List<Projectile> projectiles) {
        if (!alive) return;
        if (hitFlash > 0) hitFlash -= dt;
        if (attackCooldown > 0) attackCooldown -= dt;
        if (laserTimer > 0) laserTimer -= dt;

        // 头部朝玩家移动（比世界吞噬者更快）
        float dx = player.x + Player.W / 2f - headX;
        float dy = player.y + Player.H / 2f - headY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) len = 1f;
        float speed = 170f;
        float wobble = (float) Math.sin(System.currentTimeMillis() * 0.004) * 50f;
        vx = dx / len * speed + (float) Math.cos(System.currentTimeMillis() * 0.003) * wobble * 0.3f;
        vy = dy / len * speed + (float) Math.sin(System.currentTimeMillis() * 0.0035) * wobble * 0.3f;

        headX += vx * dt;
        headY += vy * dt;

        // 身体段跟随
        segX[0] = headX;
        segY[0] = headY;
        for (int i = 1; i < SEGMENT_COUNT; i++) {
            float sdx = segX[i - 1] - segX[i];
            float sdy = segY[i - 1] - segY[i];
            float slen = (float) Math.hypot(sdx, sdy);
            if (slen > SEG_DIST) {
                float pull = (slen - SEG_DIST) / slen;
                segX[i] += sdx * pull;
                segY[i] += sdy * pull;
            }
        }

        // 头部接触伤害
        if (attackCooldown <= 0) {
            float hdx = player.x + Player.W / 2f - headX;
            float hdy = player.y + Player.H / 2f - headY;
            if (Math.hypot(hdx, hdy) < SEG_SIZE + 15) {
                player.hurtAt(40, Math.signum(vx) * 120, -100);
                attackCooldown = 0.8f;
            }
        }

        // 每3秒，随机3-5个体段发射激光
        if (laserTimer <= 0) {
            laserTimer = 3f;
            int laserCount = 3 + (int)(Math.random() * 3);
            for (int i = 0; i < laserCount; i++) {
                int segIdx = 2 + (int)(Math.random() * (SEGMENT_COUNT - 4));
                float sx = segX[segIdx];
                float sy = segY[segIdx];
                float ldx = player.x + Player.W / 2f - sx;
                float ldy = player.y + Player.H / 2f - sy;
                float llen = (float) Math.hypot(ldx, ldy);
                if (llen < 1f) llen = 1f;
                float lspeed = 300f;
                Projectile laser = Projectile.enemyFireball(
                        sx, sy, ldx / llen * lspeed, ldy / llen * lspeed, 20);
                projectiles.add(laser);
            }
        }
    }

    /** 检测任意段是否被攻击命中。 */
    public int hitSegment(float ax, float ay, float aw, float ah) {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float cx = segX[i], cy = segY[i];
            if (cx > ax - SEG_SIZE / 2f && cx < ax + aw + SEG_SIZE / 2f
                    && cy > ay - SEG_SIZE / 2f && cy < ay + ah + SEG_SIZE / 2f) {
                return i;
            }
        }
        return -1;
    }

    /** 受到伤害（头部双倍伤害）。 */
    public void damage(int amount, int segmentIndex) {
        int dmg = (segmentIndex == 0) ? amount * 2 : amount;
        hp -= dmg;
        hitFlash = 0.15f;
        if (hp <= 0) {
            alive = false;
        }
    }

    public void draw(Graphics2D g, Camera cam) {
        if (!alive) return;
        // 从尾到头绘制
        for (int i = SEGMENT_COUNT - 1; i >= 0; i--) {
            float sx = segX[i] - cam.x;
            float sy = segY[i] - cam.y;
            int size = (i == 0) ? SEG_SIZE + 8 : SEG_SIZE - i / 2;
            if (size < 16) size = 16;

            Color base = (i == 0) ? new Color(120, 30, 30) : new Color(140 + i, 50, 50);
            if (hitFlash > 0 && i == 0) {
                base = Color.WHITE;
            }
            // 金属外壳
            g.setColor(base);
            g.fillOval((int) (sx - size / 2f), (int) (sy - size / 2f), size, size);
            // 金属高光
            g.setColor(new Color(200, 100, 100, 180));
            g.fillOval((int) (sx - size / 2f + 3), (int) (sy - size / 2f + 3), size / 2, size / 3);
            // 金属接缝
            g.setColor(new Color(60, 20, 20));
            g.drawOval((int) (sx - size / 2f), (int) (sy - size / 2f), size, size);
            // 红色指示灯
            if (i > 0 && i % 3 == 0) {
                g.setColor(new Color(255, 50, 50));
                g.fillOval((int) (sx - 2), (int) (sy - 2), 4, 4);
            }

            // 头部：眼睛和嘴
            if (i == 0) {
                g.setColor(new Color(255, 200, 50));
                g.fillOval((int) (sx - 8), (int) (sy - 5), 5, 5);
                g.fillOval((int) (sx + 3), (int) (sy - 5), 5, 5);
                g.setColor(Color.RED);
                g.fillOval((int) (sx - 7), (int) (sy - 4), 3, 3);
                g.fillOval((int) (sx + 4), (int) (sy - 4), 3, 3);
                // 机械嘴
                g.setColor(new Color(40, 10, 10));
                g.fillRoundRect((int) (sx - 8), (int) (sy + 3), 16, 6, 3, 3);
                g.setColor(new Color(200, 200, 210));
                for (int t = 0; t < 4; t++) {
                    int[] tx = {(int)(sx - 6 + t * 4), (int)(sx - 4 + t * 4), (int)(sx - 5 + t * 4)};
                    int[] ty = {(int)(sy + 3), (int)(sy + 3), (int)(sy + 7)};
                    g.fillPolygon(tx, ty, 3);
                }
            }
        }

        // Boss血条
        float bw = 350;
        float bx = GamePanel.VIEW_W / 2f - bw / 2f;
        float by = 60;
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect((int) bx - 4, (int) by - 4, (int) bw + 8, 26, 8, 8);
        g.setColor(new Color(60, 15, 15));
        g.fillRoundRect((int) bx, (int) by, (int) bw, 18, 6, 6);
        g.setColor(new Color(200, 50, 50));
        g.fillRoundRect((int) bx, (int) by, (int) (bw * Math.max(0, (float) hp / maxHp)), 18, 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13));
        g.drawString("机械毁灭者 " + hp + "/" + maxHp, (int) bx + 8, (int) by + 13);
    }

    public List<float[]> getSegmentPositions() {
        List<float[]> list = new ArrayList<>();
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            list.add(new float[]{segX[i], segY[i]});
        }
        return list;
    }
}
