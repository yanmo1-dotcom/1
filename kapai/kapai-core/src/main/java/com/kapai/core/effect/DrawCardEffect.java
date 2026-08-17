package com.kapai.core.effect;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽牌效果。通过 {@link BattleContext#getDrawer()} 委托给战斗管理器，
 * 自身不感知牌堆数据结构，便于测试与移植。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class DrawCardEffect implements CardEffect {

    public static final String TYPE_ID = "DRAW";

    /** 抽取数量。 */
    private int amount;

    @Override
    public void apply(BattleContext ctx) {
        if (ctx.getDrawer() == null) {
            log.warn("DrawCardEffect 缺少 drawer，跳过抽牌");
            return;
        }
        int n = Math.max(0, amount);
        ctx.getDrawer().draw(n);
        log.debug("抽取 {} 张牌", n);
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }
}
