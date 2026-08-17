import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.Random;

/**
 * 单架敌机：按类型决定血量、速度、射击频率与行为模式。
 *
 * 【行为】
 *   SCOUT/TANK：直线下落，周期向下射击。
 *   KAMIKAZE：不射击，每帧向玩家方向加速移动，靠碰撞造成伤害。
 *   SNIPER：慢速下落，冷却到时朝玩家当前位置发射追踪弹。
 *   GUARD：仅在 Boss 波出现，环绕 Boss 移动并偶射；Boss 死亡后转为下落。
 *
 * hit(damage) 扣血；hp<=0 标记 dead，由管理方移除并计分/爆炸。
 * 多血敌机头顶绘制血条。
 */
public class EnemyPlane {

    private final EnemyType type;
    private final int width;
    private final int height;

    private int x;
    private int y;
    private final int baseSpeed;    // 基础下落速度
    private int hp;
    private final int maxHp;
    private int shootCooldown;
    private boolean dead = false;
    private final Random random;

    // KAMIKAZE 状态
    private final int vxKamikaze;
    private final int vyKamikaze;

    // GUARD 环绕状态
    private float guardAngle;
    private final int guardRadius;
    private Integer guardCenterX;  // Boss 中心 x，null 表示无锚点（Boss 死亡）
    private Integer guardCenterY;

    public EnemyPlane(EnemyType type, int x, int y, float speedMul, Random random) {
        this.type = type;
        this.width = type.getWidth();
        this.height = type.getHeight();
        this.x = x;
        this.y = y;
        this.baseSpeed = Math.max(1, Math.round(type.getBaseSpeed() * speedMul));
        this.hp = type.getHp();
        this.maxHp = type.getHp();
        this.random = random;
        this.shootCooldown = type.getShootCooldownFrames() + random.nextInt(type.getShootJitter() + 1);

        // KAMIKAZE：朝玩家方向的速度向量，在生成时确定（撞向玩家初始位置）
        if (type == EnemyType.KAMIKAZE) {
            // 朝下方略带追踪：固定向下偏，x 留待 update 用动态追踪
            this.vxKamikaze = 0;
            this.vyKamikaze = baseSpeed + 2;
        } else {
            this.vxKamikaze = 0;
            this.vyKamikaze = 0;
        }

        if (type == EnemyType.GUARD) {
            this.guardAngle = random.nextFloat() * (float) (Math.PI * 2);
            this.guardRadius = 80 + random.nextInt(30);
        } else {
            this.guardAngle = 0;
            this.guardRadius = 0;
        }
    }

    /**
     * 通用更新：需要玩家位置（KAMIKAZE 追踪/SNIPER 瞄准）与 Boss 位置（GUARD 环绕）。
     * playerCenterX/playerCenterY 为玩家中心；bossCenterX/Y 为 Boss 中心，null 表示 Boss 不存在。
     */
    public void update(Integer playerCenterX, Integer playerCenterY,
                        Integer bossCenterX, Integer bossCenterY) {
        switch (type) {
            case KAMIKAZE:
                updateKamikaze(playerCenterX, playerCenterY);
                break;
            case GUARD:
                updateGuard(bossCenterX, bossCenterY);
                break;
            default:
                y += baseSpeed;
                break;
        }

        if (shootCooldown > 0) shootCooldown--;
        if (y > GamePanel.HEIGHT) {
            dead = true; // 越过底部，按"躲过"处理
        }
    }

