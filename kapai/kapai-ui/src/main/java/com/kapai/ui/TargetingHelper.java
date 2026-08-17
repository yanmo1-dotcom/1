package com.kapai.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.kapai.core.card.AbstractCard;
import com.kapai.core.creature.AbstractCreature;
import com.kapai.core.enums.CardType;

import java.util.List;

/**
 * 拖拽目标指示器：根据拖拽中卡牌类型，高亮合法悬停目标。
 *
 * 设计思路：把"哪张牌可拖向谁"的视觉提示从渲染主类剥离，集中管理颜色与判定，
 * 便于扩展（如将来 AOE 牌高亮全体敌人）。不持有状态，仅按入参渲染。
 */
public final class TargetingHelper {

    /** 攻击牌悬停敌人时的红框色。 */
    private static final Color ATTACK_HOVER = new Color(0.95f, 0.2f, 0.2f, 0.9f);
    /** 防御/技能牌悬停玩家时的蓝框色。 */
    private static final Color DEFEND_HOVER = new Color(0.2f, 0.5f, 0.95f, 0.9f);

    /**
     * 绘制拖拽悬停高亮。攻击牌 → 敌人红框；非攻击牌 → 玩家蓝框。
     *
     * @param shapes       形状渲染器（调用方控制 begin/end，本方法内部自管）
     * @param dragged      拖拽中的卡牌，null 则不绘制
     * @param cursorX      光标逻辑 x
     * @param cursorY      光标逻辑 y
     * @param enemies      敌人列表
     * @param enemyRects   敌人矩形列表（与 enemies 等长同序）
     * @param playerRect   玩家面板矩形
     */
    public static void drawHover(ShapeRenderer shapes, AbstractCard dragged,
                                 float cursorX, float cursorY,
                                 List<? extends AbstractCreature> enemies,
                                 List<Rectangle> enemyRects, Rectangle playerRect) {
        if (dragged == null) return;
        boolean isAttack = dragged.getType() == CardType.ATTACK;
        float pad = 6f;

        if (isAttack) {
            for (int i = 0; i < enemyRects.size() && i < enemies.size(); i++) {
                Rectangle r = enemyRects.get(i);
                if (!enemies.get(i).isDead() && r.contains(cursorX, cursorY)) {
                    drawHighlight(shapes, r, ATTACK_HOVER, pad);
                }
            }
        } else {
            if (playerRect.contains(cursorX, cursorY)) {
                drawHighlight(shapes, playerRect, DEFEND_HOVER, pad);
            }
        }
    }

    /** 画一圈描边高亮：外层加粗描边 + 内层半透明填充，营造发光感。 */
    private static void drawHighlight(ShapeRenderer shapes, Rectangle r, Color color, float pad) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // 半透明发光底
        shapes.setColor(new Color(color.r, color.g, color.b, 0.18f));
        shapes.rect(r.x - pad, r.y - pad, r.width + pad * 2, r.height + pad * 2);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(color);
        // 描边加粗：画两层 Line 近似 2px
        shapes.rect(r.x - pad, r.y - pad, r.width + pad * 2, r.height + pad * 2);
        shapes.rect(r.x - pad + 1, r.y - pad + 1, r.width + pad * 2 - 2, r.height + pad * 2 - 2);
        shapes.end();
    }

    private TargetingHelper() {
    }
}
