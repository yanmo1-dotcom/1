/**
 * 难度等级：影响玩家初始命数、敌机速度、生成间隔、Boss 血量。
 *
 * 选择后存入 GamePanel，传给 WaveManager / EnemyManager / Boss。
 */
public enum Difficulty {
    EASY(5, 0.8f, 1.3f, 0.7f, "简单"),
    NORMAL(3, 1.0f, 1.0f, 1.0f, "普通"),
    HARD(2, 1.3f, 0.7f, 1.5f, "困难");

    private final int initialLives;
    private final float enemySpeedMul;   // 敌机速度倍率
    private final float spawnIntervalMul;// 生成间隔倍率（>1 更稀疏）
    private final float bossHpMul;       // Boss 血量倍率
    private final String label;

    Difficulty(int initialLives, float enemySpeedMul, float spawnIntervalMul, float bossHpMul, String label) {
        this.initialLives = initialLives;
        this.enemySpeedMul = enemySpeedMul;
        this.spawnIntervalMul = spawnIntervalMul;
        this.bossHpMul = bossHpMul;
        this.label = label;
    }

    public int getInitialLives() { return initialLives; }
    public float getEnemySpeedMul() { return enemySpeedMul; }
    public float getSpawnIntervalMul() { return spawnIntervalMul; }
    public float getBossHpMul() { return bossHpMul; }
    public String getLabel() { return label; }
}
