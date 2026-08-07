import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;

/**
 * 浮动飘字：击中/拾取时显示 "+10" 等。
 *
 * 向上漂浮并淡出，life 归零后由 GamePanel 移除。
 */
public class FloatingText {

    private final String text;
    private float x, y;
    private final float vy;
    private int life;
    private final int maxLife;
    private final Color color;

    public FloatingText(String text, int x, int y, Color color) {
        this.text = text;
        this.x = x;
        this.y = y;
        this.vy = -0.8f;
        this.life = 40;
        this.maxLife = 40;
        this.color = color;
    }

    public void update() {
        y += vy;
        x += 0.2f; // 微微右飘
        life--;
    }

    public boolean isFinished() {
        return life <= 0;
    }

    public void draw(Graphics2D g2d) {
        float ratio = (float) life / maxLife;
        int alpha = (int) (255 * ratio);
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
        g2d.drawString(text, (int) x, (int) y);
    }
}
