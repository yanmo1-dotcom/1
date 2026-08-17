package com.jackal.core;

/**
 * LevelManager —— 关卡推进与过渡管理器。
 * <p>
 * 维护当前关卡索引、总关卡数、过渡动画计时与最终关判定。
 * 由 {@link GameWorld} 持有，GameWorld 在 Boss 击败后调用 {@link #triggerAdvance()} 启动过渡。
 *
 * <h3>关卡列表</h3>
 * {@link #LEVEL_FILES} 是关卡 tmx 文件路径表（相对 assets 根）。
 * 当前共 2 关：level1.tmx、level2.tmx。新增关卡只需往此数组追加文件路径。
 *
 * <h3>过渡动画时序</h3>
 * 击败 Boss 后延迟 2 秒（需求要求）才开始过渡；过渡总时长 2 秒：
 * <pre>
 *   WAITING(2s 延迟) → FADING_OUT(1s 黑屏淡入) → 切换关卡 → FADING_IN(1s 黑屏淡出) → PLAYING
 * </pre>
 * 用 phase 枚举 + 计时器驱动，alpha 由当前阶段进度计算（0=透明，1=全黑）。
 * 中间切换瞬间显示 "LEVEL N" 文字。
 *
 * <h3>最终关判定</h3>
 * 当当前关卡已是最后一关且 Boss 被击败，不进入下一关，
 * 而是置 {@link #gameClear} 并锁定输入，由 HUD 显示 "GAME CLEAR - FINAL SCORE: XXXX"。
 *
 * <h3>assets 目录组织</h3>
 * <pre>
 *   core/assets/
 *     tiles/      ← 地图与图集（level1.tmx, level2.tmx, tileset.tsx, tileset.png）
 *     sounds/     ← 音效
 * </pre>
 * 多关地图共享同一 tileset.tsx（图集复用），每关 tmx 独立布局。
 * VS Code 中 tmx/tsx 是 XML，装 "Tiled Map Editor" 桌面端编辑更直观，
 * 但本仓库所有 tmx 均手写，VS Code 内直接编辑 XML 即可。
 *
 * @author Jackal Dev Team
 */
public class LevelManager {

    /** 关卡文件路径表（相对 assets 根）。新增关卡追加到此数组即可。 */
    public static final String[] LEVEL_FILES = {
            "tiles/level1.tmx",
            "tiles/level2.tmx"
    };

    /** 关卡过渡阶段 */
    public enum Phase {
        /** 正常游玩中 */
        PLAYING,
        /** Boss 击败后等待 2 秒才开始过渡 */
        WAITING,
        /** 黑屏淡入（1s） */
        FADING_OUT,
        /** 黑屏淡出（1s），中间显示 LEVEL N */
        FADING_IN,
        /** 全部关卡通关，锁定输入 */
        GAME_CLEAR
    }

    /** 当前关卡索引（0-based） */
    private int currentIndex = 0;
    /** 当前过渡阶段 */
    private Phase phase = Phase.PLAYING;
    /** 当前阶段已用时间（秒） */
    private float phaseTimer = 0f;
    /** 当前黑屏 alpha [0,1]。0=透明 1=全黑，由阶段进度计算 */
    private float fadeAlpha = 0f;
    /** 是否已通关（最终关 Boss 击败） */
    private boolean gameClear = false;
    /** 待切换的目标关卡索引（FADING_OUT 结束时切换） */
    private int pendingIndex = 0;

    /** 击败 Boss 后等待时长（秒） */
    private static final float WAIT_DURATION = 2.0f;
    /** 淡入/淡出时长（秒） */
    private static final float FADE_DURATION = 1.0f;

    /** @return 当前关卡索引（0-based） */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /** @return 当前关卡文件路径 */
    public String getCurrentFilePath() {
        return LEVEL_FILES[currentIndex];
    }

    /** @return 总关卡数 */
    public int getTotalLevels() {
        return LEVEL_FILES.length;
    }

    /** @return 当前关卡序号（1-based，用于显示 LEVEL N） */
    public int getCurrentDisplayNumber() {
        return currentIndex + 1;
    }

    /** @return 过渡阶段 */
    public Phase getPhase() {
        return phase;
    }

    /** @return 黑屏 alpha [0,1]，HUD 用此值绘制半透明黑色覆盖 */
    public float getFadeAlpha() {
        return fadeAlpha;
    }

