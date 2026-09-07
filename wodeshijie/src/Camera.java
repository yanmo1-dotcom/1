/**
 * 相机类
 * 跟随玩家移动，控制视图偏移
 */
public class Camera {
    private double x;  // 相机左上角X（像素）
    private double y;  // 相机左上角Y（像素）
    private int viewWidth;
    private int viewHeight;

    // 平滑跟随系数（0-1，越大跟随越快）
    private static final double SMOOTHING = 0.1;

    public Camera(int viewWidth, int viewHeight) {
        this.viewWidth = viewWidth;
        this.viewHeight = viewHeight;
        this.x = 0;
        this.y = 0;
    }

    /**
     * 更新相机位置，平滑跟随玩家
     */
    public void update(Player player) {
        // 目标位置：让玩家处于屏幕中央
        double targetX = player.getCenterX() - viewWidth / 2.0;
        double targetY = player.getCenterY() - viewHeight / 2.0;

        // 平滑移动
        x += (targetX - x) * SMOOTHING;
        y += (targetY - y) * SMOOTHING;

        // 限制相机不超出世界边界
        double maxX = World.WORLD_WIDTH * World.BLOCK_SIZE - viewWidth;
        double maxY = World.WORLD_HEIGHT * World.BLOCK_SIZE - viewHeight;
        x = Math.max(0, Math.min(x, maxX));
        y = Math.max(0, Math.min(y, maxY));
    }

    /** 世界坐标转屏幕坐标 */
    public int worldToScreenX(double worldX) {
        return (int) (worldX - x);
    }

    /** 世界坐标转屏幕坐标 */
    public int worldToScreenY(double worldY) {
        return (int) (worldY - y);
    }

    /** 屏幕坐标转世界方块坐标 */
    public int screenToBlockX(int screenX) {
        return (int) Math.floor((screenX + x) / World.BLOCK_SIZE);
    }

    /** 屏幕坐标转世界方块坐标 */
    public int screenToBlockY(int screenY) {
        return (int) Math.floor((screenY + y) / World.BLOCK_SIZE);
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public void setViewSize(int width, int height) {
        this.viewWidth = width;
        this.viewHeight = height;
    }
}