    /** 自爆机：朝玩家方向加速移动。 */
    private void updateKamikaze(Integer px, Integer py) {
        if (px != null && py != null) {
            int dx = px - getCenterX();
            int dy = py - getCenterY();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len > 1) {
                int sp = baseSpeed + 2;
                x += (int) (dx / len * sp);
                y += (int) (dy / len * sp);
                return;
            }
        }
        y += baseSpeed + 2; // 无玩家参照则直冲
    }

    /** 护卫机：环绕 Boss 移动；Boss 死亡后转为下落。 */
    private void updateGuard(Integer bcx, Integer bcy) {
        if (bcx != null && bcy != null) {
            guardCenterX = bcx;
            guardCenterY = bcy;
        }
        if (guardCenterX != null && guardCenterY != null) {
            guardAngle += 0.04f; // 环绕角速度
            int tx = guardCenterX + (int) (Math.cos(guardAngle) * guardRadius) - width / 2;
            int ty = guardCenterY + (int) (Math.sin(guardAngle) * guardRadius * 0.6) - height / 2;
            // 平滑移向目标点
            x += Integer.signum(tx - x) * Math.min(Math.abs(tx - x), 4);
            y += Integer.signum(ty - y) * Math.min(Math.abs(ty - y), 4);
        } else {
            // Boss 已死，转为下落
            y += baseSpeed;
        }
    }

    /**
     * 冷却归零时返回应发射的子弹方向，否则返回 null。
     * SNIPER 朝玩家方向发射追踪弹；SCOUT/TANK/GUARD 直下射。
     */
    public ShotIntent shouldShoot(Integer playerCenterX, Integer playerCenterY) {
        if (type == EnemyType.KAMIKAZE) return null; // 自爆机不射击
        if (shootCooldown != 0 || y <= 0) return null;
        shootCooldown = type.getShootCooldownFrames() + random.nextInt(type.getShootJitter() + 1);

        if (type == EnemyType.SNIPER && playerCenterX != null && playerCenterY != null) {
            int dx = playerCenterX - getCenterX();
            int dy = playerCenterY - (y + height);
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1) len = 1;
            int vx = (int) (dx / len * 6);
            int vy = (int) (dy / len * 6);
            return new ShotIntent(getCenterX(), y + height, vx, vy);
        }
        // 直下射
        return new ShotIntent(getCenterX(), y + height, 0, 6);
    }

    /** 受到伤害；hp<=0 标记死亡。返回是否因此次伤害死亡。 */
    public boolean hit(int damage) {
        hp -= damage;
        if (hp <= 0) {
            dead = true;
            return true;
        }
        return false;
    }

    public boolean isDead() { return dead; }
    public EnemyType getType() { return type; }
    public int getWidth() { return width; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getCenterX() { return x + width / 2; }
    public int getCenterY() { return y + height / 2; }
    public boolean isKamikaze() { return type == EnemyType.KAMIKAZE; }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    public void draw(Graphics2D g2d) {
        switch (type) {
            case KAMIKAZE: drawKamikaze(g2d); return;
            case SNIPER:   drawSniper(g2d); break;
            case GUARD:    drawGuard(g2d); break;
            default:       drawStandard(g2d); break;
        }
        // 多血敌机头顶血条
        if (maxHp > 1 && hp < maxHp) {
            int barW = width;
            int barH = 3;
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(x, y - 6, barW, barH);
            g2d.setColor(Color.GREEN);
            g2d.fillRect(x, y - 6, (int) ((float) hp / maxHp * barW), barH);
        }
    }

    private void drawStandard(Graphics2D g2d) {
        Color body = type == EnemyType.SCOUT ? Color.RED : new Color(170, 30, 90);
        Color dark = type == EnemyType.SCOUT ? new Color(180, 40, 40) : new Color(120, 20, 70);
        g2d.setColor(body);
        g2d.fillRect(x + width / 2 - 6, y + 6, 12, height - 16);
        g2d.fillRect(x, y + height / 3, width, 8);
        g2d.setColor(dark);
        Polygon nose = new Polygon();
        nose.addPoint(x + width / 2, y + height);
        nose.addPoint(x + width / 2 - 6, y + height - 10);
        nose.addPoint(x + width / 2 + 6, y + height - 10);
        g2d.fillPolygon(nose);
        g2d.setColor(body);
        g2d.fillRect(x + width / 2 - 10, y + 4, 20, 6);
    }

    /** 自爆机：橙色圆形 + 闪烁引信。 */
    private void drawKamikaze(Graphics2D g2d) {
        g2d.setColor(Color.ORANGE);
        g2d.fillOval(x, y, width, height);
        g2d.setColor(Color.RED);
        g2d.fillOval(x + width / 2 - 4, y + height / 2 - 4, 8, 8);
        // 闪烁引信
        if ((System.currentTimeMillis() / 100) % 2 == 0) {
            g2d.setColor(Color.YELLOW);
            g2d.fillRect(x + width - 4, y + 2, 4, 4);
        }
    }

    /** 狙击机：蓝色细长机身 + 瞄准镜。 */
    private void drawSniper(Graphics2D g2d) {
        g2d.setColor(Color.BLUE);
        g2d.fillRect(x + width / 2 - 5, y + 4, 10, height - 12);
        g2d.fillRect(x, y + height / 2 - 4, width, 8);
        g2d.setColor(new Color(30, 60, 160));
        Polygon nose = new Polygon();
        nose.addPoint(x + width / 2, y + height);
        nose.addPoint(x + width / 2 - 5, y + height - 8);
        nose.addPoint(x + width / 2 + 5, y + height - 8);
        g2d.fillPolygon(nose);
        // 瞄准镜光点
        g2d.setColor(Color.CYAN);
        g2d.fillOval(x + width / 2 - 2, y + height - 4, 4, 4);
    }

    /** 护卫机：紫色六角形。 */
    private void drawGuard(Graphics2D g2d) {
        g2d.setColor(new Color(150, 80, 220));
        Polygon hex = new Polygon();
        int cx = x + width / 2;
        int cy = y + height / 2;
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            hex.addPoint(cx + (int) (Math.cos(a) * width / 2), cy + (int) (Math.sin(a) * height / 2));
        }
        g2d.fillPolygon(hex);
        g2d.setColor(Color.WHITE);
        g2d.fillOval(cx - 3, cy - 3, 6, 6);
    }

    /** 射击意图：生成位置与速度向量。 */
    public static class ShotIntent {
        public final int x, y, vx, vy;
        public ShotIntent(int x, int y, int vx, int vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }
}
