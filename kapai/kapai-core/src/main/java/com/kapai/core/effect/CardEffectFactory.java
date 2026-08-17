package com.kapai.core.effect;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 卡牌效果工厂。按 typeId 字符串创建对应效果实例（策略路由）。
 *
 * 设计思路：数据层 JSON 中效果形如 {"type":"DAMAGE","amount":6}，
 * 解析时先读 type 字段，再据此构造空实例，剩余字段由 Jackson 填充。
 * 注册表式设计便于扩展新效果——只需 put 一个新条目，无需改工厂结构。
 */
public final class CardEffectFactory {

    private static final Map<String, Supplier<CardEffect>> REGISTRY = new HashMap<>();

    static {
        register(DamageEffect.TYPE_ID, DamageEffect::new);
        register(BlockEffect.TYPE_ID, BlockEffect::new);
        register(DrawCardEffect.TYPE_ID, DrawCardEffect::new);
        register(ApplyStatusEffect.TYPE_ID, ApplyStatusEffect::new);
    }

    public static void register(String typeId, Supplier<CardEffect> supplier) {
        REGISTRY.put(typeId, supplier);
    }

    /**
     * 按 typeId 创建效果实例；未知类型抛出 IllegalArgumentException，
     * 由数据层捕获并跳过该卡牌以保证解析健壮性。
     */
    public static CardEffect create(String typeId) {
        Supplier<CardEffect> supplier = REGISTRY.get(typeId);
        if (supplier == null) {
            throw new IllegalArgumentException("未注册的卡牌效果类型: " + typeId);
        }
        return supplier.get();
    }

    private CardEffectFactory() {
    }
}
