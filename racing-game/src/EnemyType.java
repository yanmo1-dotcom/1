/**
 * 敌机类型：决定血量、速度、射击频率与外观。
 *
 * SCOUT：1 血、速度快、偶射，红色小型。
 * TANK：3 血、速度慢、频射，紫红色更大，头顶显示血条。
 * KAMIKAZE：1 血、冲向玩家、不射击，橙色小型。靠碰撞造成伤害。
 * SNIPER：2 血、慢速下落、瞄准玩家发射追踪弹，蓝色。
 * GUARD：2 血、环绕 Boss 移动、偶射，紫色。仅在 Boss 波出现。
 */
public enum EnemyType {
    SCOUT(1, 3, 90, 40, 24, 44),
    TANK(3, 1, 60, 30, 52, 56),
    KAMIKAZE(1, 4, 0, 0, 28, 28),
    SNIPER(2, 1, 130, 40, 40, 44),
    GUARD(2, 2, 110, 30, 36, 40);

    private final int hp;
    private final int baseSpeed;     // 基础下落速度，实际乘难度倍率
    private final int shootCooldownFrames; // 平均射击冷却帧
    private final int shootJitter;   // 冷却随机抖动范围
    private final int width;
    private final int height;

    EnemyType(int hp, int baseSpeed, int shootCooldownFrames, int shootJitter, int width, int height) {
        this.hp = hp;
        this.baseSpeed = baseSpeed;
        this.shootCooldownFrames = shootCooldownFrames;
        this.shootJitter = shootJitter;
        this.width = width;
        this.height = height;
    }

    public int getHp() { return hp; }
    public int getBaseSpeed() { return baseSpeed; }
    public int getShootCooldownFrames() { return shootCooldownFrames; }
    public int getShootJitter() { return shootJitter; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
