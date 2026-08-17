import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 游戏主面板：状态机、游戏循环、渲染与所有子系统的协调中心。
 *
 * 【状态机】 MENU → PLAYING ↔ PAUSED → GAME_OVER → MENU
 * 【游戏循环】 Timer 60FPS 回调 update() + repaint()
 * 【双缓冲】 JPanel 默认开启，消除闪烁
 * 【协调职责】
 *   1) 推进 player / waveManager / enemyManager / boss
 *   2) 三类碰撞：玩家子弹 vs 敌机/Boss；敌机/敌机子弹/Boss子弹 vs 玩家；玩家 vs 道具
 *   3) 维护爆炸、飘字、屏幕震动、连击计时
 *   4) 按 state 渲染对应画面与 HUD
 */
public class GamePanel extends JPanel {

    public static final int WIDTH = 480;
    public static final int HEIGHT = 720;
    public static final int TARGET_FPS = 60;

    public enum State { MENU, PLAYING, PAUSED, GAME_OVER }

    private State state = State.MENU;

    private Difficulty difficulty = Difficulty.NORMAL;
    private PlayerPlane player;
    private EnemyManager enemyManager;
    private WaveManager waveManager;
    private Boss boss;
    private final Random random = new Random();

    private final List<Explosion> explosions = new ArrayList<>();
    private final List<FloatingText> floatingTexts = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();

    private int score = 0;
    private int combo = 0;
    private long comboLastTime = 0;

    private int shakeIntensity = 0; // 屏幕震动剩余强度

    private final HighScoreManager highScores = new HighScoreManager();
    private final SoundManager sound = new SoundManager();

    private final Set<Integer> pressedKeys = new HashSet<>();

    // 菜单按钮区域（难度选择）
    private final Rectangle[] diffButtons = {
        new Rectangle(WIDTH / 2 - 160, 320, 90, 50),
        new Rectangle(WIDTH / 2 - 45,  320, 90, 50),
        new Rectangle(WIDTH / 2 + 70,  320, 90, 50),
    };

