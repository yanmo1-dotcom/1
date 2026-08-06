package com.yourname.voxelgame.entity;

/**
 * 战斗工具：扇形命中检测、击退计算。
 */
public final class CombatUtil {

    private CombatUtil() {}

    /**
     * 扇形命中判定。
     * @param attacker 攻击者
     * @param facingX  攻击朝向（+1 / -1）
     * @param range    攻击距离
     * @param arc      扇形张角（弧度）；正交侧视下主要看 x 方向同侧
     * @param target   目标
     * @return 是否命中
     */
    public static boolean inMeleeArc(Entity attacker, float facingX, float range, float arc, Entity target) {
        float dx = target.getX() - attacker.getX();
        float dy = target.getY() - attacker.getY();
        float dz = target.getZ() - attacker.getZ();
        float dist = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist > range) return false;

        // 必须在朝向一侧（x 方向）
        if (facingX > 0 && dx < 0) return false;
        if (facingX < 0 && dx > 0) return false;

        // 扇形角度：以朝向为中线，目标方向与朝向的夹角 <= arc/2
        // 朝向向量 (facingX, 0, 0)，目标方向 (dx,dy,dz)
        float dot = dx * facingX; // = |dx|
        float cosAng = dot / (dist * Math.abs(facingX) + 1e-6f);
        float halfArc = arc * 0.5f;
        if (cosAng < Math.cos(halfArc)) return false;

        return true;
    }

    /** 对目标施加击退：沿攻击朝向，给水平速度 + 小上抛。 */
    public static void applyKnockback(Entity target, float facingX, float speed, float lift) {
        target.knockback(facingX, speed, lift);
    }
}
