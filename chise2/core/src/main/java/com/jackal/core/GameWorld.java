package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMapRenderer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.Input;

/**
 * GameWorld —— 游戏世界统一入口。
 * <p>
 * 整合地图渲染、吉普车更新、武器系统、碰撞检测与相机跟随，
 * 把原本散落在 JackalGame 里的逻辑收敛到一个内聚对象。
 * JackalGame 只需每帧调用 {@link #update(float)} 与 {@link #render()}。
 *
 * <h3>更新顺序</h3>
 * <pre>
 *   1) jeep.update(dt)               —— 读输入、积分物理（含武器开火/子弹推进）
 *   2) 碰撞响应                       —— 把吉普车位移按地图墙体验分轴修正
 *   3) 子弹撞墙回收                   —— 子弹与墙壁重叠时回收到对象池
 *   4) 相机跟随 + 地图边界 clamp      —— 防止相机看到地图外黑边
 * </pre>
 *
 * <h3>渲染顺序（后绘制的在上层）</h3>
 * <pre>
 *   地图(Ground+Collision+POW) → 子弹 → 吉普车 → 炮塔线
 * </pre>
 *
 * <h3>像素级渲染对齐</h3>
 * 相机位置取整后再 update，可使瓦片纹素与屏幕像素对齐，消除亚像素插值导致的模糊与缝隙。
 * 这是 2D 像素风游戏的关键技巧：相机中心坐标 Math.round 后再喂给 camera.position。
 *
 * @author Jackal Dev Team
 */
public class GameWorld {

    /** 地图资源（加载 tmx/tsx/PNG，提供尺寸）。关卡切换时重新赋值 */
    private TileMapAssets mapAssets;
    /** 碰撞器（基于 Collision 层）。关卡切换时重新赋值 */
    private MapCollider collider;
    /** 地图渲染器（正交投影）。关卡切换时重新赋值 */
    private TiledMapRenderer mapRenderer;
    /** 玩家吉普车。首关创建，后续关卡切换时复用（保留武器等级） */
    private Jeep jeep;
    /** 相机 */
    private final OrthographicCamera camera;
    /** SpriteBatch（由外部传入，HUD 等共用） */
    private final SpriteBatch batch;
    /** ShapeRenderer（由外部传入，吉普车与子弹矢量绘制用，关卡切换复用） */
    private final com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes;

    /** 战俘营列表（地图上预设 3 个，硬编码坐标） */
    private final Array<POWCamp> powCamps = new Array<>(false, 4);
    /** 分数与任务横幅系统 */
    private final ScoreSystem scoreSystem = new ScoreSystem();

    // ============== 敌人系统（第 5 步） ==============

    /** 敌人列表 */
    private final Array<Enemy> enemies = new Array<>(false, 16);
    /** 敌人子弹对象池（与玩家子弹池独立，互不干扰容量/回收） */
    private final BulletPool enemyBulletPool = new BulletPool(32, 256);
    /** 敌人活跃子弹列表 */
    private final Array<Bullet> enemyBullets = new Array<>(false, 64);
    /** 敌人子弹速度（像素/秒）。玩家子弹速度由各武器状态决定，敌人统一用此值 */
    public static final float ENEMY_BULLET_SPEED = 280f;
    /** 敌人子弹伤害 */
    private static final float ENEMY_BULLET_DAMAGE = 1f;
    /** 敌人子弹半径 */
    private static final float ENEMY_BULLET_RADIUS = 3f;
    /** 敌人子弹寿命（秒） */
    private static final float ENEMY_BULLET_LIFE = 2.5f;

    // ============== Boss 系统（第 8 步） ==============

    /** Boss 实例（登场触发后非 null） */
    private BossArmoredVehicle boss = null;
    /** Boss 子弹独立对象池（与常规敌人子弹池分离，避免容量混淆） */
    private final BulletPool bossBulletPool = new BulletPool(32, 256);
    /** Boss 活跃子弹列表 */
    private final Array<Bullet> bossBullets = new Array<>(false, 64);
    /** Boss 子弹伤害（扇形弹/导弹统一） */
    private static final float BOSS_BULLET_DAMAGE = 1f;
    /** Boss 子弹半径 */
    private static final float BOSS_BULLET_RADIUS = 4f;
    /** Boss 子弹寿命（秒） */
    private static final float BOSS_BULLET_LIFE = 3.0f;
    /** 震动剩余时间（秒）。>0 期间相机施加随机偏移 */
    private float shakeTime = 0f;
    /** 震动幅度（像素） */
    private static final float SHAKE_MAGNITUDE = 4f;
    /** 任务是否已完成（击败 Boss）。用于 HUD 显示结算与 R 重启 */
    private boolean missionComplete = false;
    /** Boss 是否已登场（避免重复触发） */
    private boolean bossSpawned = false;
    /** Boss 包围盒复用缓存 */
    private final Rectangle BOSS_BOX = new Rectangle();

    // ============== 关卡系统（第 9 步） ==============

    /** 关卡管理器：维护当前关卡索引与过渡动画 */
    private final LevelManager levelManager = new LevelManager();

    /** 吉普车包围盒复用缓存（碰撞检测用，避免每帧 new） */
    private final Rectangle jeepBox = new Rectangle();
    /** 相机可视矩形复用缓存（边界 clamp 用） */
    private final Rectangle viewBounds = new Rectangle();
    /** 子弹/敌人碰撞包围盒复用缓存 */
    private final Rectangle BULLET_BOX = new Rectangle();
    /** 敌人包围盒复用缓存 */
    private final Rectangle ENEMY_BOX = new Rectangle();

