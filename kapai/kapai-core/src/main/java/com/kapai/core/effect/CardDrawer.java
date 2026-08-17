package com.kapai.core.effect;

/**
 * 抽牌能力接口。由战斗管理器实现并注入 {@link BattleContext}，
 * 使效果（如 DrawCardEffect）能操作牌堆而不直接依赖管理器单例。
 */
@FunctionalInterface
public interface CardDrawer {
    /** 从抽牌堆顶抽取 n 张牌到手牌。 */
    void draw(int n);
}
