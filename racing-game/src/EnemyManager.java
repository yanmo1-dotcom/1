import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 敌机管理器：持有所有普通敌机与敌机子弹，负责移动、还击与回收。
 *
 * 【生成】由 WaveManager/GamePanel 调用 spawn(type) 按需生成；GUARD 需在 Boss 附近生成。
 * 【更新】update() 需传入玩家与 Boss 中心位置，供自爆机追踪、狙击机瞄准、护卫机环绕使用。
 * 【还击】每帧询问敌机射击意图；SCOUT/TANK/GUARD 直下射，SNIPER 朝玩家方向追踪射。
 * 【回收】标记 dead 的敌机从列表移除，由 GamePanel 先处理爆炸/计分再调用。
 */
public class EnemyManager {

    private final List<EnemyPlane> enemies = new ArrayList<>();
    private final List<Bullet> enemyBullets = new ArrayList<>();
    private final Random random = new Random();

    private int pendingScore = 0; // 躲过分数待 GamePanel 取走

    public EnemyManager() {
    }

    /** 由 WaveManager 调用：在屏幕顶部任意 x 位置生成指定类型敌机。 */
    public void spawn(EnemyType type, float speedMul) {
        int w = type.getWidth();
        int x = random.nextInt(Math.max(1, GamePanel.WIDTH - w));
        enemies.add(new EnemyPlane(type, x, -type.getHeight(), speedMul, random));
    }

    /** 在指定位置生成护卫机（Boss 波专用）。 */
    public void spawnGuard(int centerX, int centerY) {
        EnemyPlane guard = new EnemyPlane(EnemyType.GUARD,
                centerX - EnemyType.GUARD.getWidth() / 2,
                centerY, 1.0f, random);
        enemies.add(guard);
    }

    /**
     * 推进所有敌机与子弹。需传入玩家中心与 Boss 中心位置（可为 null）。
     */
    public void update(Integer playerCenterX, Integer playerCenterY,
                       Integer bossCenterX, Integer bossCenterY) {
        Iterator<EnemyPlane> it = enemies.iterator();
        while (it.hasNext()) {
            EnemyPlane e = it.next();
            e.update(playerCenterX, playerCenterY, bossCenterX, bossCenterY);
            EnemyPlane.ShotIntent shot = e.shouldShoot(playerCenterX, playerCenterY);
            if (shot != null) {
                enemyBullets.add(new Bullet(shot.x - Bullet.WIDTH / 2, shot.y, shot.vy, false, shot.vx, shot.vy));
            }
            if (e.isDead()) {
                it.remove();
                pendingScore += 10;
            }
        }

        enemyBullets.removeIf(b -> {
            b.update();
            return b.isOffScreen();
        });
    }

    /** 兼容旧调用：无玩家/Boss 参照时直线下落。 */
    public void update() {
        update(null, null, null, null);
    }

    /**
     * 仅推进并回收敌机子弹，不动敌机本体。
     *
     * 【为什么需要】Boss 波时普通敌机列表可能含 GUARD，但 Boss 子弹存于 enemyBullets。
     * 实际上 Boss 波用 updateBossWave() 处理；本方法保留给无参照场景。
     */
    public void updateBulletsOnly() {
        enemyBullets.removeIf(b -> {
            b.update();
            return b.isOffScreen();
        });
    }

    public List<EnemyPlane> getEnemies() { return enemies; }
    public List<Bullet> getEnemyBullets() { return enemyBullets; }

    /** 取走本帧因"躲过"产生的分数，并清零。 */
    public int collectRecycledScore() {
        int s = pendingScore;
        pendingScore = 0;
        return s;
    }

    /** 清空所有普通敌机与敌机子弹（用于炸弹清屏 / 进入 Boss 波）。 */
    public void clearAll() {
        enemies.clear();
        enemyBullets.clear();
    }

    public void draw(Graphics2D g2d) {
        for (EnemyPlane e : enemies) e.draw(g2d);
        for (Bullet b : enemyBullets) b.draw(g2d);
    }
}
