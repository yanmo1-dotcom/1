import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 玩家飞机：四方向移动、自动射击、生命/护盾/火力/炸弹系统。
 *
 * 【移动】每帧根据 pressedKeys 决定方向，支持上下左右同按（对角线）。
 * 【射击】自动连发，火力等级 fireLevel 决定单/双/三发；墙钟冷却。
 * 【护盾】shield=true 时吸收下一次伤害，hit() 优先消耗护盾不扣命。
 * 【炸弹】按 X 释放：消耗一枚，清屏（由 GamePanel 执行清屏逻辑）。
 * 【无敌】被击中扣命后进入 ~1.5 秒无敌，期间闪烁且不参与碰撞判定。
 */
public class PlayerPlane {

    public static final int WIDTH = 40;
    public static final int HEIGHT = 44;
    private static final int SPEED = 6;
    private static final int SHOOT_COOLDOWN_MS = 130;
    private static final int INVINCIBLE_FRAMES = 90;
    private static final int MAX_LIVES = 5;

    private int x, y;
    private int lives;
    private int fireLevel = 1;   // 1 单发 / 2 双发 / 3 三发
    private boolean shield = false;
    private int bombs = 1;       // 携带炸弹数
    private int invincibleTimer = 0;
    private long lastShootTime = 0;
    private final List<Bullet> bullets = new ArrayList<>();

    public PlayerPlane(int x, int y, int initialLives) {
        this.x = x;
        this.y = y;
        this.lives = initialLives;
    }

    public int getLives() { return lives; }
    public int getFireLevel() { return fireLevel; }
    public boolean hasShield() { return shield; }
    public int getBombs() { return bombs; }
    public boolean isInvincible() { return invincibleTimer > 0; }

    /** 被击中：有护盾则消耗护盾，否则扣命+无敌。返回是否真的扣命（用于触发震动）。 */
    public boolean hit() {
        if (invincibleTimer > 0) return false;
        if (shield) {
            shield = false;
            invincibleTimer = 30; // 护盾破短暂无敌避免连击
            return false;
        }
        lives--;
        invincibleTimer = INVINCIBLE_FRAMES;
        return true;
    }

    /** 道具生效。 */
    public void applyPowerUp(PowerUpType type) {
        switch (type) {
            case HEAL:    lives = Math.min(MAX_LIVES, lives + 1); break;
            case FIRE_UP: fireLevel = Math.min(3, fireLevel + 1); break;
            case SHIELD:  shield = true; break;
            case BOMB:    bombs++; break;
        }
    }

    /**
     * 每帧逻辑：移动、自动射击、自身子弹推进。
     * 返回 true 表示本帧请求释放炸弹（按 X 且有存量），由 GamePanel 执行清屏。
     */
    public boolean update(Set<Integer> keys, long now) {
        if (invincibleTimer > 0) invincibleTimer--;

        boolean left  = keys.contains(KeyEvent.VK_LEFT)  || keys.contains(KeyEvent.VK_A);
        boolean right = keys.contains(KeyEvent.VK_RIGHT) || keys.contains(KeyEvent.VK_D);
        boolean up    = keys.contains(KeyEvent.VK_UP)    || keys.contains(KeyEvent.VK_W);
        boolean down  = keys.contains(KeyEvent.VK_DOWN)  || keys.contains(KeyEvent.VK_S);

        if (left)  x -= SPEED;
        if (right) x += SPEED;
        if (up)    y -= SPEED;
        if (down)  y += SPEED;
        clampToBounds();

        // 自动连发：按火力等级生成 1/2/3 发
        if (now - lastShootTime > SHOOT_COOLDOWN_MS) {
            int cx = x + WIDTH / 2 - Bullet.WIDTH / 2;
            if (fireLevel == 1) {
                bullets.add(new Bullet(cx, y, -12, true));
            } else if (fireLevel == 2) {
                bullets.add(new Bullet(cx - 8, y + 4, -12, true));
                bullets.add(new Bullet(cx + 8, y + 4, -12, true));
            } else {
                bullets.add(new Bullet(cx, y, -12, true));
                bullets.add(new Bullet(cx - 10, y + 6, -12, true));
                bullets.add(new Bullet(cx + 10, y + 6, -12, true));
            }
            lastShootTime = now;
        }

        bullets.removeIf(b -> {
            b.update();
            return b.isOffScreen();
        });

        // 释放炸弹
        boolean requestBomb = keys.contains(KeyEvent.VK_X) && bombs > 0;
        if (requestBomb) bombs--;
        return requestBomb;
    }

    private void clampToBounds() {
        if (x < 0) x = 0;
        if (x > GamePanel.WIDTH - WIDTH) x = GamePanel.WIDTH - WIDTH;
        if (y < 0) y = 0;
        if (y > GamePanel.HEIGHT - HEIGHT) y = GamePanel.HEIGHT - HEIGHT;
    }

    public List<Bullet> getBullets() { return bullets; }
    public void removeBullet(Bullet b) { bullets.remove(b); }

    public int getCenterX() { return x + WIDTH / 2; }
    public int getCenterY() { return y + HEIGHT / 2; }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    public void draw(Graphics2D g2d) {
        // 无敌闪烁：偶数周期跳过绘制
        if (invincibleTimer > 0 && (invincibleTimer / 4) % 2 == 0) return;

        // 护盾光环
        if (shield) {
            g2d.setColor(new Color(120, 180, 255, 90));
            g2d.fillOval(x - 8, y - 8, WIDTH + 16, HEIGHT + 16);
            g2d.setColor(new Color(150, 200, 255, 180));
            g2d.drawOval(x - 8, y - 8, WIDTH + 16, HEIGHT + 16);
        }

        // 机身
        g2d.setColor(Color.GREEN);
        g2d.fillRect(x + WIDTH / 2 - 6, y + 8, 12, HEIGHT - 16);
        g2d.fillRect(x, y + 20, WIDTH, 8);

        // 机头（朝上）
        g2d.setColor(new Color(40, 180, 40));
        Polygon nose = new Polygon();
        nose.addPoint(x + WIDTH / 2, y);
        nose.addPoint(x + WIDTH / 2 - 6, y + 10);
        nose.addPoint(x + WIDTH / 2 + 6, y + 10);
        g2d.fillPolygon(nose);

        // 尾翼
        g2d.setColor(Color.GREEN);
        g2d.fillRect(x + WIDTH / 2 - 10, y + HEIGHT - 10, 20, 6);
    }
}
