import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;

/**
 * 游戏主面板
 * 负责游戏循环、输入处理、渲染和方块交互
 * 坐标系与世界一致：y=0在顶部，向下递增
 */
public class GamePanel extends JPanel implements ActionListener, KeyListener,
        MouseListener, MouseMotionListener, MouseWheelListener {

    private static final int TARGET_FPS = 60;
    private static final int REACH_DISTANCE = 5; // 玩家可交互距离（格数）

    private World world;
    private Player player;
    private Camera camera;
    private Inventory inventory;
    private javax.swing.Timer gameTimer;

    // 输入状态
    private Set<Integer> pressedKeys;
    private boolean mouseLeftDown;
    private int mouseX;
    private int mouseY;

    // 方块破坏状态
    private int breakingX = -1;
    private int breakingY = -1;
    private double breakProgress = 0;

    // 调试信息
    private boolean showDebug = false;
    private long lastFpsTime;
    private int fps;
    private int frameCount;
    private long gameStartTime;

    public GamePanel() {
        setBackground(new Color(135, 206, 235));
        setFocusable(true);
        setPreferredSize(new Dimension(1024, 640));

        world = new World();
        // 出生点：世界中央的地表上方
        int spawnX = World.WORLD_WIDTH / 2;
        int surfaceY = world.getSurfaceHeight(spawnX);
        int spawnY = surfaceY - 3; // 玩家脚底在地表上方2格
        player = new Player(world, spawnX * World.BLOCK_SIZE, spawnY * World.BLOCK_SIZE);
        camera = new Camera(1024, 640);
        inventory = new Inventory();

        pressedKeys = new HashSet<>();
        mouseLeftDown = false;
        mouseX = 0;
        mouseY = 0;
        gameStartTime = System.currentTimeMillis();

        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);

        gameTimer = new javax.swing.Timer(1000 / TARGET_FPS, this);
        gameTimer.start();
        lastFpsTime = System.currentTimeMillis();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        update();
        repaint();
    }

    private void update() {
        // FPS统计
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            fps = frameCount;
            frameCount = 0;
            lastFpsTime = now;
        }

        boolean left = pressedKeys.contains(KeyEvent.VK_A) || pressedKeys.contains(KeyEvent.VK_LEFT);
        boolean right = pressedKeys.contains(KeyEvent.VK_D) || pressedKeys.contains(KeyEvent.VK_RIGHT);
        boolean jump = pressedKeys.contains(KeyEvent.VK_W) || pressedKeys.contains(KeyEvent.VK_UP)
                || pressedKeys.contains(KeyEvent.VK_SPACE);

        player.update(left, right, jump);
        camera.update(player);
        handleBlockBreaking();
    }

    private void handleBlockBreaking() {
        if (!mouseLeftDown) {
            breakingX = -1;
            breakingY = -1;
            breakProgress = 0;
            return;
        }

        int bx = camera.screenToBlockX(mouseX);
        int by = camera.screenToBlockY(mouseY);

        if (!isInReach(bx, by)) {
            breakingX = -1;
            breakingY = -1;
            breakProgress = 0;
            return;
        }

        BlockType target = world.getBlock(bx, by);
        if (target == BlockType.AIR || !target.isBreakable()) {
            breakingX = -1;
            breakingY = -1;
            breakProgress = 0;
            return;
        }

        if (bx != breakingX || by != breakingY) {
            breakingX = bx;
            breakingY = by;
            breakProgress = 0;
        }

        double breakSpeed = 1.0 / (target.getHardness() * TARGET_FPS * 0.4);
        breakProgress += breakSpeed;

        if (breakProgress >= 1.0) {
            BlockType dropped = world.getBlock(bx, by);
            world.setBlock(bx, by, BlockType.AIR);
            inventory.addItem(dropped);
            breakProgress = 0;
            breakingX = -1;
            breakingY = -1;
        }
    }

    private boolean isInReach(int bx, int by) {
        double playerBlockX = player.getCenterX() / World.BLOCK_SIZE;
        double playerBlockY = player.getCenterY() / World.BLOCK_SIZE;
        double dx = bx + 0.5 - playerBlockX;
        double dy = by + 0.5 - playerBlockY;
        return Math.sqrt(dx * dx + dy * dy) <= REACH_DISTANCE;
    }

    private void tryPlaceBlock(int bx, int by) {
        if (!inventory.hasSelectedItem()) return;

        BlockType target = world.getBlock(bx, by);
        int placeX = bx;
        int placeY = by;

        if (target != BlockType.AIR && target != BlockType.WATER) {
            // 指向固体方块时，在鼠标最近的面外侧放置
            int bs = World.BLOCK_SIZE;
            int blockPixelX = bx * bs;
            int blockPixelY = by * bs;
            // 鼠标在世界中的像素坐标
            double worldMouseX = mouseX + camera.getX();
            double worldMouseY = mouseY + camera.getY();

            double relX = worldMouseX - blockPixelX; // 0~bs
            double relY = worldMouseY - blockPixelY; // 0~bs

            // 计算到四个面的距离，选择最近的面
            double distLeft = relX;
            double distRight = bs - relX;
            double distTop = relY;
            double distBottom = bs - relY;

            double minDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));

            if (minDist == distLeft) placeX = bx - 1;
            else if (minDist == distRight) placeX = bx + 1;
            else if (minDist == distTop) placeY = by - 1;
            else placeY = by + 1;
        }

        if (!isInReach(placeX, placeY)) return;

        // 放置位置必须是空气或水
        BlockType placeTarget = world.getBlock(placeX, placeY);
        if (placeTarget != BlockType.AIR && placeTarget != BlockType.WATER) return;

        // 检查不与玩家碰撞箱重叠
        int bs = World.BLOCK_SIZE;
        double blockLeft = placeX * bs;
        double blockRight = blockLeft + bs;
        double blockTop = placeY * bs;
        double blockBottom = blockTop + bs;

        if (player.getX() < blockRight && player.getX() + player.getWidth() > blockLeft
                && player.getY() < blockBottom && player.getY() + player.getHeight() > blockTop) {
            return;
        }

        world.setBlock(placeX, placeY, inventory.getSelectedBlock());
        inventory.consumeSelected();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        drawSky(g2d, width, height);
        drawBlocks(g2d, width, height);
        drawPlayer(g2d);
        drawBreakOverlay(g2d);
        drawTargetHighlight(g2d);
        drawCrosshair(g2d, width, height);
        inventory.draw(g2d, width, height);

        if (showDebug) drawDebug(g2d);
        drawHint(g2d, width);
    }

    private void drawSky(Graphics2D g, int width, int height) {
        GradientPaint sky = new GradientPaint(0, 0, new Color(135, 206, 235),
                0, height, new Color(200, 230, 250));
        g.setPaint(sky);
        g.fillRect(0, 0, width, height);

        // 太阳
        g.setColor(new Color(255, 240, 150));
        g.fillOval(width - 120, 60, 60, 60);
        g.setColor(new Color(255, 250, 200, 100));
        g.fillOval(width - 135, 45, 90, 90);

        // 几朵云
        g.setColor(new Color(255, 255, 255, 200));
        drawCloud(g, 100, 80);
        drawCloud(g, 400, 120);
        drawCloud(g, 700, 60);
    }

    private void drawCloud(Graphics2D g, int x, int y) {
        g.fillOval(x, y, 60, 24);
        g.fillOval(x + 20, y - 10, 50, 30);
        g.fillOval(x + 45, y, 50, 22);
    }

    private void drawBlocks(Graphics2D g, int width, int height) {
        int bs = World.BLOCK_SIZE;
        int startX = Math.max(0, (int) (camera.getX() / bs));
        int endX = Math.min(World.WORLD_WIDTH - 1, (int) ((camera.getX() + width) / bs) + 1);
        int startY = Math.max(0, (int) (camera.getY() / bs));
        int endY = Math.min(World.WORLD_HEIGHT - 1, (int) ((camera.getY() + height) / bs) + 1);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                BlockType block = world.getBlock(x, y);
                if (block == BlockType.AIR) continue;

                int sx = camera.worldToScreenX(x * bs);
                int sy = camera.worldToScreenY(y * bs);

                g.setColor(block.getColor());
                g.fillRect(sx, sy, bs, bs);

                if (!block.isTransparent()) {
                    // 顶部高光
                    g.setColor(new Color(255, 255, 255, 30));
                    g.fillRect(sx, sy, bs, 4);
                    // 底部阴影
                    g.setColor(new Color(0, 0, 0, 40));
                    g.fillRect(sx, sy + bs - 4, bs, 4);
                    // 边框
                    g.setColor(new Color(0, 0, 0, 50));
                    g.drawRect(sx, sy, bs, bs);

                    // 矿石纹理
                    if (block == BlockType.COAL_ORE) {
                        g.setColor(new Color(20, 20, 20));
                        g.fillOval(sx + 6, sy + 8, 8, 8);
                        g.fillOval(sx + 18, sy + 18, 6, 6);
                    } else if (block == BlockType.IRON_ORE) {
                        g.setColor(new Color(200, 170, 140));
                        g.fillOval(sx + 8, sy + 10, 7, 7);
                        g.fillOval(sx + 18, sy + 20, 5, 5);
                    } else if (block == BlockType.DIAMOND_ORE) {
                        g.setColor(new Color(120, 240, 240));
                        g.fillOval(sx + 10, sy + 12, 6, 6);
                        g.fillOval(sx + 20, sy + 18, 4, 4);
                    } else if (block == BlockType.GRASS) {
                        g.setColor(new Color(90, 170, 30));
                        g.fillRect(sx, sy, bs, 8);
                    } else if (block == BlockType.WOOD) {
                        g.setColor(new Color(80, 55, 35));
                        g.drawLine(sx + 8, sy, sx + 8, sy + bs);
                        g.drawLine(sx + 24, sy, sx + 24, sy + bs);
                    } else if (block == BlockType.BRICK) {
                        g.setColor(new Color(100, 40, 35));
                        g.drawLine(sx, sy + 16, sx + bs, sy + 16);
                        g.drawLine(sx + 16, sy, sx + 16, sy + 16);
                        g.drawLine(sx + 8, sy + 16, sx + 8, sy + bs);
                        g.drawLine(sx + 24, sy + 16, sx + 24, sy + bs);
                    } else if (block == BlockType.SNOW) {
                        g.setColor(new Color(255, 255, 255, 150));
                        g.fillRect(sx, sy, bs, 6);
                    }
                } else if (block == BlockType.WATER) {
                    g.setColor(new Color(255, 255, 255, 40));
                    int waveOffset = (int) ((System.currentTimeMillis() / 200 + x) % 4);
                    g.drawLine(sx, sy + 4 + waveOffset, sx + bs, sy + 4 + waveOffset);
                }
            }
        }
    }

    private void drawPlayer(Graphics2D g) {
        int sx = camera.worldToScreenX(player.getX());
        int sy = camera.worldToScreenY(player.getY());
        int w = player.getWidth();
        int h = player.getHeight();

        // 身体
        g.setColor(new Color(0, 150, 180));
        g.fillRect(sx, sy + h / 3, w, h * 2 / 3);

        // 头部
        g.setColor(new Color(200, 160, 120));
        g.fillRect(sx, sy, w, h / 3);

        // 头发
        g.setColor(new Color(80, 50, 30));
        g.fillRect(sx, sy, w, 6);

        // 眼睛
        g.setColor(Color.WHITE);
        if (player.isFacingRight()) {
            g.fillRect(sx + w - 8, sy + 8, 4, 4);
            g.setColor(Color.BLUE);
            g.fillRect(sx + w - 7, sy + 9, 2, 2);
        } else {
            g.fillRect(sx + 4, sy + 8, 4, 4);
            g.setColor(Color.BLUE);
            g.fillRect(sx + 5, sy + 9, 2, 2);
        }

        // 腿部
        g.setColor(new Color(60, 60, 150));
        g.fillRect(sx, sy + h * 2 / 3, w / 2 - 1, h / 3);
        g.fillRect(sx + w / 2 + 1, sy + h * 2 / 3, w / 2 - 1, h / 3);
    }

    private void drawBreakOverlay(Graphics2D g) {
        if (breakingX < 0 || breakProgress <= 0) return;

        int bs = World.BLOCK_SIZE;
        int sx = camera.worldToScreenX(breakingX * bs);
        int sy = camera.worldToScreenY(breakingY * bs);

        int alpha = (int) (breakProgress * 180);
        g.setColor(new Color(0, 0, 0, alpha));
        g.fillRect(sx, sy, bs, bs);

        g.setColor(new Color(30, 30, 30, 200));
        g.drawLine(sx + bs / 2, sy, sx + bs / 3, sy + bs / 2);
        g.drawLine(sx + bs / 3, sy + bs / 2, sx + bs / 4, sy + bs);
        if (breakProgress > 0.3) {
            g.drawLine(sx + bs / 2, sy, sx + bs * 2 / 3, sy + bs / 2);
        }
        if (breakProgress > 0.6) {
            g.drawLine(sx + bs * 2 / 3, sy + bs / 2, sx + bs * 3 / 4, sy + bs);
        }
    }

    private void drawTargetHighlight(Graphics2D g) {
        int bx = camera.screenToBlockX(mouseX);
        int by = camera.screenToBlockY(mouseY);

        if (!isInReach(bx, by)) return;
        if (world.getBlock(bx, by) == BlockType.AIR) return;

        int bs = World.BLOCK_SIZE;
        int sx = camera.worldToScreenX(bx * bs);
        int sy = camera.worldToScreenY(by * bs);

        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(2));
        g.drawRect(sx, sy, bs, bs);
        g.setStroke(new BasicStroke(1));
    }

    private void drawCrosshair(Graphics2D g, int width, int height) {
        int cx = width / 2;
        int cy = height / 2;
        g.setColor(new Color(255, 255, 255, 200));
        g.drawLine(cx - 10, cy, cx + 10, cy);
        g.drawLine(cx, cy - 10, cx, cy + 10);
    }

    private void drawDebug(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(8, 8, 280, 120);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.drawString("FPS: " + fps, 16, 26);
        g.drawString(String.format("玩家像素: %.0f, %.0f", player.getX(), player.getY()), 16, 42);
        g.drawString(String.format("玩家方块: %d, %d",
                (int) (player.getCenterX() / World.BLOCK_SIZE),
                (int) (player.getCenterY() / World.BLOCK_SIZE)), 16, 58);
        g.drawString(String.format("相机: %.0f, %.0f", camera.getX(), camera.getY()), 16, 74);
        g.drawString("在地面: " + player.isOnGround() + "  在水中: " + player.isInWater(), 16, 90);
        g.drawString("世界种子: " + world.getSeed(), 16, 106);
        g.drawString("鼠标方块: " + camera.screenToBlockX(mouseX) + ", " + camera.screenToBlockY(mouseY), 16, 122);
    }

    private void drawHint(Graphics2D g, int width) {
        if (System.currentTimeMillis() - gameStartTime > 10000) return;

        String[] hints = {
                "A/D 或 ←/→ 移动",
                "W/↑/空格 跳跃",
                "鼠标左键 破坏方块",
                "鼠标右键 放置方块",
                "1-9 / 滚轮 切换物品",
                "F3 显示调试信息"
        };

        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(width - 220, 16, 204, hints.length * 20 + 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        for (int i = 0; i < hints.length; i++) {
            g.drawString(hints[i], width - 210, 36 + i * 20);
        }
    }

    // ========== 键盘事件 ==========
    @Override
    public void keyPressed(KeyEvent e) {
        pressedKeys.add(e.getKeyCode());
        if (e.getKeyCode() >= KeyEvent.VK_1 && e.getKeyCode() <= KeyEvent.VK_9) {
            inventory.selectSlot(e.getKeyCode() - KeyEvent.VK_1);
        }
        if (e.getKeyCode() == KeyEvent.VK_F3) {
            showDebug = !showDebug;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        pressedKeys.remove(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    // ========== 鼠标事件 ==========
    @Override
    public void mousePressed(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();

        if (e.getButton() == MouseEvent.BUTTON1) {
            mouseLeftDown = true;
        } else if (e.getButton() == MouseEvent.BUTTON3) {
            int bx = camera.screenToBlockX(mouseX);
            int by = camera.screenToBlockY(mouseY);
            tryPlaceBlock(bx, by);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            mouseLeftDown = false;
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) { }
    @Override
    public void mouseEntered(MouseEvent e) { }
    @Override
    public void mouseExited(MouseEvent e) { }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        inventory.scrollSelect(e.getWheelRotation());
    }
}