    private final Timer timer;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        setDoubleBuffered(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                pressedKeys.add(code);
                handleKeyPress(code);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                pressedKeys.remove(e.getKeyCode());
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (state == State.MENU) {
                    for (int i = 0; i < diffButtons.length; i++) {
                        if (diffButtons[i].contains(e.getPoint())) {
                            startGame(Difficulty.values()[i]);
                            sound.playClick();
                            return;
                        }
                    }
                }
            }
        });

        int delay = 1000 / TARGET_FPS;
        timer = new Timer(delay, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                update();
                repaint();
            }
        });
    }

    public void startGame() {
        timer.start();
    }

    private void handleKeyPress(int code) {
        switch (state) {
            case MENU:
                if (code == KeyEvent.VK_1) { startGame(Difficulty.EASY); sound.playClick(); }
                else if (code == KeyEvent.VK_2) { startGame(Difficulty.NORMAL); sound.playClick(); }
                else if (code == KeyEvent.VK_3) { startGame(Difficulty.HARD); sound.playClick(); }
                else if (code == KeyEvent.VK_SPACE) { startGame(Difficulty.NORMAL); sound.playClick(); }
                break;
            case PLAYING:
                if (code == KeyEvent.VK_P || code == KeyEvent.VK_ESCAPE) { state = State.PAUSED; sound.playClick(); }
                else if (code == KeyEvent.VK_M) { sound.toggleMute(); }
                break;
            case PAUSED:
                if (code == KeyEvent.VK_P || code == KeyEvent.VK_ESCAPE) { state = State.PLAYING; sound.playClick(); }
                else if (code == KeyEvent.VK_M) { sound.toggleMute(); }
                break;
            case GAME_OVER:
                if (code == KeyEvent.VK_SPACE) { state = State.MENU; sound.playClick(); }
                else if (code == KeyEvent.VK_M) { sound.toggleMute(); }
                break;
        }
    }

    /** 从菜单开始一局游戏。 */
    private void startGame(Difficulty diff) {
        this.difficulty = diff;
        resetGame();
        state = State.PLAYING;
    }

    private void resetGame() {
        player = new PlayerPlane(WIDTH / 2 - 20, HEIGHT - 120, difficulty.getInitialLives());
        enemyManager = new EnemyManager();
        waveManager = new WaveManager(difficulty);
        boss = null;
        explosions.clear();
        floatingTexts.clear();
        powerUps.clear();
        score = 0;
        combo = 0;
        shakeIntensity = 0;
    }

    // ===== 每帧逻辑 =====

    private void update() {
        if (state != State.PLAYING) return;

        long now = System.currentTimeMillis();

        // 连击衰减：超过 2 秒未击杀则断连
        if (combo > 0 && now - comboLastTime > 2000) combo = 0;

        boolean bombRequested = player.update(pressedKeys, now);
        if (bombRequested) {
            detonateBomb();
        }

        // 波次推进
        EnemyType[] toSpawn = waveManager.update(now,
                enemyManager.getEnemies().size(),
                boss != null && !boss.isDead());
        for (EnemyType t : toSpawn) {
            enemyManager.spawn(t, waveManager.speedMulForWave());
        }

        // Boss 波
        Integer bossCX = null, bossCY = null;
        if (waveManager.isBossWave()) {
            if (!waveManager.isBossSpawned()) {
                enemyManager.clearAll();
                boss = waveManager.createBoss();
                // 为新 Boss 生成 2~3 架护卫机环绕保护
                int guardCount = 2 + random.nextInt(2);
                for (int i = 0; i < guardCount; i++) {
                    enemyManager.spawnGuard(boss.getCenterX(), boss.getCenterY());
                }
                sound.playBossWarn();
                shakeIntensity = 12;
            } else if (boss != null && !boss.isDead()) {
                boss.update(enemyManager.getEnemyBullets(), player.getCenterX());
                bossCX = boss.getCenterX();
                bossCY = boss.getCenterY();
            }
            // Boss 波推进护卫机（环绕）与子弹；Boss 子弹存于此
            enemyManager.update(player.getCenterX(), player.getCenterY(), bossCX, bossCY);
        } else {
            enemyManager.update(player.getCenterX(), player.getCenterY(), null, null);
            boss = null;
        }

        // 碰撞协调
        handleBulletVsEnemies();
        if (waveManager.isBossWave() && boss != null && !boss.isDead()) {
            handleBulletVsBoss();
            handleBossVsPlayer();
        }
        handleEnemyVsPlayer();
        handlePowerUps();

        // Boss 死亡处理
        if (waveManager.isBossWave() && boss != null && boss.isDead()) {
            onBossDefeated(now);
        }

        score += enemyManager.collectRecycledScore();

        // 震动衰减
        if (shakeIntensity > 0) shakeIntensity = Math.max(0, shakeIntensity - 1);

        // 效果推进与清理
        explosions.forEach(Explosion::update);
        explosions.removeIf(Explosion::isFinished);
        floatingTexts.forEach(FloatingText::update);
        floatingTexts.removeIf(FloatingText::isFinished);
        powerUps.removeIf(PowerUp::isRemoved);

        if (player.getLives() <= 0) {
            state = State.GAME_OVER;
            highScores.submit(score);
        }
    }

    /** 释放炸弹：清空所有敌机+敌机子弹，每只给爆炸与分数。 */
    private void detonateBomb() {
        for (EnemyPlane e : enemyManager.getEnemies()) {
            explosions.add(new Explosion(e.getCenterX(), e.getCenterY(), random, false));
            score += 20;
        }
        enemyManager.clearAll();
        shakeIntensity = 15;
        sound.playExplosion();
        addFloatingText("BOMB!", WIDTH / 2, HEIGHT / 2, Color.ORANGE);
    }

    /** 玩家子弹 vs 普通敌机。 */
    private void handleBulletVsEnemies() {
        List<Bullet> playerBullets = player.getBullets();
        Iterator<Bullet> bit = playerBullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            Iterator<EnemyPlane> eit = enemyManager.getEnemies().iterator();
            while (eit.hasNext()) {
                EnemyPlane e = eit.next();
                if (b.getBounds().intersects(e.getBounds())) {
                    bit.remove();
                    boolean killed = e.hit(1);
                    if (killed) {
                        eit.remove();
                        onEnemyKilled(e);
                    }
                    break;
                }
            }
        }
    }

    /** 玩家子弹 vs Boss。 */
    private void handleBulletVsBoss() {
        List<Bullet> playerBullets = player.getBullets();
        Iterator<Bullet> bit = playerBullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            if (b.getBounds().intersects(boss.getBounds())) {
                bit.remove();
                boolean killed = boss.hit(1);
                if (!killed) {
                    // 命中火花
                    explosions.add(new Explosion(b.getBounds().x, b.getBounds().y, random, false));
                }
            }
        }
    }

    private void onEnemyKilled(EnemyPlane e) {
        explosions.add(new Explosion(e.getCenterX(), e.getCenterY(), random, false));
        sound.playExplosion();

        combo++;
        comboLastTime = System.currentTimeMillis();
        int base = e.getType() == EnemyType.TANK ? 40 : 20;
        int gained = (int) (base * (1 + combo / 10.0));
        score += gained;
        addFloatingText("+" + gained, e.getCenterX(), e.getCenterY(),
                combo > 3 ? Color.ORANGE : Color.WHITE);

        // 道具掉落
        double dropChance = e.getType() == EnemyType.TANK ? 0.25 : 0.12;
        if (random.nextDouble() < dropChance) {
            PowerUpType[] types = PowerUpType.values();
            powerUps.add(new PowerUp(types[random.nextInt(types.length)],
                    e.getCenterX() - PowerUp.SIZE / 2, e.getCenterY()));
        }
    }

    private void onBossDefeated(long now) {
        explosions.add(new Explosion(boss.getCenterX(), boss.getCenterY(), random, true));
        sound.playBossDeath();
        score += 500;
        addFloatingText("+500", boss.getCenterX(), boss.getCenterY(), Color.YELLOW);
        // Boss 必掉道具
        PowerUpType[] types = PowerUpType.values();
        powerUps.add(new PowerUp(types[random.nextInt(types.length)],
                boss.getCenterX() - PowerUp.SIZE / 2, boss.getCenterY()));
        boss = null;
        waveManager.bossDefeated(now);
        shakeIntensity = 18;
    }

    /** 敌机机身 / 敌机子弹 vs 玩家。 */
    private void handleEnemyVsPlayer() {
        boolean hurt = false;
        Rectangle pb = player.getBounds();

        for (EnemyPlane e : enemyManager.getEnemies()) {
            if (pb.intersects(e.getBounds())) { hurt = true; break; }
        }
        if (!hurt) {
            Iterator<Bullet> it = enemyManager.getEnemyBullets().iterator();
            while (it.hasNext()) {
                if (pb.intersects(it.next().getBounds())) {
                    it.remove();
                    hurt = true;
                    break;
                }
            }
        }
        if (hurt) onPlayerHurt();
    }

    /** Boss 机身 / Boss 子弹 vs 玩家。 */
    private void handleBossVsPlayer() {
        Rectangle pb = player.getBounds();
        if (pb.intersects(boss.getBounds())) { onPlayerHurt(); return; }
        Iterator<Bullet> it = enemyManager.getEnemyBullets().iterator();
        while (it.hasNext()) {
            if (pb.intersects(it.next().getBounds())) {
                it.remove();
                onPlayerHurt();
                return;
            }
        }
    }

    private void onPlayerHurt() {
        boolean tookLife = player.hit();
        if (tookLife) {
            shakeIntensity = 12;
            sound.playHit();
            combo = 0;
        }
    }

    /** 道具拾取。 */
    private void handlePowerUps() {
        Rectangle pb = player.getBounds();
        Iterator<PowerUp> it = powerUps.iterator();
        while (it.hasNext()) {
            PowerUp p = it.next();
            p.update();
            if (pb.intersects(p.getBounds())) {
                it.remove();
                player.applyPowerUp(p.getType());
                sound.playPowerUp();
                String label = "+" + p.getType().name();
                addFloatingText(label, p.getBounds().x, p.getBounds().y, p.getType().getColor());
            }
        }
    }

    private void addFloatingText(String text, int x, int y, Color color) {
        floatingTexts.add(new FloatingText(text, x, y, color));
    }

    // ===== 渲染 =====

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        // 屏幕震动：整体平移
        if (shakeIntensity > 0) {
            int dx = random.nextInt(shakeIntensity * 2 + 1) - shakeIntensity;
            int dy = random.nextInt(shakeIntensity * 2 + 1) - shakeIntensity;
            g2d.translate(dx, dy);
        }

        drawBackground(g2d);

        if (state == State.MENU) {
            drawMenu(g2d);
        } else {
            drawWorld(g2d);
            drawHud(g2d);
            if (state == State.PAUSED) drawPaused(g2d);
            if (state == State.GAME_OVER) drawGameOver(g2d);
        }

        g2d.dispose();
    }

    private void drawBackground(Graphics2D g2d) {
        g2d.setColor(new Color(20, 24, 40));
        g2d.fillRect(-20, -20, WIDTH + 40, HEIGHT + 40);
    }

    private void drawWorld(Graphics2D g2d) {
        for (PowerUp p : powerUps) p.draw(g2d);
        enemyManager.draw(g2d);
        if (waveManager != null && waveManager.isBossWave() && boss != null && !boss.isDead()) {
            boss.draw(g2d);
        }
        if (player != null) {
            for (Bullet b : player.getBullets()) b.draw(g2d);
            player.draw(g2d);
        }
        for (Explosion ex : explosions) ex.draw(g2d);
        for (FloatingText ft : floatingTexts) ft.draw(g2d);

        // 波间提示
        if (waveManager != null && waveManager.isInBreak()) {
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 32));
            int next = waveManager.getWave() + 1;
            String msg = (next % 5 == 0) ? "第 " + next + " 波  BOSS!" : "第 " + next + " 波";
            drawCenteredText(g2d, msg, WIDTH / 2, HEIGHT / 2);
        }
    }

    private void drawHud(Graphics2D g2d) {
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
        g2d.drawString("分数: " + score, 10, 25);

        // 连击
        if (combo > 1) {
            g2d.setColor(Color.ORANGE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
            g2d.drawString("连击 x" + combo, 10, 50);
        }

        // 波次
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g2d.drawString("波次: " + (waveManager != null ? waveManager.getWave() : 0), 10, 70);
        g2d.drawString("最高: " + highScores.getHighScore(), 10, 88);

        // 生命三角
        g2d.setColor(Color.GREEN);
        for (int i = 0; i < player.getLives(); i++) {
            int tx = WIDTH - 30 - i * 24;
            Polygon life = new Polygon();
            life.addPoint(tx, 10);
            life.addPoint(tx - 8, 26);
            life.addPoint(tx + 8, 26);
            g2d.fillPolygon(life);
        }

        // 火力 / 炸弹 / 护盾指示
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.drawString("火力:" + player.getFireLevel(), WIDTH - 110, 42);
        g2d.setColor(Color.ORANGE);
        g2d.drawString("炸弹:" + player.getBombs() + " (X)", WIDTH - 110, 60);
        if (player.hasShield()) {
            g2d.setColor(new Color(120, 180, 255));
            g2d.drawString("护盾", WIDTH - 110, 78);
        }

        // 静音指示
        if (sound.isMuted()) {
            g2d.setColor(Color.GRAY);
            g2d.drawString("静音(M)", WIDTH / 2 - 25, 25);
        }
    }

    private void drawMenu(Graphics2D g2d) {
        // 标题
        g2d.setColor(Color.YELLOW);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 48));
        drawCenteredText(g2d, "打 飞 机", WIDTH / 2, 160);
        g2d.setColor(Color.CYAN);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
        drawCenteredText(g2d, "选择难度开始游戏", WIDTH / 2, 220);

        // 难度按钮
        Difficulty[] diffs = Difficulty.values();
        for (int i = 0; i < diffButtons.length; i++) {
            Rectangle r = diffButtons[i];
            g2d.setColor(new Color(60, 70, 110));
            g2d.fillRect(r.x, r.y, r.width, r.height);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(r.x, r.y, r.width, r.height);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 20));
            drawCenteredText(g2d, diffs[i].getLabel(), r.x + r.width / 2, r.y + r.height / 2 + 7);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            drawCenteredText(g2d, "按 " + (i + 1), r.x + r.width / 2, r.y + r.height + 16);
        }

        // 操作说明
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        int y = 430;
        g2d.drawString("操作：", WIDTH / 2 - 80, y); y += 22;
        g2d.drawString("WASD / 方向键 - 移动", WIDTH / 2 - 80, y); y += 22;
        g2d.drawString("X - 释放炸弹清屏", WIDTH / 2 - 80, y); y += 22;
        g2d.drawString("P / ESC - 暂停    M - 静音", WIDTH / 2 - 80, y); y += 22;
        g2d.drawString("子弹自动发射", WIDTH / 2 - 80, y);

        // 最高分
        g2d.setColor(Color.ORANGE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        drawCenteredText(g2d, "最高分: " + highScores.getHighScore(), WIDTH / 2, 600);
    }

    private void drawPaused(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 44));
        drawCenteredText(g2d, "已 暂 停", WIDTH / 2, HEIGHT / 2 - 20);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 20));
        drawCenteredText(g2d, "按 P 或 ESC 继续", WIDTH / 2, HEIGHT / 2 + 30);
    }

    private void drawGameOver(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.fillRect(0, 0, WIDTH, HEIGHT);

        g2d.setColor(Color.RED);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 48));
        drawCenteredText(g2d, "GAME OVER", WIDTH / 2, HEIGHT / 2 - 50);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 28));
        drawCenteredText(g2d, "最终得分: " + score, WIDTH / 2, HEIGHT / 2);

        g2d.setColor(Color.ORANGE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 22));
        drawCenteredText(g2d, "最高分: " + highScores.getHighScore(), WIDTH / 2, HEIGHT / 2 + 35);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        drawCenteredText(g2d, "按 空格 回主菜单", WIDTH / 2, HEIGHT / 2 + 80);
    }

    private void drawCenteredText(Graphics2D g2d, String text, int cx, int cy) {
        FontMetrics fm = g2d.getFontMetrics();
        int w = fm.stringWidth(text);
        g2d.drawString(text, cx - w / 2, cy);
    }
}