    /** @return 是否已通关（最终关 Boss 击败） */
    public boolean isGameClear() {
        return gameClear;
    }

    /** @return 是否处于过渡中（非 PLAYING 且非 GAME_CLEAR） */
    public boolean isTransitioning() {
        return phase == Phase.WAITING || phase == Phase.FADING_OUT || phase == Phase.FADING_IN;
    }

    /** @return 是否应锁定玩家输入（过渡中或通关后） */
    public boolean isInputLocked() {
        return isTransitioning() || gameClear;
    }

    /**
     * 启动关卡推进：Boss 击败后调用。
     * <p>
     * 若已是最终关，置 gameClear；否则进入 WAITING 阶段，2 秒后开始淡出。
     */
    public void triggerAdvance() {
        if (phase != Phase.PLAYING) return; // 已在过渡中，不重复触发
        if (currentIndex >= LEVEL_FILES.length - 1) {
            // 最终关，通关
            gameClear = true;
            phase = Phase.GAME_CLEAR;
            com.badlogic.gdx.Gdx.app.log("Level", "全部通关！GAME CLEAR");
        } else {
            pendingIndex = currentIndex + 1;
            phase = Phase.WAITING;
            phaseTimer = 0f;
            com.badlogic.gdx.Gdx.app.log("Level", "击败 Boss，2 秒后进入第 " + (pendingIndex + 1) + " 关");
        }
    }

    /**
     * 每帧推进过渡计时。
     * <p>
     * 在 FADING_OUT 结束的瞬间返回 true，表示本帧需要执行关卡切换
     * （GameWorld 据此重新加载地图/敌人/战俘，并重置玩家位置）。调用方收到 true 后
     * 应完成实际切换，并把阶段推进到 FADING_IN。
     *
     * @param dt 帧时间（秒）
     * @return true 表示本帧需要执行关卡切换
     */
    public boolean update(float dt) {
        if (phase == Phase.PLAYING || phase == Phase.GAME_CLEAR) return false;
        phaseTimer += dt;
        switch (phase) {
            case WAITING:
                if (phaseTimer >= WAIT_DURATION) {
                    phase = Phase.FADING_OUT;
                    phaseTimer = 0f;
                }
                break;
            case FADING_OUT:
                fadeAlpha = Math.min(1f, phaseTimer / FADE_DURATION);
                if (phaseTimer >= FADE_DURATION) {
                    fadeAlpha = 1f;
                    // 通知调用方切换关卡；调用方切换后调 confirmSwitched() 推进到 FADING_IN
                    return true;
                }
                break;
            case FADING_IN:
                fadeAlpha = Math.max(0f, 1f - phaseTimer / FADE_DURATION);
                if (phaseTimer >= FADE_DURATION) {
                    fadeAlpha = 0f;
                    phase = Phase.PLAYING;
                    phaseTimer = 0f;
                }
                break;
            case PLAYING:
            case GAME_CLEAR:
            default:
                break;
        }
        return false;
    }

    /**
     * 确认关卡切换已完成：推进到 FADING_IN 阶段并更新当前索引。
     * <p>
     * 由 GameWorld 在执行完重新加载后调用。
     */
    public void confirmSwitched() {
        currentIndex = pendingIndex;
        phase = Phase.FADING_IN;
        phaseTimer = 0f;
        com.badlogic.gdx.Gdx.app.log("Level", "已切换到第 " + getCurrentDisplayNumber() + " 关");
    }

    /**
     * 重启到第 1 关（GAME CLEAR 后按 R 触发）。
     * <p>
     * 重置索引与阶段，GameWorld 会重新加载 level1。
     */
    public void restartFromLevel1() {
        currentIndex = 0;
        pendingIndex = 0;
        phase = Phase.PLAYING;
        phaseTimer = 0f;
        fadeAlpha = 0f;
        gameClear = false;
        com.badlogic.gdx.Gdx.app.log("Level", "从第 1 关重新开始");
    }

    /** @return 过渡中间应显示的文字（"LEVEL N" 或 GAME CLEAR 文字由 HUD 单独画） */
    public String getTransitionText() {
        if (phase == Phase.FADING_IN) {
            return "LEVEL " + getCurrentDisplayNumber();
        }
        return "";
    }
}
