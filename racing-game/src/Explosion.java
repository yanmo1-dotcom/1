import java.awt.Graphics2D;
import java.util.List;
import java.util.Random;

/**
 * 爆炸效果：一组粒子的集合。
 *
 * 在击毁点生成，每帧推进所有粒子；当全部粒子死亡时 isFinished() 返回 true，
 * 由 GamePanel 移除该爆炸。BIG 规格用于 Boss 死亡，粒子更多更猛。
 */
public class Explosion {

    private final List<Particle> particles;

    public Explosion(float x, float y, Random rnd, boolean big) {
        int count = big ? 60 : 16;
        this.particles = Particle.burst(x, y, count, rnd);
    }

    public void update() {
        particles.forEach(Particle::update);
        particles.removeIf(Particle::isDead);
    }

    public boolean isFinished() {
        return particles.isEmpty();
    }

    public void draw(Graphics2D g2d) {
        for (Particle p : particles) {
            p.draw(g2d);
        }
    }
}
