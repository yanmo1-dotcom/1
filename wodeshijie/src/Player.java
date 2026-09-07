/**
 * 玩家类
 * 处理玩家位置、物理、移动和碰撞检测
 */
public class Player {
    // 玩家尺寸（像素）
    public static final int WIDTH = 20;       // 宽度约0.625格
    public static final int HEIGHT = 56;      // 高度约1.75格

    // 物理参数
    private static final double GRAVITY = 0.6;       // 重力加速度
    private static final double MOVE_SPEED = 4.0;    // 水平移动速度
    private static final double JUMP_FORCE = 11.0;   // 跳跃力
    private static final double MAX_FALL_SPEED = 15.0; // 最大下落速度
    private static final double FRICTION = 0.75;     // 地面摩擦力

    // 位置（左上角像素坐标）
    private double x;
    private double y;

    // 速度
    private double vx;
    private double vy;

    private boolean onGround;
    private boolean facingRight; // 朝向，true为右

    private World world;

    public Player(World world, double startX, double startY) {
        this.world = world;
        this.x = startX;
        this.y = startY;
        this.vx = 0;
        this.vy = 0;
        this.onGround = false;
        this.facingRight = true;
    }

    /**
     * 更新玩家物理状态
     * @param left  是否按住左移
     * @param right 是否按住右移
     * @param jump  是否按住跳跃
     */
    public void update(boolean left, boolean right, boolean jump) {
        // 水平输入
        if (left && !right) {
            vx = -MOVE_SPEED;
            facingRight = false;
        } else if (right && !left) {
            vx = MOVE_SPEED;
            facingRight = true;
        } else {
            // 无输入时应用摩擦
            if (onGround) {
                vx *= FRICTION;
                if (Math.abs(vx) < 0.1) vx = 0;
            }
        }

        // 跳跃
        if (jump && onGround) {
            vy = -JUMP_FORCE;
            onGround = false;
        }

        // 应用重力
        vy += GRAVITY;
        if (vy > MAX_FALL_SPEED) vy = MAX_FALL_SPEED;

        // 移动并处理碰撞
        moveX();
        moveY();
    }

    /** 水平移动与碰撞 */
    private void moveX() {
        x += vx;

        // 检查碰撞
        if (collides()) {
            // 回退到碰撞前位置
            x -= vx;
            // 逐像素推进直到贴墙
            double step = Math.signum(vx);
            while (!collides()) {
                x += step;
            }
            x -= step;
            vx = 0;
        }
    }

    /** 垂直移动与碰撞 */
    private void moveY() {
        y += vy;
        onGround = false;

        if (collides()) {
            y -= vy;
            double step = Math.signum(vy);
            while (!collides()) {
                y += step;
            }
            y -= step;

            if (vy > 0) {
                onGround = true; // 落地
            }
            vy = 0;
        }
    }

    /** 检查玩家碰撞箱是否与固体方块重叠 */
    private boolean collides() {
        int bs = World.BLOCK_SIZE;
        // 计算玩家覆盖的方块范围
        int startX = (int) Math.floor(x / bs);
        int endX = (int) Math.floor((x + WIDTH - 1) / bs);
        int startY = (int) Math.floor(y / bs);
        int endY = (int) Math.floor((y + HEIGHT - 1) / bs);

        for (int bx = startX; bx <= endX; bx++) {
            for (int by = startY; by <= endY; by++) {
                if (world.isSolid(bx, by)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 检查玩家是否在水中 */
    public boolean isInWater() {
        int bs = World.BLOCK_SIZE;
        int bx = (int) Math.floor((x + WIDTH / 2.0) / bs);
        int by = (int) Math.floor((y + HEIGHT / 2.0) / bs);
        return world.getBlock(bx, by) == BlockType.WATER;
    }

    // Getter 和 Setter
    public double getX() { return x; }
    public double getY() { return y; }
    public double getVx() { return vx; }
    public double getVy() { return vy; }
    public boolean isOnGround() { return onGround; }
    public boolean isFacingRight() { return facingRight; }
    public int getWidth() { return WIDTH; }
    public int getHeight() { return HEIGHT; }

    /** 获取玩家中心X坐标 */
    public double getCenterX() { return x + WIDTH / 2.0; }
    /** 获取玩家中心Y坐标 */
    public double getCenterY() { return y + HEIGHT / 2.0; }

    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
