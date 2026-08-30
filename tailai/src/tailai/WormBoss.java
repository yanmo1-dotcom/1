package tailai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 世界吞噬者：多段蠕虫Boss。
 * 头部朝玩家移动，身体段逐节跟随，每段都有碰撞盒。
 * 整体共享血量，头部伤害更高。
 */
public class WormBoss {
    public static final int SEGMENT_COUNT = 12;
    public static final int SEG_SIZE = 28;
    public static final float SEG_DIST = 22f; // 段间距

    public float headX, headY;
    public float vx, vy;
    public int hp;
    public int maxHp;
    public boolean alive = true;
    public float hitFlash;
    public float attackCooldown;

    /** 身体段位置（索引0=头，1..N-1=身体）。 */
    private final float[] segX = new float[SEGMENT_COUNT];
    private final float[] segY = new float[SEGMENT_COUNT];

    public WormBoss(float x, float y) {
        this.headX = x;
        this.headY = y;
        this.hp = 2500;
        this.maxHp = 2500;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segX[i] = x - i * SEG_DIST;
            segY[i] = y;
        }
    }

    public void update(float dt, Player player, World world) {
        if (!alive) return;
        if (hitFlash > 0) hitFlash -= dt;
        if (attackCooldown > 0) attackCooldown -= dt;

        // 头部朝玩家移动（蠕虫式追踪）
        float dx = player.x + Player.W / 2f - headX;
        float dy = player.y + Player.H / 2f - headY;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) len = 1f;
        float speed = 130f;
        // 加一点摆动，让蠕虫移动更自然
        float wobble = (float) Math.sin(System.currentTimeMillis() * 0.003) * 40f;
        vx = dx / len * speed + (float) Math.cos(System.currentTimeMillis() * 0.002) * wobble * 0.3f;
        vy = dy / len * speed + (float) Math.sin(System.currentTimeMillis() * 0.0025) * wobble * 0.3f;

        headX += vx * dt;
        headY += vy * dt;

        // 身体段跟随：每段朝向前一段移动，保持距离
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

        // 碰撞玩家：头部接触造成伤害
        if (attackCooldown <= 0) {
            float hdx = player.x + Player.W / 2f - headX;
            float hdy = player.y + Player.H / 2f - headY;
            if (Math.hypot(hdx, hdy) < SEG_SIZE + 15) {
                player.hurtAt(25, Math.signum(vx) * 100, -80);
                attackCooldown = 1.0f;
            }
        }
    }

    /** 检测任意段是否被攻击命中，返回命中段索引（-1=未命中）。 */
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
        // 从尾到头绘制（头在最上层）
        for (int i = SEGMENT_COUNT - 1; i >= 0; i--) {
            float sx = segX[i] - cam.x;
            float sy = segY[i] - cam.y;
            int size = (i == 0) ? SEG_SIZE + 6 : SEG_SIZE - i;
            if (size < 14) size = 14;

            Color base = (i == 0) ? new Color(80, 30, 90) : new Color(100 + i * 3, 40 + i * 2, 110);
            if (hitFlash > 0 && i == 0) {
                base = Color.WHITE;
            }
            g.setColor(base);
            g.fillOval((int) (sx - size / 2f), (int) (sy - size / 2f), size, size);
            // 高光
            g.setColor(new Color(160, 80, 170, 180));
            g.fillOval((int) (sx - size / 2f + 3), (int) (sy - size / 2f + 3), size / 2, size / 3);

            // 头部：眼睛和嘴
            if (i == 0) {
                g.setColor(Color.RED);
                g.fillOval((int) (sx - 6), (int) (sy - 4), 4, 4);
                g.fillOval((int) (sx + 2), (int) (sy - 4), 4, 4);
                g.setColor(Color.BLACK);
                g.fillOval((int) (sx - 4), (int) (sy + 2), 8, 4);
            }
        }

        // Boss血条
        float bw = 300;
        float bx = GamePanel.VIEW_W / 2f - bw / 2f;
        float by = 60;
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect((int) bx - 4, (int) by - 4, (int) bw + 8, 24, 8, 8);
        g.setColor(new Color(60, 20, 70));
        g.fillRoundRect((int) bx, (int) by, (int) bw, 16, 6, 6);
        g.setColor(new Color(150, 50, 170));
        g.fillRoundRect((int) bx, (int) by, (int) (bw * Math.max(0, (float) hp / maxHp)), 16, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString("世界吞噬者 " + hp + "/" + maxHp, (int) bx + 8, (int) by + 12);
    }

    /** 获取所有身体段的中心位置（用于粒子效果）。 */
    public List<float[]> getSegmentPositions() {
        List<float[]> list = new ArrayList<>();
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            list.add(new float[]{segX[i], segY[i]});
        }
        return list;
    }
}
