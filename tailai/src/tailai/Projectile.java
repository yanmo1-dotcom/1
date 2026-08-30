package tailai;

/**
 * 远程投射物（箭矢）：直线飞行 + 轻微重力。
 * 由玩家射向鼠标方向，命中敌人造成伤害，命中固体方块即消失。
 */
public class Projectile {
    public float x, y, vx, vy;
    public int damage;
    public boolean alive = true;
    public float life = 3f;
    public float angle;
    /** true=敌人发射的火球（伤害玩家），false=玩家箭矢（伤害敌人）。 */
    public boolean fromEnemy = false;
    /** true=玩家魔法弹（发光球体），false=普通箭矢。 */
    public boolean isMagic = false;

    public Projectile(float x, float y, float vx, float vy, int damage) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.angle = (float) Math.atan2(vy, vx);
    }

    /** 敌人火球构造：不受重力，直线飞行。 */
    public static Projectile enemyFireball(float x, float y, float vx, float vy, int damage) {
        Projectile p = new Projectile(x, y, vx, vy, damage);
        p.fromEnemy = true;
        p.life = 4f;
        return p;
    }

    public void update(float dt, World world) {
        if (!alive) {
            return;
        }
        if (!fromEnemy) {
            vy += 260 * dt; // 箭矢轻微下坠
        }
        x += vx * dt;
        y += vy * dt;
        life -= dt;
        if (life <= 0) {
            alive = false;
            return;
        }
        // 命中固体方块即消失
        int gx = (int) (x / World.TILE);
        int gy = (int) (y / World.TILE);
        if (world.isSolid(gx, gy)) {
            alive = false;
        }
        angle = (float) Math.atan2(vy, vx);
    }

    /** 是否命中某个敌人（宽松的包围盒判定）。 */
    public boolean hits(Enemy e) {
        return x > e.x - 2 && x < e.x + e.w + 2
                && y > e.y - 4 && y < e.y + e.h + 4;
    }
}
