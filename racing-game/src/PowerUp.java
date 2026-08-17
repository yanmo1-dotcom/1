import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * 道具实体：从被击毁的敌机处掉落，缓慢下落，被玩家碰到即拾取生效。
 *
 * 越过屏幕底部后移除（未拾取则消失）。
 */
public class PowerUp {

    public static final int SIZE = 22;
    private static final int SPEED = 2;

    private final PowerUpType type;
    private int x, y;
    private boolean removed = false;

    public PowerUp(PowerUpType type, int x, int y) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public void update() {
        y += SPEED;
        if (y > GamePanel.HEIGHT) removed = true;
    }

    public boolean isRemoved() { return removed; }
    public PowerUpType getType() { return type; }

    public Rectangle getBounds() {
        return new Rectangle(x, y, SIZE, SIZE);
    }

    public void draw(Graphics2D g2d) {
        // 彩色圆底
        g2d.setColor(type.getColor());
        g2d.fillOval(x, y, SIZE, SIZE);
        g2d.setColor(Color.WHITE);
        g2d.drawOval(x, y, SIZE, SIZE);
        // 符号
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.drawString(type.getSymbol(), x + 6, y + 16);
    }
}
