package tailai;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 游戏主面板：状态机（主菜单 / 多人菜单 / 游戏中 / 暂停 / 合成 / 操作说明）+ 主循环 + 渲染。
 * 支持三种运行模式：SOLO（单机）、HOST（创建主机）、CLIENT（加入主机）。
 */
public class GamePanel extends JPanel implements Runnable {

    // ================= 尺寸常量 =================
    public static final int TILE = 16;
    public static final int VIEW_W = 1280;
    public static final int VIEW_H = 720;
    private static final int HOTBAR_SLOTS = 10;
    private static final int DEFAULT_PORT = 25565;

    // ================= 运行模式 =================
    enum NetRole {SOLO, HOST, CLIENT}

    // ================= 状态机 =================
    private enum State {
        MAIN_MENU, MP_MENU, PLAYING, PAUSED, CRAFTING, INVENTORY, NPC_DIALOG, HELP
    }

    // ================= 依赖 =================
    private final World world = new World();
    private final Player player = new Player();
    private final Camera cam = new Camera();
    private final InputHandler in = new InputHandler();
    private InputHandler.Snapshot snap;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Npc> npcs = new ArrayList<>();
    private Npc activeNpc;
    private String npcHint = "";
    /** 已入住的房屋区域（格子坐标矩形），避免重复分配。 */
    private final List<Rectangle> occupiedHouses = new ArrayList<>();
    private float houseCheckTimer = 5f;
    private final List<Particle> particles = new ArrayList<>();
    // 天气：0=晴，1=雨
    private int weather = 0;
    private float weatherTimer = 60f;
    private float rainSfxTimer = 0;
    private final List<Particle> raindrops = new ArrayList<>();
    private final List<Drop> drops = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final Random rnd = new Random();

    // ================= 网络 =================
    private NetRole netRole = NetRole.SOLO;
    private GameServer netServer;
    private GameClient netClient;
    private final Map<Integer, RemotePlayer> remotePlayers = new LinkedHashMap<>();
    private final Map<Integer, Enemy> netEnemyById = new HashMap<>();
    private final Map<Integer, String> playerNames = new LinkedHashMap<>();
    private int mySlot = 0;
    private boolean netConnected;
    private float netSyncTimer;
    private float netSendTimer;
    private int netEnemyIdSeq = 1;

    // ================= 运行状态 =================
    private State state = State.MAIN_MENU;
    private volatile boolean running = true;
    private Thread gameThread;
    private long lastTime;
    private float fps;
    private int frames;
    private float frameTimer;
    private float dayTime = 0.30f; // 0..1，0.25 天亮，0.75 天黑
    private static final float DAY_CYCLE = 150f;
    private boolean bloodMoon = false; // 血月事件
    private boolean bloodMoonAnnounced = false;
    private boolean wasNight = false;  // 上一帧是否夜晚（用于检测昼夜切换）
    private boolean hardMode = false;  // 困难模式（击败血肉墙后开启）
    private boolean hardModeAnnounced = false;
    private float mouseX = VIEW_W / 2f;
    private float mouseY = VIEW_H / 2f;
    private float mineProgress;
    private int mineGX = -1;
    private int mineGY = -1;
    private float placeCooldown; // 方块放置冷却，防止按住左键快速放置
    private String statusMsg = "";
    private float statusTimer;
    private int craftScroll = 0; // 合成面板滚动偏移（像素）
    private State helpReturnState = State.MAIN_MENU;
    private float spawnTimer = 3f;

    // ================= 背包拖拽 =================
    private ItemStack dragStack;       // 当前拖动的物品
    private boolean dragFromHotbar;    // 来源是否热键栏
    private int dragFromIndex = -1;    // 来源格子索引

    // ================= 聊天 =================
    private boolean chatActive;
    private final StringBuilder chatBuf = new StringBuilder();
    private final List<String> chatMessages = new ArrayList<>();
    private static final int MAX_CHAT_MSGS = 30;

    // ================= 局域网房间列表 =================
    private final List<LanDiscovery.Room> discoveredRooms = new ArrayList<>();
    private final List<Rectangle> roomRects = new ArrayList<>();
    private boolean roomScanning;
    private String roomScanInfo = "";

    // ================= 菜单按钮（主菜单/多人菜单/暂停/说明页） =================
    private final List<Button> menuButtons = new ArrayList<>();
    private final List<Button> mpButtons = new ArrayList<>();
    private final List<Button> pauseButtons = new ArrayList<>();
    private final List<Button> helpButtons = new ArrayList<>();

    // ================= 渲染缓存 =================
    private BufferedImage tileLayer;      // 方块层滚动缓冲（覆盖视口+1边）
    private int tileOX, tileOY;           // 缓冲左上全局格
    private int tileCacheW, tileCacheH;   // 缓冲尺寸（格）
    private long lastWorldRender;         // 方块修改计数（触发局部重绘）
    private final List<DamageNumber> dmgNums = new ArrayList<>();
    private int fpsFrames;
    private long fpsLast;
    private int fpsShown;
    // ---- 屏幕震动 ----
    private float shakeTimer;
    private float shakeIntensity;
    // ---- 智能光标 ----
    private boolean smartCursor = false;

    // ================= 手柄状态 =================
    private boolean wasLeftDown;
    private boolean wasRightDown;

    // ================= Boss =================
    private WormBoss wormBoss; // 世界吞噬者（多段蠕虫Boss）
    private DestroyerBoss destroyerBoss; // 机械毁灭者（困难模式机械蠕虫Boss）

    // ================= 小地图 =================
    private boolean showMinimap = true;
    private static final int MINIMAP_W = 180;
    private static final int MINIMAP_H = 120;
    private BufferedImage minimapImg;
    private long minimapLastUpdate = 0;
    private static final long MINIMAP_UPDATE_INTERVAL = 200; // 200ms更新一次

    // ================= 哥布林入侵 =================
    private boolean goblinInvasion = false;
    private int goblinKilled = 0;
    private int goblinTotal = 0;
    private float goblinSpawnTimer = 0;
    private boolean goblinInvasionDefeated = false; // 曾经击败过（解锁哥布林工匠）

    // ================= 成就系统 =================
    private final List<Achievement> achievements = Achievement.createAll();
    private final List<Achievement> achievementNotifications = new ArrayList<>();
    private float achievementTimer = 0;
    private boolean showAchievementPanel = false;
    // 成就检测用的状态
    private boolean hasMined = false;
    private boolean hasPlaced = false;
    private boolean hasKilled = false;
    private boolean hasFished = false;
    private final java.util.Set<Integer> collectedItems = new java.util.HashSet<>();

    // ================= 召唤仆从 =================
    private Minion minion; // 当前仆从（null表示无）
    private int maxMinions = 1; // 最大仆从数

    // ================= 海盗入侵 =================
    private boolean pirateInvasion = false;
    private int pirateKilled = 0;
    private int pirateTotal = 0;
    private float pirateSpawnTimer = 0;
    private boolean pirateInvasionDefeated = false;

    // ================= 粒子/掉落物 =================
    static class Particle {
        float x, y, vx, vy, life, maxLife;
        int color;
        Particle(float x, float y, float vx, float vy, float life, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.maxLife = life; this.color = color;
        }
    }

    static class Drop {
        int itemId = -1;  // -1 表示金币
        int coinValue = 0; // 金币数量（itemId=-1 时有效）
        float x, y;
        float vx, vy;     // 掉落物物理
        float bobT;
        float life = 120f; // 2 分钟后消失，防止内存泄漏
        Drop(int itemId, float x, float y) {
            this.itemId = itemId; this.x = x; this.y = y;
            this.vx = (float)(Math.random() * 60 - 30);
            this.vy = -100f;
        }
        /** 金币掉落物构造。 */
        static Drop coin(int value, float x, float y) {
            Drop d = new Drop(-1, x, y);
            d.coinValue = value;
            return d;
        }
    }

    /** 攻击伤害数字（上飘淡出）。 */
    static class DamageNumber {
        float x, y, life, maxLife;
        int dmg;
        DamageNumber(float x, float y, int dmg) {
            this.x = x;
            this.y = y;
            this.dmg = dmg;
            this.life = 0.9f;
            this.maxLife = 0.9f;
        }
    }

    // ================= 远端玩家（联机渲染） =================
    static class RemotePlayer {
        final int slot;
        final String name;
        float x, y, tx, ty;
        int hp, facing, weaponId;
        float invuln;
        RemotePlayer(int slot, String name) {
            this.slot = slot;
            this.name = name;
        }
    }

    // ================= 菜单按钮 =================
    static class Button {
        final String label;
        final float x, y, w, h;
        final Runnable action;

        Button(String label, float x, float y, float w, float h, Runnable action) {
            this.label = label; this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
        }

        boolean contains(float px, float py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }

    // ================= 构造 =================
    public GamePanel() {
        setPreferredSize(new Dimension(VIEW_W, VIEW_H));
        setFocusable(true);
        addKeyListener(in);
        addMouseListener(in);
        addMouseMotionListener(in);
        addMouseWheelListener(in);

        world.generate(System.nanoTime());
        spawnInitialEnemies();
        resetMenuButtons();
    }

    private void resetMenuButtons() {
        menuButtons.clear();
        float cw = 300, ch = 54;
        float cx = (VIEW_W - cw) / 2f;
        float cy = 300;
        menuButtons.add(new Button("开始游戏", cx, cy, cw, ch, () -> startSolo()));
        menuButtons.add(new Button("多人游戏", cx, cy + ch + 18, cw, ch, () -> state = State.MP_MENU));
        menuButtons.add(new Button("操作说明", cx, cy + 2 * (ch + 18), cw, ch, () -> {
            helpReturnState = State.MAIN_MENU;
            state = State.HELP;
        }));
        menuButtons.add(new Button("退出游戏", cx, cy + 3 * (ch + 18), cw, ch, () -> System.exit(0)));

        mpButtons.clear();
        mpButtons.add(new Button("创建主机", cx, cy, cw, ch, () -> startHost(DEFAULT_PORT)));
        mpButtons.add(new Button("加入游戏", cx, cy + ch + 18, cw, ch, () -> promptJoin()));
        mpButtons.add(new Button("刷新房间", cx, cy + 2 * (ch + 18), cw, ch, () -> scanRooms()));
        mpButtons.add(new Button("返回", cx, cy + 3 * (ch + 18), cw, ch, () -> state = State.MAIN_MENU));

        pauseButtons.clear();
        pauseButtons.add(new Button("继续游戏", cx, cy, cw, ch, () -> state = State.PLAYING));
        pauseButtons.add(new Button("操作说明", cx, cy + ch + 18, cw, ch, () -> {
            helpReturnState = State.PAUSED;
            state = State.HELP;
        }));
        pauseButtons.add(new Button("返回主菜单", cx, cy + 2 * (ch + 18), cw, ch, () -> backToMenu()));
        pauseButtons.add(new Button("退出游戏", cx, cy + 3 * (ch + 18), cw, ch, () -> System.exit(0)));

        helpButtons.clear();
        helpButtons.add(new Button("返回", (VIEW_W - 200) / 2f, VIEW_H - 120, 200, 50, () -> backFromHelp()));
    }

    private void startSolo() {
        netRole = NetRole.SOLO;
        shutdownNet();
        world.generate(System.nanoTime());
        spawnInitialEnemies();
        player.respawn(world, 0);
        player.clear();
        spawnGuideNpc();
        state = State.PLAYING;
        statusMsg = "单人游戏，祝开荒愉快！（右键向导可对话/购买）";
        statusTimer = 3f;
    }

    private void spawnGuideNpc() {
        npcs.clear();
        // 向导生成在玩家右侧 3 格的地面上
        float nx = player.x + 3 * World.TILE;
        float ny = player.y;
        npcs.add(new Npc("向导", nx, ny));
    }

    // ================= 联机启动 =================
    void startHost(int port) {
        netRole = NetRole.HOST;
        world.generate(System.nanoTime());
        spawnInitialEnemies();
        player.clear();
        mySlot = 0;
        netConnected = true;
        try {
            netServer = new GameServer(world.seed);
            netServer.start(port);
            LanDiscovery.startAnnounce("主机", port);
            playerNames.clear();
            playerNames.putAll(netServer.names());
            player.respawn(world, 0);
            spawnGuideNpc();
            state = State.PLAYING;
            statusMsg = "已创建主机，端口 " + port + "（等待玩家加入）";
            statusTimer = 4f;
        } catch (IOException e) {
            netRole = NetRole.SOLO;
            state = State.MAIN_MENU;
            statusMsg = "创建主机失败：" + e.getMessage();
            statusTimer = 4f;
        }
    }

    void startClient(String host, int port) {
        netRole = NetRole.CLIENT;
        netConnected = false;
        remotePlayers.clear();
        netEnemyById.clear();
        playerNames.clear();
        try {
            netClient = new GameClient();
            netClient.connect(host, port, System.getProperty("user.name", "玩家"));
            player.clear();
            state = State.PLAYING;
            statusMsg = "正在连接 " + host + ":" + port + " ...";
            statusTimer = 4f;
        } catch (IOException e) {
            netRole = NetRole.SOLO;
            state = State.MAIN_MENU;
            statusMsg = "连接失败：" + e.getMessage();
            statusTimer = 4f;
        }
    }

    private void promptJoin() {
        String ip = JOptionPane.showInputDialog(this, "输入主机 IP 地址", "加入游戏", JOptionPane.PLAIN_MESSAGE);
        if (ip == null || ip.trim().isEmpty()) {
            return;
        }
        startClient(ip.trim(), DEFAULT_PORT);
    }

    private void shutdownNet() {
        LanDiscovery.stopAnnounce();
        if (netServer != null) {
            netServer.stop();
            netServer = null;
        }
        if (netClient != null) {
            netClient.close();
            netClient = null;
        }
        remotePlayers.clear();
        netEnemyById.clear();
        netConnected = false;
        mySlot = 0;
    }

    private void backToMenu() {
        shutdownNet();
        netRole = NetRole.SOLO;
        state = State.MAIN_MENU;
        resetMenuButtons();
    }

    private void backFromHelp() {
        if (state == State.HELP) {
            state = helpReturnState;
        }
    }

    // ================= 启动 =================
    public void start() {
        lastTime = System.nanoTime();
        gameThread = new Thread(this, "game-loop");
        gameThread.setDaemon(false);
        gameThread.start();
    }

    @Override
    public void run() {
        final long targetFrameNs = 1000000000L / 60; // 稳定 60 FPS
        while (running) {
            long frameStart = System.nanoTime();
            float dt = Math.min(0.05f, (frameStart - lastTime) / 1e9f);
            lastTime = frameStart;

            frames++;
            frameTimer += dt;
            if (frameTimer >= 0.5f) {
                fps = frames / frameTimer;
                frames = 0;
                frameTimer = 0;
            }

            try {
                update(dt);
                repaint();
            } catch (Throwable t) {
                // 单帧异常不崩溃游戏，打印堆栈并继续
                t.printStackTrace();
                statusMsg = "帧异常：" + t.getClass().getSimpleName();
                statusTimer = 3f;
            }

            // 精确帧率控制：先 sleep 到目标前 2ms，再紧忙等待补精度（Windows sleep 精度约 15ms）
            long elapsed = System.nanoTime() - frameStart;
            long sleepNs = targetFrameNs - elapsed - 2_000_000L; // 留 2ms 给忙等待
            if (sleepNs > 0) {
                try {
                    Thread.sleep(sleepNs / 1000000L);
                } catch (InterruptedException e) {
                    break;
                }
            }
            while (System.nanoTime() - frameStart < targetFrameNs) {
                // 紧忙等待，不用 yield 避免让渡 CPU 导致精度损失
            }
        }
    }

    // ================= 状态更新 =================
    private void update(float dt) {
        snap = in.snapshot();
        mouseX = snap.mouseX;
        mouseY = snap.mouseY;
        if (statusTimer > 0) {
            statusTimer -= dt;
        }
        if (placeCooldown > 0) {
            placeCooldown -= dt;
        }
        if (snap.pressed(KeyEvent.VK_ESCAPE)) {
            togglePause();
        }
        if (snap.pressed(KeyEvent.VK_F5) && netRole == NetRole.SOLO) {
            saveGame();
        }
        if (snap.pressed(KeyEvent.VK_M)) {
            showMinimap = !showMinimap;
        }
        if (snap.pressed(KeyEvent.VK_H)) {
            showAchievementPanel = !showAchievementPanel;
        }
        if (snap.pressed(KeyEvent.VK_CONTROL)) {
            smartCursor = !smartCursor;
            statusMsg = smartCursor ? "智能光标：开启" : "智能光标：关闭";
            statusTimer = 2f;
        }
        if (snap.pressed(KeyEvent.VK_R)) {
            player.sortInventory();
            statusMsg = "背包已整理";
            statusTimer = 2f;
            SoundPlayer.play("craft");
        }
        if (snap.pressed(KeyEvent.VK_F9) && netRole == NetRole.SOLO) {
            loadGame();
        }
        if (snap.pressed(KeyEvent.VK_F6) && netRole == NetRole.SOLO) {
            world.generate(System.nanoTime());
            // 清理旧世界状态：敌人、掉落物、NPC、粒子、投射物、渲染缓存
            enemies.clear();
            drops.clear();
            npcs.clear();
            occupiedHouses.clear();
            particles.clear();
            projectiles.clear();
            dmgNums.clear();
            tileLayer = null; // 强制方块层缓存重建
            spawnInitialEnemies();
            player.respawn(world, 0);
            player.clear();
            statusMsg = "已生成新世界";
            statusTimer = 3f;
        }
        // M 键切换背景音乐
        if (snap.pressed(KeyEvent.VK_M)) {
            boolean nm = !SoundPlayer.isMusicMuted();
            SoundPlayer.setMusicMuted(nm);
            statusMsg = nm ? "背景音乐已关闭" : "背景音乐已开启";
            statusTimer = 2f;
        }

        switch (state) {
            case MAIN_MENU:
                updateMainMenu();
                break;
            case MP_MENU:
                updateMpMenu();
                break;
            case PLAYING:
                updatePlaying(dt);
                break;
            case PAUSED:
                updatePaused();
                break;
            case CRAFTING:
                updateCrafting();
                break;
            case INVENTORY:
                updateInventory();
                break;
            case NPC_DIALOG:
                updateNpcDialog();
                break;
            case HELP:
                updateHelp();
                break;
        }
    }

    private void updateMainMenu() {
        updateButtons(menuButtons);
    }

    private void updateMpMenu() {
        updateButtons(mpButtons);
        if (snap.mouseLeftPressed) {
            for (int i = 0; i < roomRects.size(); i++) {
                if (roomRects.get(i).contains((int) mouseX, (int) mouseY)) {
                    LanDiscovery.Room r = discoveredRooms.get(i);
                    statusMsg = "正在加入房间 " + r.name;
                    statusTimer = 2f;
                    startClient(r.ip, r.port);
                    return;
                }
            }
        }
    }

    /** 后台线程扫描局域网房间，完成后回到 EDT 更新列表。 */
    private void scanRooms() {
        if (roomScanning) {
            return;
        }
        roomScanning = true;
        roomScanInfo = "正在扫描局域网房间...";
        Thread t = new Thread(() -> {
            List<LanDiscovery.Room> found = LanDiscovery.discover(1500);
            javax.swing.SwingUtilities.invokeLater(() -> {
                discoveredRooms.clear();
                discoveredRooms.addAll(found);
                roomScanning = false;
                roomScanInfo = found.isEmpty()
                        ? "未发现房间（请确认主机已开启多人游戏）"
                        : "发现 " + found.size() + " 个房间，点击加入：";
            });
        }, "lan-scan");
        t.setDaemon(true);
        t.start();
    }

    private void updatePaused() {
        updateButtons(pauseButtons);
    }

    private void updateHelp() {
        if (snap.pressed(KeyEvent.VK_ESCAPE)) {
            backFromHelp();
        }
        updateButtons(helpButtons);
    }

    private void updateButtons(List<Button> buttons) {
        if (snap.mouseLeftPressed) {
            for (Button b : buttons) {
                if (b.contains(mouseX, mouseY)) {
                    b.action.run();
                    return;
                }
            }
        }
    }

