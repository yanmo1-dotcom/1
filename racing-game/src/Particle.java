import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Random;

/**
 * 单个爆炸粒子。
 *
 * 持有位置、速度向量、颜色与生命值；update() 每帧位移并衰减生命；
 * draw() 半径与透明度随生命衰减，形成飞溅消散效果。
 */
public class Particle {

    private float x, y;
    private final float vx;
    private float vy;
    private final Color color;
    private int life;        // 剩余生命帧
    private final int maxLife;

    public Particle(float x, float y, float vx, float vy, Color color, int life) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.color = color;
        this.life = life;
        this.maxLife = life;
    }

    public void update() {
        x += vx;
        y += vy;
        vy += 0.05f; // 轻微重力，粒子向下加速
        life--;
    }

    public boolean isDead() {
        return life <= 0;
    }

    public void draw(Graphics2D g2d) {
        float ratio = (float) life / maxLife;
        int alpha = (int) (255 * ratio);
        int radius = Math.max(1, (int) (4 * ratio) + 1);
        g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
        g2d.fillOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
    }

    /** 在 (x,y) 处生成 count 个随机方向、颜色的粒子，用于爆炸效果。 */
    public static java.util.List<Particle> burst(float x, float y, int count, Random rnd) {
        java.util.List<Particle> list = new java.util.ArrayList<>();
        Color[] palette = {Color.RED, Color.ORANGE, Color.YELLOW, new Color(255, 180, 60)};
        for (int i = 0; i < count; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            float speed = 1.5f + rnd.nextFloat() * 4.5f;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;
            Color c = palette[rnd.nextInt(palette.length)];
            int life = 25 + rnd.nextInt(20);
            list.add(new Particle(x, y, vx, vy, c, life));
        }
        return list;
    }
}
