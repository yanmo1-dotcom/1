import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * 通用子弹：玩家子弹向上、敌机子弹向下，Boss 弹幕可带横向速度。
 *
 * friendly=true 表示玩家发射（向上）；false 表示敌机/Boss 发射。
 * 普通敌机子弹只设 dy；Boss 斜向/追踪子弹额外设 vx。
 * 越过屏幕边界后标记离屏，由管理方移除。
 */
public class Bullet {

    public static final int WIDTH = 4;
    public static final int HEIGHT = 14;

    private int x;
    private int y;
    private final int vx;           // 每帧横向位移（普通子弹为 0）
    private final int dy;           // 每帧纵向位移：负向上、正向下
    private final boolean friendly; // true=玩家子弹, false=敌机子弹
    private boolean offScreen = false;

    /** 普通纵向子弹（玩家与普通敌机使用）。 */
    public Bullet(int x, int y, int dy, boolean friendly) {
        this(x, y, dy, friendly, 0, dy);
    }

    /** 带横向速度的子弹（Boss 弹幕/追踪弹使用）。 */
    public Bullet(int x, int y, int dy, boolean friendly, int vx, int vy) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.dy = vy;
        this.friendly = friendly;
    }

    public void update() {
        x += vx;
        y += dy;
        if (y + HEIGHT < 0 || y > GamePanel.HEIGHT || x + WIDTH < 0 || x > GamePanel.WIDTH) {
            offScreen = true;
        }
    }

    public boolean isOffScreen() {
        return offScreen;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    /** 玩家子弹黄色、敌机子弹橙红，便于在画面中区分敌我。 */
    public void draw(Graphics2D g2d) {
        g2d.setColor(friendly ? Color.YELLOW : new Color(255, 140, 0));
        g2d.fillRect(x, y, WIDTH, HEIGHT);
    }
}
