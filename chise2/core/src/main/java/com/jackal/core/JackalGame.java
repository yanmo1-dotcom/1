package com.jackal.core;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * JackalGame —— 《赤色要塞》复刻版主入口类。
 * <p>
 * 继承 LibGDX 的 {@link Game}，负责管理全局渲染资源与屏幕生命周期。
 * 从第 3 步起，游戏逻辑收敛到 {@link GameWorld}，本类只做：
 * 清屏 → 更新世界 → 渲染世界 → 渲染 HUD。
 * <p>
 * 后续模块开发计划（每一步独立提交）：
 * <ul>
 *   <li>第 1 步：吉普车控制器（移动 + 瞄准解耦）</li>
 *   <li>第 2 步：武器系统状态机（机枪/手雷/火箭弹）</li>
 *   <li>第 3 步：瓦片地图与像素对齐渲染 ← 当前</li>
 *   <li>第 4 步：战俘营交互</li>
 *   <li>第 5 步：敌人 AI（步兵/坦克/炮台）</li>
 * </ul>
 *
 * @author Jackal Dev Team
 */
public class JackalGame extends Game {

    /** 设计分辨率宽（逻辑视口宽，相机 viewportWidth 与之一致） */
    public static final int VIRTUAL_WIDTH = 512;

    /** 设计分辨率高 */
    public static final int VIRTUAL_HEIGHT = 480;

    /** 全局 SpriteBatch——地图渲染器、HUD 共用，避免重复分配 */
    private SpriteBatch batch;

    /** 矢量图形渲染器，绘制吉普车/子弹（第 1-3 步暂用占位矢量绘制） */
    private ShapeRenderer shapes;

    /** 正交相机，由 GameWorld 跟随吉普车并做地图边界 clamp */
    private OrthographicCamera camera;

    /** 视口：FitViewport 保持 4:3 比例不拉伸变形（黑边填充） */
    private Viewport viewport;

    /** 游戏世界（地图 + 吉普车 + 武器 + 碰撞 + 相机跟随） */
    private GameWorld world;

    /** HUD 字体（默认 BitmapFont，无需外部 ttf 资源） */
    private BitmapFont font;

    /** 鼠标世界坐标复用缓存，避免每帧 new Vector3 造成 GC 抖动 */
    private final Vector3 mouseWorld = new Vector3();

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        // —— 初始化全局渲染对象 ——
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        camera = new OrthographicCamera();
        viewport = new FitViewport(VIRTUAL_WIDTH, VIRTUAL_HEIGHT, camera);
        viewport.apply(true);

        // —— 创建游戏世界（内部加载地图、吉普车、武器、碰撞器） ——
        world = new GameWorld(camera, batch, shapes);

        // —— 初始化音频系统（加载 BGM 与 SFX，BGM 自动循环播放） ——
        AudioManager.create();

        // —— HUD 字体：用 FreeType 从 simhei.ttf 生成含中文的 BitmapFont ——
        // 默认 BitmapFont 用 Arial 不含中文字形，会显示方块；FreeType 运行时生成可支持中文。
        font = createChineseFont(18);

