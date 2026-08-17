import java.util.Random;

/**
 * 波次系统：推进关卡、控制敌机生成节奏与 Boss 波。
 *
 * 【普通波】每波需生成 spawnCount 架敌机（随波次递增），按墙钟间隔逐个生成；
 *           全部生成完毕且屏幕清空后，进入波间 2 秒提示，再开下一波。
 * 【Boss 波】每 5 波（第 5/10/15…）为 Boss 波：清空普通敌机后生成一只 Boss，
 *           Boss 死亡后进入下一波。
 * 【难度递增】敌机速度倍率随波次缓慢上升；生成间隔随波次略微缩短。
 */
public class WaveManager {

    private final Random random = new Random();
    private final Difficulty difficulty;

    private int wave = 0;            // 当前波次（0 表示尚未开始）
    private int spawnedThisWave = 0; // 本波已生成数量
    private long lastSpawnTime = 0;
    private long waveBreakEnd = 0;   // 波间提示结束时刻；0 表示不在波间

    private boolean bossWave = false;
    private boolean bossSpawned = false;

    public WaveManager(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public int getWave() { return wave; }
    public boolean isBossWave() { return bossWave; }
    public boolean isBossSpawned() { return bossSpawned; }
    public boolean isInBreak() { return waveBreakEnd > 0; }

    /** 本波需要生成的敌机总数。 */
    private int spawnCountForWave(int w) {
        return 4 + w * 2;
    }

    /** 本波生成间隔（毫秒），随波次缩短，乘难度倍率。 */
    private long spawnIntervalForWave(int w) {
        long base = Math.max(500, 1200 - w * 40);
        return (long) (base * difficulty.getSpawnIntervalMul());
    }

    /** 敌机速度倍率：基础难度倍率 × (1 + 波次微增)。GamePanel 生成敌机时取用。 */
    public float speedMulForWave() {
        return difficulty.getEnemySpeedMul() * (1f + wave * 0.04f);
    }

    /**
     * 推进波次逻辑。返回需要本帧生成的敌机类型数组（通常 0 或 1 个）；
     * Boss 波时返回空，Boss 由 GamePanel 通过 spawnBoss() 取得。
     */
    public EnemyType[] update(long now, int aliveEnemies, boolean bossAlive) {
        // 波间提示期
        if (waveBreakEnd > 0) {
            if (now >= waveBreakEnd) {
                waveBreakEnd = 0;
                startNextWave(now);
            }
            return new EnemyType[0];
        }

        if (bossWave) {
            return new EnemyType[0]; // Boss 波由 GamePanel 处理生成与判定
        }

        // 普通波：按间隔生成
        if (spawnedThisWave < spawnCountForWave(wave)) {
            if (now - lastSpawnTime > spawnIntervalForWave(wave)) {
                lastSpawnTime = now;
                spawnedThisWave++;
                return new EnemyType[] { rollEnemyType(wave) };
            }
            return new EnemyType[0];
        }

        // 本波已全部生成，等待屏幕清空
        if (aliveEnemies == 0) {
            spawnedThisWave = 0;
            // 进入波间提示（2 秒）
            waveBreakEnd = now + 2000;
        }
        return new EnemyType[0];
    }

    private void startNextWave(long now) {
        wave++;
        bossWave = (wave % 5 == 0);
        bossSpawned = false;
        spawnedThisWave = 0;
        lastSpawnTime = now;
    }

    /** 按波次加权抽取本帧生成的敌机类型。波次越高，高级敌机出现概率越大。 */
    private EnemyType rollEnemyType(int w) {
        double r = random.nextDouble();
        // 概率门槛随波次递增
        double kamikazeP = 0.10 + Math.min(0.15, w * 0.015);   // 自爆机
        double sniperP  = 0.08 + Math.min(0.15, w * 0.012);    // 狙击机
        double tankP    = 0.12 + Math.min(0.18, w * 0.018);    // 坦克
        if (r < kamikazeP) return EnemyType.KAMIKAZE;
        r -= kamikazeP;
        if (r < sniperP)  return EnemyType.SNIPER;
        r -= sniperP;
        if (r < tankP)   return EnemyType.TANK;
        return EnemyType.SCOUT;
    }

    /** Boss 波时由 GamePanel 调用：创建 Boss。 */
    public Boss createBoss() {
        bossSpawned = true;
        int hp = (int) (50 * difficulty.getBossHpMul() * (1f + (wave / 5 - 1) * 0.3f));
        return new Boss(hp);
    }

    /** Boss 被击毁后由 GamePanel 调用，结束 Boss 波并进入波间。 */
    public void bossDefeated(long now) {
        bossWave = false;
        bossSpawned = false;
        waveBreakEnd = now + 2000;
    }
}
