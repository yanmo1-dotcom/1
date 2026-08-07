import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Boss：每 5 波出现一只的大型敌机。
 *
 * 【移动】从屏幕上方入场到位后，横向来回匀速移动。
 * 【弹幕】三种攻击模式轮换，每模式持续若干秒后切换：
 *   1) 扇形 5 弹：向下方扇形发散 5 颗子弹
 *   2) 追踪单弹：朝玩家当前位置发射 1 颗较快的子弹
 *   3) 密集直射：正下方连发 3 颗紧凑子弹
 * 【血量】基础 50 × 难度倍率；头顶绘制血条。
 * 【死亡】标记 dead，由 GamePanel 触发大爆炸+必掉道具+大量分数。
 */
public class Boss {

    public static final int WIDTH = 80;
    public static final int HEIGHT = 80;

    private int x, y;
    private final int targetY;       // 入场目标 y
    private int hp;
    private final int maxHp;
    private boolean dead = false;
    private boolean entered = false; // 是否入场完毕

    private int dirX = 2;            // 横向移动方向
    private int pattern = 0;         // 当前弹幕模式 0/1/2
    private int patternTimer = 0;    // 模式剩余帧
    private int fireTimer = 0;       // 模式内射击倒计时

    public Boss(int hp) {
        this.x = GamePanel.WIDTH / 2 - WIDTH / 2;
        this.y = -HEIGHT;
        this.targetY = 60;
        this.hp = hp;
        this.maxHp = hp;
        this.patternTimer = 180;
    }

    public void update(List<Bullet> enemyBullets, int playerCenterX) {
        // 入场动画
        if (!entered) {
            y += 2;
            if (y >= targetY) {
                y = targetY;
                entered = true;
            }
            return;
        }

        // 横向来回移动
        x += dirX;
        if (x < 0) { x = 0; dirX = 2; }
        if (x > GamePanel.WIDTH - WIDTH) { x = GamePanel.WIDTH - WIDTH; dirX = -2; }

        // 弹幕模式计时
        patternTimer--;
        if (patternTimer <= 0) {
            pattern = (pattern + 1) % 3;
            patternTimer = 180;
            fireTimer = 30;
        }

        fireTimer--;
        if (fireTimer <= 0) {
            fire(enemyBullets, playerCenterX);
            fireTimer = pattern == 2 ? 8 : (pattern == 1 ? 45 : 40);
        }
    }

    private void fire(List<Bullet> enemyBullets, int playerCenterX) {
        int cx = x + WIDTH / 2;
        int cy = y + HEIGHT;
        if (pattern == 0) {
            // 扇形 5 弹
            for (int i = -2; i <= 2; i++) {
                double angle = Math.PI / 2 + i * 0.22; // 向下为基准，左右展开
                float sp = 5f;
                int dx = (int) (Math.cos(angle) * sp * 2);
                int dy = (int) (Math.sin(angle) * sp * 2);
                enemyBullets.add(new Bullet(cx + dx * 2, cy, dy, false, dx, dy));
            }
        } else if (pattern == 1) {
            // 追踪单弹：朝玩家方向
            int dx = playerCenterX - cx;
            int dy = (GamePanel.HEIGHT - cy);
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1) len = 1;
            int vx = (int) (dx / len * 6);
            int vy = (int) (dy / len * 6);
            enemyBullets.add(new Bullet(cx, cy, vy, false, vx, vy));
        } else {
            // 密集直射 3 颗
            for (int i = 0; i < 3; i++) {
                enemyBullets.add(new Bullet(cx - 2, cy + i * 14, 7, false, 0, 7));
            }
        }
    }

    public boolean hit(int damage) {
        if (!entered) return false; // 入场期间无敌
        hp -= damage;
        if (hp <= 0) {
            dead = true;
            return true;
        }
        return false;
    }

    public boolean isDead() { return dead; }
    public boolean isEntered() { return entered; }
    public int getCenterX() { return x + WIDTH / 2; }
    public int getCenterY() { return y + HEIGHT / 2; }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public void draw(Graphics2D g2d) {
        // 大型紫红机身
        g2d.setColor(new Color(150, 20, 80));
        g2d.fillRect(x + WIDTH / 2 - 14, y + 10, 28, HEIGHT - 24);
        g2d.fillRect(x, y + HEIGHT / 3, WIDTH, 14);
        // 机头
        g2d.setColor(new Color(110, 10, 60));
        Polygon nose = new Polygon();
        nose.addPoint(x + WIDTH / 2, y + HEIGHT);
        nose.addPoint(x + WIDTH / 2 - 14, y + HEIGHT - 16);
        nose.addPoint(x + WIDTH / 2 + 14, y + HEIGHT - 16);
        g2d.fillPolygon(nose);
        // 尾翼
        g2d.setColor(new Color(150, 20, 80));
        g2d.fillRect(x + WIDTH / 2 - 24, y + 4, 48, 10);

        // 顶部血条
        int barW = WIDTH + 20;
        int barH = 6;
        int barX = x - 10;
        int barY = y - 14;
        g2d.setColor(Color.DARK_GRAY);
        g2d.fillRect(barX, barY, barW, barH);
        g2d.setColor(hp > maxHp * 0.3 ? Color.RED : Color.ORANGE);
        g2d.fillRect(barX, barY, (int) ((float) hp / maxHp * barW), barH);
        g2d.setColor(Color.WHITE);
        g2d.drawString("BOSS", barX, barY - 3);
    }
}