    /**
     * 构造游戏世界。
     *
     * @param camera 外部相机（HUD 与世界共用同一相机对象）
     * @param batch  外部 SpriteBatch
     * @param shapes 外部 ShapeRenderer（吉普车与子弹矢量绘制用）
     */
    public GameWorld(OrthographicCamera camera, SpriteBatch batch,
                     com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes) {
        this.camera = camera;
        this.batch = batch;
        this.shapes = shapes;

        // —— 加载第 1 关（地图 + 吉普车 + 战俘营 + 敌人）——
        loadLevel();

        // —— 子弹边界回收范围改为地图实际尺寸 ——
        Bullet.setBounds(0f, 0f,
                mapAssets.getWorldPixelWidth(), mapAssets.getWorldPixelHeight());
    }

    /**
     * 加载当前关卡：地图 + 碰撞器 + 渲染器 + 吉普车 + 战俘营 + 敌人。
     * <p>
     * 从 {@link #levelManager} 获取当前关卡文件路径。第 1 次加载与关卡切换时都调用此方法。
     * 关卡切换时由 {@link #switchToNextLevel()} 先清空旧状态再调用本方法。
     */
    private void loadLevel() {
        // —— 加载地图（按 LevelManager 当前索引）——
        this.mapAssets = new TileMapAssets(levelManager.getCurrentFilePath());
        // —— 碰撞器 ——
        this.collider = new MapCollider(mapAssets);

        // —— 地图渲染器：用传入的 batch 渲染地图 ——
        this.mapRenderer = new OrthogonalTiledMapRenderer(mapAssets.getMap(), 1f, batch);

        // —— 吉普车：放在地图中心 ——
        // 首次构造需创建实例；关卡切换时复用原实例只重置位置
        if (jeep == null) {
            this.jeep = new Jeep(mapAssets.getWorldPixelWidth() * 0.5f,
                    mapAssets.getWorldPixelHeight() * 0.5f, shapes);
        } else {
            jeep.getPosition().set(mapAssets.getWorldPixelWidth() * 0.5f,
                    mapAssets.getWorldPixelHeight() * 0.5f);
            jeep.getVelocity().setZero();
        }

        // —— 子弹边界回收范围改为地图实际尺寸 ——
        Bullet.setBounds(0f, 0f,
                mapAssets.getWorldPixelWidth(), mapAssets.getWorldPixelHeight());

        // —— 创建战俘营与敌人 ——
        createPOWCamps();
        spawnEnemies();

        com.badlogic.gdx.Gdx.app.log("World", "加载第 " + levelManager.getCurrentDisplayNumber()
                + " 关: " + levelManager.getCurrentFilePath());
    }

    /**
     * 随机生成敌人：步兵 + 炮台在地图非墙区域随机刷出，坦克从 TankPath 读取。
     * <p>
     * 第 9 步后改为大地图 + 随机生成：
     * <ul>
     *   <li>步兵：随机数量（按地图面积），避开墙与玩家起点一定半径</li>
     *   <li>炮台：随机数量，固定位置朝随机扫描轴</li>
     *   <li>坦克：仍从 tmx Object Layer "TankPath" 读取（数据驱动），缺失则降级</li>
     * </ul>
     * 随机生成采用拒绝采样：随机一个候选点，若在墙内或距玩家太近则重试，最多尝试若干次。
     */
    private void spawnEnemies() {
        float mapW = mapAssets.getWorldPixelWidth();
        float mapH = mapAssets.getWorldPixelHeight();
        Vector2 jeepPos = jeep.getPosition();
        // 玩家起点保护半径（此范围内不刷敌人，避免一出生就被围殴）
        final float SAFE_RADIUS = 160f;

        // —— 步兵：按地图面积随机刷出 8~12 个 ——
        int infantryCount = MathUtils.random(8, 12);
        for (int i = 0; i < infantryCount; i++) {
            Vector2 pos = randomFreePosition(mapW, mapH, jeepPos, SAFE_RADIUS);
            if (pos == null) continue;
            Infantry inf = new Infantry(pos.x, pos.y);
            inf.setCollider(collider);
            enemies.add(inf);
        }

        // —— 炮台：随机刷出 3~5 个，朝随机扫描轴 ——
        int turretCount = MathUtils.random(3, 5);
        for (int i = 0; i < turretCount; i++) {
            Vector2 pos = randomFreePosition(mapW, mapH, jeepPos, SAFE_RADIUS);
            if (pos == null) continue;
            float baseFacing = MathUtils.random(MathUtils.PI2);
            Turret turret = new Turret(pos.x, pos.y, baseFacing);
            turret.setFireCallback(this::spawnEnemyBullet);
            enemies.add(turret);
        }

        // —— 坦克：巡逻路径从 tmx Object Layer "TankPath" 读取 ——
        com.badlogic.gdx.math.Vector2[] tankPath = mapAssets.getWaypoints("TankPath");
        if (tankPath.length < 2) {
            com.badlogic.gdx.Gdx.app.log("Enemy", "TankPath 缺失，使用降级路径");
            tankPath = new com.badlogic.gdx.math.Vector2[]{
                    new com.badlogic.gdx.math.Vector2(mapW * 0.25f, mapH * 0.7f),
                    new com.badlogic.gdx.math.Vector2(mapW * 0.75f, mapH * 0.7f)
            };
        }
        Tank tank = new Tank(tankPath[0].x, tankPath[0].y, tankPath);
        tank.setFireCallback(this::spawnEnemyBullet);
        enemies.add(tank);

        com.badlogic.gdx.Gdx.app.log("Enemy", "随机生成敌人："
                + infantryCount + " 步兵 + " + turretCount + " 炮台 + 1 坦克(路径"
                + tankPath.length + "点)");
    }

