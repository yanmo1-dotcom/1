package com.kapai.core.effect;

import com.kapai.core.creature.AbstractCreature;
import lombok.Builder;
import lombok.Data;

/**
 * 卡牌/效果执行时的上下文。
 *
 * 设计思路：把"当前回合的执行环境"打包传递，避免效果实现依赖全局单例，
 * 使逻辑可单元测试、可脱离 UI 运行。{@code self} 为打出牌的一方，
 * {@code target} 为卡牌目标（可能为 null，如 AOE 牌由效果内部遍历）。
 */
@Data
@Builder
public class BattleContext {

    /** 打出此卡牌/触发此效果的生物（通常是玩家）。 */
    private final AbstractCreature self;

    /** 卡牌显式目标，AOE 或自身牌可为 null。 */
    private final AbstractCreature target;

    /** 来源卡牌的原始数值（如基础伤害/基础格挡），供效果乘算 Buff 后使用。 */
    private final int baseValue;

    /** 抽牌能力入口，供 DrawCardEffect 等需要操作牌堆的效果使用，可为 null。 */
    private final CardDrawer drawer;
}