    private void togglePause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
        }
    }

    // ================= 游戏主更新 =================
    private void updatePlaying(float dt) {
        // ---- 网络：先处理入站消息 ----
        if (netRole == NetRole.HOST && netServer != null) {
            processHostInbox();
        } else if (netRole == NetRole.CLIENT && netClient != null) {
            processClientInbox();
        }
        updateRemotePlayers(dt);

        // ---- 网络：定时广播 / 上报 ----
        if (netRole == NetRole.HOST && netServer != null) {
            netSyncTimer -= dt;
            if (netSyncTimer <= 0) {
                netSyncTimer = 0.1f;
                broadcastPlayers();
                if (!enemies.isEmpty()) {
                    broadcastEnemies();
                }
            }
            // 刷新玩家名（含新加入者）
            playerNames.clear();
            playerNames.putAll(netServer.names());
        } else if (netRole == NetRole.CLIENT && netClient != null && netConnected) {
            netSendTimer -= dt;
            if (netSendTimer <= 0) {
                netSendTimer = 0.05f;
                sendMyState();
            }
        }

        // ---- 本地玩家可否操作（客户端需等待 WELCOME；聊天时暂停） ----
        boolean chatting = false;
        if (netRole != NetRole.SOLO) {
            if (chatActive) {
                handleChatInput();
                chatting = true;
            } else if (snap.pressed(KeyEvent.VK_ENTER)) {
                chatActive = true;
                chatBuf.setLength(0);
                chatting = true;
            }
        }
        boolean canPlay = (netRole != NetRole.CLIENT || netConnected) && !chatting;

        if (canPlay) {
            player.update(dt, world, snap);

            // 熔岩伤害：接触熔岩每秒扣 15 血
            checkLavaDamage(dt);

            // ---- 钓鱼状态更新 ----
            if (player.fishingState == 1) {
                player.fishTimer -= dt;
                if (player.fishTimer <= 0) {
                    player.fishingState = 2;
                    player.fishTimer = 1.5f; // 咬钩窗口 1.5 秒
                    player.bobberDip = 8f;
                    statusMsg = "咬钩了！快按左键收竿！";
                    statusTimer = 1.5f;
                    SoundPlayer.play("pickup");
                }
            } else if (player.fishingState == 2) {
                player.fishTimer -= dt;
                // 浮标上下抖动
                player.bobberDip = 4f + (float) Math.sin(frameTimer * 20f) * 4f;
                if (player.fishTimer <= 0) {
                    player.fishingState = 0;
                    statusMsg = "鱼跑了...";
                    statusTimer = 2f;
                }
            }

            // 热键栏切换：滚轮 + 数字键
            if (snap.scroll != 0) {
                player.selected = ((player.selected + (snap.scroll > 0 ? 1 : -1)) % HOTBAR_SLOTS + HOTBAR_SLOTS) % HOTBAR_SLOTS;
            }
            for (int i = 0; i < HOTBAR_SLOTS; i++) {
                if (snap.pressed(KeyEvent.VK_1 + i)) {
                    player.selected = i;
                }
            }

            // 合成面板 E 键（附近有任意制作站即可）
            if (snap.pressed(KeyEvent.VK_E)) {
                if (nearWorkbench() || nearFurnace() || nearAnvil()) {
                    state = State.CRAFTING;
                } else {
                    statusMsg = "需要靠近工作台/熔炉/铁砧才能合成（E）";
                    statusTimer = 2f;
                }
            }
            // 背包面板 B / Tab
            if (snap.pressed(KeyEvent.VK_B) || snap.pressed(KeyEvent.VK_TAB)) {
                state = State.INVENTORY;
                dragStack = null;
            }

            handleMouse();

            updateEnemies(dt);
            if (wormBoss != null && wormBoss.alive) {
                wormBoss.update(dt, player, world);
                if (!wormBoss.alive) {
                    handleWormBossDeath();
                }
            }
            if (destroyerBoss != null && destroyerBoss.alive) {
                destroyerBoss.update(dt, player, world, projectiles);
                if (!destroyerBoss.alive) {
                    handleDestroyerDeath();
                }
            }
            updateNpcs(dt);
            updateHouseChecking(dt);
            updateEnemySpawn(dt);
            updateDrops(dt);
            updateParticles(dt);
            updateWeather(dt);
            if (shakeTimer > 0) shakeTimer -= dt;
            updateProjectiles(dt);
            updateDamageNumbers(dt);
            updateMinimap();
            updateGoblinInvasion(dt);
            updatePirateInvasion(dt);
            updateAchievements(dt);

            // 召唤仆从更新
            if (minion != null && minion.alive) {
                minion.update(dt, player, world, enemies);
            }

            // 成就检测：地狱探索者
            int pgy = (int)(player.y / World.TILE);
            if (pgy > world.height - 50) {
                unlockAchievement("hell_explorer");
            }
            // 成就检测：生命巅峰
            if (player.maxHp >= 300) {
                unlockAchievement("max_hp");
            }

            // 昼夜
            dayTime += dt / DAY_CYCLE;
            if (dayTime >= 1f) {
                dayTime -= 1f;
            }
            // 血月：进入夜晚时 20% 概率触发，白天结束
            boolean nowNight = isNight();
            if (nowNight && !wasNight) {
                if (rnd.nextFloat() < 0.20f && !bloodMoon) {
                    bloodMoon = true;
                    bloodMoonAnnounced = false;
                }
            }
            if (!nowNight && wasNight) {
                bloodMoon = false;
                bloodMoonAnnounced = false;
                // 每天早上10%概率触发哥布林入侵（已击败过则5%）
                if (!goblinInvasion && rnd.nextFloat() < (goblinInvasionDefeated ? 0.05f : 0.10f)) {
                    startGoblinInvasion();
                }
                // 困难模式后，每天早上5%概率触发海盗入侵
                if (hardMode && !pirateInvasion && rnd.nextFloat() < 0.05f) {
                    startPirateInvasion();
                }
            }
            wasNight = nowNight;
            // 背景音乐切换
            updateMusic();
            // 血月公告（触发后第一帧显示）
            if (bloodMoon && !bloodMoonAnnounced) {
                statusMsg = "血月降临！敌人变得更加凶猛...";
                statusTimer = 4f;
                bloodMoonAnnounced = true;
                SoundPlayer.play("boss");
            }
            handleFallingOut();
        }

        cam.follow(player.x + Player.W / 2f, player.y + Player.H / 2f,
                VIEW_W, VIEW_H, world.width * TILE, world.height * TILE, dt);
    }

    private void handleFallingOut() {
        if (player.y > world.height * TILE + 200) {
            player.hp = 0;
            player.respawn(world, mySlot);
            statusMsg = "坠落出世界，已重生";
            statusTimer = 3f;
        }
    }

    // ================= 联机：主机入站消息处理 =================
    private void processHostInbox() {
        GameServer.InMsg m;
        while ((m = netServer.inbox().poll()) != null) {
            switch (m.type) {
                case NetMessages.PSTATE: {
                    RemotePlayer rp = parsePState(m.slot, m.payload);
                    if (rp != null) {
                        remotePlayers.put(rp.slot, rp);
                        netServer.broadcast(NetMessages.PSTATE, m.payload); // 转发给其他客户端
                    }
                    break;
                }
                case NetMessages.BLOCK: {
                    applyBlockPayload(m.payload);
                    netServer.broadcast(NetMessages.BLOCK, m.payload); // 转发全员
                    break;
                }
                case NetMessages.ATTACK: {
                    handleHostAttack(m.slot, m.payload);
                    break;
                }
                case NetMessages.CHAT: {
                    handleHostChat(m.slot, m.payload);
                    break;
                }
                case NetMessages.LEAVE: {
                    remotePlayers.remove(m.slot);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void handleHostChat(int slot, byte[] payload) {
        try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(payload))) {
            int s = d.readInt();
            String name = d.readUTF();
            String text = d.readUTF();
            if (text.trim().isEmpty()) {
                return;
            }
            netServer.broadcast(NetMessages.CHAT, NetMessages.encodeChat(s, name, text));
            addChatMessage(s, text);
        } catch (IOException ignored) {
        }
    }

    private void handleHostAttack(int slot, byte[] payload) {
        try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(payload))) {
            int enemyId = d.readInt();
            int dmg = d.readInt();
            float dir = d.readFloat();
            Enemy e = findEnemyById(enemyId);
            if (e != null && e.alive) {
                e.lastAttackerSlot = slot;
                e.hurt(dmg, dir * 300f, -200);
            }
        } catch (IOException ignored) {
        }
    }

    private Enemy findEnemyById(int id) {
        for (Enemy e : enemies) {
            if (e.netId == id) {
                return e;
            }
        }
        return null;
    }

    // ================= 联机：客户端入站消息处理 =================
    private void processClientInbox() {
        GameClient.InMsg m;
        while ((m = netClient.inbox().poll()) != null) {
            switch (m.type) {
                case NetMessages.WELCOME: {
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(m.payload))) {
                        long seed = d.readLong();
                        mySlot = d.readInt();
                        world.generate(seed);
                        enemies.clear();
                        netEnemyById.clear();
                        playerNames.clear();
                        netConnected = true;
                        player.respawn(world, mySlot);
                        statusMsg = "已加入主机，世界已同步";
                        statusTimer = 3f;
                    } catch (IOException ignored) {
                    }
                    break;
                }
                case NetMessages.PSTATE: {
                    int slot = -1;
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(m.payload))) {
                        slot = d.readInt();
                    } catch (IOException ignored) {
                    }
                    if (slot == mySlot) {
                        break; // 忽略自己
                    }
                    RemotePlayer rp = parsePState(slot, m.payload);
                    if (rp != null) {
                        remotePlayers.put(rp.slot, rp);
                    }
                    break;
                }
                case NetMessages.BLOCK: {
                    applyBlockPayload(m.payload);
                    break;
                }
                case NetMessages.ENEMY_SYNC: {
                    applyEnemySync(m.payload);
                    break;
                }
                case NetMessages.DAMAGE: {
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(m.payload))) {
                        int dmg = d.readInt();
                        float kx = d.readFloat();
                        float ky = d.readFloat();
                        player.hurtAt(dmg, kx, ky);
                        if (player.hp <= 0) {
                            handlePlayerDeath();
                        }
                    } catch (IOException ignored) {
                    }
                    break;
                }
                case NetMessages.HEAL: {
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(m.payload))) {
                        int amt = d.readInt();
                        player.hp = Math.min(player.maxHp, player.hp + amt);
                    } catch (IOException ignored) {
                    }
                    break;
                }
                case NetMessages.LOOT: {
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(m.payload))) {
                        int itemId = d.readInt();
                        int count = d.readInt();
                        player.addItem(Item.byId(itemId), count);
                        statusMsg = "获得战利品 " + (Item.byId(itemId) != null ? Item.byId(itemId).name : "?") + " ×" + count;
                        statusTimer = 2.5f;
                    } catch (IOException ignored) {
                    }
                    break;
                }
                case NetMessages.LIST: {
                    applyPlayerList(m.payload);
                    break;
                }
                case NetMessages.CHAT: {
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(m.payload))) {
                        int s = d.readInt();
                        String name = d.readUTF();
                        String text = d.readUTF();
                        addChatMessage(s, text);
                    } catch (IOException ignored) {
                    }
                    break;
                }
                case NetMessages.LEAVE: {
                    int slot = -1;
                    try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(m.payload))) {
                        slot = d.readInt();
                    } catch (IOException ignored) {
                    }
                    remotePlayers.remove(slot);
                    playerNames.remove(slot);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void applyPlayerList(byte[] payload) {
        try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(payload))) {
            int n = d.readInt();
            playerNames.clear();
            for (int i = 0; i < n; i++) {
                int slot = d.readInt();
                String name = d.readUTF();
                playerNames.put(slot, name);
                remotePlayers.computeIfAbsent(slot, s -> new RemotePlayer(s, name));
            }
        } catch (IOException ignored) {
        }
    }

    private RemotePlayer parsePState(int slot, byte[] payload) {
        try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(payload))) {
            int pSlot = d.readInt();
            float x = d.readFloat();
            float y = d.readFloat();
            float vx = d.readFloat();
            float vy = d.readFloat();
            int hp = d.readInt();
            int facing = d.readInt();
            int weaponId = d.readInt();
            String name = playerNames.getOrDefault(slot, "玩家" + slot);
            RemotePlayer rp = remotePlayers.get(slot);
            if (rp == null) {
                rp = new RemotePlayer(slot, name);
                rp.x = rp.tx = x;
                rp.y = rp.ty = y;
            }
            rp.tx = x;
            rp.ty = y;
            rp.hp = hp;
            rp.facing = facing;
            rp.weaponId = weaponId;
            return rp;
        } catch (IOException e) {
            return null;
        }
    }

    private void updateRemotePlayers(float dt) {
        for (RemotePlayer rp : remotePlayers.values()) {
            float k = Math.min(1f, 10f * dt);
            rp.x += (rp.tx - rp.x) * k;
            rp.y += (rp.ty - rp.y) * k;
            if (rp.invuln > 0) {
                rp.invuln -= dt;
            }
        }
    }

    // ================= 联机：方块同步 =================
    private void applyBlockPayload(byte[] payload) {
        try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(payload))) {
            int gx = d.readInt();
            int gy = d.readInt();
            int tileId = d.readByte() & 0xFF;
            if (gx >= 0 && gx < world.width && gy >= 0 && gy < world.height) {
                TileType cur = world.get(gx, gy);
                if (cur.id != tileId) {
                    world.set(gx, gy, TileType.byId(tileId));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void netSendBlock(int gx, int gy, int tileId) {
        if (netRole == NetRole.SOLO) {
            return;
        }
        byte[] payload = NetMessages.encodeBlock(gx, gy, (byte) tileId);
        if (netRole == NetRole.HOST && netServer != null) {
            netServer.broadcast(NetMessages.BLOCK, payload);
        } else if (netRole == NetRole.CLIENT && netClient != null && netConnected) {
            netClient.send(NetMessages.BLOCK, payload);
        }
    }

    // ================= 联机：敌人同步 =================
    private void applyEnemySync(byte[] payload) {
        try (DataInputStream d = new DataInputStream(new ByteArrayInputStream(payload))) {
            int count = d.readInt();
            Map<Integer, Enemy> next = new HashMap<>();
            for (int i = 0; i < count; i++) {
                int id = d.readInt();
                float x = d.readFloat();
                float y = d.readFloat();
                int hp = d.readInt();
                int typeOrd = d.readInt();
                Enemy.Type type = Enemy.Type.values()[typeOrd];
                Enemy e = netEnemyById.get(id);
                if (e == null) {
                    e = new Enemy(0, 0, type);
                    e.networkMode();
                    e.netId = id;
                    e.x = e.targetX = x;
                    e.y = e.targetY = y;
                }
                if (hp < e.hp && e.hp > 0) {
                    e.hitFlash = 0.25f; // 被攻击反馈
                }
                e.hp = hp;
                e.maxHp = type.maxHp;
                e.targetX = x;
                e.targetY = y;
                e.type = type;
                next.put(id, e);
            }
            netEnemyById.clear();
            netEnemyById.putAll(next);
            // 重建渲染列表：联网敌人 = 网络同步敌人
            enemies.clear();
            enemies.addAll(netEnemyById.values());
        } catch (IOException ignored) {
        }
    }

    private void broadcastEnemies() {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(enemies.size());
            for (Enemy e : enemies) {
                if (!e.alive) {
                    continue;
                }
                d.writeInt(e.netId);
                d.writeFloat(e.x);
                d.writeFloat(e.y);
                d.writeInt(e.hp);
                d.writeInt(e.type.ordinal());
            }
        } catch (IOException ignored) {
        }
        netServer.broadcast(NetMessages.ENEMY_SYNC, bo.toByteArray());
    }

    // ================= 联机：玩家状态上报/广播 =================
    private void sendMyState() {
        if (netClient == null || !netClient.isConnected()) {
            return;
        }
        netClient.send(NetMessages.PSTATE,
                NetMessages.encodePState(mySlot, player.x, player.y, player.vx, player.vy,
                        player.hp, player.facing, player.weapon != null ? player.weapon.id : 0));
    }

    private void broadcastPlayers() {
        if (netServer == null) {
            return;
        }
        // 主机本地玩家
        netServer.broadcast(NetMessages.PSTATE,
                NetMessages.encodePState(0, player.x, player.y, player.vx, player.vy,
                        player.hp, player.facing, player.weapon != null ? player.weapon.id : 0));
        // 各客户端状态
        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp.slot == 0) {
                continue;
            }
            netServer.broadcast(NetMessages.PSTATE,
                    NetMessages.encodePState(rp.slot, rp.tx, rp.ty, 0, 0,
                            rp.hp, rp.facing, rp.weaponId));
        }
    }

    // ================= 联机：敌人对远端玩家伤害 =================
    private void damageRemotePlayers(Enemy e, float dt) {
        if (netRole != NetRole.HOST || netServer == null) {
            return;
        }
        float ex = e.x, ey = e.y, ew = e.w, eh = e.h;
        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp.invuln > 0) {
                continue;
            }
            boolean hit = rp.tx + Player.W > ex && rp.tx < ex + ew
                    && rp.ty + Player.H > ey && rp.ty < ey + eh;
            if (hit) {
                rp.invuln = 1.0f;
                float dir = (rp.tx + Player.W / 2f < ex + ew / 2f) ? -1 : 1;
                netServer.sendTo(rp.slot, NetMessages.DAMAGE,
                        NetMessages.encodeDamage(e.damage, dir * 260, -200));
            }
        }
    }

    // ================= 鼠标交互 =================
    private void handleMouse() {
        boolean left = snap.mouseLeft;
        boolean right = snap.mouseRight;
        boolean clicked = snap.mouseLeftPressed;

        float wx = cam.x + mouseX;
        float wy = cam.y + mouseY;

        // 智能光标：根据鼠标指向目标自动选择合适的工具/武器
        if (smartCursor) {
            autoSelectTool(wx, wy);
        }

        // 右键：NPC 对话优先，否则使用 / 放置
        if (right && !wasRightDown) {
            Npc n = npcAt(wx, wy);
            if (n != null) {
                activeNpc = n;
                npcHint = randomNpcHint();
                state = State.NPC_DIALOG;
            } else {
                useOrPlace();
            }
        }

        if (left && player.weapon != null && player.weapon.isBow()) {
            tryShoot();
        } else if (left) {
            ItemStack sel = player.hotbar[player.selected];
            if (sel != null && sel.item.isMagic()) {
                // 选中魔法武器：发射魔法
                tryCastMagic(sel.item);
            } else if (sel != null && sel.item.placeable) {
                // 选中方块：左键智能放置（不挖掘）
                tryPlaceSelected(wx, wy);
            } else {
                // 未选中方块：挥剑范围攻击 + 挖掘
                swingAttack(wx, wy);
                startOrContinueMining(wx, wy);
            }
        } else {
            mineProgress = 0;
            mineGX = -1;
            mineGY = -1;
        }

        wasLeftDown = left;
        wasRightDown = right;
    }

    /** 智能光标：根据鼠标指向目标自动选择合适的工具/武器。 */
    private void autoSelectTool(float wx, float wy) {
        // 检查是否指向敌人
        for (Enemy e : enemies) {
            if (!e.alive) continue;
            if (wx >= e.x && wx <= e.x + e.w && wy >= e.y && wy <= e.y + e.h) {
                // 指向敌人：选择最强武器
                selectBestWeapon();
                return;
            }
        }
        // 检查是否指向方块
        int tx = (int) Math.floor(wx / TILE);
        int ty = (int) Math.floor(wy / TILE);
        if (tx < 0 || tx >= world.width || ty < 0 || ty >= world.height) return;
        TileType tt = world.get(tx, ty);
        if (tt == null || tt == TileType.AIR) {
            // 指向空气：选择方块（如果有）
            selectBestBlock();
            return;
        }
        // 指向方块：根据类型选择工具
        String name = tt.name();
        if (name.contains("WOOD") || name.contains("LEAF") || name.contains("CACTUS")) {
            // 木头/树叶/仙人掌：选斧头
            selectBestTool(Item.ToolType.AXE);
        } else {
            // 其他方块：选镐子
            selectBestTool(Item.ToolType.PICKAXE);
        }
    }

    /** 选择热键栏中最强的武器。 */
    private void selectBestWeapon() {
        int bestIdx = -1;
        int bestDmg = 0;
        for (int i = 0; i < Player.SLOTS; i++) {
            ItemStack st = player.hotbar[i];
            if (st != null && st.item.isWeapon() && st.item.damage > bestDmg) {
                bestDmg = st.item.damage;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0 && bestIdx != player.selected) {
            player.selected = bestIdx;
        }
    }

    /** 选择热键栏中指定类型的最强工具。 */
    private void selectBestTool(Item.ToolType type) {
        int bestIdx = -1;
        int bestLvl = -1;
        for (int i = 0; i < Player.SLOTS; i++) {
            ItemStack st = player.hotbar[i];
            if (st != null && st.item.toolType == type && st.item.toolLevel > bestLvl) {
                bestLvl = st.item.toolLevel;
                bestIdx = i;
            }
        }
        if (bestIdx >= 0 && bestIdx != player.selected) {
            player.selected = bestIdx;
        }
    }

    /** 选择热键栏中第一个可放置方块。 */
    private void selectBestBlock() {
        for (int i = 0; i < Player.SLOTS; i++) {
            ItemStack st = player.hotbar[i];
            if (st != null && st.item.placeable && st.count > 0) {
                if (i != player.selected) {
                    player.selected = i;
                }
                return;
            }
        }
    }

    /** 挥剑范围攻击：朝鼠标方向扇形挥砍，范围内所有敌人都受击。 */
    private void swingAttack(float wx, float wy) {
        if (player.attackCooldown > 0) {
            return;
        }
        // 武器攻击速度：伤害越高越慢，短剑快，巨剑慢（接近原版手感）
        int baseDmg = player.weapon != null ? player.weapon.damage : 1;
        float useTime = Math.max(0.22f, Math.min(0.65f, 0.25f + baseDmg * 0.008f));
        player.swingTimer = useTime * 0.8f;
        player.swingDuration = useTime * 0.8f;
        player.attackCooldown = useTime;
        float pcx = player.x + Player.W / 2f;
        float pcy = player.y + Player.H / 2f;
        float dx = wx - pcx, dy = wy - pcy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) {
            dx = player.facing;
            dy = 0;
            len = 1f;
        }
        player.facing = dx >= 0 ? 1 : -1;
        float range = player.attackRange();
        float modMul = player.weaponModifier != null ? 1f + player.weaponModifier.damageMul : 1f;
        int dmg = (int)(baseDmg * player.damageMul(0) * modMul);
        boolean anyHit = false;
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            float ecx = e.x + e.w / 2f;
            float ecy = e.y + e.h / 2f;
            float ex = ecx - pcx, ey = ecy - pcy;
            float dist = (float) Math.hypot(ex, ey);
            if (dist > range) {
                continue;
            }
            // 扇形判定：敌人方向与挥剑方向夹角 < 70°（cos70°≈0.342）
            float dot = (ex * dx + ey * dy) / (dist * len);
            if (dot < 0.34f) {
                continue;
            }
            if (netRole == NetRole.CLIENT) {
                netClient.send(NetMessages.ATTACK,
                        NetMessages.encodeAttack(e.netId, dmg, player.facing));
            } else {
                player.attack(e);
                e.lastAttackerSlot = 0;
            }
            addDamageNumber(ecx, ecy - e.h / 2f, dmg);
            spawnHitParticles(ecx, ecy);
            anyHit = true;
        }
        if (anyHit) {
            SoundPlayer.play("hurt");
        }

        // 世界吞噬者：检测任意段是否在攻击范围内
        if (wormBoss != null && wormBoss.alive) {
            int seg = wormBoss.hitSegment(pcx - range, pcy - range, range * 2, range * 2);
            if (seg >= 0) {
                wormBoss.damage(dmg, seg);
                addDamageNumber(wormBoss.headX, wormBoss.headY - 20, dmg * (seg == 0 ? 2 : 1));
                spawnHitParticles(wormBoss.headX, wormBoss.headY);
                SoundPlayer.play("hurt");
            }
        }
        // 机械毁灭者：检测任意段
        if (destroyerBoss != null && destroyerBoss.alive) {
            int seg = destroyerBoss.hitSegment(pcx - range, pcy - range, range * 2, range * 2);
            if (seg >= 0) {
                destroyerBoss.damage(dmg, seg);
                addDamageNumber(destroyerBoss.headX, destroyerBoss.headY - 20, dmg * (seg == 0 ? 2 : 1));
                spawnHitParticles(destroyerBoss.headX, destroyerBoss.headY);
                SoundPlayer.play("hurt");
            }
        }
    }

    private Enemy enemyAt(float wx, float wy) {
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            float ew = e.w * 1.3f, eh = e.h * 1.3f;
            if (wx >= e.x - (ew - e.w) / 2 && wx <= e.x + ew - (ew - e.w) / 2
                    && wy >= e.y - (eh - e.h) / 2 && wy <= e.y + eh - (eh - e.h) / 2) {
                return e;
            }
        }
        return null;
    }

    private Npc npcAt(float wx, float wy) {
        for (Npc n : npcs) {
            if (wx >= n.x - 8 && wx <= n.x + Npc.W + 8
                    && wy >= n.y - 16 && wy <= n.y + Npc.H + 8) {
                return n;
            }
        }
        return null;
    }

    private static final String[] NPC_HINTS = {
            "你好，冒险者！地下深处藏着生命水晶，能永久提升生命上限。",
            "夜晚要小心僵尸，建议先造一把铜剑或铁剑。",
            "收集腐肉和铁，可以合成可疑眼球，在夜晚召唤强大的 Boss。",
            "记得放工作台，靠近它按 E 可以合成更好的装备。",
            "弓和箭矢适合远程作战，风筝敌人更安全。",
            "挖矿时带足火把，地下很黑。按 B 打开背包整理物品。",
            "护甲能减少受到的伤害，铜套铁套都很有用。",
    };

    private String randomNpcHint() {
        return NPC_HINTS[(int) (Math.random() * NPC_HINTS.length)];
    }

    private void useOrPlace() {
        ItemStack stack = player.hotbar[player.selected];
        if (stack == null || stack.count <= 0) {
            return;
        }
        float wx = cam.x + mouseX;
        float wy = cam.y + mouseY;
        int tx = (int) Math.floor(wx / TILE);
        int ty = (int) Math.floor(wy / TILE);

        if (stack.item.isWeapon()) {
            // 装备武器
            player.equipWeapon(stack.item);
            statusMsg = "已装备 " + stack.item.name;
            statusTimer = 2f;
            SoundPlayer.play("equip");
            return;
        }

        if (stack.item.isArmor()) {
            // 装备护甲：旧护甲回背包
            Item old = player.equipArmor(stack.item);
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            if (old != null) {
                player.addItem(old, 1);
            }
            String setName = player.armorSetName();
            statusMsg = "已装备 " + stack.item.name + "（防御 " + player.defense() + "）"
                    + (setName.isEmpty() ? "" : " 触发" + setName + "效果");
            statusTimer = 2.5f;
            SoundPlayer.play("equip");
            return;
        }

        if (stack.item.isAccessory()) {
            // 装备饰品
            Item old = player.equipAccessory(stack.item);
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            if (old != null) {
                player.addItem(old, 1);
            }
            statusMsg = "已装备饰品 " + stack.item.name;
            statusTimer = 2f;
            SoundPlayer.play("equip");
            return;
        }

        if (stack.item.isPotion()) {
            if (player.potionCooldown > 0) {
                statusMsg = "药水冷却中（" + (int) Math.ceil(player.potionCooldown) + "秒）";
                statusTimer = 2f;
                return;
            }
            if (stack.item == Item.POTION_HEALTH) {
                if (player.hp >= player.maxHp) {
                    statusMsg = "生命已满，无需使用生命药水";
                    statusTimer = 2f;
                    return;
                }
                player.hp = Math.min(player.maxHp, player.hp + 50);
                player.potionCooldown = 30f;
                statusMsg = "使用生命药水，恢复 50 点生命";
                statusTimer = 2.5f;
                SoundPlayer.play("pickup");
            } else if (stack.item == Item.POTION_THORNS) {
                player.thornsTimer = 120f;
                player.potionCooldown = 30f;
                statusMsg = "使用荆棘药水，2 分钟内反弹 30% 伤害";
                statusTimer = 2.5f;
                SoundPlayer.play("craft");
            }
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            return;
        }

        if (stack.item == Item.FISHING_ROD) {
            handleFishingRod(tx, ty);
            return;
        }

        if (stack.item == Item.BOMB) {
            // 炸弹：在鼠标位置爆炸
            explode(tx, ty);
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            return;
        }

        if (stack.item == Item.FISH) {
            // 鱼：食用回 30 血（受药水冷却影响）
            if (player.potionCooldown > 0) {
                statusMsg = "药水冷却中（" + (int) Math.ceil(player.potionCooldown) + "秒）";
                statusTimer = 2f;
                return;
            }
            if (player.hp >= player.maxHp) {
                statusMsg = "生命已满，无需吃鱼";
                statusTimer = 2f;
                return;
            }
            player.hp = Math.min(player.maxHp, player.hp + 30);
            player.potionCooldown = 15f;
            statusMsg = "吃了一条鱼，恢复 30 点生命";
            statusTimer = 2f;
            SoundPlayer.play("pickup");
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            return;
        }

        if (stack.item == Item.LIFE_CRYSTAL) {
            // 使用生命水晶：+20 上限并回血
            int before = player.maxHp;
            player.maxHp = Math.min(Player.MAX_HP_LIMIT, player.maxHp + 20);
            if (player.maxHp > before) {
                player.hp = Math.min(player.maxHp, player.hp + 20);
                stack.count--;
                if (stack.count <= 0) {
                    player.hotbar[player.selected] = null;
                }
                statusMsg = "生命上限 +20（" + player.maxHp + "）";
                statusTimer = 2.5f;
            } else {
                statusMsg = "生命上限已达最大";
                statusTimer = 2f;
            }
            return;
        }

        if (stack.item == Item.MANA_CRYSTAL) {
            // 魔力水晶：+20 最大魔力并回满
            int before = player.maxMana;
            player.maxMana = Math.min(Player.MAX_MANA_LIMIT, player.maxMana + 20);
            if (player.maxMana > before) {
                player.mana = player.maxMana;
                stack.count--;
                if (stack.count <= 0) {
                    player.hotbar[player.selected] = null;
                }
                statusMsg = "魔力上限 +20（" + player.maxMana + "）";
                statusTimer = 2.5f;
                SoundPlayer.play("craft");
            } else {
                statusMsg = "魔力上限已达最大";
                statusTimer = 2f;
            }
            return;
        }

        if (stack.item == Item.POTION_MANA) {
            // 魔力药水：恢复50魔力
            if (player.mana >= player.maxMana) {
                statusMsg = "魔力已满";
                statusTimer = 2f;
                return;
            }
            player.mana = Math.min(player.maxMana, player.mana + 50);
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            statusMsg = "恢复 50 点魔力";
            statusTimer = 2f;
            SoundPlayer.play("pickup");
            return;
        }

        // ---- 增益药水（共享药水冷却） ----
        if (stack.item == Item.POTION_IRONSKIN
                || stack.item == Item.POTION_SWIFTNESS
                || stack.item == Item.POTION_RAGE
                || stack.item == Item.POTION_NIGHTVISION) {
            if (player.potionCooldown > 0) {
                statusMsg = "药水冷却中 " + (int)Math.ceil(player.potionCooldown) + "s";
                statusTimer = 2f;
                return;
            }
            String msg = "";
            if (stack.item == Item.POTION_IRONSKIN) {
                player.ironskinTimer = Player.POTION_DURATION;
                msg = "铁皮药水：防御+8（5分钟）";
            } else if (stack.item == Item.POTION_SWIFTNESS) {
                player.swiftnessTimer = Player.POTION_DURATION;
                msg = "敏捷药水：速度+25%（5分钟）";
            } else if (stack.item == Item.POTION_RAGE) {
                player.rageTimer = Player.POTION_DURATION;
                msg = "怒气药水：伤害+15%（5分钟）";
            } else if (stack.item == Item.POTION_NIGHTVISION) {
                player.nightVisionTimer = Player.POTION_DURATION;
                msg = "夜视药水：夜间视野增强（5分钟）";
            }
            player.potionCooldown = 10f;
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            statusMsg = msg;
            statusTimer = 3f;
            SoundPlayer.play("pickup");
            return;
        }

        if (stack.item == Item.GEL) {
            // 凝胶 → 凝胶块（直接右键合成）
            if (stack.count >= 2) {
                stack.count -= 2;
                if (stack.count <= 0) {
                    player.hotbar[player.selected] = null;
                }
                player.addItem(Item.GEL_BLOCK, 1);
                statusMsg = "合成凝胶块";
                statusTimer = 2f;
            }
            return;
        }

        if (stack.item == Item.SUSPICIOUS_EYE) {
            // 可疑眼球：夜晚召唤 Boss
            if (!isNight()) {
                statusMsg = "可疑眼球只在夜晚躁动（夜晚才能召唤）";
                statusTimer = 2.5f;
                return;
            }
            for (Enemy e : enemies) {
                if (e.type == Enemy.Type.EYE_OF_CTHULHU && e.alive) {
                    statusMsg = "克苏鲁之眼已在附近";
                    statusTimer = 2f;
                    return;
                }
            }
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            spawnBoss();
            return;
        }

        if (stack.item == Item.MECHANICAL_SKULL) {
            // 机械骷髅：夜晚召唤骷髅王
            if (!isNight()) {
                statusMsg = "机械骷髅只在夜晚躁动（夜晚才能召唤）";
                statusTimer = 2.5f;
                return;
            }
            for (Enemy e : enemies) {
                if (e.type == Enemy.Type.SKELETRON && e.alive) {
                    statusMsg = "骷髅王已在附近";
                    statusTimer = 2f;
                    return;
                }
            }
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            spawnSkeletron();
            return;
        }

        if (stack.item == Item.WORM_FOOD) {
            // 蠕虫诱饵：召唤世界吞噬者（任意时间）
            for (Enemy e : enemies) {
                if (e.type == Enemy.Type.EATER_OF_WORLDS && e.alive) {
                    statusMsg = "世界吞噬者已在附近";
                    statusTimer = 2f;
                    return;
                }
            }
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            spawnEater();
            return;
        }

        if (stack.item == Item.WALL_SPAWNER) {
            // 血肉娃娃：在地狱召唤血肉墙
            int pgy = (int) (player.y / World.TILE);
            if (pgy < world.height - 50) {
                statusMsg = "血肉娃娃只在地狱深处才有反应（往下挖！）";
                statusTimer = 2.5f;
                return;
            }
            if (hardMode) {
                statusMsg = "困难模式已开启，血肉墙不再出现";
                statusTimer = 2f;
                return;
            }
            for (Enemy e : enemies) {
                if (e.type == Enemy.Type.WALL_OF_FLESH && e.alive) {
                    statusMsg = "血肉墙已在逼近";
                    statusTimer = 2f;
                    return;
                }
            }
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            spawnWallOfFlesh();
            return;
        }

        if (stack.item == Item.WORM_FOOD) {
            // 蠕虫诱饵：召唤世界吞噬者
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            spawnWormBoss();
            return;
        }

        if (stack.item == Item.GOBLIN_STANDARD) {
            // 哥布林战旗：召唤哥布林入侵
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            startGoblinInvasion();
            return;
        }

        if (stack.item == Item.MECHANICAL_WORM) {
            // 机械蠕虫：召唤机械毁灭者（困难模式）
            stack.count--;
            if (stack.count <= 0) {
                player.hotbar[player.selected] = null;
            }
            spawnDestroyer();
            return;
        }

        if (stack.item == Item.SLIME_STAFF) {
            // 史莱姆法杖：召唤/取消史莱姆仆从
            if (minion != null && minion.alive) {
                minion = null;
                statusMsg = "取消召唤史莱姆仆从";
            } else {
                minion = new Minion(player.x, player.y - 50, stack.item.damage);
                statusMsg = "召唤史莱姆仆从！它会自动攻击敌人";
            }
            statusTimer = 3f;
            SoundPlayer.play("craft");
            return;
        }

        if (stack.item == Item.SLIME_MOUNT) {
            // 史莱姆坐骑：召唤/取消坐骑
            if (player.mount != null) {
                player.mount = null;
                statusMsg = "取消坐骑";
            } else {
                player.mount = stack.item;
                statusMsg = "骑上史莱姆坐骑！速度+50%，跳跃+25%";
            }
            statusTimer = 3f;
            SoundPlayer.play("craft");
            return;
        }

        if (stack.item == Item.PIRATE_MAP) {
            if (!hardMode) {
                statusMsg = "海盗地图只在困难模式有效！";
                statusTimer = 2f;
                return;
            }
            stack.count--;
            if (stack.count <= 0) player.hotbar[player.selected] = null;
            startPirateInvasion();
            return;
        }

        // 放置方块
        if (!stack.item.placeable) {
            return;
        }
        TileType placeTile = World.itemToTile(stack.item);
        if (placeTile == null) {
            return;
        }
        if (tx < 0 || tx >= world.width || ty < 0 || ty >= world.height) {
            return;
        }
        if (world.get(tx, ty) != TileType.AIR) {
            return;
        }
        // 必须与已有方块相邻
        if (!hasNeighbor(tx, ty)) {
            return;
        }
        // 不能放在玩家身上
        Rectangle pr = new Rectangle((int) player.x, (int) player.y, Player.W, Player.H);
        Rectangle tr = new Rectangle(tx * TILE, ty * TILE, TILE, TILE);
        if (pr.intersects(tr)) {
            return;
        }
        world.set(tx, ty, placeTile);
        netSendBlock(tx, ty, placeTile.id);
        stack.count--;
        if (stack.count <= 0) {
            player.hotbar[player.selected] = null;
        }
        spawnBlockParticles(tx, ty);
    }

    /** 左键智能放置：选中格子是方块时，在鼠标指向位置放置（带冷却）。 */
    private void tryPlaceSelected(float wx, float wy) {
        ItemStack stack = player.hotbar[player.selected];
        if (stack == null || stack.count <= 0 || !stack.item.placeable) {
            return;
        }
        if (placeCooldown > 0) {
            return;
        }
        int tx = (int) Math.floor(wx / TILE);
        int ty = (int) Math.floor(wy / TILE);
        TileType placeTile = World.itemToTile(stack.item);
        if (placeTile == null) {
            return;
        }
        if (tx < 0 || tx >= world.width || ty < 0 || ty >= world.height) {
            return;
        }
        if (world.get(tx, ty) != TileType.AIR) {
            return;
        }
        if (!hasNeighbor(tx, ty)) {
            return;
        }
        Rectangle pr = new Rectangle((int) player.x, (int) player.y, Player.W, Player.H);
        Rectangle tr = new Rectangle(tx * TILE, ty * TILE, TILE, TILE);
        if (pr.intersects(tr)) {
            return;
        }
        world.set(tx, ty, placeTile);
        netSendBlock(tx, ty, placeTile.id);
        stack.count--;
        if (stack.count <= 0) {
            player.hotbar[player.selected] = null;
        }
        placeCooldown = 0.12f; // 放置冷却约 7 格/秒
        spawnBlockParticles(tx, ty);
        // 成就检测
        if (!hasPlaced) {
            hasPlaced = true;
            unlockAchievement("first_place");
        }
    }

    private boolean hasNeighbor(int tx, int ty) {
        int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dd : d) {
            int nx = tx + dd[0], ny = ty + dd[1];
            if (nx >= 0 && nx < world.width && ny >= 0 && ny < world.height) {
                if (world.get(nx, ny) != TileType.AIR) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startOrContinueMining(float wx, float wy) {
        int tx = (int) Math.floor(wx / TILE);
        int ty = (int) Math.floor(wy / TILE);
        if (tx < 0 || tx >= world.width || ty < 0 || ty >= world.height) {
            mineProgress = 0;
            mineGX = -1;
            mineGY = -1;
            return;
        }
        TileType tile = world.get(tx, ty);
        if (tile == TileType.AIR || tile.mineSeconds <= 0) {
            // 空气或不可挖掘方块（水等）
            mineProgress = 0;
            mineGX = -1;
            mineGY = -1;
            return;
        }
        // 工具检查：矿石需要镐，且等级要够
        Item held = (player.hotbar[player.selected] != null) ? player.hotbar[player.selected].item : null;
        int pickLevel = (held != null && held.isPickaxe()) ? held.toolLevel : 0;
        int axeLevel = (held != null && held.isAxe()) ? held.toolLevel : 0;
        if (tile.minPickaxe > 0 && pickLevel < tile.minPickaxe) {
            // 镐等级不够，不能挖
            if (tx != mineGX || ty != mineGY) {
                String need = tile.minPickaxe >= 2 ? "铁镐以上" : "铜镐以上";
                statusMsg = "需要" + need + "才能挖掘 " + tile.name;
                statusTimer = 1.5f;
            }
            mineProgress = 0;
            mineGX = tx;
            mineGY = ty;
            return;
        }
        // 攻击距离限制
        float px = player.x + Player.W / 2f;
        float py = player.y + Player.H / 2f;
        float dx = (tx * TILE + TILE / 2f) - px;
        float dy = (ty * TILE + TILE / 2f) - py;
        float reach = player.attackRange() * TILE;
        if (dx * dx + dy * dy > reach * reach) {
            mineProgress = 0;
            mineGX = -1;
            mineGY = -1;
            return;
        }

        if (tx != mineGX || ty != mineGY) {
            mineGX = tx;
            mineGY = ty;
            mineProgress = 0;
        }
        // 挖掘速度：正确工具加速，错误工具减速
        float speedMul = 1.0f;
        boolean isOre = (tile == TileType.COPPER || tile == TileType.IRON || tile == TileType.GOLD
                || tile == TileType.EBONSTONE || tile == TileType.STONE);
        boolean isWood = (tile == TileType.WOOD || tile == TileType.SHADOW_WOOD);
        if (isOre && pickLevel > 0) {
            speedMul = 1.0f + pickLevel * 0.5f; // 铜1.5x 铁2.0x 金2.5x
        } else if (isOre && pickLevel == 0 && tile.minPickaxe == 0) {
            speedMul = 0.4f; // 徒手挖石头很慢
        } else if (isWood && axeLevel > 0) {
            speedMul = 1.0f + axeLevel * 0.5f; // 斧砍树加速
        } else if (isWood && axeLevel == 0) {
            speedMul = 0.5f; // 徒手砍树较慢
        }
        float hardness = tile.mineSeconds;
        mineProgress += speedMul / hardness / 60f;
        if (mineProgress >= 1f) {
            finishMining(tx, ty);
            mineProgress = 0;
            mineGX = -1;
            mineGY = -1;
        }
    }

    private void finishMining(int tx, int ty) {
        TileType tile = world.get(tx, ty);
        world.set(tx, ty, TileType.AIR);
        netSendBlock(tx, ty, TileType.AIR.id);
        spawnBlockParticles(tx, ty);
        SoundPlayer.play("mine");
        Item drop = World.tileToItem(tile);
        if (drop != null) {
            player.addItem(drop, 1);
            collectedItems.add(drop.ordinal());
        }
        // 成就检测
        if (!hasMined) {
            hasMined = true;
            unlockAchievement("first_mine");
        }
        if (drop == Item.COPPER) {
            unlockAchievement("copper_mine");
        }
        if (collectedItems.size() >= 30) {
            unlockAchievement("collector");
        }
    }

    private void spawnBlockParticles(int tx, int ty) {
        float cx = tx * TILE + TILE / 2f;
        float cy = ty * TILE + TILE / 2f;
        for (int i = 0; i < 8; i++) {
            particles.add(new Particle(cx, cy, (rnd.nextFloat() - 0.5f) * 140f, -rnd.nextFloat() * 160f,
                    0.3f + rnd.nextFloat() * 0.3f, 0x8A6B4A));
        }
    }

    // ================= 敌人逻辑 =================
    private void spawnInitialEnemies() {
        int n = 5 + rnd.nextInt(4);
        for (int i = 0; i < n; i++) {
            int x = 60 + rnd.nextInt(world.width - 120);
            int y = groundY(x) - 20;
            Enemy e = new Enemy(x * TILE, y, Enemy.Type.SLIME);
            e.netId = netEnemyIdSeq++;
            enemies.add(e);
        }
    }

    private int groundY(int gx) {
        for (int y = 1; y < world.height; y++) {
            if (world.get(gx, y) != TileType.AIR) {
                return y - 1;
            }
        }
        return world.surfaceY;
    }

    private void updateEnemySpawn(float dt) {
        if (netRole == NetRole.CLIENT) {
            return; // 客户端敌人由主机同步
        }
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            float rainMul = (weather == 1) ? 0.6f : 1f;
            float bloodMul = bloodMoon ? 0.4f : 1f; // 血月刷新更快
            spawnTimer = (isNight() ? 2.0f : 4.5f) * rainMul * bloodMul;
            int maxEnemies = (isNight() ? 14 : 8) + (weather == 1 ? 4 : 0) + (bloodMoon ? 6 : 0);
            if (enemies.size() < maxEnemies) {
                int x = 80 + rnd.nextInt(world.width - 160);
                int y = groundY(x) - 20;
                // 根据玩家所在生物群系决定敌人类型
                int pgx = Math.max(0, Math.min(world.width - 1,
                        (int) Math.floor((player.x + Player.W / 2f) / TILE)));
                int pgy = Math.max(0, Math.min(world.height - 1,
                        (int) Math.floor((player.y + Player.H / 2f) / TILE)));
                int pb = world.biome[pgx];
                boolean inHell = pgy >= world.height - 50;
                Enemy.Type t;
                if (inHell) {
                    // 地狱：生成恶魔（飞行）
                    t = Enemy.Type.DEMON;
                    y = pgy - 5 - rnd.nextInt(10);
                } else if (isNight() && rnd.nextFloat() < 0.35f) {
                    // 夜晚：35% 概率刷新恶魔眼（飞行敌人，在玩家上方生成）
                    t = Enemy.Type.DEMON_EYE;
                    y = Math.max(20, (int) (player.y / TILE) - 15 - rnd.nextInt(10));
                } else if (isNight() && rnd.nextFloat() < 0.5f) {
                    t = Enemy.Type.ZOMBIE;
                } else {
                    switch (pb) {
                        case 1: t = (rnd.nextFloat() < 0.4f) ? Enemy.Type.MUMMY : Enemy.Type.SLIME; break;
                        case 2: t = (rnd.nextFloat() < 0.5f) ? Enemy.Type.ICE_SLIME : Enemy.Type.SLIME; break;
                        case 3: t = (rnd.nextFloat() < 0.5f) ? Enemy.Type.JUNGLE_SLIME : Enemy.Type.SLIME; break;
                        case 4: t = (rnd.nextFloat() < 0.5f) ? Enemy.Type.DEVOURER : Enemy.Type.SLIME; break;
                        default: t = Enemy.Type.SLIME; break;
                    }
                }
                Enemy e = new Enemy(x * TILE, y, t);
                e.netId = netEnemyIdSeq++;
                // 困难模式：敌人血量+50%
                if (hardMode) {
                    e.hp = (int) (e.hp * 1.5f);
                    e.maxHp = e.hp;
                }
                enemies.add(e);
                if (netRole == NetRole.HOST) {
                    broadcastEnemies();
                }
            }
        }
    }

    private void updateEnemies(float dt) {
        for (Enemy e : enemies) {
            // 骷髅王白天暴走
            if (e.type == Enemy.Type.SKELETRON) {
                e.enraged = !isNight();
            }
            e.update(dt, world, player);
            if (!e.alive) {
                handleEnemyDeath(e);
            } else if (netRole != NetRole.CLIENT) {
                // 本地（含主机）敌人碰撞伤害
                Rectangle er = new Rectangle((int) e.x, (int) e.y, (int) e.w, (int) e.h);
                Rectangle pr = new Rectangle((int) player.x, (int) player.y, Player.W, Player.H);
                if (er.intersects(pr)) {
                    int dmg = bloodMoon ? (int) (e.damage * 1.3f) : e.damage;
                    if (hardMode) dmg = (int) (dmg * 1.5f);
                    boolean hurt = player.hurt(dmg, e);
                    if (hurt) {
                        shakeTimer = 0.25f;
                        shakeIntensity = 6f;
                    }
                    // 荆棘药水：受伤时反弹 30% 伤害给敌人
                    if (hurt && player.thornsTimer > 0 && e.alive) {
                        int reflect = Math.max(1, (int) (e.damage * 0.3f));
                        e.hurt(reflect, 0, -100);
                        addDamageNumber(e.x + e.w / 2f, e.y, reflect);
                    }
                    if (player.hp <= 0) {
                        handlePlayerDeath();
                    }
                }
                damageRemotePlayers(e, dt);
            }
            // 恶魔火球
            if (e.fireballRequested) {
                e.fireballRequested = false;
                float dx = (player.x + Player.W / 2f) - (e.x + e.w / 2f);
                float dy = (player.y + Player.H / 2f) - (e.y + e.h / 2f);
                float dist = (float) Math.hypot(dx, dy);
                if (dist > 1) {
                    float speed = 180f;
                    projectiles.add(Projectile.enemyFireball(
                            e.x + e.w / 2f, e.y + e.h / 2f,
                            dx / dist * speed, dy / dist * speed, 18));
                }
            }
            // 血肉墙激光
            if (e.laserRequested) {
                e.laserRequested = false;
                float speed = 350f;
                Projectile laser = Projectile.enemyFireball(
                        e.x + e.w / 2f, e.y + e.h / 2f,
                        (float) Math.cos(e.laserAngle) * speed,
                        (float) Math.sin(e.laserAngle) * speed,
                        25);
                laser.life = 2.5f;
                projectiles.add(laser);
            }
        }
        enemies.removeIf(e -> !e.alive);
    }

    /** 玩家死亡：掉落金币、重生、提示。 */
    private void handlePlayerDeath() {
        // 掉落 50% 金币（分成 2-4 堆）
        int lost = player.coins / 2;
        if (lost > 0) {
            player.coins -= lost;
            int piles = 2 + rnd.nextInt(3);
            for (int i = 0; i < piles; i++) {
                int pile = lost / piles + (i == 0 ? lost % piles : 0);
                if (pile > 0) {
                    drops.add(Drop.coin(pile, player.x + Player.W / 2f, player.y));
                }
            }
        }
        // 死亡粒子
        for (int i = 0; i < 20; i++) {
            particles.add(new Particle(player.x + Player.W / 2f, player.y + Player.H / 2f,
                    (float)(Math.random() * 200 - 100), (float)(Math.random() * -200),
                    0.8f, new Color(200, 50, 50).getRGB()));
        }
        SoundPlayer.play("kill");
        statusMsg = "你被击败了！掉落 " + lost + " 金币，已在出生点重生";
        statusTimer = 4f;
        player.respawn(world, mySlot);
    }

    /** 熔岩伤害：玩家接触熔岩时持续扣血。 */
    private float lavaDamageTimer = 0;
    private void checkLavaDamage(float dt) {
        int cx = (int) ((player.x + Player.W / 2f) / World.TILE);
        int cy = (int) ((player.y + Player.H / 2f) / World.TILE);
        boolean inLava = false;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (world.get(cx + dx, cy + dy) == TileType.LAVA) {
                    inLava = true;
                    break;
                }
            }
            if (inLava) break;
        }
        if (inLava) {
            lavaDamageTimer += dt;
            if (lavaDamageTimer >= 0.5f) {
                lavaDamageTimer = 0;
                player.hurtAt(15, 0, -50);
                if (player.hp <= 0) handlePlayerDeath();
            }
            // 熔岩粒子
            if (Math.random() < 0.3) {
                particles.add(new Particle(player.x + Player.W / 2f, player.y,
                        (float)(Math.random() * 60 - 30), (float)(Math.random() * -80),
                        0.5f, new Color(255, 120, 30).getRGB()));
            }
        } else {
            lavaDamageTimer = Math.max(0, lavaDamageTimer - dt);
        }
    }

    private void updateNpcs(float dt) {
        for (Npc n : npcs) {
            n.update(dt, world, player);
        }
    }

    // ================= 房屋检测与 NPC 入住 =================
    private void updateHouseChecking(float dt) {
        houseCheckTimer -= dt;
        if (houseCheckTimer > 0) {
            return;
        }
        houseCheckTimer = 8f;
        Rectangle house = findQualifiedHouse();
        if (house == null) {
            return;
        }
        // 检查是否已被占用
        for (Rectangle occ : occupiedHouses) {
            if (occ.intersects(house)) {
                return;
            }
        }
        // 找一个没入住的 NPC
        Npc tenant = null;
        for (Npc n : npcs) {
            if (!n.hasHome) {
                tenant = n;
                break;
            }
        }
        // 没有空闲 NPC 则按顺序生成下一个职业（向导→商人→护士→军火商→爆破专家→树妖→渔夫→哥布林工匠）
        if (tenant == null) {
            String[] npcOrder = {"向导", "商人", "护士", "军火商", "爆破专家", "树妖", "渔夫", "哥布林工匠"};
            String nextName = null;
            for (String name : npcOrder) {
                // 哥布林工匠需要击败过哥布林入侵
                if (name.equals("哥布林工匠") && !goblinInvasionDefeated) continue;
                boolean exists = false;
                for (Npc n : npcs) {
                    if (n.name.equals(name)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    nextName = name;
                    break;
                }
            }
            if (nextName != null) {
                float hx = (house.x + house.width / 2f) * TILE;
                float hy = (house.y + house.height - 1) * TILE - Npc.H;
                tenant = new Npc(nextName, hx, hy);
                npcs.add(tenant);
            }
        }
        if (tenant != null) {
            float hx = (house.x + house.width / 2f) * TILE;
            float hy = (house.y + house.height - 1) * TILE - Npc.H;
            tenant.setHome(hx, hy);
            occupiedHouses.add(house);
            statusMsg = tenant.name + "入住了你的房屋！";
            statusTimer = 3f;
            SoundPlayer.play("craft");
        }
    }

    /**
     * 在玩家附近寻找合格房屋：被实心方块完全包围的矩形空腔，
     * 宽 6-15 格，高 4-8 格，内部有火把和工作台。
     * 返回格子坐标矩形，未找到返回 null。
     */
    private Rectangle findQualifiedHouse() {
        int pcx = (int) Math.floor((player.x + Player.W / 2f) / TILE);
        int pcy = (int) Math.floor((player.y + Player.H / 2f) / TILE);
        int range = 35;
        int x0 = Math.max(1, pcx - range);
        int x1 = Math.min(world.width - 2, pcx + range);
        int y0 = Math.max(1, pcy - range);
        int y1 = Math.min(world.height - 2, pcy + range);

        // 对每个空气块尝试扩展为最大全空气矩形
        for (int gy = y0; gy < y1; gy++) {
            for (int gx = x0; gx < x1; gx++) {
                if (world.get(gx, gy) != TileType.AIR) {
                    continue;
                }
                // 向右扩展宽度
                int w = 0;
                while (gx + w <= x1 && world.get(gx + w, gy) == TileType.AIR) {
                    w++;
                }
                // 对每个可能宽度，向下扩展高度
                for (int tw = Math.min(w, 16); tw >= 6; tw--) {
                    int th = 1;
                    boolean valid = true;
                    while (gy + th <= y1 && valid) {
                        for (int dx = 0; dx < tw; dx++) {
                            if (world.get(gx + dx, gy + th) != TileType.AIR) {
                                valid = false;
                                break;
                            }
                        }
                        if (valid) {
                            th++;
                        }
                    }
                    if (th >= 4 && th <= 8 && tw >= 6 && tw <= 15) {
                        // 检查边界是否全实心（外一圈）
                        if (isBoundarySolid(gx - 1, gy - 1, tw + 2, th + 2)) {
                            // 检查内部有火把和工作台
                            if (hasBlockInside(gx, gy, tw, th, TileType.TORCH)
                                    && hasBlockInside(gx, gy, tw, th, TileType.WORKBENCH)) {
                                return new Rectangle(gx, gy, tw, th);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 检查矩形区域（含边界）是否全是实心方块。 */
    private boolean isBoundarySolid(int x, int y, int w, int h) {
        for (int dx = 0; dx < w; dx++) {
            if (!world.isSolid(x + dx, y) || !world.isSolid(x + dx, y + h - 1)) {
                return false;
            }
        }
        for (int dy = 0; dy < h; dy++) {
            if (!world.isSolid(x, y + dy) || !world.isSolid(x + w - 1, y + dy)) {
                return false;
            }
        }
        return true;
    }

    /** 检查矩形内部是否含指定方块。 */
    private boolean hasBlockInside(int x, int y, int w, int h, TileType t) {
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                if (world.get(x + dx, y + dy) == t) {
                    return true;
                }
            }
        }
        return false;
    }

    private void drawNpcs(Graphics2D g) {
        for (Npc n : npcs) {
            n.draw(g, cam.x, cam.y);
        }
    }

    /** 夜晚使用可疑眼球：在玩家附近生成克苏鲁之眼。 */
    private void spawnBoss() {
        float bx = player.x + (rnd.nextBoolean() ? 1 : -1) * 420f;
        float by = Math.max(40, player.y - 260f);
        Enemy boss = new Enemy(bx, by, Enemy.Type.EYE_OF_CTHULHU);
        if (netRole == NetRole.HOST && netServer != null) {
            boss.netId = netEnemyIdSeq++;
        }
        enemies.add(boss);
        statusMsg = "克苏鲁之眼苏醒了！击败它获得稀有战利品";
        statusTimer = 3.5f;
        SoundPlayer.play("boss");
    }

    /** 炸弹爆炸：以格子坐标为中心，破坏半径 2 格内方块，伤害周围敌人。 */
    private void explode(int cx, int cy) {
        int radius = 2;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy > radius * radius + 1) {
                    continue;
                }
                int gx = cx + dx, gy = cy + dy;
                if (gx < 0 || gx >= world.width || gy < 0 || gy >= world.height) {
                    continue;
                }
                TileType t = world.get(gx, gy);
                if (t != TileType.AIR && t.mineSeconds > 0) {
                    world.set(gx, gy, TileType.AIR);
                    netSendBlock(gx, gy, TileType.AIR.id);
                    Item drop = World.tileToItem(t);
                    if (drop != null && Math.random() < 0.5f) {
                        player.addItem(drop, 1);
                    }
                    spawnBlockParticles(gx, gy);
                }
            }
        }
        // 伤害周围敌人
        float ex = cx * TILE + TILE / 2f;
        float ey = cy * TILE + TILE / 2f;
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            float d = (float) Math.hypot(e.x + e.w / 2f - ex, e.y + e.h / 2f - ey);
            if (d < radius * TILE + 20) {
                e.hurt(35, (e.x > ex ? 1 : -1) * 200, -250);
            }
        }
        // 爆炸粒子
        for (int i = 0; i < 20; i++) {
            particles.add(new Particle(ex, ey,
                    (rnd.nextFloat() - 0.5f) * 300f, -rnd.nextFloat() * 250f,
                    0.4f + rnd.nextFloat() * 0.3f, 0xFF6600));
        }
        SoundPlayer.play("boss");
        statusMsg = "轰！炸弹爆炸了";
        statusTimer = 1.5f;
    }

    /** 钓鱼竿操作：未钓时抛竿，等待时收竿，咬钩时钓获。 */
    private void handleFishingRod(int tx, int ty) {
        if (player.fishingState == 0) {
            // 抛竿：找鼠标附近的水方块
            int waterX = -1, waterY = -1;
            for (int dy = -2; dy <= 2 && waterX < 0; dy++) {
                for (int dx = -2; dx <= 2 && waterX < 0; dx++) {
                    int gx = tx + dx, gy = ty + dy;
                    if (gx >= 0 && gx < world.width && gy >= 0 && gy < world.height
                            && world.get(gx, gy) == TileType.WATER) {
                        waterX = gx;
                        waterY = gy;
                    }
                }
            }
            if (waterX < 0) {
                statusMsg = "附近没有水，无法钓鱼";
                statusTimer = 2f;
                return;
            }
            // 浮标放在水方块顶部（水面）
            player.bobberX = waterX * TILE + TILE / 2f;
            player.bobberY = waterY * TILE + 4f;
            player.bobberDip = 0;
            player.fishingState = 1;
            player.fishTimer = 3f + rnd.nextFloat() * 5f; // 3-8 秒咬钩
            statusMsg = "抛竿！等待鱼咬钩...";
            statusTimer = 2f;
            SoundPlayer.play("pickup");
        } else if (player.fishingState == 1) {
            // 等待中收竿：取消
            player.fishingState = 0;
            statusMsg = "收回了钓鱼竿";
            statusTimer = 1.5f;
        } else if (player.fishingState == 2) {
            // 咬钩了！收竿钓获
            int fishCount = 1 + (rnd.nextFloat() < 0.25f ? 1 : 0); // 25% 概率双鱼
            player.addItem(Item.FISH, fishCount);
            player.fishingState = 0;
            statusMsg = "钓到了 " + fishCount + " 条鱼！";
            statusTimer = 2.5f;
            SoundPlayer.play("pickup");
            // 钓鱼成功粒子
            for (int i = 0; i < 8; i++) {
                particles.add(new Particle(player.bobberX, player.bobberY,
                        (rnd.nextFloat() - 0.5f) * 120f, -rnd.nextFloat() * 150f,
                        0.4f + rnd.nextFloat() * 0.3f, 0x88CCFF));
            }
        }
    }

    private void spawnSkeletron() {        float bx = player.x + (rnd.nextBoolean() ? 1 : -1) * 380f;
        float by = Math.max(40, player.y - 200f);
        Enemy boss = new Enemy(bx, by, Enemy.Type.SKELETRON);
        if (netRole == NetRole.HOST && netServer != null) {
            boss.netId = netEnemyIdSeq++;
        }
        enemies.add(boss);
        statusMsg = "骷髅王苏醒了！白天它会变得狂暴";
        statusTimer = 3.5f;
        SoundPlayer.play("boss");
    }

    private void spawnEater() {
        float bx = player.x + (rnd.nextBoolean() ? 1 : -1) * 350f;
        float by = Math.max(40, player.y - 100f);
        Enemy boss = new Enemy(bx, by, Enemy.Type.EATER_OF_WORLDS);
        if (netRole == NetRole.HOST && netServer != null) {
            boss.netId = netEnemyIdSeq++;
        }
        enemies.add(boss);
        statusMsg = "世界吞噬者从地下钻出！";
        statusTimer = 3.5f;
        SoundPlayer.play("boss");
    }

    /** 召唤血肉墙：从玩家一侧的地图边缘出现，缓慢推进。 */
    private void spawnWallOfFlesh() {
        // 从玩家左侧或右侧的远处出现
        float side = rnd.nextBoolean() ? -1 : 1;
        float wx = side < 0 ? 50f : world.width * World.TILE - 250f;
        float wy = player.y - 40f;
        Enemy boss = new Enemy(wx, wy, Enemy.Type.WALL_OF_FLESH);
        if (netRole == NetRole.HOST && netServer != null) {
            boss.netId = netEnemyIdSeq++;
        }
        enemies.add(boss);
        statusMsg = "血肉墙苏醒了！击败它以开启困难模式！";
        statusTimer = 4f;
        SoundPlayer.play("boss");
    }

    /** 召唤机械毁灭者：困难模式机械蠕虫Boss。 */
    private void spawnDestroyer() {
        if (!hardMode) {
            statusMsg = "机械毁灭者只在困难模式出现！先击败血肉墙";
            statusTimer = 3f;
            return;
        }
        if (destroyerBoss != null && destroyerBoss.alive) {
            statusMsg = "机械毁灭者已经在活动中！";
            statusTimer = 2f;
            return;
        }
        float wx = player.x + (rnd.nextBoolean() ? -300 : 300);
        float wy = player.y - 150;
        destroyerBoss = new DestroyerBoss(wx, wy);
        statusMsg = "机械毁灭者苏醒了！困难模式最强Boss之一！";
        statusTimer = 4f;
        SoundPlayer.play("boss");
    }

    /** 机械毁灭者击败处理：掉落神圣锭和视野之魂。 */
    private void handleDestroyerDeath() {
        float cx = destroyerBoss.headX;
        float cy = destroyerBoss.headY;
        // 死亡粒子（金属碎片+火花）
        for (float[] pos : destroyerBoss.getSegmentPositions()) {
            for (int i = 0; i < 10; i++) {
                particles.add(new Particle(pos[0], pos[1],
                        (float)(Math.random() * 250 - 125), (float)(Math.random() * -180),
                        1.0f, new Color(200, 80, 80).getRGB()));
            }
        }
        // 掉落：神圣锭 + 视野之魂 + 大量金币
        for (int i = 0; i < 12; i++) {
            drops.add(new Drop(Item.HALLOWED_BAR.ordinal(), cx + (float)(Math.random()*80-40), cy));
        }
        for (int i = 0; i < 15; i++) {
            drops.add(new Drop(Item.SOUL_OF_SIGHT.ordinal(), cx + (float)(Math.random()*80-40), cy));
        }
        drops.add(Drop.coin(100, cx, cy));
        statusMsg = "机械毁灭者被击败了！获得神圣锭和视野之魂！";
        statusTimer = 5f;
        SoundPlayer.play("boss");
        unlockAchievement("destroyer");
        destroyerBoss = null;
    }

    /** 召唤世界吞噬者：在玩家附近生成多段蠕虫Boss。 */
    private void spawnWormBoss() {
        if (wormBoss != null && wormBoss.alive) {
            statusMsg = "世界吞噬者已经在活动中！";
            statusTimer = 2f;
            return;
        }
        float wx = player.x + (rnd.nextBoolean() ? -200 : 200);
        float wy = player.y - 100;
        wormBoss = new WormBoss(wx, wy);
        statusMsg = "世界吞噬者苏醒了！攻击头部造成双倍伤害！";
        statusTimer = 4f;
        SoundPlayer.play("boss");
    }

    // ================= 成就系统 =================
    /** 检查玩家是否靠近哥布林工匠。 */
    private boolean nearGoblinTinkerer() {
        for (Npc n : npcs) {
            if (n.name.equals("哥布林工匠")) {
                float dx = n.x - player.x;
                float dy = n.y - player.y;
                if (Math.hypot(dx, dy) < 200) return true;
            }
        }
        return false;
    }

    /** 重铸当前选中的物品。 */
    private void reforgeSelectedItem() {
        ItemStack sel = player.hotbar[player.selected];
        if (sel == null || sel.item == null) {
            statusMsg = "请先选中要重铸的物品";
            statusTimer = 2f;
            return;
        }
        Item item = sel.item;
        // 检查是否可重铸（武器、护甲、饰品）
        boolean canReforge = item.isWeapon() || item.isMagic() || item.ranged
                || (item.defense > 0 && !item.isAccessory()) || item.isAccessory();
        if (!canReforge) {
            statusMsg = "该物品无法重铸";
            statusTimer = 2f;
            return;
        }
        // 重铸费用：物品价值相关，丐版固定20金币
        int cost = 20;
        if (player.coins < cost) {
            statusMsg = "金币不足！需要 " + cost + " 金币";
            statusTimer = 2f;
            return;
        }
        player.coins -= cost;
        // 随机分配修饰语
        ItemStack.Modifier[] mods;
        if (item.isWeapon() || item.isMagic() || item.ranged) {
            mods = ItemStack.Modifier.weaponModifiers();
        } else if (item.isAccessory()) {
            mods = ItemStack.Modifier.accessoryModifiers();
        } else {
            mods = ItemStack.Modifier.armorModifiers();
        }
        sel.modifier = mods[rnd.nextInt(mods.length)];
        statusMsg = "重铸成功！获得修饰语：" + sel.modifier.name + "（花费" + cost + "金币）";
        statusTimer = 3f;
        SoundPlayer.play("craft");
        // 如果是当前装备的武器，更新修饰语
        if (player.weapon == item) {
            player.weaponModifier = sel.modifier;
        }
    }

    /** 解锁成就并显示通知。 */
    private void unlockAchievement(String id) {
        for (Achievement a : achievements) {
            if (a.id.equals(id) && !a.unlocked) {
                a.unlocked = true;
                a.unlockTime = 5f;
                achievementNotifications.add(a);
                SoundPlayer.play("craft");
                return;
            }
        }
    }

    private void updateAchievements(float dt) {
        for (int i = achievementNotifications.size() - 1; i >= 0; i--) {
            Achievement a = achievementNotifications.get(i);
            a.unlockTime -= dt;
            if (a.unlockTime <= 0) achievementNotifications.remove(i);
        }
    }

    private void drawAchievementNotifications(Graphics2D g) {
        float yOffset = 0;
        for (Achievement a : achievementNotifications) {
            Achievement.drawNotification(g, a, yOffset);
            yOffset += 70;
        }
    }

    private void drawAchievementPanel(Graphics2D g) {
        if (!showAchievementPanel) return;
        int w = 500, h = 450;
        int x = (VIEW_W - w) / 2, y = (VIEW_H - h) / 2;
        g.setColor(new Color(20, 20, 30, 240));
        g.fillRoundRect(x, y, w, h, 15, 15);
        g.setColor(new Color(180, 180, 200));
        g.drawRoundRect(x, y, w, h, 15, 15);
        g.setColor(new Color(255, 215, 80));
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 20));
        g.drawString("成就列表", x + 20, y + 35);
        int unlocked = 0;
        for (Achievement a : achievements) if (a.unlocked) unlocked++;
        g.setColor(new Color(200, 200, 200));
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 14));
        g.drawString("已解锁 " + unlocked + "/" + achievements.size(), x + w - 150, y + 35);
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12));
        int iy = y + 55;
        for (Achievement a : achievements) {
            if (iy > y + h - 30) break;
            g.setColor(a.unlocked ? new Color(60, 50, 20, 200) : new Color(40, 40, 50, 200));
            g.fillRoundRect(x + 15, iy, w - 30, 24, 6, 6);
            g.setColor(a.unlocked ? new Color(255, 215, 80) : new Color(80, 80, 90));
            g.fillOval(x + 22, iy + 4, 16, 16);
            if (a.unlocked) {
                g.setColor(new Color(200, 160, 40));
                g.fillOval(x + 25, iy + 7, 10, 10);
            }
            g.setColor(a.unlocked ? Color.WHITE : new Color(120, 120, 130));
            g.setFont(new java.awt.Font("Dialog", a.unlocked ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 12));
            g.drawString(a.name, x + 48, iy + 16);
            g.setColor(a.unlocked ? new Color(180, 180, 180) : new Color(90, 90, 100));
            g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));
            g.drawString(a.description, x + 160, iy + 16);
            iy += 28;
        }
        g.setColor(new Color(150, 150, 160));
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 12));
        g.drawString("按 H 或 ESC 关闭", x + w / 2 - 60, y + h - 15);
    }

    // ================= 海盗入侵 =================
    private void startPirateInvasion() {
        if (pirateInvasion) return;
        pirateInvasion = true;
        pirateKilled = 0;
        pirateTotal = 60 + rnd.nextInt(40);
        pirateSpawnTimer = 0;
        statusMsg = "海盗来袭！击败所有海盗！";
        statusTimer = 5f;
        SoundPlayer.play("boss");
    }

    private void updatePirateInvasion(float dt) {
        if (!pirateInvasion) return;
        pirateSpawnTimer -= dt;
        int pirateCount = 0;
        for (Enemy e : enemies) {
            if (e.alive && (e.type == Enemy.Type.PIRATE_DECKHAND || e.type == Enemy.Type.PIRATE_GUNNER)) pirateCount++;
        }
        if (pirateSpawnTimer <= 0 && pirateCount < 12) {
            pirateSpawnTimer = 2.5f + rnd.nextFloat() * 2f;
            spawnPirateWave();
        }
        if (pirateKilled >= pirateTotal && pirateCount == 0) {
            endPirateInvasion();
        }
    }

    private void spawnPirateWave() {
        int count = 2 + rnd.nextInt(3);
        for (int i = 0; i < count; i++) {
            if (pirateKilled + countPiratesAlive() >= pirateTotal) break;
            float side = rnd.nextBoolean() ? -1 : 1;
            float sx = side < 0 ? player.x - 450 - rnd.nextFloat() * 200 : player.x + 450 + rnd.nextFloat() * 200;
            float sy = player.y - 100;
            Enemy.Type type = rnd.nextFloat() < 0.55f ? Enemy.Type.PIRATE_DECKHAND : Enemy.Type.PIRATE_GUNNER;
            Enemy pirate = new Enemy(sx, sy, type);
            if (netRole == NetRole.HOST && netServer != null) pirate.netId = netEnemyIdSeq++;
            enemies.add(pirate);
        }
    }

    private int countPiratesAlive() {
        int c = 0;
        for (Enemy e : enemies) {
            if (e.alive && (e.type == Enemy.Type.PIRATE_DECKHAND || e.type == Enemy.Type.PIRATE_GUNNER)) c++;
        }
        return c;
    }

    private void endPirateInvasion() {
        pirateInvasion = false;
        pirateInvasionDefeated = true;
        statusMsg = "海盗被击退了！获得大量金币奖励！";
        statusTimer = 5f;
        SoundPlayer.play("boss");
        for (int i = 0; i < 15; i++) {
            drops.add(Drop.coin(8 + rnd.nextInt(15), player.x + (float)(Math.random()*120-60), player.y - 50));
        }
        drops.add(new Drop(Item.PIRATE_MAP.ordinal(), player.x, player.y - 50));
    }

    // ================= 哥布林入侵 =================
    /** 开始哥布林入侵。 */
    private void startGoblinInvasion() {
        if (goblinInvasion) return;
        goblinInvasion = true;
        goblinKilled = 0;
        goblinTotal = 50 + rnd.nextInt(30); // 50-80只哥布林
        goblinSpawnTimer = 0;
        statusMsg = "哥布林军队正在逼近！击败所有哥布林！";
        statusTimer = 5f;
        SoundPlayer.play("boss");
    }

    /** 更新哥布林入侵：生成哥布林，检查结束条件。 */
    private void updateGoblinInvasion(float dt) {
        if (!goblinInvasion) return;
        goblinSpawnTimer -= dt;
        // 每2-4秒生成一波哥布林（最多同时存在15只）
        int goblinCount = 0;
        for (Enemy e : enemies) {
            if (e.alive && (e.type == Enemy.Type.GOBLIN_WARRIOR || e.type == Enemy.Type.GOBLIN_ARCHER)) {
                goblinCount++;
            }
        }
        if (goblinSpawnTimer <= 0 && goblinCount < 15) {
            goblinSpawnTimer = 2f + rnd.nextFloat() * 2f;
            spawnGoblinWave();
        }
        // 检查是否击败所有哥布林
        if (goblinKilled >= goblinTotal && goblinCount == 0) {
            endGoblinInvasion();
        }
    }

    /** 生成一波哥布林（从地图边缘）。 */
    private void spawnGoblinWave() {
        int count = 2 + rnd.nextInt(3);
        for (int i = 0; i < count; i++) {
            if (goblinKilled + countGoblinsAlive() >= goblinTotal) break;
            float side = rnd.nextBoolean() ? -1 : 1;
            float sx = side < 0 ? player.x - 400 - rnd.nextFloat() * 200 : player.x + 400 + rnd.nextFloat() * 200;
            float sy = player.y - 100;
            Enemy.Type type = rnd.nextFloat() < 0.6f ? Enemy.Type.GOBLIN_WARRIOR : Enemy.Type.GOBLIN_ARCHER;
            Enemy goblin = new Enemy(sx, sy, type);
            if (netRole == NetRole.HOST && netServer != null) {
                goblin.netId = netEnemyIdSeq++;
            }
            enemies.add(goblin);
        }
    }

    private int countGoblinsAlive() {
        int c = 0;
        for (Enemy e : enemies) {
            if (e.alive && (e.type == Enemy.Type.GOBLIN_WARRIOR || e.type == Enemy.Type.GOBLIN_ARCHER)) c++;
        }
        return c;
    }

    /** 结束哥布林入侵。 */
    private void endGoblinInvasion() {
        goblinInvasion = false;
        goblinInvasionDefeated = true;
        unlockAchievement("goblin_slayer");
        statusMsg = "哥布林入侵被击退了！获得奖励！";
        statusTimer = 5f;
        SoundPlayer.play("boss");
        // 奖励：掉落金币和物品
        for (int i = 0; i < 10; i++) {
            drops.add(Drop.coin(5 + rnd.nextInt(10), player.x + (float)(Math.random()*100-50), player.y - 50));
        }
        drops.add(new Drop(Item.GOBLIN_STANDARD.ordinal(), player.x, player.y - 50));
    }

    /** 世界吞噬者击败处理：掉落魔金矿和暗影鳞片。 */
    private void handleWormBossDeath() {
        float cx = wormBoss.headX;
        float cy = wormBoss.headY;
        // 死亡粒子
        for (float[] pos : wormBoss.getSegmentPositions()) {
            for (int i = 0; i < 8; i++) {
                particles.add(new Particle(pos[0], pos[1],
                        (float)(Math.random() * 200 - 100), (float)(Math.random() * -150),
                        0.8f, new Color(150, 50, 170).getRGB()));
            }
        }
        // 掉落：魔金矿 x15 + 暗影鳞片 x8 + 金币 x50
        for (int i = 0; i < 15; i++) {
            drops.add(new Drop(Item.DEMONITE_ORE.ordinal(), cx + (float)(Math.random()*60-30), cy));
        }
        for (int i = 0; i < 8; i++) {
            drops.add(new Drop(Item.SHADOW_SCALE.ordinal(), cx + (float)(Math.random()*60-30), cy));
        }
        drops.add(Drop.coin(50, cx, cy));
        statusMsg = "世界吞噬者被击败了！获得魔金矿和暗影鳞片！";
        statusTimer = 4f;
        SoundPlayer.play("boss");
        wormBoss = null;
    }

    private void handleEnemyDeath(Enemy e) {
        float cx = e.x + e.w / 2f;
        float cy = e.y + e.h / 2f;
        spawnDeathParticles(cx, cy);
        SoundPlayer.play("kill");

        // 哥布林入侵击杀统计
        if (goblinInvasion && (e.type == Enemy.Type.GOBLIN_WARRIOR || e.type == Enemy.Type.GOBLIN_ARCHER)) {
            goblinKilled++;
        }
        // 海盗入侵击杀统计
        if (pirateInvasion && (e.type == Enemy.Type.PIRATE_DECKHAND || e.type == Enemy.Type.PIRATE_GUNNER)) {
            pirateKilled++;
        }
        // 成就检测
        if (!hasKilled) {
            hasKilled = true;
            unlockAchievement("first_kill");
        }
        if (e.type == Enemy.Type.EYE_OF_CTHULHU) {
            unlockAchievement("eye_boss");
        }

        if (e.type == Enemy.Type.EYE_OF_CTHULHU) {
            handleBossDeath(e);
            return;
        }
        if (e.type == Enemy.Type.SKELETRON) {
            handleSkeletronDeath(e);
            return;
        }
        if (e.type == Enemy.Type.EATER_OF_WORLDS) {
            handleEaterDeath(e);
            return;
        }
        if (e.type == Enemy.Type.WALL_OF_FLESH) {
            handleWallDeath(e);
            return;
        }

        if (netRole == NetRole.HOST && netServer != null && e.lastAttackerSlot > 0) {
            // 击杀者是被远端客户端：发战利品与回血
            boolean dropsMeat = e.type == Enemy.Type.ZOMBIE || e.type == Enemy.Type.MUMMY || e.type == Enemy.Type.DEMON_EYE;
            Item loot = dropsMeat ? Item.ROTTEN_MEAT : Item.GEL;
            int n = dropsMeat ? (1 + rnd.nextInt(2)) : (2 + rnd.nextInt(3));
            netServer.sendTo(e.lastAttackerSlot, NetMessages.LOOT, encodeLoot(loot.id, n));
            netServer.sendTo(e.lastAttackerSlot, NetMessages.HEAL, encodeHeal(20));
        } else {
            // 单机，或主机本地击杀，或客户端（不处理）
            if (netRole == NetRole.CLIENT) {
                return;
            }
            // 金币掉落：不同敌人不同数量
            int coinDrop = coinDropFor(e.type);
            if (coinDrop > 0) {
                drops.add(Drop.coin(coinDrop, cx, cy));
            }
            // 恶魔掉落狱石
            if (e.type == Enemy.Type.DEMON) {
                int hell = 1 + rnd.nextInt(3);
                player.addItem(Item.HELLSTONE, hell);
                if (rnd.nextFloat() < 0.3f) {
                    player.addItem(Item.OBSIDIAN, 1);
                }
            }
            boolean dropsMeat = e.type == Enemy.Type.ZOMBIE || e.type == Enemy.Type.MUMMY || e.type == Enemy.Type.DEMON_EYE;
            Item loot = dropsMeat ? Item.ROTTEN_MEAT : Item.GEL;
            int n = dropsMeat ? (1 + rnd.nextInt(2)) : (2 + rnd.nextInt(3));
            player.addItem(loot, n);
            if (netRole == NetRole.SOLO) {
                // 单机保留红心掉落
                if (rnd.nextFloat() < 0.30f) {
                    drops.add(new Drop(Item.HEART.id, cx, cy));
                }
            } else {
                // 联机击杀奖励：直接回血
                player.hp = Math.min(player.maxHp, player.hp + 20);
            }
        }
    }

    /** 不同敌人的金币掉落量。 */
    private int coinDropFor(Enemy.Type t) {
        switch (t) {
            case SLIME: return 1 + rnd.nextInt(3);
            case ICE_SLIME: return 2 + rnd.nextInt(3);
            case JUNGLE_SLIME: return 2 + rnd.nextInt(4);
            case DEMON_EYE: return 2 + rnd.nextInt(4);
            case ZOMBIE: return 3 + rnd.nextInt(6);
            case MUMMY: return 4 + rnd.nextInt(7);
            case DEVOURER: return 5 + rnd.nextInt(8);
            case DEMON: return 8 + rnd.nextInt(12);
            case EATER_OF_WORLDS: return 50 + rnd.nextInt(30);
            case SKELETRON: return 80 + rnd.nextInt(40);
            case EYE_OF_CTHULHU: return 60 + rnd.nextInt(30);
            default: return 2 + rnd.nextInt(3);
        }
    }

    /** Boss 击败：稀有战利品 + 公告。 */
    private void handleBossDeath(Enemy e) {
        statusMsg = "克苏鲁之眼被击败！战利品已入包";
        statusTimer = 4f;
        if (netRole == NetRole.HOST && netServer != null && e.lastAttackerSlot > 0) {
            int[] loot = {Item.IRON.id, 12, Item.COPPER.id, 12,
                    Item.ROTTEN_MEAT.id, 5, Item.LIFE_CRYSTAL.id, 1};
            for (int i = 0; i < loot.length; i += 2) {
                netServer.sendTo(e.lastAttackerSlot, NetMessages.LOOT, encodeLoot(loot[i], loot[i + 1]));
            }
            netServer.sendTo(e.lastAttackerSlot, NetMessages.HEAL, encodeHeal(40));
        } else if (netRole != NetRole.CLIENT) {
            player.addItem(Item.IRON, 12);
            player.addItem(Item.COPPER, 12);
            player.addItem(Item.ROTTEN_MEAT, 5);
            player.addItem(Item.LIFE_CRYSTAL, 1);
        }
        if (netRole != NetRole.CLIENT) {
            drops.add(new Drop(Item.HEART.id, e.x + e.w / 2f, e.y));
            drops.add(new Drop(Item.SUSPICIOUS_EYE.id, e.x + e.w / 2f + 22, e.y));
        }
    }

    private void handleSkeletronDeath(Enemy e) {
        statusMsg = "骷髅王被击败！地牢的诅咒解除了";
        statusTimer = 4f;
        if (netRole == NetRole.HOST && netServer != null && e.lastAttackerSlot > 0) {
            int[] loot = {Item.IRON.id, 20, Item.COPPER.id, 15,
                    Item.LIFE_CRYSTAL.id, 1, Item.MECHANICAL_SKULL.id, 1};
            for (int i = 0; i < loot.length; i += 2) {
                netServer.sendTo(e.lastAttackerSlot, NetMessages.LOOT, encodeLoot(loot[i], loot[i + 1]));
            }
            netServer.sendTo(e.lastAttackerSlot, NetMessages.HEAL, encodeHeal(50));
        } else if (netRole != NetRole.CLIENT) {
            player.addItem(Item.IRON, 20);
            player.addItem(Item.COPPER, 15);
            player.addItem(Item.LIFE_CRYSTAL, 1);
            player.addItem(Item.MECHANICAL_SKULL, 1);
        }
        if (netRole != NetRole.CLIENT) {
            drops.add(new Drop(Item.HEART.id, e.x + e.w / 2f, e.y));
            drops.add(new Drop(Item.HEART.id, e.x + e.w / 2f + 30, e.y));
        }
    }

    private void handleEaterDeath(Enemy e) {
        statusMsg = "世界吞噬者被击败！地下的威胁解除了";
        statusTimer = 4f;
        if (netRole == NetRole.HOST && netServer != null && e.lastAttackerSlot > 0) {
            int[] loot = {Item.IRON.id, 25, Item.COPPER.id, 20,
                    Item.LIFE_CRYSTAL.id, 1, Item.WORM_FOOD.id, 1};
            for (int i = 0; i < loot.length; i += 2) {
                netServer.sendTo(e.lastAttackerSlot, NetMessages.LOOT, encodeLoot(loot[i], loot[i + 1]));
            }
            netServer.sendTo(e.lastAttackerSlot, NetMessages.HEAL, encodeHeal(60));
        } else if (netRole != NetRole.CLIENT) {
            player.addItem(Item.IRON, 25);
            player.addItem(Item.COPPER, 20);
            player.addItem(Item.LIFE_CRYSTAL, 1);
            player.addItem(Item.WORM_FOOD, 1);
        }
        if (netRole != NetRole.CLIENT) {
            for (int i = 0; i < 3; i++) {
                drops.add(new Drop(Item.HEART.id, e.x + e.w / 2f + i * 24, e.y));
            }
        }
    }

    /** 血肉墙被击败：进入困难模式，生成钴/秘银矿脉。 */
    private void handleWallDeath(Enemy e) {
        hardMode = true;
        hardModeAnnounced = false;
        unlockAchievement("hard_mode");
        statusMsg = "血肉墙被击败！困难模式开启！新的矿物在地下生成...";
        statusTimer = 5f;
        SoundPlayer.play("boss");
        // 大量金币奖励
        if (netRole != NetRole.CLIENT) {
            drops.add(Drop.coin(200, e.x + e.w / 2f, e.y));
            player.addItem(Item.HELLSTONE, 10);
            player.addItem(Item.OBSIDIAN, 5);
        }
        // 在地下生成钴矿和秘银矿脉
        if (netRole != NetRole.CLIENT) {
            generateHardModeOres();
        }
    }

    /** 困难模式：在地下随机生成钴矿和秘银矿脉。 */
    private void generateHardModeOres() {
        // 钴矿：中层地下
        int cobaltVeins = 8 + rnd.nextInt(5);
        for (int i = 0; i < cobaltVeins; i++) {
            int cx = rnd.nextInt(world.width);
            int cy = world.surfaceY + 15 + rnd.nextInt(world.height - world.surfaceY - 60);
            for (int k = 0; k < 25; k++) {
                int x = cx + rnd.nextInt(5) - 2;
                int y = cy + rnd.nextInt(5) - 2;
                if (x >= 0 && x < world.width && y >= 0 && y < world.height
                        && world.get(x, y) == TileType.STONE) {
                    world.set(x, y, TileType.COBALT);
                }
            }
        }
        // 秘银矿：深层
        int mythrilVeins = 6 + rnd.nextInt(4);
        for (int i = 0; i < mythrilVeins; i++) {
            int cx = rnd.nextInt(world.width);
            int cy = world.surfaceY + 30 + rnd.nextInt(world.height - world.surfaceY - 75);
            for (int k = 0; k < 20; k++) {
                int x = cx + rnd.nextInt(4) - 2;
                int y = cy + rnd.nextInt(4) - 2;
                if (x >= 0 && x < world.width && y >= 0 && y < world.height
                        && world.get(x, y) == TileType.STONE) {
                    world.set(x, y, TileType.MYTHRIL);
                }
            }
        }
        tileLayer = null; // 强制方块层缓存重建
    }

    /** Boss 顶部血条。 */
    private void drawBossBar(Graphics2D g) {
        Enemy boss = null;
        for (Enemy e : enemies) {
            if ((e.type == Enemy.Type.EYE_OF_CTHULHU || e.type == Enemy.Type.SKELETRON
                    || e.type == Enemy.Type.EATER_OF_WORLDS) && e.alive) {
                boss = e;
                break;
            }
        }
        if (boss == null) {
            return;
        }
        int barW = 460, barH = 20;
        int bx = (VIEW_W - barW) / 2;
        int by = 40;
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(bx - 4, by - 4, barW + 8, barH + 8, 10, 10);
        g.setColor(new Color(80, 10, 20));
        g.fillRoundRect(bx, by, barW, barH, 8, 8);
        g.setColor(new Color(235, 60, 80));
        g.fillRoundRect(bx, by, (int) (barW * Math.max(0, (float) boss.hp / boss.maxHp)), barH, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(UI.FONT_BOLD_14);
        String bossName = boss.type == Enemy.Type.EYE_OF_CTHULHU ? "克苏鲁之眼"
                : boss.type == Enemy.Type.SKELETRON ? "骷髅王" : "世界吞噬者";
        String t = bossName + "  " + Math.max(0, boss.hp) + "/" + boss.maxHp;
        int tw = g.getFontMetrics().stringWidth(t);
        g.drawString(t, bx + (barW - tw) / 2, by + 16);
    }

    private byte[] encodeLoot(int itemId, int count) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(itemId);
            d.writeInt(count);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    private byte[] encodeHeal(int amt) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (DataOutputStream d = new DataOutputStream(bo)) {
            d.writeInt(amt);
        } catch (IOException ignored) {
        }
        return bo.toByteArray();
    }

    private void spawnDeathParticles(float x, float y) {
        for (int i = 0; i < 10; i++) {
            particles.add(new Particle(x, y, (rnd.nextFloat() - 0.5f) * 200f, -rnd.nextFloat() * 180f,
                    0.3f + rnd.nextFloat() * 0.4f, 0x60C060));
        }
    }

    // ================= 掉落物 =================
    private void updateDrops(float dt) {
        for (Iterator<Drop> it = drops.iterator(); it.hasNext(); ) {
            Drop d = it.next();
            d.bobT += dt;
            d.life -= dt;
            if (d.life <= 0) {
                it.remove();
                continue;
            }
            // 掉落物物理：重力+地面碰撞
            d.vy += 500 * dt;
            if (d.vy > 400) d.vy = 400;
            d.x += d.vx * dt;
            d.y += d.vy * dt;
            d.vx *= 0.92f;
            // 地面碰撞
            int tx = (int) (d.x / World.TILE);
            int ty = (int) (d.y / World.TILE);
            if (world.isSolid(tx, ty)) {
                d.y = ty * World.TILE - 2;
                d.vy = 0;
                d.vx *= 0.7f;
            }
            // 拾取检测
            if (Math.abs(d.x - (player.x + Player.W / 2f)) < 28
                    && Math.abs(d.y - (player.y + Player.H / 2f)) < 34) {
                if (d.itemId == -1) {
                    // 金币
                    player.coins += d.coinValue;
                    statusMsg = "+" + d.coinValue + " 金币";
                    statusTimer = 1.2f;
                } else {
                    Item item = Item.byId(d.itemId);
                    if (item == Item.HEART) {
                        player.hp = Math.min(player.maxHp, player.hp + 20);
                    } else {
                        player.addItem(item, 1);
                    }
                }
                SoundPlayer.play("pickup");
                it.remove();
            }
        }
    }

    private void updateParticles(float dt) {
        // 粒子上限：超过 400 时丢弃最老的，避免爆炸/挖掘后粒子堆积拖慢渲染
        if (particles.size() > 400) {
            particles.subList(0, particles.size() - 300).clear();
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += 300 * dt;
        }
    }

    // ================= 天气 =================
    private void updateWeather(float dt) {
        weatherTimer -= dt;
        if (weatherTimer <= 0) {
            if (weather == 0) {
                // 晴天：30% 概率转雨
                if (Math.random() < 0.3f) {
                    weather = 1;
                    statusMsg = "开始下雨了";
                    statusTimer = 2.5f;
                }
                weatherTimer = 120f + (float) Math.random() * 180f;
            } else {
                // 雨天：70% 概率转晴
                if (Math.random() < 0.7f) {
                    weather = 0;
                    raindrops.clear();
                    statusMsg = "雨停了";
                    statusTimer = 2.5f;
                }
                weatherTimer = 60f + (float) Math.random() * 120f;
            }
        }

        if (weather == 1) {
            // 生成雨滴（屏幕顶部，斜向左下），上限 160 个避免拖慢渲染
            if (raindrops.size() < 160) {
                int spawn = 2 + (int) (Math.random() * 3);
                for (int i = 0; i < spawn; i++) {
                    float rx = cam.x + (float) Math.random() * VIEW_W;
                    float ry = cam.y - 20;
                    raindrops.add(new Particle(rx, ry, -40f - (float) Math.random() * 30f,
                            380f + (float) Math.random() * 120f, 1.5f, 0x88AACC));
                }
            }
            // 更新雨滴
            for (Iterator<Particle> it = raindrops.iterator(); it.hasNext(); ) {
                Particle p = it.next();
                p.life -= dt;
                p.x += p.vx * dt;
                p.y += p.vy * dt;
                if (p.life <= 0 || p.y > cam.y + VIEW_H + 40) {
                    it.remove();
                }
            }
            // 雨声（间隔播放短噪声）
            rainSfxTimer -= dt;
            if (rainSfxTimer <= 0) {
                SoundPlayer.play("rain");
                rainSfxTimer = 0.12f;
            }
        }
    }

    private void drawWeather(Graphics2D g) {
        if (weather != 1) {
            return;
        }
        // 天色变暗（蓝灰遮罩）
        g.setColor(new Color(40, 50, 70, 90));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        // 雨滴（斜线）
        g.setColor(new Color(150, 180, 220, 180));
        for (Particle p : raindrops) {
            int sx = (int) (p.x - cam.x);
            int sy = (int) (p.y - cam.y);
            g.drawLine(sx, sy, sx + 3, sy + 12);
        }
    }

    private boolean isNight() {
        return dayTime < 0.25f || dayTime > 0.75f;
    }

    /** 根据游戏状态切换背景音乐。 */
    private void updateMusic() {
        if (state != State.PLAYING) {
            SoundPlayer.playMusic(null);
            return;
        }
        // Boss 战优先
        boolean bossAlive = false;
        for (Enemy e : enemies) {
            if (e.type == Enemy.Type.EYE_OF_CTHULHU || e.type == Enemy.Type.SKELETRON
                    || e.type == Enemy.Type.EATER_OF_WORLDS) {
                bossAlive = true;
                break;
            }
        }
        if (bossAlive) {
            SoundPlayer.playMusic(SoundPlayer.Music.BOSS);
            return;
        }
        // 地下
        int playerGY = (int) (player.y / World.TILE);
        if (playerGY > world.surfaceY + 15) {
            SoundPlayer.playMusic(SoundPlayer.Music.UNDERGROUND);
            return;
        }
        // 昼夜
        if (isNight()) {
            SoundPlayer.playMusic(SoundPlayer.Music.NIGHT);
        } else {
            SoundPlayer.playMusic(SoundPlayer.Music.DAY);
        }
    }

    // ================= 合成 =================
    private static class Recipe {
        final Item result;
        final int resultCount;
        final Item[] items;
        final int[] counts;
        /** 制作站：0=工作台，1=熔炉，2=铁砧。 */
        final int station;

        Recipe(Item result, int resultCount, Item[] items, int[] counts) {
            this(result, resultCount, items, counts, 0);
        }

        Recipe(Item result, int resultCount, Item[] items, int[] counts, int station) {
            this.result = result;
            this.resultCount = resultCount;
            this.items = items;
            this.counts = counts;
            this.station = station;
        }
    }

    private static class ShopItem {
        final Item result;
        final int resultCount;
        final int price;          // 金币价格
        final Item[] costs;       // 旧版以物易物（保留兼容）
        final int[] costCounts;

        ShopItem(Item result, int resultCount, int price) {
            this.result = result;
            this.resultCount = resultCount;
            this.price = price;
            this.costs = new Item[0];
            this.costCounts = new int[0];
        }

        ShopItem(Item result, int resultCount, Item[] costs, int[] costCounts) {
            this.result = result;
            this.resultCount = resultCount;
            this.price = 0;
            this.costs = costs;
            this.costCounts = costCounts;
        }
    }

    private static final ShopItem[] SHOP_ITEMS = {
            new ShopItem(Item.TORCH, 2, 5),
            new ShopItem(Item.WORKBENCH, 1, 25),
            new ShopItem(Item.WOODEN_SWORD, 1, 20),
            new ShopItem(Item.ARROW, 5, 10),
            new ShopItem(Item.COPPER_HELMET, 1, 30),
    };

    /** 商人商店：基础材料与消耗品。 */
    private static final ShopItem[] MERCHANT_ITEMS = {
            new ShopItem(Item.COPPER, 1, 3),
            new ShopItem(Item.IRON, 1, 5),
            new ShopItem(Item.ARROW, 10, 15),
            new ShopItem(Item.TORCH, 5, 8),
            new ShopItem(Item.GEL, 5, 6),
            new ShopItem(Item.POTION_HEALTH, 1, 25),
    };

    /** 军火商商店：武器与弹药。 */
    private static final ShopItem[] GUNMERCHANT_ITEMS = {
            new ShopItem(Item.WOOD_BOW, 1, 35),
            new ShopItem(Item.IRON_BOW, 1, 60),
            new ShopItem(Item.ARROW, 20, 25),
            new ShopItem(Item.COPPER_SWORD, 1, 45),
            new ShopItem(Item.IRON_SWORD, 1, 80),
            new ShopItem(Item.TORCH, 10, 12),
    };

    /** 爆破专家商店：爆炸物与挖掘用品。 */
    private static final ShopItem[] DEMOLITIONIST_ITEMS = {
            new ShopItem(Item.BOMB, 3, 15),
            new ShopItem(Item.TORCH, 10, 10),
            new ShopItem(Item.GEL, 10, 10),
            new ShopItem(Item.STONE, 10, 5),
    };

    /** 树妖商店：自然物品与净化。 */
    private static final ShopItem[] DRYAD_ITEMS = {
            new ShopItem(Item.GRASS, 10, 8),
            new ShopItem(Item.LEAF, 10, 6),
            new ShopItem(Item.LIFE_CRYSTAL, 1, 150),
            new ShopItem(Item.WOOD, 10, 5),
            new ShopItem(Item.POTION_THORNS, 1, 35),
    };

    /** 渔夫商店：鱼与海洋物品。 */
    private static final ShopItem[] FISHERMAN_ITEMS = {
            new ShopItem(Item.FISH, 3, 10),
            new ShopItem(Item.GEL, 5, 5),
            new ShopItem(Item.WOOD, 10, 4),
            new ShopItem(Item.TORCH, 5, 6),
            new ShopItem(Item.FISHING_ROD, 1, 40),
    };

    private ShopItem[] currentShopItems() {
        if (activeNpc == null) {
            return SHOP_ITEMS;
        }
        switch (activeNpc.name) {
            case "商人":
                return MERCHANT_ITEMS;
            case "军火商":
                return GUNMERCHANT_ITEMS;
            case "爆破专家":
                return DEMOLITIONIST_ITEMS;
            case "树妖":
                return DRYAD_ITEMS;
            case "渔夫":
                return FISHERMAN_ITEMS;
            default:
                return SHOP_ITEMS;
        }
    }

    private static final Recipe[] RECIPES = {
            // ---- 工作台配方 ----
            new Recipe(Item.WOODEN_SWORD, 1, new Item[]{Item.WOOD}, new int[]{8}),
            // 工具：镐（挖矿石）和斧（砍树）
            new Recipe(Item.COPPER_PICKAXE, 1, new Item[]{Item.COPPER, Item.WOOD}, new int[]{10, 4}),
            new Recipe(Item.IRON_PICKAXE, 1, new Item[]{Item.IRON, Item.WOOD}, new int[]{10, 4}),
            new Recipe(Item.GOLD_PICKAXE, 1, new Item[]{Item.GOLD, Item.WOOD}, new int[]{10, 4}),
            new Recipe(Item.COPPER_AXE, 1, new Item[]{Item.COPPER, Item.WOOD}, new int[]{8, 4}),
            new Recipe(Item.IRON_AXE, 1, new Item[]{Item.IRON, Item.WOOD}, new int[]{8, 4}),
            new Recipe(Item.GOLD_AXE, 1, new Item[]{Item.GOLD, Item.WOOD}, new int[]{8, 4}),
            new Recipe(Item.COPPER_SWORD, 1, new Item[]{Item.COPPER, Item.WOOD}, new int[]{10, 4}),
            new Recipe(Item.IRON_SWORD, 1, new Item[]{Item.IRON, Item.WOOD}, new int[]{10, 4}),
            new Recipe(Item.WOOD_BOW, 1, new Item[]{Item.WOOD}, new int[]{10}),
            new Recipe(Item.IRON_BOW, 1, new Item[]{Item.IRON, Item.WOOD}, new int[]{10, 6}),
            new Recipe(Item.ARROW, 5, new Item[]{Item.WOOD, Item.STONE}, new int[]{1, 1}),
            new Recipe(Item.SUSPICIOUS_EYE, 1, new Item[]{Item.ROTTEN_MEAT, Item.IRON}, new int[]{6, 3}),
            new Recipe(Item.MECHANICAL_SKULL, 1, new Item[]{Item.IRON, Item.COPPER, Item.ROTTEN_MEAT}, new int[]{8, 5, 10}),
            new Recipe(Item.WORM_FOOD, 1, new Item[]{Item.ROTTEN_MEAT, Item.DIRT}, new int[]{12, 8}),
            new Recipe(Item.WALL_SPAWNER, 1, new Item[]{Item.ROTTEN_MEAT, Item.HELLSTONE, Item.WOOD}, new int[]{10, 5, 10}),
            new Recipe(Item.COPPER_HELMET, 1, new Item[]{Item.COPPER}, new int[]{10}),
            new Recipe(Item.COPPER_CHESTPLATE, 1, new Item[]{Item.COPPER}, new int[]{15}),
            new Recipe(Item.COPPER_LEGGINGS, 1, new Item[]{Item.COPPER}, new int[]{12}),
            new Recipe(Item.IRON_HELMET, 1, new Item[]{Item.IRON}, new int[]{10}),
            new Recipe(Item.IRON_CHESTPLATE, 1, new Item[]{Item.IRON}, new int[]{15}),
            new Recipe(Item.IRON_LEGGINGS, 1, new Item[]{Item.IRON}, new int[]{12}),
            new Recipe(Item.HERMES_BOOTS, 1, new Item[]{Item.GEL, Item.WOOD}, new int[]{10, 8}),
            new Recipe(Item.CLOUD_IN_BOTTLE, 1, new Item[]{Item.GEL, Item.ICE}, new int[]{8, 4}),
            new Recipe(Item.LUCKY_HORSESHOE, 1, new Item[]{Item.IRON, Item.COPPER}, new int[]{8, 4}),
            new Recipe(Item.REGEN_BAND, 1, new Item[]{Item.GEL, Item.HEART}, new int[]{6, 2}),
            new Recipe(Item.POTION_HEALTH, 2, new Item[]{Item.GEL, Item.HEART, Item.LEAF}, new int[]{5, 2, 5}),
            new Recipe(Item.POTION_THORNS, 1, new Item[]{Item.GEL, Item.ROTTEN_MEAT, Item.CACTUS}, new int[]{5, 3, 3}),
            new Recipe(Item.FISHING_ROD, 1, new Item[]{Item.WOOD, Item.GEL}, new int[]{8, 4}),
            new Recipe(Item.TORCH, 2, new Item[]{Item.WOOD, Item.GEL}, new int[]{1, 1}),
            new Recipe(Item.WORKBENCH, 1, new Item[]{Item.WOOD}, new int[]{10}),
            // 制作站
            new Recipe(Item.FURNACE, 1, new Item[]{Item.STONE}, new int[]{20}),
            new Recipe(Item.ANVIL, 1, new Item[]{Item.IRON_BAR}, new int[]{5}),
            // ---- 熔炉配方（station=1）----
            new Recipe(Item.COPPER_BAR, 1, new Item[]{Item.COPPER}, new int[]{3}, 1),
            new Recipe(Item.IRON_BAR, 1, new Item[]{Item.IRON}, new int[]{3}, 1),
            new Recipe(Item.GOLD_BAR, 1, new Item[]{Item.GOLD}, new int[]{3}, 1),
            new Recipe(Item.HELLSTONE_BAR, 1, new Item[]{Item.HELLSTONE, Item.OBSIDIAN}, new int[]{3, 1}, 1),
            new Recipe(Item.COBALT_BAR, 1, new Item[]{Item.COBALT}, new int[]{3}, 1),
            new Recipe(Item.MYTHRIL_BAR, 1, new Item[]{Item.MYTHRIL}, new int[]{3}, 1),
            // ---- 铁砧配方（station=2）：用锭合成更省材料 ----
            new Recipe(Item.COPPER_SWORD, 1, new Item[]{Item.COPPER_BAR, Item.WOOD}, new int[]{6, 2}, 2),
            new Recipe(Item.IRON_SWORD, 1, new Item[]{Item.IRON_BAR, Item.WOOD}, new int[]{6, 2}, 2),
            new Recipe(Item.GOLD_SWORD, 1, new Item[]{Item.GOLD_BAR, Item.WOOD}, new int[]{6, 2}, 2),
            new Recipe(Item.HELLSTONE_SWORD, 1, new Item[]{Item.HELLSTONE_BAR, Item.WOOD}, new int[]{8, 2}, 2),
            new Recipe(Item.HELLSTONE_PICKAXE, 1, new Item[]{Item.HELLSTONE_BAR, Item.WOOD}, new int[]{10, 4}, 2),
            new Recipe(Item.COBALT_SWORD, 1, new Item[]{Item.COBALT_BAR, Item.WOOD}, new int[]{8, 2}, 2),
            new Recipe(Item.MYTHRIL_SWORD, 1, new Item[]{Item.MYTHRIL_BAR, Item.WOOD}, new int[]{10, 3}, 2),
            // 魔法武器
            new Recipe(Item.FIRE_STAFF, 1, new Item[]{Item.HELLSTONE_BAR, Item.GEL}, new int[]{5, 10}, 2),
            new Recipe(Item.MAGIC_DAGGER, 1, new Item[]{Item.COBALT_BAR, Item.GEL}, new int[]{5, 8}, 2),
            new Recipe(Item.MANA_CRYSTAL, 1, new Item[]{Item.GEL, Item.GOLD}, new int[]{10, 5}),
            new Recipe(Item.POTION_MANA, 2, new Item[]{Item.GEL, Item.LEAF}, new int[]{3, 5}),
            // 世界吞噬者相关
            new Recipe(Item.WORM_FOOD, 1, new Item[]{Item.GEL, Item.COPPER_BAR}, new int[]{8, 3}),
            new Recipe(Item.DEMONITE_BAR, 1, new Item[]{Item.DEMONITE_ORE}, new int[]{3}, 1),
            new Recipe(Item.DEMONITE_SWORD, 1, new Item[]{Item.DEMONITE_BAR, Item.WOOD}, new int[]{8, 2}, 2),
            // 新饰品
            new Recipe(Item.OBSIDIAN_SHIELD, 1, new Item[]{Item.OBSIDIAN, Item.HELLSTONE_BAR}, new int[]{20, 10}, 2),
            new Recipe(Item.WARRIOR_EMBLEM, 1, new Item[]{Item.DEMONITE_BAR, Item.SHADOW_SCALE}, new int[]{5, 3}, 2),
            new Recipe(Item.RANGER_EMBLEM, 1, new Item[]{Item.IRON_BAR, Item.LEAF}, new int[]{10, 5}, 2),
            new Recipe(Item.SORCERER_EMBLEM, 1, new Item[]{Item.MANA_CRYSTAL, Item.GEL}, new int[]{1, 10}, 2),
            // 新药水（一次出2瓶）
            new Recipe(Item.POTION_IRONSKIN, 2, new Item[]{Item.GEL, Item.IRON_BAR}, new int[]{3, 2}),
            new Recipe(Item.POTION_SWIFTNESS, 2, new Item[]{Item.GEL, Item.LEAF}, new int[]{3, 5}),
            new Recipe(Item.POTION_RAGE, 2, new Item[]{Item.GEL, Item.DEMONITE_ORE}, new int[]{3, 2}),
            new Recipe(Item.POTION_NIGHTVISION, 2, new Item[]{Item.GEL, Item.GOLD}, new int[]{3, 3}),
            // 哥布林战旗
            new Recipe(Item.GOBLIN_STANDARD, 1, new Item[]{Item.WOOD, Item.LEAF}, new int[]{10, 5}),
            // 机械Boss（困难模式）
            new Recipe(Item.MECHANICAL_WORM, 1, new Item[]{Item.COBALT_BAR, Item.MYTHRIL_BAR, Item.SHADOW_SCALE}, new int[]{5, 5, 3}, 2),
            new Recipe(Item.HALLOWED_SWORD, 1, new Item[]{Item.HALLOWED_BAR, Item.SOUL_OF_SIGHT}, new int[]{12, 5}, 2),
            // 召唤武器
            new Recipe(Item.SLIME_STAFF, 1, new Item[]{Item.GEL, Item.WOOD}, new int[]{20, 10}),
            // 坐骑
            new Recipe(Item.SLIME_MOUNT, 1, new Item[]{Item.GEL, Item.GOLD}, new int[]{30, 10}),
            new Recipe(Item.COPPER_HELMET, 1, new Item[]{Item.COPPER_BAR}, new int[]{6}, 2),
            new Recipe(Item.COPPER_CHESTPLATE, 1, new Item[]{Item.COPPER_BAR}, new int[]{10}, 2),
            new Recipe(Item.COPPER_LEGGINGS, 1, new Item[]{Item.COPPER_BAR}, new int[]{8}, 2),
            new Recipe(Item.IRON_HELMET, 1, new Item[]{Item.IRON_BAR}, new int[]{6}, 2),
            new Recipe(Item.IRON_CHESTPLATE, 1, new Item[]{Item.IRON_BAR}, new int[]{10}, 2),
            new Recipe(Item.IRON_LEGGINGS, 1, new Item[]{Item.IRON_BAR}, new int[]{8}, 2),
            new Recipe(Item.GOLD_HELMET, 1, new Item[]{Item.GOLD_BAR}, new int[]{6}, 2),
            new Recipe(Item.GOLD_CHESTPLATE, 1, new Item[]{Item.GOLD_BAR}, new int[]{10}, 2),
            new Recipe(Item.GOLD_LEGGINGS, 1, new Item[]{Item.GOLD_BAR}, new int[]{8}, 2),
            new Recipe(Item.HELLSTONE_HELMET, 1, new Item[]{Item.HELLSTONE_BAR}, new int[]{8}, 2),
            new Recipe(Item.HELLSTONE_CHESTPLATE, 1, new Item[]{Item.HELLSTONE_BAR}, new int[]{12}, 2),
            new Recipe(Item.HELLSTONE_LEGGINGS, 1, new Item[]{Item.HELLSTONE_BAR}, new int[]{10}, 2),
    };

    /** 玩家附近 2 格内是否有指定方块（制作站检测通用）。 */
    private boolean nearTile(TileType t) {
        int px = (int) Math.floor((player.x + Player.W / 2f) / TILE);
        int py = (int) Math.floor((player.y + Player.H / 2f) / TILE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                int x = px + dx, y = py + dy;
                if (x >= 0 && x < world.width && y >= 0 && y < world.height) {
                    if (world.get(x, y) == t) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean nearWorkbench() {
        return nearTile(TileType.WORKBENCH);
    }

    private boolean nearFurnace() {
        return nearTile(TileType.FURNACE);
    }

    private boolean nearAnvil() {
        return nearTile(TileType.ANVIL);
    }

    private void updateCrafting() {
        if (snap.pressed(KeyEvent.VK_E) || snap.pressed(KeyEvent.VK_ESCAPE)) {
            state = State.PLAYING;
            craftScroll = 0;
            return;
        }
        // 滚轮滚动配方列表
        if (snap.scroll != 0) {
            int contentH = RECIPES.length * 56;
            int viewH = 340;
            craftScroll = Math.max(0, Math.min(contentH - viewH, craftScroll + snap.scroll * 40));
        }
        if (snap.mouseLeftPressed) {
            for (int i = 0; i < RECIPES.length; i++) {
                Rectangle r = recipeRect(i);
                if (r.contains((int) mouseX, (int) mouseY)) {
                    craft(i);
                    break;
                }
            }
            if (!nearWorkbench() && !nearFurnace() && !nearAnvil()) {
                state = State.PLAYING;
                craftScroll = 0;
            }
        }
    }

    private Rectangle recipeRect(int i) {
        int pw = 280;
        int panelX = (VIEW_W - pw) / 2;
        int panelY = (VIEW_H - 420) / 2;
        int itemY = panelY + 60 + i * 56 - craftScroll;
        return new Rectangle(panelX + 16, itemY, pw - 32, 44);
    }

    private boolean canCraft(Recipe r) {
        // 制作站检查
        if (r.station == 1 && !nearFurnace()) {
            return false;
        }
        if (r.station == 2 && !nearAnvil()) {
            return false;
        }
        // 材料检查
        for (int i = 0; i < r.items.length; i++) {
            if (player.countOf(r.items[i]) < r.counts[i]) {
                return false;
            }
        }
        return true;
    }

    private void craft(int idx) {
        Recipe r = RECIPES[idx];
        if (!canCraft(r)) {
            return;
        }
        for (int i = 0; i < r.items.length; i++) {
            player.consume(r.items[i], r.counts[i]);
        }
        player.addItem(r.result, r.resultCount);
        collectedItems.add(r.result.ordinal());
        statusMsg = "合成 " + r.result.name + " ×" + r.resultCount;
        statusTimer = 2f;
        SoundPlayer.play("craft");
        // 成就检测
        if (r.result == Item.IRON_SWORD) unlockAchievement("iron_sword");
        if (collectedItems.size() >= 30) unlockAchievement("collector");
    }

    // ================= 背包（拖拽 / 丢弃 / 整理） =================
    private static final int INV_SLOT = 48;
    private static final int INV_GAP = 4;
    private static final int INV_CELL = INV_SLOT + INV_GAP;

    // ================= 聊天 =================
    private void handleChatInput() {
        if (!snap.typed.isEmpty()) {
            chatBuf.append(snap.typed);
        }
        if (chatBuf.length() > 120) {
            chatBuf.setLength(120);
        }
        if (snap.pressed(KeyEvent.VK_BACK_SPACE) && chatBuf.length() > 0) {
            chatBuf.deleteCharAt(chatBuf.length() - 1);
        }
        if (snap.pressed(KeyEvent.VK_ENTER)) {
            String text = chatBuf.toString().trim();
            if (!text.isEmpty()) {
                sendChat(text);
            }
            chatActive = false;
            chatBuf.setLength(0);
        } else if (snap.pressed(KeyEvent.VK_ESCAPE)) {
            chatActive = false;
            chatBuf.setLength(0);
        }
    }

    private void sendChat(String text) {
        if (netRole == NetRole.HOST) {
            netServer.broadcast(NetMessages.CHAT,
                    NetMessages.encodeChat(0, playerNames.getOrDefault(0, "主机"), text));
            addChatMessage(0, text);
        } else {
            netClient.send(NetMessages.CHAT,
                    NetMessages.encodeChat(mySlot, System.getProperty("user.name", "玩家"), text));
            addChatMessage(mySlot, text);
        }
        SoundPlayer.play("chat");
    }

    private void addChatMessage(int slot, String text) {
        String name = playerNames.getOrDefault(slot, "玩家" + slot);
        chatMessages.add(name + "：" + text);
        if (chatMessages.size() > MAX_CHAT_MSGS) {
            chatMessages.remove(0);
        }
    }

    private void drawChat(Graphics2D g) {
        int x = 12;
        int y = VIEW_H - 24;
        g.setFont(UI.FONT_PLAIN_14);
        int start = Math.max(0, chatMessages.size() - 5);
        for (int i = start; i < chatMessages.size(); i++) {
            g.setColor(new Color(0, 0, 0, 130));
            int w = g.getFontMetrics().stringWidth(chatMessages.get(i));
            g.fillRect(x - 4, y - 42, w + 8, 20);
            g.setColor(new Color(235, 235, 235));
            g.drawString(chatMessages.get(i), x, y - 28);
            y -= 22;
        }
        if (chatActive) {
            String line = "> " + chatBuf.toString() + (System.currentTimeMillis() / 500 % 2 == 0 ? "▏" : "");
            g.setColor(UI.BLACK_170);
            g.fillRect(x - 6, y - 26, 460, 26);
            g.setColor(UI.GRAY_240);
            g.drawString(line, x, y - 6);
        }
    }

    // ================= NPC 对话与商店 =================
    private static final int NPC_PANEL_W = 520;
    private static final int NPC_PANEL_H = 460;
    private static final int NPC_PANEL_X = (VIEW_W - NPC_PANEL_W) / 2;
    private static final int NPC_PANEL_Y = (VIEW_H - NPC_PANEL_H) / 2;

    private Rectangle npcCloseRect() {
        return new Rectangle(NPC_PANEL_X + NPC_PANEL_W - 70, NPC_PANEL_Y + 12, 56, 26);
    }

    private Rectangle npcShopRect(int i) {
        int x = NPC_PANEL_X + 20;
        int y = NPC_PANEL_Y + 160 + i * 52;
        return new Rectangle(x, y, NPC_PANEL_W - 40, 46);
    }

    /** 护士治疗按钮矩形。 */
    private Rectangle nurseHealRect() {
        return new Rectangle(NPC_PANEL_X + 20, NPC_PANEL_Y + 100, NPC_PANEL_W - 40, 40);
    }

    private void updateNpcDialog() {
        if (snap.pressed(KeyEvent.VK_ESCAPE) || snap.pressed(KeyEvent.VK_B)
                || snap.pressed(KeyEvent.VK_E) || snap.pressed(KeyEvent.VK_ENTER)) {
            state = State.PLAYING;
            activeNpc = null;
            return;
        }
        if (snap.mouseLeftPressed) {
            if (npcCloseRect().contains((int) mouseX, (int) mouseY)) {
                state = State.PLAYING;
                activeNpc = null;
                return;
            }
            // 护士治疗按钮
            if (activeNpc != null && activeNpc.name.equals("护士")
                    && nurseHealRect().contains((int) mouseX, (int) mouseY)) {
                doNurseHeal();
            }
            for (int i = 0; i < currentShopItems().length; i++) {
                if (npcShopRect(i).contains((int) mouseX, (int) mouseY)) {
                    tryBuy(i);
                    break;
                }
            }
        }
    }

    /** 护士治疗：消耗凝胶×3，回满生命。 */
    private void doNurseHeal() {
        if (player.hp >= player.maxHp) {
            statusMsg = "生命已满，无需治疗";
            statusTimer = 2f;
            return;
        }
        if (player.countOf(Item.GEL) < 3) {
            statusMsg = "材料不足：需要 凝胶×3";
            statusTimer = 2f;
            return;
        }
        player.consume(Item.GEL, 3);
        player.hp = player.maxHp;
        statusMsg = "护士治疗了你，生命已回满";
        statusTimer = 2.5f;
        SoundPlayer.play("pickup");
    }

    private void tryBuy(int idx) {
        ShopItem s = currentShopItems()[idx];
        // 金币购买
        if (s.price > 0) {
            if (player.coins < s.price) {
                statusMsg = "金币不足：需要 " + s.price + " 金币（当前 " + player.coins + "）";
                statusTimer = 2f;
                return;
            }
            player.coins -= s.price;
            player.addItem(s.result, s.resultCount);
            statusMsg = "购买 " + s.result.name + " ×" + s.resultCount + "（-" + s.price + "金币）";
            statusTimer = 2f;
            SoundPlayer.play("pickup");
            return;
        }
        // 旧版以物易物（保留兼容）
        for (int i = 0; i < s.costs.length; i++) {
            if (player.countOf(s.costs[i]) < s.costCounts[i]) {
                statusMsg = "材料不足：需要 " + s.costs[i].name + " ×" + s.costCounts[i];
                statusTimer = 2f;
                return;
            }
        }
        for (int i = 0; i < s.costs.length; i++) {
            player.consume(s.costs[i], s.costCounts[i]);
        }
        player.addItem(s.result, s.resultCount);
        statusMsg = "购买 " + s.result.name + " ×" + s.resultCount;
        statusTimer = 2f;
    }

    private void drawNpcDialog(Graphics2D g) {
        g.setColor(UI.BLACK_170);
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setColor(UI.DARK_52);
        g.fillRoundRect(NPC_PANEL_X, NPC_PANEL_Y, NPC_PANEL_W, NPC_PANEL_H, 12, 12);
        g.setColor(UI.DARK_90);
        g.drawRoundRect(NPC_PANEL_X, NPC_PANEL_Y, NPC_PANEL_W, NPC_PANEL_H, 12, 12);
        g.setColor(UI.BLUE_180);
        g.setFont(UI.FONT_BOLD_20);
        g.drawString((activeNpc != null ? activeNpc.name : "NPC") + " · 对话与商店", NPC_PANEL_X + 20, NPC_PANEL_Y + 36);
        Rectangle cr = npcCloseRect();
        g.setColor(new Color(140, 60, 60));
        g.fillRoundRect(cr.x, cr.y, cr.width, cr.height, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(UI.FONT_BOLD_13);
        g.drawString("关闭", cr.x + 14, cr.y + 18);
        // 提示文字
        g.setColor(UI.GRAY_220);
        g.setFont(UI.FONT_PLAIN_14);
        drawWrappedText(g, npcHint, NPC_PANEL_X + 20, NPC_PANEL_Y + 72, NPC_PANEL_W - 40, 22);
        // 护士：治疗按钮
        boolean isNurse = activeNpc != null && activeNpc.name.equals("护士");
        if (isNurse) {
            Rectangle hr = nurseHealRect();
            boolean hover = hr.contains((int) mouseX, (int) mouseY);
            g.setColor(hover ? new Color(120, 80, 140) : new Color(90, 60, 110));
            g.fillRoundRect(hr.x, hr.y, hr.width, hr.height, 8, 8);
            g.setColor(new Color(180, 140, 200));
            g.drawRoundRect(hr.x, hr.y, hr.width, hr.height, 8, 8);
            g.setColor(Color.WHITE);
            g.setFont(UI.FONT_BOLD_14);
            String healTxt = "治疗（消耗 凝胶×3，回满生命） 当前 " + player.hp + "/" + player.maxHp;
            int tw = g.getFontMetrics().stringWidth(healTxt);
            g.drawString(healTxt, hr.x + (hr.width - tw) / 2, hr.y + 26);
        }
        // 商店标题
        g.setColor(UI.GOLD_220B);
        g.setFont(UI.FONT_BOLD_15);
        String shopTitle = isNurse ? "护士商店" : "商店（金币购买，点击购买）  持有：" + player.coins + " 金币";
        g.drawString(shopTitle, NPC_PANEL_X + 20, NPC_PANEL_Y + 150);
        // 商店物品
        ShopItem[] shop = currentShopItems();
        for (int i = 0; i < shop.length; i++) {
            ShopItem s = shop[i];
            Rectangle r = npcShopRect(i);
            boolean hover = r.contains((int) mouseX, (int) mouseY);
            g.setColor(hover ? new Color(70, 90, 110) : new Color(40, 44, 54));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g.setColor(new Color(120, 140, 160));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            ItemIcon.draw(g, s.result, r.x + 10, r.y + 10, 26);
            g.setColor(Color.WHITE);
            g.setFont(UI.FONT_BOLD_14);
            g.drawString(s.result.name + " ×" + s.resultCount, r.x + 44, r.y + 28);
            // 价格显示
            String priceStr;
            if (s.price > 0) {
                boolean afford = player.coins >= s.price;
                priceStr = afford ? (s.price + " 金币") : (s.price + " 金币（不足）");
                g.setColor(afford ? new Color(255, 215, 80) : new Color(220, 80, 80));
            } else {
                StringBuilder costStr = new StringBuilder("需要 ");
                for (int j = 0; j < s.costs.length; j++) {
                    if (j > 0) costStr.append(" + ");
                    costStr.append(s.costs[j].name).append("×").append(s.costCounts[j]);
                }
                priceStr = costStr.toString();
                g.setColor(UI.GRAY_200);
            }
            g.setFont(UI.FONT_PLAIN_12);
            g.drawString(priceStr, r.x + 220, r.y + 28);
        }
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight) {
        String[] words = text.split("(?<=\\s)");
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String w : words) {
            if (g.getFontMetrics().stringWidth(line + w) > maxWidth && line.length() > 0) {
                g.drawString(line.toString(), x, cy);
                cy += lineHeight;
                line.setLength(0);
            }
            line.append(w);
        }
        if (line.length() > 0) {
            g.drawString(line.toString(), x, cy);
        }
    }

    private void updateInventory() {
        if (snap.pressed(KeyEvent.VK_B) || snap.pressed(KeyEvent.VK_TAB)
                || snap.pressed(KeyEvent.VK_ESCAPE)) {
            state = State.PLAYING;
            dragStack = null;
            return;
        }
        // 按下：拾取格子物品
        if (snap.mouseLeftPressed) {
            int[] pick = slotAt(mouseX, mouseY);
            if (pick != null && dragStack == null) {
                ItemStack s = getSlot(pick[1], pick[0] == 1);
                if (s != null) {
                    dragStack = s;
                    dragFromHotbar = pick[0] == 1;
                    dragFromIndex = pick[1];
                    setSlot(dragFromIndex, dragFromHotbar, null);
                }
            }
            // 整理按钮
            Rectangle sortRect = new Rectangle(INV_PANEL_X + INV_PANEL_W - 96, INV_PANEL_Y + 12, 80, 26);
            if (sortRect.contains((int) mouseX, (int) mouseY)) {
                sortInventory();
            }
            // 重铸按钮（靠近哥布林工匠时可用）
            if (nearGoblinTinkerer()) {
                Rectangle reforgeRect = new Rectangle(INV_PANEL_X + INV_PANEL_W - 186, INV_PANEL_Y + 12, 80, 26);
                if (reforgeRect.contains((int) mouseX, (int) mouseY)) {
                    reforgeSelectedItem();
                }
            }
            // 点击护甲格卸下（放回背包）
            if (dragStack == null) {
                for (int i = 0; i < 3; i++) {
                    if (armorSlotRect(i).contains((int) mouseX, (int) mouseY) && player.armor[i] != null) {
                        player.addItem(player.armor[i], 1);
                        player.armor[i] = null;
                        statusMsg = "已卸下护甲";
                        statusTimer = 1.5f;
                        break;
                    }
                }
                // 点击饰品格卸下
                for (int i = 0; i < 4; i++) {
                    if (accSlotRect(i).contains((int) mouseX, (int) mouseY) && player.accessories[i] != null) {
                        player.addItem(player.accessories[i], 1);
                        player.accessories[i] = null;
                        statusMsg = "已卸下饰品";
                        statusTimer = 1.5f;
                        break;
                    }
                }
            }
        }
        // 松开：放下到格子或丢弃
        if (!snap.mouseLeft && dragStack != null) {
            int[] dst = slotAt(mouseX, mouseY);
            if (dst != null) {
                placeDrag(dst[1], dst[0] == 1);
            } else {
                discardDrag();
            }
            dragStack = null;
        }
    }

    /** 主背包面板布局常量。 */
    private static final int INV_PANEL_W = 600;
    private static final int INV_PANEL_H = 400;
    private static final int INV_PANEL_X = (VIEW_W - INV_PANEL_W) / 2;
    private static final int INV_PANEL_Y = (VIEW_H - INV_PANEL_H) / 2;
    private static final int INV_GRID_COLS = 5;
    private static final int INV_GRID_ROWS = 4;

    private void drawInventoryPanel(Graphics2D g) {
        g.setColor(UI.BLACK_170);
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setColor(UI.DARK_52);
        g.fillRoundRect(INV_PANEL_X, INV_PANEL_Y, INV_PANEL_W, INV_PANEL_H, 12, 12);
        g.setColor(UI.DARK_90);
        g.drawRoundRect(INV_PANEL_X, INV_PANEL_Y, INV_PANEL_W, INV_PANEL_H, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(UI.FONT_BOLD_18);
        g.drawString("背包", INV_PANEL_X + 16, INV_PANEL_Y + 32);
        g.setFont(UI.FONT_PLAIN_12);
        g.setColor(UI.GRAY_200);
        g.drawString("拖拽移动 · 拖出丢弃 · 整理按钮排序", INV_PANEL_X + 90, INV_PANEL_Y + 32);
        // 整理按钮
        g.setColor(new Color(70, 120, 90));
        g.fillRoundRect(INV_PANEL_X + INV_PANEL_W - 96, INV_PANEL_Y + 12, 80, 26, 8, 8);
        g.setColor(Color.WHITE);
        g.drawString("整理", INV_PANEL_X + INV_PANEL_W - 78, INV_PANEL_Y + 30);
        // 重铸按钮（靠近哥布林工匠时显示）
        if (nearGoblinTinkerer()) {
            g.setColor(new Color(120, 80, 140));
            g.fillRoundRect(INV_PANEL_X + INV_PANEL_W - 186, INV_PANEL_Y + 12, 80, 26, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString("重铸(20金)", INV_PANEL_X + INV_PANEL_W - 178, INV_PANEL_Y + 30);
        }
        // 主背包 4×5
        for (int i = 0; i < Player.INV_SLOTS; i++) {
            Rectangle r = invSlotRect(i);
            drawSlot(g, r, player.inventory[i], false);
        }
        // 热键栏 10 格（底部）
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            Rectangle r = hotbarSlotRect(i);
            drawSlot(g, r, player.hotbar[i], i == player.selected);
        }
        g.setColor(UI.GRAY_200);
        g.setFont(UI.FONT_PLAIN_12);
        g.drawString("热键栏", INV_PANEL_X + 16, hotbarSlotRect(0).y - 8);
        // 护甲栏（右侧竖排：头盔/胸甲/护腿）
        g.drawString("护甲", armorSlotRect(0).x, armorSlotRect(0).y - 8);
        String[] armorLabels = {"头", "胸", "腿"};
        for (int i = 0; i < 3; i++) {
            Rectangle r = armorSlotRect(i);
            drawSlot(g, r, player.armor[i] != null ? new ItemStack(player.armor[i], 1) : null, false);
            g.setColor(new Color(160, 160, 175));
            g.setFont(new Font("Dialog", Font.PLAIN, 11));
            g.drawString(armorLabels[i], r.x + 2, r.y + r.height - 4);
        }
        // 总防御
        g.setColor(UI.GOLD_220B);
        g.setFont(UI.FONT_BOLD_13);
        g.drawString("防御 " + player.defense(), armorSlotRect(0).x, armorSlotRect(2).y + 64);
        // 饰品栏 2x2
        g.setColor(UI.GRAY_200);
        g.setFont(UI.FONT_PLAIN_12);
        g.drawString("饰品", accSlotRect(0).x, accSlotRect(0).y - 6);
        for (int i = 0; i < 4; i++) {
            Rectangle r = accSlotRect(i);
            drawSlot(g, r, player.accessories[i] != null ? new ItemStack(player.accessories[i], 1) : null, false);
        }
        // 拖动的物品跟随鼠标
        if (dragStack != null) {
            int dx = (int) mouseX - INV_SLOT / 2;
            int dy = (int) mouseY - INV_SLOT / 2;
            drawStack(g, dx, dy, dragStack, true);
        }
    }

    private Rectangle armorSlotRect(int i) {
        int x = INV_PANEL_X + INV_PANEL_W - INV_SLOT - 24;
        int y = INV_PANEL_Y + 60 + i * (INV_SLOT + 10);
        return new Rectangle(x, y, INV_SLOT, INV_SLOT);
    }

    /** 饰品栏 2x2 网格，位于护甲栏左下方。 */
    private Rectangle accSlotRect(int i) {
        int col = i % 2;
        int row = i / 2;
        int x = INV_PANEL_X + INV_PANEL_W - 2 * INV_SLOT - 40;
        int y = INV_PANEL_Y + 230 + row * (INV_SLOT + 6);
        return new Rectangle(x + col * (INV_SLOT + 6), y, INV_SLOT, INV_SLOT);
    }

    private void drawSlot(Graphics2D g, Rectangle r, ItemStack s, boolean sel) {
        g.setColor(new Color(30, 30, 38));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        g.setColor(sel ? new Color(255, 230, 120) : new Color(120, 120, 130));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 6, 6);
        if (s != null) {
            drawStack(g, r.x + 2, r.y + 2, s, false);
        }
    }

    private void drawStack(Graphics2D g, int x, int y, ItemStack s, boolean big) {
        int pad = big ? 10 : 6;
        int iconSize = INV_SLOT - 2 * pad;
        // 物品图标
        ItemIcon.draw(g, s.item, x + pad, y + pad, iconSize);
        // 数量
        if (s.count > 1) {
            g.setColor(UI.WHITE_230);
            g.setFont(UI.FONT_BOLD_13);
            String cnt = "×" + s.count;
            int cw = g.getFontMetrics().stringWidth(cnt);
            g.drawString(cnt, x + INV_SLOT - pad - cw, y + INV_SLOT - 6);
        }
    }

    private Rectangle invSlotRect(int i) {
        int col = i % INV_GRID_COLS;
        int row = i / INV_GRID_COLS;
        return new Rectangle(INV_PANEL_X + 16 + col * INV_CELL,
                INV_PANEL_Y + 48 + row * INV_CELL, INV_SLOT, INV_SLOT);
    }

    private Rectangle hotbarSlotRect(int i) {
        int y = INV_PANEL_Y + 48 + INV_GRID_ROWS * INV_CELL + 12;
        return new Rectangle(INV_PANEL_X + 16 + i * INV_CELL, y, INV_SLOT, INV_SLOT);
    }

    /** 返回鼠标下的格子：[0] 1=热键栏 0=主背包，[1] 索引；无则 null。 */
    private int[] slotAt(float mx, float my) {
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            if (hotbarSlotRect(i).contains((int) mx, (int) my)) {
                return new int[]{1, i};
            }
        }
        for (int i = 0; i < Player.INV_SLOTS; i++) {
            if (invSlotRect(i).contains((int) mx, (int) my)) {
                return new int[]{0, i};
            }
        }
        return null;
    }

    private ItemStack getSlot(int idx, boolean hotbar) {
        return hotbar ? player.hotbar[idx] : player.inventory[idx];
    }

    private void setSlot(int idx, boolean hotbar, ItemStack s) {
        if (hotbar) {
            player.hotbar[idx] = s;
        } else {
            player.inventory[idx] = s;
        }
    }

    /** 把拖动的物品放到目标格子：同物品合并，否则交换。 */
    private void placeDrag(int idx, boolean hotbar) {
        ItemStack target = getSlot(idx, hotbar);
        if (target == null) {
            setSlot(idx, hotbar, dragStack);
        } else if (target.item == dragStack.item) {
            target.count += dragStack.count;
        } else {
            setSlot(idx, hotbar, dragStack);
            setSlot(dragFromIndex, dragFromHotbar, target);
        }
    }

    /** 拖出面板外：把物品丢到世界（生成掉落物）。 */
    private void discardDrag() {
        if (dragStack == null || dragStack.count <= 0) {
            return;
        }
        float dx = player.x + Player.W / 2f + (rnd.nextFloat() - 0.5f) * 30;
        float dy = player.y + 10;
        drops.add(new Drop(dragStack.item.id, dx, dy));
        statusMsg = "丢弃 " + dragStack.item.name;
        statusTimer = 1.5f;
    }

    /** 一键整理：合并同类堆叠，按物品 id 排序，热键栏优先填充。 */
    private void sortInventory() {
        player.sortInventory();
        statusMsg = "已整理背包";
        statusTimer = 1.5f;
    }

    // ================= 存档 =================
    private static final int SAVE_MAGIC = 0x54414E54;
    private static final int SAVE_VER = 3;

    private void saveGame() {
        try (DataOutputStream d = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("world.sav")))) {
            d.writeInt(SAVE_MAGIC);
            d.writeInt(SAVE_VER);
            d.writeLong(world.seed);
            d.writeFloat(dayTime);
            for (int gy = 0; gy < world.height; gy++) {
                for (int gx = 0; gx < world.width; gx++) {
                    d.writeByte(world.get(gx, gy).id);
                }
            }
            d.writeFloat(player.x);
            d.writeFloat(player.y);
            d.writeInt(player.hp);
            d.writeInt(player.maxHp);
            d.writeInt(player.weapon != null ? player.weapon.id : 0);
            d.writeInt(player.selected);
            for (int i = 0; i < HOTBAR_SLOTS; i++) {
                ItemStack s = player.hotbar[i];
                if (s == null) {
                    d.writeInt(-1);
                } else {
                    d.writeInt(s.item.id);
                    d.writeInt(s.count);
                }
            }
            // v3：主背包 20 格
            for (int i = 0; i < Player.INV_SLOTS; i++) {
                ItemStack s = player.inventory[i];
                if (s == null) {
                    d.writeInt(-1);
                } else {
                    d.writeInt(s.item.id);
                    d.writeInt(s.count);
                }
            }
            statusMsg = "已保存到 world.sav（v3，含背包）";
        } catch (IOException e) {
            statusMsg = "保存失败：" + e.getMessage();
        }
        statusTimer = 3f;
    }

    private void loadGame() {
        try (DataInputStream d = new DataInputStream(new BufferedInputStream(new FileInputStream("world.sav")))) {
            int magic = d.readInt();
            if (magic != SAVE_MAGIC) {
                throw new IOException("存档损坏");
            }
            int ver = d.readInt();
            long seed = d.readLong();
            dayTime = d.readFloat();
            world.generate(seed);
            for (int gy = 0; gy < world.height; gy++) {
                for (int gx = 0; gx < world.width; gx++) {
                    world.set(gx, gy, TileType.byId(d.readByte()));
                }
            }
            player.x = d.readFloat();
            player.y = d.readFloat();
            player.hp = d.readInt();
            if (ver >= 2) {
                player.maxHp = d.readInt();
                int wid = d.readInt();
                player.weapon = Item.byId(wid);
            }
            player.selected = d.readInt();
            for (int i = 0; i < HOTBAR_SLOTS; i++) {
                int id = d.readInt();
                if (id < 0) {
                    player.hotbar[i] = null;
                } else {
                    int count = d.readInt();
                    player.hotbar[i] = new ItemStack(Item.byId(id), count);
                }
            }
            // v3：主背包 20 格（旧档无此段，保持空）
            if (ver >= 3) {
                for (int i = 0; i < Player.INV_SLOTS; i++) {
                    int id = d.readInt();
                    if (id < 0) {
                        player.inventory[i] = null;
                    } else {
                        int count = d.readInt();
                        player.inventory[i] = new ItemStack(Item.byId(id), count);
                    }
                }
            } else {
                for (int i = 0; i < Player.INV_SLOTS; i++) {
                    player.inventory[i] = null;
                }
            }
            enemies.clear();
            netEnemyById.clear();
            npcs.clear();
            occupiedHouses.clear();
            drops.clear();
            particles.clear();
            projectiles.clear();
            dmgNums.clear();
            tileLayer = null; // 强制方块层缓存重建
            statusMsg = "已读档（v" + ver + "）";
        } catch (IOException e) {
            statusMsg = "读档失败：" + e.getMessage();
        }
        statusTimer = 3f;
    }

    // ================= 渲染 =================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        // 像素方块游戏关闭全局抗锯齿，显著提升渲染速度（文字需要时单独开）
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);

        switch (state) {
            case MAIN_MENU:
                drawMainMenu(g2);
                break;
            case MP_MENU:
                drawMpMenu(g2);
                break;
            case HELP:
                drawHelpScreen(g2);
                break;
            default:
                drawWorld(g2);
                drawWeather(g2);
                drawHUD(g2);
                if (state == State.PAUSED) {
                    drawPauseMenu(g2);
                } else if (state == State.CRAFTING) {
                    drawCraftingPanel(g2);
                } else if (state == State.INVENTORY) {
                    drawInventoryPanel(g2);
                } else if (state == State.NPC_DIALOG) {
                    drawNpcDialog(g2);
                }
                break;
        }
    }

    private void drawBackground(Graphics2D g) {
        float t = (dayTime - 0.25f) / 0.5f; // 0..1 白天到黑夜
        t = Math.max(0, Math.min(1, t));
        int r = (int) lerp(135, 30, t);
        int gr = (int) lerp(206, 60, t);
        int b = (int) lerp(235, 90, t);
        // 血月：夜晚天空偏红
        if (bloodMoon && isNight()) {
            r = Math.min(255, r + 60);
            gr = Math.max(0, gr - 20);
            b = Math.max(0, b - 40);
        }
        g.setColor(new Color(r, gr, b));
        g.fillRect(0, 0, VIEW_W, VIEW_H);

        // 生物群系天空色调叠加
        int pgx = Math.max(0, Math.min(world.width - 1,
                (int) Math.floor((player.x + Player.W / 2f) / TILE)));
        int pb = world.biome[pgx];
        Color biomeTint = null;
        switch (pb) {
            case 1: biomeTint = new Color(255, 220, 140, 55); break;  // 沙漠：暖黄
            case 2: biomeTint = new Color(190, 225, 255, 65); break;  // 雪原：冷蓝
            case 3: biomeTint = new Color(90, 190, 110, 50); break;   // 丛林：绿
            case 4: biomeTint = new Color(140, 70, 170, 70); break;   // 腐化：紫
        }
        if (biomeTint != null) {
            g.setColor(biomeTint);
            g.fillRect(0, 0, VIEW_W, VIEW_H);
        }

        // 太阳/月亮
        float prog = (dayTime < 0.5f) ? dayTime * 2f : (dayTime - 0.5f) * 2f;
        float sx = VIEW_W * prog;
        float sy = VIEW_H * 0.15f + (float) Math.sin(prog * Math.PI) * 120f;
        if (dayTime > 0.75f || dayTime < 0.25f) {
            g.setColor(bloodMoon ? new Color(220, 50, 50) : UI.GRAY_220);
            g.fillOval((int) (sx - 30), (int) (sy - 30), 60, 60);
        } else {
            g.setColor(UI.GOLD_240B);
            g.fillOval((int) (sx - 40), (int) (sy - 40), 80, 80);
        }

        // 云
        g.setColor(UI.WHITE_180);
        for (int i = 0; i < 5; i++) {
            float cx = ((i * 320f + frameTimer * 12f) % (VIEW_W + 300)) - 150;
            float cy = 60 + (i * 97) % 180;
            g.fillOval((int) cx, (int) cy, 90, 40);
            g.fillOval((int) (cx + 40), (int) (cy - 15), 70, 36);
            g.fillOval((int) (cx + 85), (int) cy, 60, 30);
        }
    }

    private void drawWorld(Graphics2D g) {
        // 屏幕震动
        int shakeX = 0, shakeY = 0;
        if (shakeTimer > 0) {
            float t = shakeTimer / 0.25f;
            shakeX = (int) ((Math.random() * 2 - 1) * shakeIntensity * t);
            shakeY = (int) ((Math.random() * 2 - 1) * shakeIntensity * t);
            g.translate(shakeX, shakeY);
        }
        drawBackground(g);
        drawTileLayer(g);

        // 方块放置预览
        drawPlacePreview(g);
        // 挖掘进度条
        drawMiningBar(g);

        // 掉落物
        for (Drop d : drops) {
            int sx = (int) (d.x - cam.x);
            int sy = (int) (d.y - cam.y + (float) Math.sin(d.bobT * 3f) * 4f);
            if (d.itemId == -1) {
                // 金币：金色硬币
                g.setColor(new Color(255, 200, 50));
                g.fillOval(sx - 6, sy - 6, 12, 12);
                g.setColor(new Color(255, 230, 120));
                g.fillOval(sx - 4, sy - 4, 6, 6);
                g.setColor(new Color(200, 150, 20));
                g.drawOval(sx - 6, sy - 6, 12, 12);
                // 大额金币显示数量
                if (d.coinValue >= 10) {
                    g.setColor(Color.WHITE);
                    g.setFont(UI.FONT_BOLD_11);
                    g.drawString(String.valueOf(d.coinValue), sx + 8, sy + 4);
                }
            } else if (d.itemId == Item.HEART.id) {
                g.setColor(UI.RED_255);
                g.fillOval(sx - 7, sy - 7, 14, 14);
                g.setColor(UI.RED_220_LIGHT);
                g.fillOval(sx - 3, sy - 3, 6, 6);
            } else {
                Item item = Item.byId(d.itemId);
                if (item != null) {
                    ItemIcon.draw(g, item, sx - 8, sy - 8, 16);
                } else {
                    g.setColor(new Color(120, 200, 120));
                    g.fillRect(sx - 4, sy - 4, 8, 8);
                }
            }
        }

        drawEnemies(g);
        if (wormBoss != null && wormBoss.alive) {
            wormBoss.draw(g, cam);
        }
        if (destroyerBoss != null && destroyerBoss.alive) {
            destroyerBoss.draw(g, cam);
        }
        if (minion != null && minion.alive) {
            minion.draw(g, cam);
        }
        drawNpcs(g);
        drawRemotePlayers(g);
        drawPlayer(g);
        drawBobber(g);
        drawProjectiles(g);
        drawParticles(g);
        drawDamageNumbers(g);
        applyDarkness(g);
        drawTorchGlows(g);
        drawBossBar(g);
        drawChat(g);
        drawAchievementPanel(g);
        // 恢复屏幕震动平移
        if (shakeTimer > 0) {
            g.translate(-shakeX, -shakeY);
        }
    }

    /** 方块放置预览：手持方块时在鼠标位置显示半透明预览。 */
    private void drawPlacePreview(Graphics2D g) {
        ItemStack sel = player.hotbar[player.selected];
        if (sel == null || !sel.item.placeable) return;
        TileType placeTile = World.itemToTile(sel.item);
        if (placeTile == null) return;
        float wx = cam.x + mouseX;
        float wy = cam.y + mouseY;
        int tx = (int) Math.floor(wx / TILE);
        int ty = (int) Math.floor(wy / TILE);
        if (tx < 0 || tx >= world.width || ty < 0 || ty >= world.height) return;
        int sx = tx * TILE - (int) cam.x;
        int sy = ty * TILE - (int) cam.y;
        // 检查是否可放置
        boolean canPlace = world.get(tx, ty) == TileType.AIR && hasNeighbor(tx, ty);
        Rectangle pr = new Rectangle((int) player.x, (int) player.y, Player.W, Player.H);
        Rectangle tr = new Rectangle(tx * TILE, ty * TILE, TILE, TILE);
        if (pr.intersects(tr)) canPlace = false;
        // 绘制半透明预览
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, canPlace ? 0.5f : 0.25f));
        g.setColor(canPlace ? placeTile.color : new Color(255, 80, 80));
        g.fillRect(sx, sy, TILE, TILE);
        g.setColor(canPlace ? new Color(255, 255, 255, 150) : new Color(255, 100, 100, 150));
        g.drawRect(sx, sy, TILE - 1, TILE - 1);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private void drawMiningBar(Graphics2D g) {
        if (mineGX < 0 || mineGY < 0 || mineProgress <= 0) {
            return;
        }
        int sx = (int) (mineGX * TILE - cam.x);
        int sy = (int) (mineGY * TILE - cam.y);
        g.setColor(UI.BLACK_160);
        g.fillRect(sx, sy - 12, TILE, 6);
        g.setColor(UI.GOLD_220);
        g.fillRect(sx, sy - 12, (int) (TILE * mineProgress), 6);
    }

    private void drawEnemies(Graphics2D g) {
        for (Enemy e : enemies) {
            if (!e.alive) {
                continue;
            }
            int sx = (int) (e.x - cam.x);
            int sy = (int) (e.y - cam.y);
            if (e.type == Enemy.Type.WALL_OF_FLESH) {
                // 血肉墙：大型肉色墙壁+眼睛+嘴
                Color flesh = e.hitFlash > 0 ? new Color(255, 180, 180) : new Color(180, 60, 70);
                g.setColor(flesh);
                g.fillRoundRect(sx, sy, (int) e.w, (int) e.h, 20, 20);
                // 肉壁纹理
                g.setColor(new Color(150, 40, 50));
                for (int i = 0; i < 8; i++) {
                    int wx = sx + 15 + i * 22;
                    g.fillOval(wx, sy + 10 + (i % 3) * 30, 12, 8);
                }
                // 眼睛（两只）
                int ey = sy + 40;
                g.setColor(new Color(240, 220, 200));
                g.fillOval(sx + 40, ey, 35, 30);
                g.fillOval(sx + (int)e.w - 75, ey, 35, 30);
                g.setColor(new Color(200, 30, 30));
                g.fillOval(sx + 50, ey + 8, 15, 14);
                g.fillOval(sx + (int)e.w - 65, ey + 8, 15, 14);
                g.setColor(Color.BLACK);
                g.fillOval(sx + 55, ey + 11, 6, 6);
                g.fillOval(sx + (int)e.w - 60, ey + 11, 6, 6);
                // 嘴（大裂缝）
                g.setColor(new Color(80, 10, 20));
                g.fillRoundRect(sx + 50, sy + (int)e.h - 55, (int)e.w - 100, 30, 10, 10);
                // 牙齿
                g.setColor(new Color(240, 230, 210));
                for (int i = 0; i < 6; i++) {
                    int tx = sx + 60 + i * ((int)e.w - 120) / 5;
                    int[] txp = {tx, tx + 6, tx + 3};
                    int[] typ = {sy + (int)e.h - 55, sy + (int)e.h - 55, sy + (int)e.h - 42};
                    g.fillPolygon(txp, typ, 3);
                }
            } else if (e.type == Enemy.Type.EYE_OF_CTHULHU) {
                // 克苏鲁之眼：红色大眼球，瞳孔跟随玩家
                Color body = e.hitFlash > 0 ? UI.RED_200_LIGHT : new Color(190, 50, 60);
                g.setColor(body);
                g.fillOval(sx, sy, (int) e.w, (int) e.h);
                g.setColor(new Color(120, 18, 30));
                g.drawOval(sx, sy, (int) e.w, (int) e.h);
                int ecx = sx + (int) e.w / 2;
                int ecy = sy + (int) e.h / 2;
                float dxp = (player.x + Player.W / 2f) - (ecx + cam.x);
                float dyp = (player.y + Player.H / 2f) - (ecy + cam.y);
                float plen = (float) Math.hypot(dxp, dyp);
                if (plen < 1) {
                    plen = 1;
                }
                int px = (int) (dxp / plen * 14), py = (int) (dyp / plen * 14);
                g.setColor(new Color(232, 222, 200));
                g.fillOval(ecx - 14 + px, ecy - 12 + py, 28, 24);
                g.setColor(new Color(45, 20, 24));
                g.fillOval(ecx - 6 + px, ecy - 6 + py, 12, 12);
                g.setColor(UI.WHITE_180);
                g.fillOval(ecx - 8 + px, ecy - 9 + py, 5, 5);
            } else if (e.type == Enemy.Type.SKELETRON) {
                // 骷髅王：头骨 + 双手
                int ecx = sx + (int) e.w / 2;
                int ecy = sy + (int) e.h / 2;
                Color bone = e.hitFlash > 0 ? new Color(255, 240, 240) : new Color(225, 220, 210);
                // 头骨
                g.setColor(bone);
                g.fillOval(sx + 8, sy, (int) e.w - 16, (int) e.h - 12);
                g.setColor(UI.GRAY_180);
                g.drawOval(sx + 8, sy, (int) e.w - 16, (int) e.h - 12);
                // 眼窝（发光红眼）
                g.setColor(UI.DARK_30);
                g.fillOval(ecx - 20, ecy - 12, 14, 14);
                g.fillOval(ecx + 6, ecy - 12, 14, 14);
                g.setColor(e.enraged ? UI.RED_255_80 : new Color(255, 50, 50));
                g.fillOval(ecx - 17, ecy - 9, 8, 8);
                g.fillOval(ecx + 9, ecy - 9, 8, 8);
                // 鼻腔
                g.setColor(new Color(40, 40, 40));
                g.fillOval(ecx - 3, ecy + 2, 6, 8);
                // 牙齿
                g.setColor(new Color(240, 235, 225));
                for (int i = 0; i < 5; i++) {
                    g.fillRect(ecx - 18 + i * 8, ecy + 14, 5, 8);
                }
                // 双手（骨头手）
                float h1x = ecx + e.hand1OffsetX;
                float h1y = ecy + e.hand1OffsetY;
                float h2x = ecx + e.hand2OffsetX;
                float h2y = ecy + e.hand2OffsetY;
                g.setColor(bone);
                g.fillOval((int) h1x - 16, (int) h1y - 16, 32, 32);
                g.fillOval((int) h2x - 16, (int) h2y - 16, 32, 32);
                g.setColor(UI.GRAY_180);
                g.drawOval((int) h1x - 16, (int) h1y - 16, 32, 32);
                g.drawOval((int) h2x - 16, (int) h2y - 16, 32, 32);
                // 手指
                g.setColor(bone);
                for (int i = 0; i < 3; i++) {
                    g.fillRect((int) h1x - 12 + i * 8, (int) h1y + 10, 4, 8);
                    g.fillRect((int) h2x - 12 + i * 8, (int) h2y + 10, 4, 8);
                }
            } else if (e.type == Enemy.Type.EATER_OF_WORLDS) {
                // 世界吞噬者：蠕虫多节
                Color body = e.hitFlash > 0 ? UI.RED_200_LIGHT : new Color(120, 60, 140);
                Color dark = new Color(80, 30, 100);
                // 从尾部画到头部（头部在上层）
                for (int i = Enemy.EATER_SEGMENTS - 1; i >= 0; i--) {
                    int sxp = (int) (e.segX[i] - cam.x);
                    int syp = (int) (e.segY[i] - cam.y);
                    int size = (i == 0) ? 36 : 28 - i;
                    g.setColor(i == 0 ? new Color(150, 70, 170) : body);
                    g.fillOval(sxp - size / 2, syp - size / 2, size, size);
                    g.setColor(dark);
                    g.drawOval(sxp - size / 2, syp - size / 2, size, size);
                    // 节段高光
                    g.setColor(new Color(180, 100, 200, 120));
                    g.fillOval(sxp - size / 4, syp - size / 3, size / 3, size / 4);
                }
                // 头部嘴
                int hxp = (int) (e.segX[0] - cam.x);
                int hyp = (int) (e.segY[0] - cam.y);
                g.setColor(new Color(40, 10, 50));
                g.fillOval(hxp - 8, hyp - 4, 16, 12);
                // 牙齿
                g.setColor(new Color(240, 230, 220));
                for (int i = 0; i < 4; i++) {
                    g.fillRect(hxp - 7 + i * 5, hyp - 4, 3, 5);
                }
            } else if (e.type == Enemy.Type.DEMON_EYE) {
                // 恶魔眼：飞行小眼球，翅膀扇动
                int ecx = sx + (int) e.w / 2;
                int ecy = sy + (int) e.h / 2;
                // 翅膀（扇动动画）
                float wing = (float) Math.sin(frameTimer * 18f) * 0.5f + 0.5f;
                g.setColor(new Color(180, 60, 80, 180));
                g.fillOval(ecx - 18, ecy - 6 - (int) (wing * 4), 14, 10 + (int) (wing * 6));
                g.fillOval(ecx + 4, ecy - 6 - (int) (wing * 4), 14, 10 + (int) (wing * 6));
                // 眼球
                Color eyeBody = e.hitFlash > 0 ? UI.RED_200_LIGHT : new Color(200, 70, 80);
                g.setColor(eyeBody);
                g.fillOval(sx, sy, (int) e.w, (int) e.h);
                g.setColor(new Color(120, 20, 30));
                g.drawOval(sx, sy, (int) e.w, (int) e.h);
                // 眼白 + 瞳孔（跟随玩家）
                float dxp = (player.x + Player.W / 2f) - (ecx + cam.x);
                float dyp = (player.y + Player.H / 2f) - (ecy + cam.y);
                float plen = (float) Math.hypot(dxp, dyp);
                if (plen < 1) plen = 1;
                int ppx = (int) (dxp / plen * 4), ppy = (int) (dyp / plen * 3);
                g.setColor(new Color(240, 230, 220));
                g.fillOval(ecx - 6 + ppx, ecy - 5 + ppy, 12, 10);
                g.setColor(new Color(40, 10, 15));
                g.fillOval(ecx - 3 + ppx, ecy - 2 + ppy, 6, 6);
            } else if (e.type == Enemy.Type.ZOMBIE || e.type == Enemy.Type.MUMMY) {
                // 僵尸 / 木乃伊
                Color bodyColor = (e.type == Enemy.Type.MUMMY)
                        ? new Color(200, 180, 140) : new Color(80, 130, 80);
                g.setColor(e.hitFlash > 0 ? UI.RED_200_LIGHT : bodyColor);
                g.fillRect(sx, sy, (int) e.w, (int) e.h);
                g.setColor(new Color(30, 50, 30));
                g.fillRect(sx + 4, sy - 4, (int) e.w - 8, 5);
                g.fillRect(sx + 3, sy - 6, 3, 8);
                g.fillRect(sx + (int) e.w - 6, sy - 6, 3, 8);
                // 木乃伊绷带条纹
                if (e.type == Enemy.Type.MUMMY) {
                    g.setColor(new Color(170, 150, 110));
                    for (int i = 0; i < 4; i++) {
                        g.fillRect(sx, sy + 8 + i * 9, (int) e.w, 2);
                    }
                }
                g.setColor(UI.RED_255);
                g.fillOval(sx + 5, sy + 6, 4, 4);
                g.fillOval(sx + (int) e.w - 9, sy + 6, 4, 4);
            } else if (e.type == Enemy.Type.GOBLIN_WARRIOR || e.type == Enemy.Type.GOBLIN_ARCHER) {
                // 哥布林：绿色皮肤+尖耳+武器
                Color skin = e.hitFlash > 0 ? UI.RED_200_LIGHT : new Color(90, 160, 70);
                g.setColor(skin);
                g.fillRect(sx + 2, sy + 8, (int) e.w - 4, (int) e.h - 10); // 身体
                g.fillOval(sx, sy, (int) e.w, (int) (e.h * 0.45)); // 头
                // 尖耳朵
                g.setColor(new Color(70, 130, 50));
                int[] le = {sx, sx - 4, sx + 2};
                int[] lep = {sy + 4, sy + 8, sy + 10};
                g.fillPolygon(le, lep, 3);
                int[] re = {sx + (int)e.w, sx + (int)e.w + 4, sx + (int)e.w - 2};
                int[] rep = {sy + 4, sy + 8, sy + 10};
                g.fillPolygon(re, rep, 3);
                // 眼睛（红色）
                g.setColor(Color.RED);
                g.fillOval(sx + 5, sy + 6, 3, 3);
                g.fillOval(sx + (int) e.w - 8, sy + 6, 3, 3);
                // 衣服（棕色）
                g.setColor(new Color(120, 80, 50));
                g.fillRect(sx + 2, sy + (int)(e.h * 0.5), (int) e.w - 4, (int)(e.h * 0.3));
                // 武器
                if (e.type == Enemy.Type.GOBLIN_WARRIOR) {
                    // 战士：短剑
                    g.setColor(new Color(180, 180, 190));
                    g.fillRect(sx + (int)e.w - 2, sy + (int)(e.h * 0.4), 3, 12);
                    g.setColor(new Color(100, 70, 40));
                    g.fillRect(sx + (int)e.w - 3, sy + (int)(e.h * 0.35), 5, 3);
                } else {
                    // 弓箭手：弓
                    g.setColor(new Color(120, 80, 40));
                    g.drawArc(sx + (int)e.w - 4, sy + (int)(e.h * 0.3), 8, 16, -60, 120);
                    g.setColor(Color.WHITE);
                    g.drawLine(sx + (int)e.w, sy + (int)(e.h * 0.35), sx + (int)e.w, sy + (int)(e.h * 0.75));
                }
            } else if (e.type == Enemy.Type.PIRATE_DECKHAND || e.type == Enemy.Type.PIRATE_GUNNER) {
                // 海盗：白衬衫+蓝裤子+三角帽
                Color skin = e.hitFlash > 0 ? UI.RED_200_LIGHT : new Color(220, 180, 140);
                // 身体（白衬衫）
                g.setColor(new Color(230, 230, 220));
                g.fillRect(sx + 2, sy + (int)(e.h * 0.4), (int) e.w - 4, (int)(e.h * 0.35));
                // 裤子（蓝）
                g.setColor(new Color(50, 70, 130));
                g.fillRect(sx + 2, sy + (int)(e.h * 0.75), (int) e.w - 4, (int)(e.h * 0.25));
                // 头
                g.setColor(skin);
                g.fillOval(sx, sy, (int) e.w, (int)(e.h * 0.4));
                // 三角帽
                g.setColor(new Color(40, 40, 50));
                int[] hx = {sx, sx + (int)e.w, sx + (int)e.w - 4, sx + 4};
                int[] hy = {sy + 2, sy + 2, sy - 4, sy - 4};
                g.fillPolygon(hx, hy, 4);
                // 眼睛
                g.setColor(Color.BLACK);
                g.fillOval(sx + 5, sy + 8, 3, 3);
                g.fillOval(sx + (int) e.w - 8, sy + 8, 3, 3);
                // 胡子
                g.setColor(new Color(80, 50, 30));
                g.fillRect(sx + 4, sy + 14, (int)e.w - 8, 3);
                // 武器
                if (e.type == Enemy.Type.PIRATE_DECKHAND) {
                    // 弯刀
                    g.setColor(new Color(200, 200, 210));
                    g.drawArc(sx + (int)e.w - 2, sy + (int)(e.h * 0.35), 12, 14, -30, 120);
                } else {
                    // 火枪
                    g.setColor(new Color(80, 60, 40));
                    g.fillRect(sx + (int)e.w - 2, sy + (int)(e.h * 0.45), 10, 4);
                    g.setColor(new Color(150, 120, 60));
                    g.fillRect(sx + (int)e.w - 4, sy + (int)(e.h * 0.42), 4, 8);
                }
            } else {
                // 史莱姆（含生物群系变种）
                Color slimeColor;
                switch (e.type) {
                    case ICE_SLIME: slimeColor = new Color(140, 200, 230); break;
                    case JUNGLE_SLIME: slimeColor = new Color(50, 160, 80); break;
                    case DEVOURER: slimeColor = new Color(140, 70, 170); break;
                    default: slimeColor = new Color(90, 190, 120); break;
                }
                g.setColor(e.hitFlash > 0 ? UI.RED_220_LIGHT : slimeColor);
                g.fillOval(sx, sy, (int) e.w, (int) e.h);
                g.setColor(new Color(30, 90, 60));
                g.fillOval(sx + 5, sy + 6, 4, 5);
                g.fillOval(sx + (int) e.w - 9, sy + 6, 4, 5);
            }
            // 血条
            if (e.hp < e.maxHp && e.hp > 0) {
                int bw = (int) e.w;
                g.setColor(UI.BLACK_150);
                g.fillRect(sx, sy - 9, bw, 4);
                g.setColor(new Color(220, 60, 60));
                g.fillRect(sx, sy - 9, (int) (bw * Math.max(0, (float) e.hp / e.maxHp)), 4);
            }
        }
    }

    private void drawRemotePlayers(Graphics2D g) {
        for (RemotePlayer rp : remotePlayers.values()) {
            if (rp.slot == mySlot) {
                continue;
            }
            int sx = (int) (rp.x - cam.x);
            int sy = (int) (rp.y - cam.y);
            if (sx < -60 || sx > VIEW_W + 60) {
                continue;
            }
            // 远端玩家：蓝色服装区分
            boolean blink = rp.invuln > 0 && ((int) (rp.invuln * 12) % 2 == 0);
            Color body = blink ? new Color(160, 180, 255) : new Color(70, 110, 220);
            g.setColor(UI.DARK_60);
            g.fillRect(sx, sy + Player.H - 20, Player.W, 20); // 腿
            g.setColor(body);
            g.fillRect(sx, sy, Player.W, Player.H - 20); // 身体
            g.setColor(UI.GOLD_230);
            g.fillRect(sx + 3, sy + 2, Player.W - 6, 10); // 头
            g.setColor(UI.DARK_30);
            if (rp.facing >= 0) {
                g.fillRect(sx + Player.W - 8, sy + 12, 8, 4);
            } else {
                g.fillRect(sx, sy + 12, 8, 4);
            }
            // 名字
            g.setColor(UI.BLACK_180);
            g.fillRect(sx - 20, sy - 24, Player.W + 40, 18);
            g.setColor(Color.WHITE);
            g.setFont(UI.FONT_BOLD_13);
            String label = playerNames.getOrDefault(rp.slot, rp.name) + " " + rp.hp;
            int tw = g.getFontMetrics().stringWidth(label);
            g.drawString(label, sx + Player.W / 2f - tw / 2f, sy - 10);
        }
    }

    private void drawPlayer(Graphics2D g) {
        int sx = (int) (player.x - cam.x);
        int sy = (int) (player.y - cam.y);
        // 坐骑（在玩家下方绘制）
        if (player.mount != null) {
            float bounce = (float) Math.sin(System.currentTimeMillis() * 0.005) * 2f;
            int my = sy + Player.H - 5 + (int)bounce;
            // 史莱姆坐骑身体（蓝色大史莱姆）
            g.setColor(new Color(100, 180, 255));
            g.fillOval(sx - 6, my, Player.W + 12, 22);
            g.setColor(new Color(150, 210, 255, 200));
            g.fillOval(sx - 2, my + 2, Player.W + 4, 10);
            // 眼睛
            g.setColor(Color.WHITE);
            g.fillOval(sx + 2, my + 6, 5, 5);
            g.fillOval(sx + Player.W - 7, my + 6, 5, 5);
            g.setColor(Color.BLACK);
            g.fillOval(sx + 4, my + 8, 2, 2);
            g.fillOval(sx + Player.W - 5, my + 8, 2, 2);
        }
        boolean blink = player.invulnTimer > 0 && ((int) (player.invulnTimer * 12) % 2 == 0);
        if (blink) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }
        // 装备外观：根据装备改变颜色
        Color pantsColor = UI.DARK_60;
        Color bodyColor = new Color(190, 60, 60);
        Color helmetColor = null;
        if (player.armor[2] != null) {
            pantsColor = armorColor(player.armor[2]);
        }
        if (player.armor[1] != null) {
            bodyColor = armorColor(player.armor[1]);
        }
        if (player.armor[0] != null) {
            helmetColor = armorColor(player.armor[0]);
        }
        // 裤子
        g.setColor(pantsColor);
        g.fillRect(sx, sy + Player.H - 20, Player.W, 20);
        // 身体（衣服）
        g.setColor(bodyColor);
        g.fillRect(sx, sy, Player.W, Player.H - 20);
        // 头部/头盔
        if (helmetColor != null) {
            // 戴头盔：覆盖头部
            g.setColor(helmetColor);
            g.fillRect(sx - 1, sy - 2, Player.W + 2, 14);
            // 头盔面甲开口
            g.setColor(new Color(40, 30, 30));
            g.fillRect(sx + 2, sy + 4, Player.W - 4, 5);
        } else {
            // 没戴头盔：显示头发
            g.setColor(UI.GOLD_230);
            g.fillRect(sx + 3, sy + 2, Player.W - 6, 10);
        }
        // 眼睛
        g.setColor(UI.DARK_30);
        if (player.facing >= 0) {
            g.fillRect(sx + Player.W - 8, sy + 12, 8, 4);
        } else {
            g.fillRect(sx, sy + 12, 8, 4);
        }
        if (blink) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
        if (player.weapon != null && player.weapon.isBow()) {
            drawBow(g, sx, sy);
        } else {
            drawSword(g, sx, sy);
        }
    }

    /** 根据装备返回对应颜色。 */
    private Color armorColor(Item item) {
        if (item == null) return UI.DARK_60;
        String name = item.name;
        if (name.contains("铜")) return new Color(200, 120, 60);
        if (name.contains("铁")) return new Color(170, 170, 180);
        if (name.contains("金")) return new Color(230, 200, 70);
        if (name.contains("狱石") || name.contains("熔岩")) return new Color(200, 60, 40);
        if (name.contains("钴")) return new Color(80, 140, 220);
        if (name.contains("秘银")) return new Color(120, 200, 200);
        if (name.contains("精金")) return new Color(200, 100, 160);
        if (name.contains("神圣")) return new Color(240, 200, 230);
        if (name.contains("叶绿")) return new Color(80, 180, 80);
        return new Color(150, 150, 150);
    }

    /** 渲染钓鱼浮标与鱼线。 */
    private void drawBobber(Graphics2D g) {
        if (player.fishingState == 0) {
            return;
        }
        int bx = (int) (player.bobberX - cam.x);
        int by = (int) (player.bobberY + player.bobberDip - cam.y);
        // 鱼线：从玩家手到浮标
        int handX = (int) (player.x + Player.W / 2f - cam.x);
        int handY = (int) (player.y + 12 - cam.y);
        g.setColor(new Color(230, 230, 230, 200));
        g.setStroke(UI.STROKE_1);
        g.drawLine(handX, handY, bx, by);
        // 浮标：上红下白
        g.setColor(UI.RED_220);
        g.fillOval(bx - 4, by - 6, 8, 6);
        g.setColor(UI.GRAY_240);
        g.fillOval(bx - 4, by, 8, 5);
        // 咬钩时水花
        if (player.fishingState == 2) {
            g.setColor(new Color(180, 220, 255, 180));
            for (int i = 0; i < 3; i++) {
                int ox = (int) (Math.sin(frameTimer * 15f + i * 2f) * 8);
                g.fillOval(bx + ox - 2, by + 6 + i * 2, 4, 2);
            }
        }
    }

    private void drawBow(Graphics2D g, int sx, int sy) {
        int cx = sx + Player.W / 2;
        int cy = sy + Player.H / 2 - 4;
        int dir = player.facing;
        // 拉弓动画：蓄力时弦后拉，箭搭上
        float pull = player.swingTimer > 0 ? Math.max(0, Math.min(1, player.swingTimer / 0.35f)) : 0;
        g.setColor(new Color(120, 84, 48));
        g.setStroke(new BasicStroke(3));
        g.drawArc(cx - 12, cy - 14, 26, 28, dir > 0 ? -75 : -105, 150);
        g.setColor(new Color(225, 222, 205));
        g.setStroke(UI.STROKE_1);
        int nockX = cx + 10 * dir;
        int gripX = cx - 6 * dir;
        g.drawLine(nockX, cy - 14 + 8, gripX + (int) (7 * pull * dir), cy);
        g.drawLine(gripX + (int) (7 * pull * dir), cy, nockX, cy + 14 - 8);
        // 箭（搭在弦上）
        if (pull > 0.2f) {
            g.setColor(UI.GOLD_150);
            g.fillRect(gripX + (int) (7 * pull * dir) + 2 * dir, cy - 1, 14, 3);
            g.setColor(UI.GREEN_235);
            g.fillOval(gripX + (int) (7 * pull * dir) + 12 * dir, cy - 2, 4, 4);
        }
        g.setStroke(UI.STROKE_1);
    }

    private void drawSword(Graphics2D g, int sx, int sy) {
        if (player.swingTimer <= 0) {
            return;
        }
        float swing = player.swingDuration > 0 ? 1f - player.swingTimer / player.swingDuration : 0f; // 0..1
        float len = 26 + (player.weapon != null ? player.weapon.damage : 1) * 0.5f; // 武器越强剑越长
        float base = (float) Math.toRadians(-62);
        float sweep = (float) Math.toRadians(224);
        float angle = (base + swing * sweep) * player.facing;
        int bx = sx + Player.W / 2;
        int by = sy + Player.H / 2 - 4;
        int ex = bx + (int) (len * Math.cos(angle));
        int ey = by + (int) (len * Math.sin(angle));
        // 剑身轮廓
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(7));
        g.drawLine(bx, by, ex, ey);
        // 剑光
        g.setColor(UI.WHITE_200);
        g.setStroke(new BasicStroke(4));
        g.drawLine(bx, by, ex, ey);
        // 剑端闪光
        g.setColor(new Color(255, 255, 210, 230));
        g.fillOval(ex - 3, ey - 3, 6, 6);
        // 挥砍弧光残影（从上一位置到当前位置的粗轨迹）
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.28f));
        g.setColor(UI.WHITE);
        g.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float prevAngle = (base + Math.max(0f, swing - 0.18f) * sweep) * player.facing;
        int pex = bx + (int) (len * Math.cos(prevAngle));
        int pey = by + (int) (len * Math.sin(prevAngle));
        g.drawLine(pex, pey, ex, ey);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        g.setStroke(UI.STROKE_1);
    }

    private void drawParticles(Graphics2D g) {
        for (Particle p : particles) {
            float a = Math.max(0, p.life / p.maxLife);
            g.setColor(new Color((p.color >> 16) & 0xFF, (p.color >> 8) & 0xFF, p.color & 0xFF,
                    (int) (200 * a)));
            g.fillRect((int) (p.x - cam.x - 2), (int) (p.y - cam.y - 2), 4, 4);
        }
    }

    // ================= 攻击特效：伤害数字 / 命中粒子 =================
    private void addDamageNumber(float wx, float wy, int dmg) {
        if (dmgNums.size() > 32) {
            dmgNums.remove(0);
        }
        dmgNums.add(new DamageNumber(wx + (rnd.nextFloat() - 0.5f) * 6f, wy - 6f, dmg));
    }

    private void updateDamageNumbers(float dt) {
        for (Iterator<DamageNumber> it = dmgNums.iterator(); it.hasNext(); ) {
            DamageNumber n = it.next();
            n.life -= dt;
            n.y -= 40 * dt;
            if (n.life <= 0) {
                it.remove();
            }
        }
    }

    private void drawDamageNumbers(Graphics2D g) {
        for (DamageNumber n : dmgNums) {
            float a = Math.max(0, n.life / n.maxLife);
            int sx = (int) (n.x - cam.x);
            int sy = (int) (n.y - cam.y);
            g.setFont(new Font("Dialog", Font.BOLD, 16));
            String txt = "-" + n.dmg;
            g.setColor(new Color(0, 0, 0, (int) (150 * a)));
            g.drawString(txt, sx + 1, sy + 1);
            g.setColor(new Color(255, 230, 120, (int) (255 * a)));
            g.drawString(txt, sx, sy);
        }
    }

    /** 攻击命中时在目标处溅出粒子。 */
    private void spawnHitParticles(float wx, float wy) {
        for (int i = 0; i < 8; i++) {
            float vx = (rnd.nextFloat() - 0.5f) * 220f;
            float vy = -rnd.nextFloat() * 180f;
            int col = rnd.nextBoolean() ? 0xFFFFFF : 0xFFE8A0;
            particles.add(new Particle(wx, wy, vx, vy, 0.25f + rnd.nextFloat() * 0.2f, col));
        }
    }

    // ================= 远程武器（弓 + 箭矢） =================
    private void tryShoot() {
        if (player.attackCooldown > 0) {
            return;
        }
        if (player.countOf(Item.ARROW) <= 0) {
            statusMsg = "没有箭矢！先去工作台合成箭";
            statusTimer = 2.5f;
            return;
        }
        player.consume(Item.ARROW, 1);
        player.attackCooldown = 0.5f;
        player.swingTimer = 0.35f; // 拉弓动画计时（复用挥剑计时器驱动动画）
        float pcx = player.x + Player.W / 2f;
        float pcy = player.y + Player.H / 2f - 4;
        float wx = cam.x + mouseX;
        float wy = cam.y + mouseY;
        float dx = wx - pcx, dy = wy - pcy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) {
            dx = player.facing;
            dy = 0;
            len = 1f;
        }
        float sp = 470f;
        int dmg = (int)((player.weapon != null ? player.weapon.damage : 8) * player.damageMul(1));
        projectiles.add(new Projectile(pcx, pcy, dx / len * sp, dy / len * sp, dmg));
        player.facing = dx >= 0 ? 1 : -1;
    }

    /** 施放魔法：消耗魔力，发射魔法投射物。 */
    private float magicCooldown = 0;
    private void tryCastMagic(Item staff) {
        if (magicCooldown > 0) {
            magicCooldown -= 0.016f;
            return;
        }
        if (!player.consumeMana(staff.manaCost)) {
            statusMsg = "魔力不足！需要 " + staff.manaCost + " 点魔力";
            statusTimer = 1.5f;
            return;
        }
        magicCooldown = 0.35f;
        player.swingTimer = 0.25f;
        float pcx = player.x + Player.W / 2f;
        float pcy = player.y + Player.H / 2f - 4;
        float wx = cam.x + mouseX;
        float wy = cam.y + mouseY;
        float dx = wx - pcx, dy = wy - pcy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) {
            dx = player.facing;
            dy = 0;
            len = 1f;
        }
        float sp = (staff == Item.MAGIC_DAGGER) ? 550f : 380f;
        int magicDmg = (int)(staff.damage * player.damageMul(2));
        Projectile magic = new Projectile(pcx, pcy, dx / len * sp, dy / len * sp, magicDmg);
        magic.fromEnemy = false;
        magic.isMagic = true;
        magic.life = 2.0f;
        projectiles.add(magic);
        player.facing = dx >= 0 ? 1 : -1;
        SoundPlayer.play("craft");
        // 施法粒子
        for (int i = 0; i < 5; i++) {
            particles.add(new Particle(pcx, pcy,
                    (float)(Math.random() * 80 - 40), (float)(Math.random() * -60),
                    0.4f, staff.color.getRGB()));
        }
    }

    private void updateProjectiles(float dt) {
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile pr = it.next();
            pr.update(dt, world);
            if (!pr.alive) {
                spawnHitParticles(pr.x, pr.y);
                it.remove();
                continue;
            }
            // 敌人火球：只伤害玩家
            if (pr.fromEnemy) {
                if (Math.abs(pr.x - (player.x + Player.W / 2f)) < Player.W / 2f + 6
                        && Math.abs(pr.y - (player.y + Player.H / 2f)) < Player.H / 2f + 6) {
                    player.hurtAt(pr.damage, Math.signum(pr.vx) * 100, -80);
                    if (player.hp <= 0) handlePlayerDeath();
                    spawnHitParticles(pr.x, pr.y);
                    it.remove();
                    continue;
                }
            }
            // 命中判定：单机/主机本地结算；客户端对本地渲染的敌人做乐观判定（主机 ENEMY_SYNC 会校正血量）
            Enemy hit = null;
            if (!pr.fromEnemy) {
                for (Enemy e : enemies) {
                    if (e.alive && pr.hits(e)) {
                        hit = e;
                        break;
                    }
                }
            }
            if (hit != null) {
                hit.hurt(pr.damage, Math.signum(pr.vx) * 140, -120);
                hit.lastAttackerSlot = 0;
                addDamageNumber(hit.x + hit.w / 2f, hit.y, pr.damage);
                spawnHitParticles(pr.x, pr.y);
                it.remove();
                continue;
            }
            // 世界吞噬者：箭矢/魔法弹命中任意段
            if (!pr.fromEnemy && wormBoss != null && wormBoss.alive) {
                int seg = wormBoss.hitSegment(pr.x - 4, pr.y - 4, 8, 8);
                if (seg >= 0) {
                    wormBoss.damage(pr.damage, seg);
                    addDamageNumber(pr.x, pr.y - 10, pr.damage * (seg == 0 ? 2 : 1));
                    spawnHitParticles(pr.x, pr.y);
                    it.remove();
                    continue;
                }
            }
            // 机械毁灭者：箭矢/魔法弹命中任意段
            if (!pr.fromEnemy && destroyerBoss != null && destroyerBoss.alive) {
                int seg = destroyerBoss.hitSegment(pr.x - 4, pr.y - 4, 8, 8);
                if (seg >= 0) {
                    destroyerBoss.damage(pr.damage, seg);
                    addDamageNumber(pr.x, pr.y - 10, pr.damage * (seg == 0 ? 2 : 1));
                    spawnHitParticles(pr.x, pr.y);
                    it.remove();
                }
            }
        }
    }

    private void drawProjectiles(Graphics2D g) {
        for (Projectile pr : projectiles) {
            int sx = (int) (pr.x - cam.x);
            int sy = (int) (pr.y - cam.y);
            if (pr.fromEnemy) {
                // 敌人火球：橙色发光圆形
                g.setColor(new Color(255, 120, 20, 180));
                g.fillOval(sx - 8, sy - 8, 16, 16);
                g.setColor(new Color(255, 200, 60));
                g.fillOval(sx - 5, sy - 5, 10, 10);
                g.setColor(new Color(255, 255, 180));
                g.fillOval(sx - 2, sy - 2, 4, 4);
            } else if (pr.isMagic) {
                // 玩家魔法弹：发光球体（颜色由速度推断，火花=橙，飞刀=蓝）
                Color mc = (pr.vx * pr.vx + pr.vy * pr.vy) > 200000
                        ? new Color(180, 200, 255) : new Color(255, 140, 50);
                g.setColor(new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), 160));
                g.fillOval(sx - 7, sy - 7, 14, 14);
                g.setColor(mc);
                g.fillOval(sx - 4, sy - 4, 8, 8);
                g.setColor(Color.WHITE);
                g.fillOval(sx - 2, sy - 2, 3, 3);
            } else {
                g.rotate(pr.angle, sx, sy);
                g.setColor(UI.GOLD_150);
                g.fillRect(sx - 7, sy - 1, 14, 3);
                g.setColor(UI.GREEN_235);
                g.fillOval(sx + 4, sy - 2, 4, 4);
                g.rotate(-pr.angle, sx, sy);
            }
        }
    }

    /** 当前画面黑暗度 0..1（白天低、夜晚/地下高）。 */
    private float darknessAlpha() {
        float t = (dayTime - 0.25f) / 0.5f;
        t = Math.max(0, Math.min(1, t));
        float a = 0.62f * t;
        boolean under = player.y > world.surfaceY * TILE + 20;
        if (under) {
            float depth = Math.max(0, (player.y - world.surfaceY * TILE) / (world.height * TILE));
            a = 0.75f + depth * 0.2f;
        }
        return a;
    }

    /** 火把光晕：仅在较暗时绘制，且只遍历视口附近的格子（避免全图扫描）。 */
    private void drawTorchGlows(Graphics2D g) {
        if (darknessAlpha() < 0.35f) {
            return;
        }
        int ox = (int) (cam.x / TILE);
        int oy = (int) (cam.y / TILE);
        int tw = VIEW_W / TILE + 2;
        int th = VIEW_H / TILE + 2;
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g.setColor(new Color(255, 180, 80));
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                int gx = ox + tx, gy = oy + ty;
                if (gx < 0 || gx >= world.width || gy < 0 || gy >= world.height) {
                    continue;
                }
                if (world.get(gx, gy) == TileType.TORCH) {
                    int sx = (int) (gx * TILE - cam.x);
                    int sy = (int) (gy * TILE - cam.y);
                    g.fillOval(sx - 90, sy - 90, 180, 180);
                }
            }
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }

    private void applyDarkness(Graphics2D g) {
        float a = darknessAlpha();
        if (a <= 0.01f) {
            return;
        }
        // 玩家周围径向光照：中心透明，远处渐暗（一次填充，替代原 24 次无效 fillOval）
        int px = (int) (player.x + Player.W / 2f - cam.x);
        int py = (int) (player.y + Player.H / 2f - cam.y);
        int pr = 170;
        RadialGradientPaint rgp = new RadialGradientPaint(
                px, py, pr,
                new float[]{0f, 0.55f, 1f},
                new Color[]{
                        new Color(8, 8, 30, 0),
                        new Color(8, 8, 30, (int) (255 * a * 0.55f)),
                        new Color(8, 8, 30, (int) (255 * a))
                });
        g.setPaint(rgp);
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setPaint(null);
    }

    /**
     * 方块层滚动缓冲：首帧全量渲染；摄像机跨格时平移旧内容并只重绘新露出的列/行；
     * 方块被挖掘/放置时按 modCount 全量重绘视口。避免每帧全量重建导致的卡顿。
     */
    private void drawTileLayer(Graphics2D g) {
        int tw = VIEW_W / TILE + 2;
        int th = VIEW_H / TILE + 2;
        int ox = (int) (cam.x / TILE);
        int oy = (int) (cam.y / TILE);
        // 每次全量渲染视口，避免滚动缓存导致的空白问题
        if (tileLayer == null || tileCacheW != tw || tileCacheH != th) {
            tileLayer = new BufferedImage(tw * TILE, th * TILE, BufferedImage.TYPE_INT_RGB);
            tileCacheW = tw;
            tileCacheH = th;
        }
        tileOX = ox;
        tileOY = oy;
        renderTiles(ox, oy, tw, th, true);
        lastWorldRender = world.modCount;
        g.drawImage(tileLayer, (int)(-cam.x % TILE), (int)(-cam.y % TILE), null);
    }

    /** 全量渲染 [gx0,gx0+tw) x [gy0,gy0+th) 到 tileLayer。 */
    private void renderTiles(int gx0, int gy0, int tw, int th, boolean fillBg) {
        Graphics2D wg = tileLayer.createGraphics();
        if (fillBg) {
            wg.setColor(UI.DARK_24);
            wg.fillRect(0, 0, tw * TILE, th * TILE);
        }
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                drawTileAt(wg, gx0 + tx, gy0 + ty, tx * TILE, ty * TILE);
            }
        }
        wg.dispose();
    }

    /** 滚动平移：用 copyArea 在缓存图内平移，只重绘新露出的列与行。 */
    private void scrollTiles(int ox, int oy, int tw, int th) {
        int shiftX = (tileOX - ox) * TILE;
        int shiftY = (tileOY - oy) * TILE;
        int cw = tw * TILE, ch = th * TILE;
        Graphics2D lg = tileLayer.createGraphics();
        // 同一图像内平移像素（硬件加速，无临时内存分配）
        lg.copyArea(0, 0, cw, ch, shiftX, shiftY);
        // 填充平移后露出的背景区域
        lg.setColor(UI.DARK_24);
        if (shiftX > 0) {
            lg.fillRect(0, 0, shiftX, ch);
        } else if (shiftX < 0) {
            lg.fillRect(cw + shiftX, 0, -shiftX, ch);
        }
        if (shiftY > 0) {
            lg.fillRect(0, 0, cw, shiftY);
        } else if (shiftY < 0) {
            lg.fillRect(0, ch + shiftY, cw, -shiftY);
        }
        // 新露出的列
        int dx = tileOX - ox;
        if (dx > 0) {
            for (int tx = tw - dx; tx < tw; tx++) {
                for (int ty = 0; ty < th; ty++) {
                    drawTileAt(lg, ox + tx, oy + ty, tx * TILE, ty * TILE);
                }
            }
        } else if (dx < 0) {
            for (int tx = 0; tx < -dx; tx++) {
                for (int ty = 0; ty < th; ty++) {
                    drawTileAt(lg, ox + tx, oy + ty, tx * TILE, ty * TILE);
                }
            }
        }
        // 新露出的行
        int dy = tileOY - oy;
        if (dy > 0) {
            for (int ty = th - dy; ty < th; ty++) {
                for (int tx = 0; tx < tw; tx++) {
                    drawTileAt(lg, ox + tx, oy + ty, tx * TILE, ty * TILE);
                }
            }
        } else if (dy < 0) {
            for (int ty = 0; ty < -dy; ty++) {
                for (int tx = 0; tx < tw; tx++) {
                    drawTileAt(lg, ox + tx, oy + ty, tx * TILE, ty * TILE);
                }
            }
        }
        lg.dispose();
        tileOX = ox;
        tileOY = oy;
    }

    /** 在目标 Graphics 的 (px,py) 处绘制一格方块纹理。 */
    private void drawTileAt(Graphics2D g, int gx, int gy, int px, int py) {
        if (gx < 0 || gx >= world.width || gy < 0 || gy >= world.height) {
            return;
        }
        TileType tt = world.get(gx, gy);
        if (tt == null || tt == TileType.AIR) {
            return;
        }
        Random r = new Random(gx * 7919L + gy * 104729L);
        g.setColor(tt.color);
        g.fillRect(px, py, TILE, TILE);
        if (tt == TileType.GRASS) {
            g.setColor(new Color(90, 190, 70));
            g.fillRect(px, py, TILE, 6);
            g.setColor(new Color(70, 150, 60));
            for (int i = 0; i < 6; i++) {
                int x = r.nextInt(TILE);
                g.fillRect(px + x, py, 2, 8);
            }
        } else if (tt == TileType.STONE) {
            g.setColor(new Color(110, 112, 118));
            for (int i = 0; i < 4; i++) {
                int x = r.nextInt(TILE - 5);
                int y = r.nextInt(TILE - 4);
                g.fillRect(px + x, py + y, 4 + r.nextInt(3), 3);
            }
        } else if (tt == TileType.COPPER) {
            g.setColor(new Color(180, 100, 60));
            for (int i = 0; i < 3; i++) {
                int x = r.nextInt(TILE - 5);
                int y = r.nextInt(TILE - 5);
                g.fillOval(px + x, py + y, 5, 5);
            }
        } else if (tt == TileType.IRON) {
            g.setColor(new Color(150, 150, 160));
            for (int i = 0; i < 3; i++) {
                int x = r.nextInt(TILE - 5);
                int y = r.nextInt(TILE - 5);
                g.fillRect(px + x, py + y, 5, 5);
            }
        } else if (tt == TileType.WOOD) {
            g.setColor(new Color(110, 80, 50));
            for (int i = 0; i < 3; i++) {
                int x = r.nextInt(TILE - 4);
                int y = r.nextInt(TILE - 3);
                g.fillRect(px + x, py + y, 3, 2);
            }
        } else if (tt == TileType.LEAF) {
            g.setColor(new Color(60, 140, 70));
            for (int i = 0; i < 5; i++) {
                int x = r.nextInt(TILE - 4);
                int y = r.nextInt(TILE - 4);
                g.fillOval(px + x, py + y, 4, 4);
            }
        } else if (tt == TileType.WORKBENCH) {
            g.setColor(new Color(120, 84, 50));
            g.fillRect(px, py + 10, TILE, 6);
            g.fillRect(px + 2, py, 3, 10);
            g.fillRect(px + TILE - 5, py, 3, 10);
            g.fillRect(px + 4, py + 13, 4, 3);
            g.fillRect(px + TILE - 8, py + 13, 4, 3);
        } else if (tt == TileType.HEART_CRYSTAL) {
            g.setColor(new Color(255, 120, 160));
            g.fillOval(px + 2, py + 2, 12, 12);
            g.setColor(new Color(255, 230, 240));
            g.fillOval(px + 5, py + 5, 4, 4);
        } else if (tt == TileType.GEL_BLOCK) {
            g.setColor(new Color(120, 200, 255, 200));
            g.fillRect(px + 1, py + 1, TILE - 2, TILE - 2);
            g.setColor(new Color(220, 250, 255));
            g.fillOval(px + 3, py + 3, 4, 4);
        } else if (tt == TileType.TORCH) {
            g.setColor(new Color(120, 84, 50));
            g.fillRect(px + 7, py + 4, 2, 10);
            g.setColor(new Color(255, 200, 60));
            g.fillOval(px + 5, py, 6, 6);
            g.setColor(UI.GOLD_240B);
            g.fillOval(px + 7, py + 2, 3, 3);
        } else if (tt == TileType.SAND) {
            g.setColor(new Color(200, 178, 120));
            for (int i = 0; i < 4; i++) {
                g.fillRect(px + r.nextInt(TILE - 3), py + r.nextInt(TILE - 3), 3, 2);
            }
        } else if (tt == TileType.SNOW) {
            g.setColor(new Color(220, 228, 240));
            for (int i = 0; i < 3; i++) {
                g.fillOval(px + r.nextInt(TILE - 4), py + r.nextInt(TILE - 4), 4, 3);
            }
        } else if (tt == TileType.ICE) {
            g.setColor(new Color(180, 225, 250));
            g.drawRect(px + 3, py + 3, TILE - 7, TILE - 7);
            g.fillRect(px + 6, py + 5, 3, 3);
        } else if (tt == TileType.CACTUS) {
            g.setColor(new Color(90, 170, 70));
            for (int i = 0; i < 3; i++) {
                g.fillRect(px + 3 + r.nextInt(TILE - 6), py + r.nextInt(TILE - 4), 3, 2);
            }
            g.setColor(new Color(200, 240, 160));
            g.fillOval(px + 2, py + 1, 3, 3);
            g.fillOval(px + TILE - 5, py + TILE - 6, 3, 3);
        } else if (tt == TileType.JUNGLE_GRASS) {
            g.setColor(new Color(70, 170, 72));
            g.fillRect(px, py, TILE, 6);
            g.setColor(new Color(40, 130, 60));
            for (int i = 0; i < 8; i++) {
                int x = r.nextInt(TILE);
                g.fillRect(px + x, py, 2, 8);
            }
        }
    }

    // ================= 小地图 =================
    /** 更新小地图图像（每200ms一次，避免性能开销）。 */
    private void updateMinimap() {
        long now = System.currentTimeMillis();
        if (now - minimapLastUpdate < MINIMAP_UPDATE_INTERVAL) return;
        minimapLastUpdate = now;

        if (minimapImg == null || minimapImg.getWidth() != MINIMAP_W || minimapImg.getHeight() != MINIMAP_H) {
            minimapImg = new BufferedImage(MINIMAP_W, MINIMAP_H, BufferedImage.TYPE_INT_RGB);
        }

        // 小地图显示范围：以玩家为中心，覆盖 MINIMAP_W*4 格宽，MINIMAP_H*4 格高
        int tileScale = 4; // 每格在小地图上占4像素
        int centerGX = (int)((player.x + Player.W / 2f) / World.TILE);
        int centerGY = (int)((player.y + Player.H / 2f) / World.TILE);
        int halfW = MINIMAP_W / (2 * tileScale);
        int halfH = MINIMAP_H / (2 * tileScale);

        Graphics2D mg = minimapImg.createGraphics();
        mg.setColor(new Color(10, 10, 20));
        mg.fillRect(0, 0, MINIMAP_W, MINIMAP_H);

        for (int my = 0; my < MINIMAP_H; my += tileScale) {
            for (int mx = 0; mx < MINIMAP_W; mx += tileScale) {
                int gx = centerGX - halfW + mx / tileScale;
                int gy = centerGY - halfH + my / tileScale;
                if (gx < 0 || gx >= world.width || gy < 0 || gy >= world.height) continue;
                TileType tile = world.get(gx, gy);
                if (tile == TileType.AIR) continue; // 空气不画
                Color c = minimapColor(tile.ordinal(), gy);
                mg.setColor(c);
                mg.fillRect(mx, my, tileScale, tileScale);
            }
        }
        mg.dispose();
    }

    /** 小地图方块颜色（简化版，按方块类型分组）。 */
    private Color minimapColor(int tile, int gy) {
        return switch (tile) {
            case 1 -> new Color(90, 140, 60);    // 草
            case 2 -> new Color(120, 90, 60);    // 泥土
            case 3 -> new Color(100, 100, 110);  // 石头
            case 4 -> new Color(130, 110, 70);   // 木头
            case 5 -> new Color(50, 80, 120);    // 树叶
            case 6 -> new Color(200, 180, 100);  // 沙子
            case 7 -> new Color(220, 220, 230);  // 雪
            case 8 -> new Color(60, 120, 50);    // 丛林草
            case 9 -> new Color(80, 50, 100);    // 腐化
            case 10 -> new Color(180, 50, 30);   // 地狱岩
            case 11 -> new Color(40, 40, 50);     // 黑曜石
            case 12 -> new Color(180, 120, 60);  // 铜矿石
            case 13 -> new Color(180, 180, 190); // 铁矿石
            case 14 -> new Color(220, 200, 80);  // 金矿石
            case 15 -> new Color(200, 60, 40);   // 狱石
            case 16 -> new Color(60, 160, 210);  // 钴矿
            case 17 -> new Color(190, 150, 230); // 秘银矿
            case 18 -> new Color(120, 50, 160);  // 魔金矿
            case 19 -> new Color(100, 100, 120); // 砖块
            case 20 -> new Color(80, 120, 160);  // 玻璃
            default -> new Color(100, 100, 100);
        };
    }

    /** 绘制小地图（右上角，FPS下方）。 */
    private void drawMinimap(Graphics2D g) {
        if (!showMinimap || minimapImg == null) return;
        int mx = VIEW_W - MINIMAP_W - 16;
        int my = 44;
        // 边框
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(mx - 4, my - 4, MINIMAP_W + 8, MINIMAP_H + 24, 8, 8);
        g.setColor(new Color(180, 180, 200));
        g.drawRoundRect(mx - 4, my - 4, MINIMAP_W + 8, MINIMAP_H + 24, 8, 8);
        // 地图图像
        g.drawImage(minimapImg, mx, my, null);
        // 玩家位置标记（中心白点）
        int px = mx + MINIMAP_W / 2;
        int py = my + MINIMAP_H / 2;
        g.setColor(Color.WHITE);
        g.fillOval(px - 3, py - 3, 6, 6);
        g.setColor(Color.BLACK);
        g.drawOval(px - 3, py - 3, 6, 6);
        // 标题
        g.setColor(new Color(200, 200, 220));
        g.setFont(UI.FONT_BOLD_11);
        g.drawString("小地图 [M切换]", mx + 4, my + MINIMAP_H + 16);
    }

    // ================= HUD =================
    private void drawHUD(Graphics2D g) {
        // 成就通知（右上角）
        drawAchievementNotifications(g);
        // 小地图（右上角）
        drawMinimap(g);
        // FPS（右上角）
        fpsFrames++;
        long now = System.currentTimeMillis();
        if (now - fpsLast >= 500) {
            fpsShown = (int) (fpsFrames * 1000.0 / (now - fpsLast));
            fpsFrames = 0;
            fpsLast = now;
        }
        String fpsTxt = fpsShown + " FPS";
        g.setFont(UI.FONT_BOLD_13);
        int fpsW = g.getFontMetrics().stringWidth(fpsTxt);
        g.setColor(UI.BLACK_160);
        g.fillRoundRect(VIEW_W - fpsW - 26, 14, fpsW + 16, 22, 8, 8);
        g.setColor(new Color(200, 230, 255));
        g.drawString(fpsTxt, VIEW_W - fpsW - 18, 30);
        // 天气
        String weatherTxt = weather == 1 ? "雨天" : "晴天";
        if (bloodMoon) {
            weatherTxt = "血月";
        }
        int wW = g.getFontMetrics().stringWidth(weatherTxt);
        g.setColor(UI.BLACK_160);
        g.fillRoundRect(VIEW_W - wW - 26, 40, wW + 16, 20, 6, 6);
        g.setColor(bloodMoon ? new Color(255, 80, 80) : (weather == 1 ? new Color(150, 190, 240) : new Color(255, 230, 120)));
        g.drawString(weatherTxt, VIEW_W - wW - 18, 55);
        // 智能光标状态
        if (smartCursor) {
            String scTxt = "智能光标";
            int scW = g.getFontMetrics().stringWidth(scTxt);
            g.setColor(new Color(60, 180, 100, 200));
            g.fillRoundRect(VIEW_W - scW - 26, 64, scW + 16, 20, 6, 6);
            g.setColor(Color.WHITE);
            g.drawString(scTxt, VIEW_W - scW - 18, 79);
        }
        // 哥布林入侵进度
        if (goblinInvasion) {
            String invTxt = "哥布林入侵 " + goblinKilled + "/" + goblinTotal;
            int invW = g.getFontMetrics().stringWidth(invTxt);
            int invX = (int)(VIEW_W / 2f - invW / 2f);
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRoundRect(invX - 10, 14, invW + 20, 24, 8, 8);
            g.setColor(new Color(200, 80, 60));
            g.setFont(UI.FONT_BOLD_14);
            g.drawString(invTxt, invX, 31);
        }
        // 海盗入侵进度
        if (pirateInvasion) {
            String invTxt = "海盗入侵 " + pirateKilled + "/" + pirateTotal;
            int invW = g.getFontMetrics().stringWidth(invTxt);
            int invX = (int)(VIEW_W / 2f - invW / 2f);
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRoundRect(invX - 10, 42, invW + 20, 24, 8, 8);
            g.setColor(new Color(200, 160, 60));
            g.setFont(UI.FONT_BOLD_14);
            g.drawString(invTxt, invX, 59);
        }
        // 生命
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(16, 14, 240, 30, 10, 10);
        g.setColor(new Color(120, 20, 20));
        g.fillRoundRect(20, 18, 232, 22, 8, 8);
        g.setColor(UI.RED_220);
        g.fillRoundRect(20, 18, (int) (232 * Math.max(0, (float) player.hp / player.maxHp)), 22, 8, 8);
        g.setColor(Color.WHITE);
        g.setFont(UI.FONT_BOLD_14);
        g.drawString("生命 " + player.hp + "/" + player.maxHp, 30, 34);

        // 魔力条
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(16, 48, 240, 24, 8, 8);
        g.setColor(new Color(20, 40, 100));
        g.fillRoundRect(20, 51, 232, 18, 6, 6);
        g.setColor(new Color(80, 140, 255));
        g.fillRoundRect(20, 51, (int) (232 * Math.max(0, (float) player.mana / player.maxMana)), 18, 6, 6);
        g.setColor(Color.WHITE);
        g.setFont(UI.FONT_BOLD_12);
        g.drawString("魔力 " + player.mana + "/" + player.maxMana, 30, 65);

        // 武器信息
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(16, 78, 230, 26, 8, 8);
        g.setColor(UI.GOLD_220B);
        String weaponTxt = "武器 " + (player.weapon != null ? player.weapon.name : "空手")
                + " 伤害" + (player.weapon != null ? player.weapon.damage : 1);
        g.drawString(weaponTxt, 26, 96);

        // 防御信息
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(16, 110, 230, 24, 8, 8);
        g.setColor(UI.BLUE_180);
        String setName = player.armorSetName();
        String defTxt = "防御 " + player.defense();
        if (!setName.isEmpty()) {
            defTxt += "  [" + setName + "]";
        }
        g.drawString(defTxt, 26, 127);

        // 金币
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(16, 140, 230, 24, 8, 8);
        g.setColor(new Color(255, 215, 80));
        g.setFont(UI.FONT_BOLD_13);
        g.drawString("金币 " + player.coins, 26, 157);

        // 药水状态
        int py = 170;
        if (player.potionCooldown > 0) {
            g.setColor(UI.BLACK_180);
            g.fillRoundRect(16, py, 230, 22, 8, 8);
            g.setColor(new Color(200, 160, 160));
            g.drawString("药水冷却 " + (int) Math.ceil(player.potionCooldown) + "s", 26, py + 16);
            py += 26;
        }
        if (player.thornsTimer > 0) {
            g.setColor(UI.BLACK_180);
            g.fillRoundRect(16, py, 230, 22, 8, 8);
            g.setColor(new Color(140, 220, 120));
            g.drawString("荆棘效果 " + (int) Math.ceil(player.thornsTimer) + "s（反弹30%伤害）", 26, py + 16);
            py += 26;
        }
        if (player.ironskinTimer > 0) {
            g.setColor(UI.BLACK_180);
            g.fillRoundRect(16, py, 230, 22, 8, 8);
            g.setColor(new Color(180, 180, 200));
            g.drawString("铁皮 " + (int) Math.ceil(player.ironskinTimer) + "s（防御+8）", 26, py + 16);
            py += 26;
        }
        if (player.swiftnessTimer > 0) {
            g.setColor(UI.BLACK_180);
            g.fillRoundRect(16, py, 230, 22, 8, 8);
            g.setColor(new Color(230, 210, 100));
            g.drawString("敏捷 " + (int) Math.ceil(player.swiftnessTimer) + "s（速度+25%）", 26, py + 16);
            py += 26;
        }
        if (player.rageTimer > 0) {
            g.setColor(UI.BLACK_180);
            g.fillRoundRect(16, py, 230, 22, 8, 8);
            g.setColor(new Color(230, 100, 80));
            g.drawString("怒气 " + (int) Math.ceil(player.rageTimer) + "s（伤害+15%）", 26, py + 16);
            py += 26;
        }
        if (player.nightVisionTimer > 0) {
            g.setColor(UI.BLACK_180);
            g.fillRoundRect(16, py, 230, 22, 8, 8);
            g.setColor(new Color(120, 220, 120));
            g.drawString("夜视 " + (int) Math.ceil(player.nightVisionTimer) + "s", 26, py + 16);
        }

        // 坐标与时间
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(16, 84, 260, 26, 10, 10);
        g.setColor(new Color(230, 230, 230));
        String info = String.format("坐标(%d,%d) %s", (int) (player.x + Player.W / 2f) / TILE,
                (int) (player.y + Player.H / 2f) / TILE, isNight() ? "夜晚" : "白天");
        g.drawString(info, 26, 103);

        // 联机状态
        if (netRole != NetRole.SOLO) {
            String netTxt = netRole == NetRole.HOST ? "主机 " + netServer.clientCount() + " 人"
                    : (netConnected ? "已连接" : "连接中…");
            g.setColor(UI.BLACK_180);
            g.fillRoundRect(16, 116, 200, 26, 10, 10);
            g.setColor(UI.GREEN_160);
            g.drawString(netTxt, 26, 135);
        }

        // 热键栏
        int bw = 46;
        int total = HOTBAR_SLOTS * (bw + 4) + 4;
        int bx = (VIEW_W - total) / 2;
        int by = VIEW_H - 64;
        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            int x = bx + i * (bw + 4);
            g.setColor(i == player.selected ? new Color(255, 255, 255, 240) : UI.BLACK_160);
            g.fillRoundRect(x, by, bw, 50, 8, 8);
            g.setColor(i == player.selected ? new Color(60, 60, 60) : new Color(255, 255, 255, 40));
            g.drawRoundRect(x, by, bw, 50, 8, 8);
            ItemStack s = player.hotbar[i];
            if (s != null) {
                ItemIcon.draw(g, s.item, x + 8, by + 6, bw - 16);
                if (s.count > 1) {
                    g.setColor(UI.WHITE_230);
                    g.setFont(UI.FONT_BOLD_12);
                    String cnt = "×" + s.count;
                    int cw = g.getFontMetrics().stringWidth(cnt);
                    g.drawString(cnt, x + bw - 5 - cw, by + 44);
                }
            }
            g.setColor(new Color(255, 255, 255, 120));
            g.setFont(new Font("Dialog", Font.PLAIN, 10));
            g.drawString(String.valueOf(i + 1), x + 3, by + 12);
        }

        // 玩家列表（联机）
        if (netRole != NetRole.SOLO) {
            drawPlayerList(g);
        }

        // 状态消息
        if (statusTimer > 0 && !statusMsg.isEmpty()) {
            g.setColor(UI.BLACK_180);
            int sw = g.getFontMetrics().stringWidth(statusMsg);
            g.fillRoundRect((VIEW_W - sw) / 2 - 14, 40, sw + 28, 32, 8, 8);
            g.setColor(new Color(255, 230, 140));
            g.drawString(statusMsg, (VIEW_W - sw) / 2, 62);
        }

        // 钓鱼状态提示
        if (player.fishingState == 1) {
            g.setColor(new Color(180, 220, 255, 220));
            g.setFont(UI.FONT_BOLD_14);
            String fs = "钓鱼中... 等待鱼咬钩（左键收竿）";
            int fsw = g.getFontMetrics().stringWidth(fs);
            g.drawString(fs, (VIEW_W - fsw) / 2, VIEW_H - 80);
        } else if (player.fishingState == 2) {
            boolean blink = ((int) (frameTimer * 6) % 2 == 0);
            g.setColor(blink ? UI.RED_255_80 : new Color(255, 200, 80));
            g.setFont(UI.FONT_BOLD_18);
            String fs = "咬钩了！快按左键收竿！";
            int fsw = g.getFontMetrics().stringWidth(fs);
            g.drawString(fs, (VIEW_W - fsw) / 2, VIEW_H - 80);
        }

        // 准星
        g.setColor(UI.WHITE_200);
        g.drawLine((int) mouseX - 8, (int) mouseY, (int) mouseX + 8, (int) mouseY);
        g.drawLine((int) mouseX, (int) mouseY - 8, (int) mouseX, (int) mouseY + 8);
    }

    private void drawPlayerList(Graphics2D g) {
        int y = 150;
        int count = playerNames.size();
        g.setColor(UI.BLACK_180);
        g.fillRoundRect(16, y - 14, 250, 22 + count * 22, 10, 10);
        g.setColor(UI.WHITE);
        g.drawString("玩家 " + count + " 人", 26, y);
        for (Map.Entry<Integer, String> e : playerNames.entrySet()) {
            int slot = e.getKey();
            String name = e.getValue();
            int hp;
            if (slot == mySlot) {
                hp = player.hp;
            } else {
                RemotePlayer rp = remotePlayers.get(slot);
                hp = (rp != null) ? rp.hp : 100;
            }
            g.setColor(slot == mySlot ? UI.GOLD_220B : new Color(230, 230, 230));
            g.drawString(name + "  ♥" + hp, 26, y + 22);
            y += 22;
        }
    }

    // ================= 菜单渲染 =================
    private void drawMainMenu(Graphics2D g) {
        g.setColor(UI.DARK_26);
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setColor(new Color(60, 140, 90));
        g.fillRect(0, VIEW_H - 60, VIEW_W, 60);

        g.setColor(UI.GOLD_220);
        g.setFont(new Font("Dialog", Font.BOLD, 64));
        String title = "丐版泰拉瑞亚";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (VIEW_W - tw) / 2, 150);

        g.setColor(new Color(200, 220, 240));
        g.setFont(new Font("Dialog", Font.PLAIN, 20));
        String sub = "Terraria Lite · JDK 26 · 挖掘 / 建造 / 合成 / 战斗 / 联机";
        int sw = g.getFontMetrics().stringWidth(sub);
        g.drawString(sub, (VIEW_W - sw) / 2, 195);

        for (Button b : menuButtons) {
            drawButton(g, b);
        }

        g.setColor(new Color(150, 160, 180));
        g.setFont(UI.FONT_PLAIN_14);
        String tip = "F5 保存 / F9 读档 / F6 新世界（游戏内）";
        int t2 = g.getFontMetrics().stringWidth(tip);
        g.drawString(tip, (VIEW_W - t2) / 2, VIEW_H - 20);
    }

    private void drawMpMenu(Graphics2D g) {
        g.setColor(UI.DARK_26);
        g.fillRect(0, 0, VIEW_W, VIEW_H);

        g.setColor(UI.GREEN_160);
        g.setFont(UI.FONT_BOLD_40);
        String title = "多人游戏";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (VIEW_W - tw) / 2, 160);

        g.setColor(new Color(200, 210, 220));
        g.setFont(new Font("Dialog", Font.PLAIN, 16));
        String[] lines = {
                "创建主机：本机作为主机，其他玩家输入你的 IP 加入（端口 " + DEFAULT_PORT + "）",
                "加入游戏：输入主机 IP，同步世界并共同冒险",
                "主机权威裁决世界方块与敌人，最多 5 人"
        };
        for (int i = 0; i < lines.length; i++) {
            int lw = g.getFontMetrics().stringWidth(lines[i]);
            g.drawString(lines[i], (VIEW_W - lw) / 2, 210 + i * 26);
        }

        for (Button b : mpButtons) {
            drawButton(g, b);
        }

        // 局域网房间列表
        roomRects.clear();
        int ry = 565;
        g.setFont(UI.FONT_PLAIN_14);
        g.setColor(UI.GREEN_230);
        int iw = g.getFontMetrics().stringWidth(roomScanInfo);
        g.drawString(roomScanInfo, (VIEW_W - iw) / 2, ry);
        ry += 28;
        for (LanDiscovery.Room r : discoveredRooms) {
            int rw = 460, rh = 34;
            int rx = (VIEW_W - rw) / 2;
            roomRects.add(new Rectangle(rx, ry, rw, rh));
            g.setColor(new Color(40, 46, 64));
            g.fillRoundRect(rx, ry, rw, rh, 8, 8);
            g.setColor(UI.GREEN_160);
            g.drawRoundRect(rx, ry, rw, rh, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(r.name, rx + 12, ry + 22);
            g.setColor(new Color(180, 190, 205));
            g.drawString(r.ip + ":" + r.port, rx + 300, ry + 22);
            ry += rh + 6;
        }
    }

    private void drawPauseMenu(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setColor(UI.WHITE);
        g.setFont(UI.FONT_BOLD_40);
        String title = "已暂停";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (VIEW_W - tw) / 2, 210);
        for (Button b : pauseButtons) {
            drawButton(g, b);
        }
    }

    private void drawHelpScreen(Graphics2D g) {
        g.setColor(new Color(20, 22, 34));
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setColor(UI.GOLD_220);
        g.setFont(new Font("Dialog", Font.BOLD, 36));
        String title = "操作说明";
        int tw = g.getFontMetrics().stringWidth(title);
        g.drawString(title, (VIEW_W - tw) / 2, 70);

        g.setColor(UI.GREEN_230);
        g.setFont(UI.FONT_PLAIN_18);
        String[] keys = {
                "A / D（或 ← →）   左右移动",
                "空格 / W / ↑         跳跃",
                "鼠标左键              按住挖掘 / 点击敌人近战 / 拉弓时射箭",
                "鼠标右键              放置方块 / 使用物品 / 装备弓后拉弓",
                "E                      合成面板（需靠近工作台）",
                "B / Tab                完整背包（拖拽移动、拖出丢弃、整理）",
                "Enter（联机）         聊天输入，再按 Enter 发送",
                "1 - 0 / 滚轮          切换物品栏",
                "Esc                    暂停菜单",
                "F5 保存 · F9 读档 · F6 新世界",
        };
        int y = 120;
        for (String k : keys) {
            g.drawString(k, 220, y);
            y += 30;
        }
        g.setColor(UI.GREEN_160);
        g.setFont(UI.FONT_PLAIN_18);
        String goal = "游戏目标：挖掘资源 → 合成武器（剑 / 弓）→ 击败怪物 → 夜晚用可疑眼球挑战克苏鲁之眼 → 探索地下寻找生命水晶！";
        int gw = g.getFontMetrics().stringWidth(goal);
        g.drawString(goal, (VIEW_W - gw) / 2, 545);

        for (Button b : helpButtons) {
            drawButton(g, b);
        }
    }

    private void drawButton(Graphics2D g, Button b) {
        boolean hover = b.contains(mouseX, mouseY);
        g.setColor(hover ? new Color(90, 170, 120) : new Color(50, 90, 70));
        g.fillRoundRect((int) b.x, (int) b.y, (int) b.w, (int) b.h, 14, 14);
        g.setColor(hover ? UI.WHITE : new Color(220, 230, 220));
        g.setFont(UI.FONT_BOLD_20);
        int tw = g.getFontMetrics().stringWidth(b.label);
        g.drawString(b.label, b.x + (b.w - tw) / 2, b.y + b.h / 2 + 7);
    }

    private void drawCraftingPanel(Graphics2D g) {
        int pw = 280, ph = 420;
        int px = (VIEW_W - pw) / 2;
        int py = (VIEW_H - ph) / 2;
        int listTop = py + 60;
        int listH = 340;
        g.setColor(UI.BLACK_150);
        g.fillRect(0, 0, VIEW_W, VIEW_H);
        g.setColor(new Color(40, 44, 56));
        g.fillRoundRect(px, py, pw, ph, 16, 16);
        g.setColor(UI.GOLD_220);
        g.setFont(UI.FONT_BOLD_20);
        // 标题显示当前可用的制作站
        StringBuilder title = new StringBuilder("合成");
        if (nearWorkbench()) title.append(" [工作台]");
        if (nearFurnace()) title.append(" [熔炉]");
        if (nearAnvil()) title.append(" [铁砧]");
        int tw = g.getFontMetrics().stringWidth(title.toString());
        g.drawString(title.toString(), px + (pw - tw) / 2, py + 30);
        g.setColor(new Color(160, 170, 180));
        g.setFont(UI.FONT_PLAIN_12);
        g.drawString("Esc/E 关闭  滚轮滚动", px + 16, py + 26);

        // 裁剪配方列表区域，只渲染可见配方
        Shape clip = g.getClip();
        g.setClip(px + 8, listTop, pw - 24, listH);
        for (int i = 0; i < RECIPES.length; i++) {
            Recipe r = RECIPES[i];
            Rectangle rect = recipeRect(i);
            if (rect.y + rect.height < listTop || rect.y > listTop + listH) {
                continue;
            }
            boolean ok = canCraft(r);
            g.setColor(ok ? new Color(60, 110, 80) : new Color(70, 70, 80));
            if (rect.contains((int) mouseX, (int) mouseY)) {
                g.setColor(ok ? new Color(90, 160, 110) : new Color(90, 90, 100));
            }
            g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
            g.setColor(UI.WHITE);
            g.setFont(UI.FONT_BOLD_15);
            String stationTag = r.station == 1 ? " [熔炉]" : (r.station == 2 ? " [铁砧]" : "");
            String rn = r.result.name + (r.resultCount > 1 ? " ×" + r.resultCount : "") + stationTag;
            g.drawString(rn, rect.x + 10, rect.y + 20);
            g.setFont(UI.FONT_PLAIN_12);
            StringBuilder cost = new StringBuilder();
            for (int j = 0; j < r.items.length; j++) {
                if (j > 0) {
                    cost.append(" + ");
                }
                int have = player.countOf(r.items[j]);
                cost.append(r.items[j].name).append(" ").append(have).append("/").append(r.counts[j]);
            }
            g.setColor(ok ? new Color(210, 235, 210) : new Color(190, 90, 90));
            g.drawString(cost.toString(), rect.x + 10, rect.y + 38);
        }
        g.setClip(clip);

        // 滚动条
        int contentH = RECIPES.length * 56;
        if (contentH > listH) {
            int trackX = px + pw - 14;
            int trackH = listH;
            int thumbH = Math.max(30, (int) (trackH * (float) listH / contentH));
            int maxScroll = contentH - listH;
            int thumbY = listTop + (int) ((float) craftScroll / maxScroll * (trackH - thumbH));
            g.setColor(new Color(60, 60, 70));
            g.fillRoundRect(trackX, listTop, 8, trackH, 4, 4);
            g.setColor(new Color(140, 140, 160));
            g.fillRoundRect(trackX, thumbY, 8, thumbH, 4, 4);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