    /**
     * 拒绝采样：在地图内随机选一个非墙、且距玩家起点超过 safeRadius 的点。
     * <p>
     * 最多尝试 30 次；每次随机 (x,y)，用 {@link MapCollider#isSolidAtPixel} 判定是否在墙内，
     * 并用距离判定是否在玩家安全圈内。找到合格点返回，否则返回 null（调用方跳过）。
     *
     * @param mapW       地图宽
     * @param mapH       地图高
     * @param jeepPos    玩家位置（避开其周围）
     * @param safeRadius 安全半径（像素）
     * @return 合格的随机点，或 null（尝试失败）
     */
    private Vector2 randomFreePosition(float mapW, float mapH, Vector2 jeepPos, float safeRadius) {
        final int MAX_TRIES = 30;
        for (int t = 0; t < MAX_TRIES; t++) {
            // 在地图内随机（留 1 瓦片边距，避免贴外墙）
            float px = MathUtils.random(32f, mapW - 32f);
            float py = MathUtils.random(32f, mapH - 32f);
            // 排除墙内
            if (collider.isSolidAtPixel(px, py)) continue;
            // 排除玩家安全圈
            if (Vector2.dst(px, py, jeepPos.x, jeepPos.y) < safeRadius) continue;
            return new Vector2(px, py);
        }
        return null;
    }

    /**
     * 敌人开火回调实现：从敌人子弹池取一颗子弹，初始化为直线弹道，加入敌人活跃列表。
     * <p>
     * 作为 {@link EnemyFireCallback} 传给 Tank/Turret。shooter 参数当前未用，
     * 预留后续避免命中自己。
     */
    private void spawnEnemyBullet(float originX, float originY, float angleRad, Enemy shooter) {
        Bullet b = enemyBulletPool.obtain();
        b.initLine(Bullet.Type.MACHINE_GUN, originX, originY, angleRad,
                ENEMY_BULLET_SPEED, ENEMY_BULLET_DAMAGE, ENEMY_BULLET_LIFE, ENEMY_BULLET_RADIUS);
        enemyBullets.add(b);
    }

    /**
     * 创建地图上的战俘营并绑定救援回调。
     * <p>
     * 坐标硬编码（与 level1.tmx 的 POW 层对应即可，但本实现独立于地图层，
     * 便于不依赖 Object Layer 解析）。回调在本营战俘全部救出时触发武器升级 + 任务横幅。
     */
    private void createPOWCamps() {
        // 坐标用地图相对比例，适配不同地图尺寸（1280x960 等）
        float mapW = mapAssets.getWorldPixelWidth();
        float mapH = mapAssets.getWorldPixelHeight();
        float[][] spots = {
                {mapW * 0.10f, mapH * 0.15f},
                {mapW * 0.90f, mapH * 0.15f},
                {mapW * 0.10f, mapH * 0.85f},
                {mapW * 0.90f, mapH * 0.85f}
        };
        for (float[] spot : spots) {
            POWCamp camp = new POWCamp(spot[0], spot[1]);
            // 救援回调：武器升级一级 + 显示任务横幅
            camp.setOnAllRescued(() -> {
                jeep.getWeaponSystem().upgrade();
                scoreSystem.showMissionAccomplished();
            });
            powCamps.add(camp);
        }
    }

