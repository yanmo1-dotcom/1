package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * ScoreSystem —— 分数与任务横幅管理。
 * <p>
 * 统一管理玩家得分、任务完成横幅的显示与计时。
 * <p>
 * 横幅显示逻辑：{@link #showMissionAccomplished()} 启动 2 秒倒计时，
 * {@link #renderHud(SpriteBatch, BitmapFont, int, int)} 在屏幕中央绘制大字，
 * 倒计时归零后自动消失。
 *
 * @author Jackal Dev Team
 */
public class ScoreSystem {

    /** 任务横幅显示时长（秒），按需求为 2 秒 */
    public static final float BANNER_DURATION = 2.0f;

    /** 当前总分 */
    private int score = 0;

    /** 横幅剩余显示时间（秒）。≤0 表示不显示 */
    private float bannerTimer = 0f;

    /** 累计已救援战俘总数（统计用，含各营） */
    private int totalRescued = 0;

    /** 累计已摧毁战俘营数（暴力解法统计） */
    private int totalDestroyed = 0;

    /** 任务横幅色复用实例（alpha 动态变化，避免每帧 new Color） */
    private final Color bannerColor = new Color(1f, 0.85f, 0.2f, 1f);

    /**
     * 增加分数（救援/摧毁均调用）。
     *
     * @param amount 分数增量（已由调用方按规则计算好，如摧毁减半）
     */
    public void addScore(int amount) {
        score += amount;
        if (score < 0) score = 0;
    }

    /** 记录一次救援（统计） */
    public void recordRescue() {
        totalRescued++;
    }

    /** 记录一次摧毁（统计） */
    public void recordDestroy() {
        totalDestroyed++;
    }

    /** 重置分数与统计（关卡重启用） */
    public void reset() {
        score = 0;
        totalRescued = 0;
        totalDestroyed = 0;
        bannerTimer = 0f;
    }

    /** @return 当前总分 */
    public int getScore() {
        return score;
    }

    /** @return 累计救援数 */
    public int getTotalRescued() {
        return totalRescued;
    }

    /** @return 累计摧毁营数 */
    public int getTotalDestroyed() {
        return totalDestroyed;
    }

    /** 启动"任务完成"横幅，显示 BANNER_DURATION 秒 */
    public void showMissionAccomplished() {
        bannerTimer = BANNER_DURATION;
    }

    /** @return 横幅是否仍在显示中 */
    public boolean isBannerActive() {
        return bannerTimer > 0f;
    }

    /**
     * 每帧推进横幅计时。
     *
     * @param dt 帧时间（秒）
     */
    public void update(float dt) {
        if (bannerTimer > 0f) {
            bannerTimer -= dt;
            if (bannerTimer < 0f) bannerTimer = 0f;
        }
    }

    /**
     * 渲染 HUD 文本部分：分数行 + 中央任务横幅。
     * <p>
     * 使用屏幕坐标系（SpriteBatch 已设屏幕正交投影）。
     *
     * @param batch       SpriteBatch（已 begin）
     * @param font        HUD 字体
     * @param screenWidth 屏幕逻辑宽（用于居中横幅）
     * @param screenHeight 屏幕逻辑高
     */
    public void renderHudText(SpriteBatch batch, BitmapFont font,
                              int screenWidth, int screenHeight) {
        // 分数行：显示在右上角
        String scoreLine = "分数: " + score
                + "  救援: " + totalRescued
                + "  摧毁: " + totalDestroyed;
        // 右上角：x 取屏幕宽减去文字估算宽度（BitmapFont 无直接宽度，用近似）
        font.draw(batch, scoreLine, screenWidth - 220f, screenHeight - 12f);

        // 中央任务横幅
        if (isBannerActive()) {
            // 临时放大字体显示横幅：用 font.getData().setScale 设置缩放
            // 为避免影响其他文本，save/restore 模式较重；这里用直接绘制大字替代：
            // 取横幅剩余比例做淡出（最后 0.3 秒渐隐）
            float alpha = bannerTimer < 0.3f ? (bannerTimer / 0.3f) : 1f;
            Color oldColor = font.getColor();
            bannerColor.a = alpha;
            font.setColor(bannerColor);
            // 居中：估算文字宽 ~ 12px/字 * 20 字 ≈ 240
            String msg = "MISSION ACCOMPLISHED";
            font.getData().setScale(1.6f);
            font.draw(batch, msg,
                    (screenWidth - 240f) * 0.5f,
                    screenHeight * 0.5f + 12f);
            font.getData().setScale(1.0f);
            font.setColor(oldColor);
        }
    }
}
