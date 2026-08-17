package com.kapai.core.effect;

/**
 * 卡牌效果策略接口（策略模式）。
 *
 * 设计思路：将卡牌"做什么"与卡牌"是什么"解耦。一张卡牌持有若干 CardEffect，
 * 打出时按顺序执行。新增效果（如治疗、增益）只需新增实现，无需修改卡牌基类，
 * 符合开闭原则。效果从 JSON 反序列化时由工厂按类型字符串映射到具体实现。
 */
public interface CardEffect {

    /**
     * 在给定上下文中执行效果。
     *
     * @param ctx 战斗上下文，提供 self/target/baseValue
     */
    void apply(BattleContext ctx);

    /**
     * 效果类型标识，用于 JSON 反序列化时路由到具体实现。
     * 例如 "DAMAGE"、"DRAW"。
     */
    String typeId();
}