    /**
     * 每帧更新世界状态。
     *
     * @param dt 帧时间（秒）
     * @param mouseWorldX 鼠标世界 X（已 unproject）
     * @param mouseWorldY 鼠标世界 Y
     */
    public void update(float dt, float mouseWorldX, float mouseWorldY) {
        // —— 关卡过渡中：锁定玩家输入，跳过物理与敌人更新，只推进过渡计时 ——
        // 过渡的 update 在下方 4.66 统一调用，这里先判断是否跳过主体逻辑
        boolean lockInput = levelManager.isInputLocked();

        if (!lockInput) {
            // —— 1. 瞄准（在 update 前设置 turretAngle，供武器开火使用） ——
            jeep.aimAt(mouseWorldX, mouseWorldY);

            // 记录更新前的位置，用于计算本帧位移
            float prevX = jeep.getPosition().x;
            float prevY = jeep.getPosition().y;

            // —— 2. 吉普车物理 + 武器更新 ——
            jeep.update(dt);

            // —— 3. 碰撞响应：把吉普车位移按墙体分轴修正 ——
            float dx = jeep.getPosition().x - prevX;
            float dy = jeep.getPosition().y - prevY;
            // 构造碰撞前的包围盒
            jeepBox.set(prevX - Jeep.HALF_WIDTH, prevY - Jeep.HALF_HEIGHT,
                    Jeep.HALF_WIDTH * 2f, Jeep.HALF_HEIGHT * 2f);
            // 分轴解决，得到允许的实际位移
            Vector2 allowed = collider.resolve(jeepBox, dx, dy);
            // 应用修正后的位置
            jeep.getPosition().x = prevX + allowed.x;
            jeep.getPosition().y = prevY + allowed.y;

            // —— 4. 子弹撞墙回收：遍历活跃子弹，撞墙即回收 ——
            recycleBulletsHittingWalls();

            // —— 4.5 战俘营交互：E 键救援 + 子弹摧毁 ——
            updatePOWCamps();

            // —— 4.6 敌人 AI + 玩家子弹击杀敌人 + 敌人接触/子弹伤害玩家 ——
            updateEnemies(dt);
        }

        // —— 4.65 Boss 系统：登场检测 + 更新 + 碰撞 ——
        // 过渡中仍需 updateBoss 以推进 Boss 死亡动画（若有），但 Boss 在新关为 null
        if (!lockInput || boss != null) {
            updateBoss(dt);
        }

        // —— 4.66 关卡过渡：推进计时，FADING_OUT 结束时切换关卡 ——
        if (levelManager.update(dt)) {
            switchToNextLevel();
        }

        // —— 4.7 分数/横幅计时推进 ——
        scoreSystem.update(dt);

        // —— 5. 相机跟随 + 地图边界 clamp ——
        // 先按吉普车位置设定相机，再 clamp 到地图范围，防止看到地图外黑边。
        float camX = jeep.getPosition().x;
        float camY = jeep.getPosition().y;
        viewBounds.set(camX - camera.viewportWidth * 0.5f,
                camY - camera.viewportHeight * 0.5f,
                camera.viewportWidth, camera.viewportHeight);
        mapAssets.clampToWorld(viewBounds);
        // clamp 后重新计算相机中心
        camX = viewBounds.x + camera.viewportWidth * 0.5f;
        camY = viewBounds.y + camera.viewportHeight * 0.5f;
        // —— 震动偏移（Boss 阶段3触发）——
        if (shakeTime > 0f) {
            shakeTime -= dt;
            camX += (MathUtils.random(-1f, 1f)) * SHAKE_MAGNITUDE;
            camY += (MathUtils.random(-1f, 1f)) * SHAKE_MAGNITUDE;
        }
        // —— 像素对齐：相机中心取整，消除亚像素模糊 ——
        camera.position.set(Math.round(camX), Math.round(camY), 0f);
        camera.update();

        // 同步地图渲染器的视图
        mapRenderer.setView(camera);
    }