        Gdx.app.log("Jackal", "第 9 步：关卡推进系统已加载（2 关，击败 Boss 后延迟 2 秒自动推进）");
    }

    /**
     * 用 FreeType 从 simhei.ttf 生成含中文的 BitmapFont。
     * <p>
     * 同时生成一个较小（18px）的基础字体；HUD 需要放大文字时用 setScale 临时缩放
     * （FreeType BitmapFont 仍支持 setScale，画质会稍模糊但功能正常）。
     * FreeTypeFontGenerator 用完必须 dispose。
     *
     * @param size 字号（像素）
     * @return 配置好颜色的 BitmapFont
     */
    private BitmapFont createChineseFont(int size) {
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator gen =
                new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(
                        Gdx.files.internal("fonts/simhei.ttf"));
        com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter param =
                new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();
        param.size = size;
        // 预生成常用字符，避免运行时逐个生成卡顿
        // 含 HUD 所需全部中文 + ASCII。这里用通用的 GBK 常用字 + 标点
        param.characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
                + " :,./[](){}-+=*!?'\"<>;_"
                + "关卡武器冷却活跃子弹战俘剩余生命敌人救援摧毁分坐标按重新开始游戏"
                + "秒后进入下一关总分全部通关最终分数阵亡任务完成空格切换左键开火移动"
                + "背景音乐音效退出激光火箭弹手雷机枪追踪导弹装甲车阶段攻击命中"
                + "巡逻扫描扇形视野预判发射召唤步兵冲撞旋转震动";
        // 边缘留 1px padding 防字符粘连
        param.borderWidth = 0f;
        BitmapFont f = gen.generateFont(param);
        f.setColor(Color.WHITE);
        gen.dispose();
        return f;
    }

    @Override
    public void render() {
        // 1. 清屏（深色背景，地图外的区域显示此色）
        ScreenUtils.clear(Color.BLACK);

        float dt = Gdx.graphics.getDeltaTime();

        // 2. 鼠标屏幕坐标 → 世界坐标（unproject），供吉普车瞄准与开火
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        camera.unproject(mouseWorld);

        // 3. 更新世界（吉普车物理 + 武器 + 碰撞 + 相机跟随，均在 GameWorld 内完成）
        world.update(dt, mouseWorld.x, mouseWorld.y);

        // 4. 渲染世界（地图 → 子弹 → 吉普车 → 炮塔线）
        world.render(shapes);

        // 5. HUD（屏幕坐标，固定在左上角）
        renderHud();

        // 调试：ESC 退出
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }
        // 音频开关：M 切换 BGM，N 切换 SFX（便于运行时调试音效）
        AudioManager am = AudioManager.get();
        if (am != null) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
                am.setMusicEnabled(!am.isMusicEnabled());
                Gdx.app.log("Audio", "BGM " + (am.isMusicEnabled() ? "开" : "关"));
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
                am.setSfxEnabled(!am.isSfxEnabled());
                Gdx.app.log("Audio", "SFX " + (am.isSfxEnabled() ? "开" : "关"));
            }
        }
        // GAME CLEAR 后按 R 从第 1 关重新开始游戏
        if (world.isGameClear() && Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            world.restartGame();
            Gdx.app.log("Jackal", "按 R 重新开始游戏");
        }
    }

    /**
     * 渲染 HUD：武器名、冷却条、活跃子弹计数、操作提示。
     * <p>
     * 使用屏幕坐标系（原点左下），不受游戏相机跟随影响。
     */
    private void renderHud() {
        batch.begin();
        WeaponSystem ws = world.getJeep().getWeaponSystem();
        ScoreSystem score = world.getScoreSystem();

        // —— 左上：武器与操作信息 ——
        font.draw(batch, "关卡 " + world.getLevelManager().getCurrentDisplayNumber()
                + "/" + world.getLevelManager().getTotalLevels()
                + "  武器: " + ws.getCurrentWeaponName()
                + "  冷却: " + ws.getCurrentCooldown() + "s", 10f, VIRTUAL_HEIGHT - 12f);
        font.draw(batch, "活跃子弹: " + ws.getActiveBulletCount(), 10f, VIRTUAL_HEIGHT - 30f);
        // 战俘剩余总数：统计所有 ACTIVE 营的剩余人数
        int powRemaining = 0;
        for (POWCamp camp : world.getPOWCamps()) {
            powRemaining += camp.getRemainingPows();
        }
        font.draw(batch, "战俘剩余: " + powRemaining, 10f, VIRTUAL_HEIGHT - 48f);
        // 生命与敌人数（第 5 步）
        Jeep jeep = world.getJeep();
        font.draw(batch, "生命: " + jeep.getHp() + "/" + jeep.getMaxHp()
                + "  敌人: " + world.getEnemies().size, 10f, VIRTUAL_HEIGHT - 66f);
        font.draw(batch, "[空格]切换  [左键]开火  [WASD]移动  [E]救援  [M]BGM  [N]SFX  [ESC]退出",
                10f, VIRTUAL_HEIGHT - 84f);
        // 显示吉普车世界坐标（调试地图碰撞用）
        font.draw(batch, "坐标: (" + (int) jeep.getPosition().x
                + ", " + (int) jeep.getPosition().y + ")", 10f, VIRTUAL_HEIGHT - 102f);

        // —— 右上 + 中央：分数与任务横幅（由 ScoreSystem 绘制） ——
        score.renderHudText(batch, font, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        batch.end();

        // —— 矢量 HUD：冷却条 + 红色生命条 + 死亡提示 ——
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
        shapes.updateMatrices();
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // 冷却进度条
        shapes.setColor(Color.DARK_GRAY);
        shapes.rect(10f, VIRTUAL_HEIGHT - 114f, 120f, 6f);
        float ratio = ws.getCooldownRatio();
        if (ratio > 0f) {
            shapes.setColor(Color.YELLOW);
            shapes.rect(10f, VIRTUAL_HEIGHT - 114f, 120f * ratio, 6f);
        } else {
            shapes.setColor(Color.GREEN);
            shapes.rect(10f, VIRTUAL_HEIGHT - 114f, 120f, 6f);
        }

        // —— 红色生命条：每格 24px，共 MAX_HP 格 ——
        float heartX = 10f;
        float heartY = VIRTUAL_HEIGHT - 128f;
        for (int i = 0; i < jeep.getMaxHp(); i++) {
            shapes.setColor(Color.DARK_GRAY);
            shapes.rect(heartX + i * 28f, heartY, 24f, 10f);
            if (i < jeep.getHp()) {
                // 有血：红色填充
                shapes.setColor(Color.RED);
                shapes.rect(heartX + i * 28f, heartY, 24f, 10f);
            }
        }

        // —— Boss 血条：屏幕顶部红色长条（Boss 登场后显示）——
        BossArmoredVehicle boss = world.getBoss();
        if (boss != null && !boss.isDefeated()) {
            float barW = VIRTUAL_WIDTH - 40f;
            float barX = 20f;
            float barY = VIRTUAL_HEIGHT - 14f;
            // 背景槽
            shapes.setColor(Color.DARK_GRAY);
            shapes.rect(barX, barY - 8f, barW, 8f);
            // 血量填充：红→橙随阶段变色
            float hpRatio = (float) boss.getHp() / boss.getMaxHp();
            shapes.setColor(boss.getPhase() == BossArmoredVehicle.BossPhase.PHASE_3
                    ? Color.RED : (boss.getPhase() == BossArmoredVehicle.BossPhase.PHASE_2
                    ? Color.ORANGE : Color.SCARLET));
            shapes.rect(barX, barY - 8f, barW * hpRatio, 8f);
            // 阶段标记文字在 batch 块画
        }

        // —— 死亡提示：玩家阵亡时屏幕中央红色横条 ——
        if (jeep.isDead()) {
            shapes.setColor(new Color(0.6f, 0f, 0f, 0.6f));
            shapes.rect(0, VIRTUAL_HEIGHT * 0.5f - 20f, VIRTUAL_WIDTH, 40f);
        }
        // —— 任务完成提示：击败 Boss 后绿色横条 ——
        if (world.isMissionComplete()) {
            shapes.setColor(new Color(0f, 0.5f, 0f, 0.6f));
            shapes.rect(0, VIRTUAL_HEIGHT * 0.5f - 30f, VIRTUAL_WIDTH, 60f);
        }
        shapes.end();

        // —— 死亡文字（需单独 batch.begin，因上面已 end） ——
        if (jeep.isDead()) {
            batch.begin();
            Color old = font.getColor();
            font.setColor(Color.WHITE);
            font.getData().setScale(1.4f);
            font.draw(batch, "GAME OVER - 按 ESC 退出",
                    VIRTUAL_WIDTH * 0.5f - 130f, VIRTUAL_HEIGHT * 0.5f + 8f);
            font.getData().setScale(1.0f);
            font.setColor(old);
            batch.end();
        }
        // —— MISSION COMPLETE 结算（击败 Boss 后显示总分，2 秒后自动推进）——
        if (world.isMissionComplete() && !world.getLevelManager().isGameClear()) {
            batch.begin();
            Color old = font.getColor();
            font.setColor(Color.YELLOW);
            font.getData().setScale(1.5f);
            font.draw(batch, "MISSION COMPLETE",
                    VIRTUAL_WIDTH * 0.5f - 110f, VIRTUAL_HEIGHT * 0.5f + 10f);
            font.getData().setScale(1.0f);
            font.setColor(Color.WHITE);
            font.draw(batch, "总分: " + world.getScoreSystem().getScore(),
                    VIRTUAL_WIDTH * 0.5f - 50f, VIRTUAL_HEIGHT * 0.5f - 14f);
            font.draw(batch, "2 秒后进入下一关...",
                    VIRTUAL_WIDTH * 0.5f - 70f, VIRTUAL_HEIGHT * 0.5f - 32f);
            font.setColor(old);
            batch.end();
        }

        // —— 关卡过渡：黑屏淡入淡出 + LEVEL N 文字 ——
        LevelManager lm = world.getLevelManager();
        if (lm.getFadeAlpha() > 0f) {
            // 半透明黑色全屏覆盖（alpha 由 LevelManager 驱动）
            shapes.getProjectionMatrix().setToOrtho2D(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            shapes.updateMatrices();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(new Color(0, 0, 0, lm.getFadeAlpha()));
            shapes.rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            shapes.end();
            // 黑屏期间显示 LEVEL N 文字（FADING_IN 阶段，已切到新关）
            String ttext = lm.getTransitionText();
            if (!ttext.isEmpty() && lm.getFadeAlpha() > 0.5f) {
                batch.begin();
                Color old = font.getColor();
                font.setColor(Color.WHITE);
                font.getData().setScale(2.0f);
                font.draw(batch, ttext,
                        VIRTUAL_WIDTH * 0.5f - 60f, VIRTUAL_HEIGHT * 0.5f + 10f);
                font.getData().setScale(1.0f);
                font.setColor(old);
                batch.end();
            }
        }

        // —— GAME CLEAR：全部关卡通关，显示最终分数 + R 重启游戏 ——
        if (world.isGameClear()) {
            shapes.getProjectionMatrix().setToOrtho2D(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            shapes.updateMatrices();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(new Color(0, 0, 0, 0.8f));
            shapes.rect(0, 0, VIRTUAL_WIDTH, VIRTUAL_HEIGHT);
            shapes.end();
            batch.begin();
            Color old = font.getColor();
            font.setColor(Color.GOLD);
            font.getData().setScale(1.6f);
            font.draw(batch, "GAME CLEAR",
                    VIRTUAL_WIDTH * 0.5f - 85f, VIRTUAL_HEIGHT * 0.5f + 20f);
            font.getData().setScale(1.2f);
            font.setColor(Color.WHITE);
            font.draw(batch, "FINAL SCORE: " + world.getScoreSystem().getScore(),
                    VIRTUAL_WIDTH * 0.5f - 90f, VIRTUAL_HEIGHT * 0.5f - 10f);
            font.getData().setScale(1.0f);
            font.draw(batch, "按 R 重新开始游戏",
                    VIRTUAL_WIDTH * 0.5f - 70f, VIRTUAL_HEIGHT * 0.5f - 40f);
            font.setColor(old);
            batch.end();
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        world.dispose();
        AudioManager.disposeInstance();
        font.dispose();
        batch.dispose();
        shapes.dispose();
    }
}