    /** 遍历活跃子弹，撞墙即回收（倒序移除） */
    private void recycleBulletsHittingWalls() {
        com.badlogic.gdx.utils.Array<Bullet> bullets =
                jeep.getWeaponSystem().getActiveBulletsInternal();
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            // 用子弹中心做一个 2r 的方形包围盒判定
            if (collider.collides(BULLET_BOX.set(b.position.x - b.radius,
                    b.position.y - b.radius, b.radius * 2f, b.radius * 2f))) {
                jeep.getWeaponSystem().getPool().free(b);
                bullets.removeIndex(i);
            }
        }
    }

    /**
     * 战俘营交互更新：E 键救援 + 子弹摧毁。
     * <p>
     * 两路并行：
     * <ul>
     *   <li>救援：吉普车矩形靠近营(距离&lt;INTERACT_RADIUS)且本帧刚按 E → tryRescue +1000/人</li>
     *   <li>摧毁：手雷/火箭弹矩形与营命中圆相交 → tryDestroy 得分减半</li>
     * </ul>
     * 摧毁只接受 GRENADE/ROCKET（高爆），机枪子弹(MACHINE_GUN)忽略，避免随手打爆营。
     */
    private void updatePOWCamps() {
        // —— 救援：检测 E 键 ——
        boolean ePressed = com.badlogic.gdx.Gdx.input.isKeyJustPressed(Input.Keys.E);
        if (ePressed) {
            // 吉普车当前包围盒（碰撞修正后的位置）
            jeepBox.set(jeep.getPosition().x - Jeep.HALF_WIDTH,
                    jeep.getPosition().y - Jeep.HALF_HEIGHT,
                    Jeep.HALF_WIDTH * 2f, Jeep.HALF_HEIGHT * 2f);
            for (int i = 0; i < powCamps.size; i++) {
                POWCamp camp = powCamps.get(i);
                int gained = camp.tryRescue(jeepBox, true);
                if (gained > 0) {
                    scoreSystem.addScore(gained);
                    scoreSystem.recordRescue();
                    playSfx("rescue");
                    com.badlogic.gdx.Gdx.app.log("POW", "救援 1 人 +"
                            + gained + " 分，剩余 " + camp.getRemainingPows());
                }
            }
        }

        // —— 摧毁：遍历活跃子弹，高爆弹药判定 ——
        Array<Bullet> bullets = jeep.getWeaponSystem().getActiveBulletsInternal();
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            // 仅手雷/火箭弹能摧毁战俘营；机枪子弹跳过
            if (b.type != Bullet.Type.GRENADE && b.type != Bullet.Type.ROCKET) {
                continue;
            }
            // 子弹包围盒
            BULLET_BOX.set(b.position.x - b.radius, b.position.y - b.radius,
                    b.radius * 2f, b.radius * 2f);
            for (int j = 0; j < powCamps.size; j++) {
                POWCamp camp = powCamps.get(j);
                int gained = camp.tryDestroy(BULLET_BOX, b.type);
                if (gained > 0) {
                    scoreSystem.addScore(gained);
                    scoreSystem.recordDestroy();
                    com.badlogic.gdx.Gdx.app.log("POW", "战俘营被摧毁 +"
                            + gained + " 分（减半惩罚）");
                    // 命中后该子弹也消耗（穿透弹虽穿透墙，但摧毁营后回收，避免连锁多营）
                    jeep.getWeaponSystem().getPool().free(b);
                    bullets.removeIndex(i);
                    break; // 该子弹已用，跳出营地循环
                }
            }
        }
    }

    /** 渲染所有战俘营（矢量绘制）。调用前 shapes 须已 begin(Filled) 并设置投影矩阵 */
    private void renderPOWCamps(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes) {
        for (int i = 0; i < powCamps.size; i++) {
            powCamps.get(i).render(shapes);
        }
    }

    // ============== Boss 系统更新与碰撞 ==============

    /**
     * Boss 系统每帧更新：
     * <ol>
     *   <li>登场触发：敌人全清 + 战俘营全终态 → 生成 Boss</li>
     *   <li>Boss 更新（移动/攻击/激光）</li>
     *   <li>玩家子弹击中 Boss</li>
     *   <li>Boss 子弹推进 + 撞墙/撞玩家</li>
     *   <li>激光命中玩家</li>
     *   <li>Boss 接触玩家</li>
     *   <li>击败判定 → missionComplete</li>
     * </ol>
     */
    private void updateBoss(float dt) {
        // —— 1. 登场触发 ——
        if (!bossSpawned && allEnemiesCleared() && allPowsResolved()) {
            spawnBoss();
        }
        if (boss == null) return;

        // —— 2. Boss 更新 ——
        boss.update(dt, jeep.getPosition());

        // —— 3. 玩家子弹击中 Boss（仅战斗中/登场后均可被攻击）——
        handlePlayerBulletsHitBoss();

        // —— 4. Boss 子弹推进 + 撞墙 + 撞玩家 ——
        handleBossBullets(dt);

        // —— 5. 激光命中玩家 ——
        if (boss.laser.active) {
            boss.laser.update(dt);
            boss.laser.tryHit(jeep);
        }

        // —— 6. Boss 接触玩家 ——
        if (boss.isActive()) {
            boss.getBounds(BOSS_BOX);
            jeepBox.set(jeep.getPosition().x - Jeep.HALF_WIDTH,
                    jeep.getPosition().y - Jeep.HALF_HEIGHT,
                    Jeep.HALF_WIDTH * 2f, Jeep.HALF_HEIGHT * 2f);
            if (BOSS_BOX.overlaps(jeepBox)) {
                jeep.takeDamage(boss.getContactDamage());
            }
        }

        // —— 7. 击败判定 ——
        if (boss.isDefeated() && !missionComplete) {
            missionComplete = true;
            scoreSystem.addScore(10000);
            scoreSystem.showMissionAccomplished();
            // 启动关卡推进：2 秒后自动切换到下一关（或最终关 GAME CLEAR）
            levelManager.triggerAdvance();
            com.badlogic.gdx.Gdx.app.log("Boss", "MISSION COMPLETE！总分 " + scoreSystem.getScore());
        }
    }

    /** @return 所有常规敌人是否已清空 */
    private boolean allEnemiesCleared() {
        return enemies.size == 0;
    }

    /** @return 所有战俘营是否都已救援或摧毁（终态） */
    private boolean allPowsResolved() {
        for (int i = 0; i < powCamps.size; i++) {
            if (powCamps.get(i).isInteractable()) return false;
        }
        return true;
    }

    /**
     * 生成 Boss：从地图右侧外驶入，战斗区域为地图宽度范围。
     * 绑定开火/召唤/震动回调。
     */
    private void spawnBoss() {
        bossSpawned = true;
        float mapW = mapAssets.getWorldPixelWidth();
        float mapH = mapAssets.getWorldPixelHeight();
        // 从右侧外驶入，目标位置在地图右 1/3 处，战斗高度在中部偏上
        boss = new BossArmoredVehicle(
                mapW + 100f,            // 起点在地图右侧外
                mapH * 0.65f,           // 战斗高度
                mapW * 0.7f,            // 驶入目标 X
                mapW * 0.35f,           // 战斗区域左边界
                mapW - 30f);            // 战斗区域右边界
        boss.setFireCallback(this::spawnBossBullet);
        boss.setSummonCallback(this::summonBossAdds);
        boss.setShakeCallback(() -> shakeTime = 0.3f);
        com.badlogic.gdx.Gdx.app.log("Boss", "Boss 登场！从右侧驶入");
    }

    /**
     * Boss 开火回调：按子弹类型从 Boss 子弹池取 Bullet 初始化。
     * <ul>
     *   <li>BOSS_BULLET：扇形直线弹，速度用 P1_BULLET_SPEED</li>
     *   <li>BOSS_MISSILE：追踪导弹，速度用 P2_MISSILE_SPEED，每帧由 GameWorld 更新 target</li>
     * </ul>
     */
    private void spawnBossBullet(float originX, float originY, float angleRad, Bullet.Type type) {
        Bullet b = bossBulletPool.obtain();
        if (type == Bullet.Type.BOSS_MISSILE) {
            b.initLine(Bullet.Type.BOSS_MISSILE, originX, originY, angleRad,
                    BossArmoredVehicle.P2_MISSILE_SPEED, BOSS_BULLET_DAMAGE * 1.5f,
                    BOSS_BULLET_LIFE, BOSS_BULLET_RADIUS);
            b.target.set(jeep.getPosition());
        } else {
            b.initLine(Bullet.Type.BOSS_BULLET, originX, originY, angleRad,
                    BossArmoredVehicle.P1_BULLET_SPEED, BOSS_BULLET_DAMAGE,
                    BOSS_BULLET_LIFE, BOSS_BULLET_RADIUS);
        }
        bossBullets.add(b);
    }

    /** 阶段2召唤 2 步兵在 Boss 附近 */
    private void summonBossAdds() {
        Infantry inf0 = new Infantry(boss.position.x - 40f, boss.position.y - 20f);
        inf0.setCollider(collider);
        enemies.add(inf0);
        Infantry inf1 = new Infantry(boss.position.x + 40f, boss.position.y - 20f);
        inf1.setCollider(collider);
        enemies.add(inf1);
    }

    /**
     * 玩家子弹击中 Boss 判定。Boss 包围盒大，用 AABB。
     * 火箭弹穿透：命中 Boss 后仍继续飞（可同时打 Boss 与其他敌人）。
     */
    private void handlePlayerBulletsHitBoss() {
        if (!boss.isActive() && !boss.isDefeated()) {
            // 登场中也可被攻击（ENTERING 阶段）
            if (boss.getPhase() != BossArmoredVehicle.BossPhase.ENTERING) return;
        }
        if (boss.isDefeated()) return;
        boss.getBounds(BOSS_BOX);
        Array<Bullet> pBullets = jeep.getWeaponSystem().getActiveBulletsInternal();
        for (int i = pBullets.size - 1; i >= 0; i--) {
            Bullet b = pBullets.get(i);
            BULLET_BOX.set(b.position.x - b.radius, b.position.y - b.radius,
                    b.radius * 2f, b.radius * 2f);
            if (BOSS_BOX.overlaps(BULLET_BOX)) {
                boolean dmg = boss.takeDamage((int) b.damage);
                if (dmg) {
                    // 火箭弹穿透不回收，其余命中即回收
                    if (b.type != Bullet.Type.ROCKET) {
                        jeep.getWeaponSystem().getPool().free(b);
                        pBullets.removeIndex(i);
                    }
                }
            }
        }
    }

    /**
     * Boss 子弹推进 + 撞墙回收 + 撞玩家扣血。
     * 导弹每帧更新 target 为玩家当前位置（追踪）。
     */
    private void handleBossBullets(float dt) {
        for (int i = bossBullets.size - 1; i >= 0; i--) {
            Bullet b = bossBullets.get(i);
            // 导弹追踪：每帧刷新目标
            if (b.type == Bullet.Type.BOSS_MISSILE) {
                b.target.set(jeep.getPosition());
            }
            if (b.update(dt, bossBulletPool)) {
                bossBullets.removeIndex(i);
                continue;
            }
            // 撞墙回收
            if (collider.collides(BULLET_BOX.set(b.position.x - b.radius,
                    b.position.y - b.radius, b.radius * 2f, b.radius * 2f))) {
                bossBulletPool.free(b);
                bossBullets.removeIndex(i);
                continue;
            }
            // 撞玩家扣血
            jeepBox.set(jeep.getPosition().x - Jeep.HALF_WIDTH,
                    jeep.getPosition().y - Jeep.HALF_HEIGHT,
                    Jeep.HALF_WIDTH * 2f, Jeep.HALF_HEIGHT * 2f);
            BULLET_BOX.set(b.position.x - b.radius, b.position.y - b.radius,
                    b.radius * 2f, b.radius * 2f);
            if (jeepBox.overlaps(BULLET_BOX)) {
                boolean hurt = jeep.takeDamage(b.damage);
                if (hurt) playSfx("hit");
                bossBulletPool.free(b);
                bossBullets.removeIndex(i);
            }
        }
    }

    /** 渲染 Boss 与其子弹/激光。调用前 shapes 须已 begin(Filled) 并设置投影矩阵 */
    private void renderBoss(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes) {
        if (boss == null) return;
        // Boss 子弹
        for (int i = 0; i < bossBullets.size; i++) {
            bossBullets.get(i).render(shapes, true);
        }
        // 激光（在子弹之上、Boss 之下）
        boss.laser.render(shapes);
        // Boss 本体
        boss.render(shapes);
    }

    // ============== 敌人系统更新与碰撞 ==============

    /**
     * 敌人系统每帧更新：
     * <ol>
     *   <li>给 Turret 注入玩家速度（用于预判开火）</li>
     *   <li>推进所有敌人 AI（think），清理死亡敌人并加分</li>
     *   <li>玩家子弹击中敌人：扣敌血，击杀则回收子弹；机枪克步兵，手雷/火箭克坦克/炮台</li>
     *   <li>敌人子弹推进 + 撞墙回收 + 撞玩家扣血</li>
     *   <li>敌人接触玩家扣血（带玩家无敌帧保护）</li>
     * </ol>
     */
    private void updateEnemies(float dt) {
        Vector2 jeepPos = jeep.getPosition();

        // —— 1. 给炮台注入玩家速度 ——
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (e instanceof Turret) {
                ((Turret) e).setTargetVelocity(jeep.getVelocity());
            }
        }

        // —— 2. 推进敌人 AI，清理死亡敌人 ——
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            e.update(dt, jeepPos);
            if (e.isDead()) {
                // 击杀奖励（与血量成正比：步兵100、炮台200、坦克300）
                int reward = e.getMaxHp() * 100;
                scoreSystem.addScore(reward);
                playSfx("enemy_die");
                com.badlogic.gdx.Gdx.app.log("Enemy", "击杀敌人 +" + reward
                        + " 分（" + e.getTypeName() + "）");
                enemies.removeIndex(i);
            }
        }

        // —— 3. 玩家子弹击中敌人 ——
        handlePlayerBulletsHitEnemies();

        // —— 4. 敌人子弹推进 + 撞墙回收 + 撞玩家扣血 ——
        handleEnemyBullets(dt);

        // —— 5. 敌人接触玩家扣血 ——
        handleEnemyContactDamage();
    }

    /**
     * 玩家子弹击中敌人判定。
     * <p>
     * 伤害规则（呼应需求）：
     * <ul>
     *   <li>步兵 hp=1：任意子弹（含机枪）一击必杀</li>
     *   <li>坦克 hp=3：机枪每发扣 1（需多发），手雷/火箭弹高伤可速杀</li>
     *   <li>炮台 hp=2：同坦克</li>
     * </ul>
     * 伤害值取子弹 damage 字段（机枪8/手雷45/火箭60），对步兵足够一击死。
     * 火箭弹穿透特性：命中敌人后仍继续飞（不回收），可连杀。机枪/手雷命中即回收。
     */
    private void handlePlayerBulletsHitEnemies() {
        Array<Bullet> pBullets = jeep.getWeaponSystem().getActiveBulletsInternal();
        for (int i = pBullets.size - 1; i >= 0; i--) {
            Bullet b = pBullets.get(i);
            BULLET_BOX.set(b.position.x - b.radius, b.position.y - b.radius,
                    b.radius * 2f, b.radius * 2f);
            for (int j = 0; j < enemies.size; j++) {
                Enemy e = enemies.get(j);
                e.getBounds(ENEMY_BOX);
                if (!ENEMY_BOX.overlaps(BULLET_BOX)) continue;
                // 命中
                boolean dmg = e.takeDamage((int) b.damage);
                if (dmg) {
                    // 火箭弹穿透：命中后不回收子弹，继续飞可连杀
                    if (b.type != Bullet.Type.ROCKET) {
                        jeep.getWeaponSystem().getPool().free(b);
                        pBullets.removeIndex(i);
                        break; // 该子弹已消耗，跳出敌人循环
                    }
                    // 火箭弹：不 break，继续判定下一个敌人（穿透）
                }
            }
        }
    }

    /**
     * 敌人子弹：推进 + 撞墙回收 + 撞玩家扣血。
     */
    private void handleEnemyBullets(float dt) {
        // 推进与撞墙回收
        for (int i = enemyBullets.size - 1; i >= 0; i--) {
            Bullet b = enemyBullets.get(i);
            // update 处理寿命/边界回收（边界已是地图尺寸）
            if (b.update(dt, enemyBulletPool)) {
                enemyBullets.removeIndex(i);
                continue;
            }
            // 撞墙回收
            if (collider.collides(BULLET_BOX.set(b.position.x - b.radius,
                    b.position.y - b.radius, b.radius * 2f, b.radius * 2f))) {
                enemyBulletPool.free(b);
                enemyBullets.removeIndex(i);
                continue;
            }
            // 撞玩家扣血
            jeepBox.set(jeep.getPosition().x - Jeep.HALF_WIDTH,
                    jeep.getPosition().y - Jeep.HALF_HEIGHT,
                    Jeep.HALF_WIDTH * 2f, Jeep.HALF_HEIGHT * 2f);
            BULLET_BOX.set(b.position.x - b.radius, b.position.y - b.radius,
                    b.radius * 2f, b.radius * 2f);
            if (jeepBox.overlaps(BULLET_BOX)) {
                boolean hurt = jeep.takeDamage(b.damage);
                if (hurt) playSfx("hit");
                enemyBulletPool.free(b);
                enemyBullets.removeIndex(i);
            }
        }
    }

    /**
     * 敌人接触玩家扣血。
     * <p>
     * 玩家有 1.2 秒受击无敌帧（Jeep.takeDamage 内处理），避免持续接触每帧扣血。
     */
    private void handleEnemyContactDamage() {
        jeepBox.set(jeep.getPosition().x - Jeep.HALF_WIDTH,
                jeep.getPosition().y - Jeep.HALF_HEIGHT,
                Jeep.HALF_WIDTH * 2f, Jeep.HALF_HEIGHT * 2f);
        for (int i = 0; i < enemies.size; i++) {
            Enemy e = enemies.get(i);
            if (e.isDead()) continue;
            e.getBounds(ENEMY_BOX);
            if (ENEMY_BOX.overlaps(jeepBox)) {
                boolean hurt = jeep.takeDamage(e.getContactDamage());
                if (hurt) playSfx("hit");
                // 接触伤害也受无敌帧保护，不主动 break：无敌帧内不会重复扣
            }
        }
    }

    /** 渲染所有敌人（矢量绘制）。调用前 shapes 须已 begin(Filled) 并设置投影矩阵 */
    private void renderEnemies(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes) {
        for (int i = 0; i < enemies.size; i++) {
            enemies.get(i).render(shapes);
        }
    }

    /** 渲染敌人子弹（矢量绘制） */
    private void renderEnemyBullets(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes) {
        for (int i = 0; i < enemyBullets.size; i++) {
            // 敌人子弹用紫红色，与玩家黄色区分
            com.badlogic.gdx.graphics.Color old = shapes.getColor();
            shapes.setColor(Color.MAGENTA);
            enemyBullets.get(i).render(shapes, true);
            shapes.setColor(old);
        }
    }

    /**
     * 渲染世界：地图 → 子弹 → 吉普车 → 炮塔线。
     * <p>
     * 渲染前外部须已清屏。
     *
     * @param shapes 外部 ShapeRenderer
     */
    public void render(com.badlogic.gdx.graphics.glutils.ShapeRenderer shapes) {
        // —— 1. 地图（用 batch 渲染，内部已 begin/end） ——
        mapRenderer.render();

        // —— 2. 子弹、吉普车、敌人、战俘营（矢量 Filled 绘制） ——
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        jeep.render(batch);
        jeep.renderWeapons();
        renderEnemyBullets(shapes);
        renderEnemies(shapes);
        renderPOWCamps(shapes);
        renderBoss(shapes);
        shapes.end();
        // —— 3. 炮塔指示线（Line 模式，叠在最上层） ——
        shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line);
        shapes.setProjectionMatrix(camera.combined);
        jeep.render(batch);
        shapes.end();
    }

    /** @return 吉普车引用（HUD 读取武器信息用） */
    public Jeep getJeep() {
        return jeep;
    }

    /** @return 地图资源 */
    public TileMapAssets getMapAssets() {
        return mapAssets;
    }

    /** @return 分数系统（HUD 读取分数/横幅用） */
    public ScoreSystem getScoreSystem() {
        return scoreSystem;
    }

    /** @return 战俘营列表（HUD 读取剩余战俘总数用） */
    public Array<POWCamp> getPOWCamps() {
        return powCamps;
    }

    /** @return 敌人列表（HUD 读取剩余敌人数用） */
    public Array<Enemy> getEnemies() {
        return enemies;
    }

    /** @return Boss 实例（未登场为 null；HUD 读取血条用） */
    public BossArmoredVehicle getBoss() {
        return boss;
    }

    /** @return 任务是否已完成（击败 Boss） */
    public boolean isMissionComplete() {
        return missionComplete;
    }

    /** @return 关卡管理器（HUD 读取关卡号/过渡/通关状态用） */
    public LevelManager getLevelManager() {
        return levelManager;
    }

    /** @return 是否已通关全部关卡（最终关 Boss 击败） */
    public boolean isGameClear() {
        return levelManager.isGameClear();
    }

    /**
     * 从第 1 关重新开始整个游戏（GAME CLEAR 后按 R 触发）。
     * <p>
     * 重置 LevelManager 索引与分数，重新加载 level1。
     */
    public void restartGame() {
        levelManager.restartFromLevel1();
        scoreSystem.reset();
        // 清空状态并重新加载第 1 关
        mapAssets.dispose();
        enemies.clear();
        enemyBullets.clear();
        bossBullets.clear();
        enemyBulletPool.clear();
        bossBulletPool.clear();
        jeep.getWeaponSystem().getActiveBulletsInternal().clear();
        powCamps.clear();
        boss = null;
        bossSpawned = false;
        missionComplete = false;
        shakeTime = 0f;
        jeep.resetForRestart();
        loadLevel();
        com.badlogic.gdx.Gdx.app.log("World", "从第 1 关重新开始游戏");
    }

    /**
     * 切换到下一关：清空旧状态 → 加载新地图 → 确认过渡完成。
     * <p>
     * 关键保留项：分数（scoreSystem）与武器等级（weaponSystem.unlockedLevel）不重置，
     * 玩家以累积状态进入下一关。重置项：敌人/子弹/战俘营/Boss/玩家位置与血量。
     */
    private void switchToNextLevel() {
        // —— 释放旧地图资源 ——
        mapAssets.dispose();

        // —— 清空旧关卡状态 ——
        enemies.clear();
        enemyBullets.clear();
        bossBullets.clear();
        enemyBulletPool.clear();
        bossBulletPool.clear();
        jeep.getWeaponSystem().getActiveBulletsInternal().clear();
        powCamps.clear();
        boss = null;
        bossSpawned = false;
        missionComplete = false;
        shakeTime = 0f;

        // —— 加载新关卡（地图 + 碰撞器 + 渲染器 + 战俘营 + 敌人）——
        loadLevel();

        // —— 确认过渡完成，进入 FADING_IN 阶段 ——
        levelManager.confirmSwitched();
    }

    /**
     * 重启关卡：重置玩家、敌人、战俘营、Boss 到初始状态。
     * <p>
     * 由 HUD 的 R 键触发。保留分数系统（可选清零，这里清零重新计分）。
     */
    public void restartLevel() {
        // 清空敌人与子弹
        enemies.clear();
        enemyBullets.clear();
        bossBullets.clear();
        enemyBulletPool.clear();
        bossBulletPool.clear();
        // 重置玩家：位置回中心、满血、清武器子弹
        jeep.getPosition().set(mapAssets.getWorldPixelWidth() * 0.5f,
                mapAssets.getWorldPixelHeight() * 0.5f);
        jeep.getVelocity().setZero();
        jeep.resetForRestart();
        jeep.getWeaponSystem().getActiveBulletsInternal().clear();
        // 重置战俘营
        powCamps.clear();
        createPOWCamps();
        // 重置 Boss
        boss = null;
        bossSpawned = false;
        missionComplete = false;
        shakeTime = 0f;
        // 重置分数
        scoreSystem.reset();
        // 重新生成敌人
        spawnEnemies();
        com.badlogic.gdx.Gdx.app.log("World", "关卡重启");
    }

    /** 释放世界资源 */
    public void dispose() {
        jeep.disposeWeapons();
        enemyBulletPool.clear();
        bossBulletPool.clear();
        mapAssets.dispose();
    }

    /**
     * 播放短音效的便捷入口（空安全）。
     * <p>
     * AudioManager 单例可能未初始化（如测试环境），此时静默跳过。
     *
     * @param name SFX 事件名（enemy_die/rescue/hit 等）
     */
    private void playSfx(String name) {
        AudioManager am = AudioManager.get();
        if (am != null) am.playSfx(name);
    }
}
