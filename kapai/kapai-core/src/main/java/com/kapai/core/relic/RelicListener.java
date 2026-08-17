package com.kapai.core.relic;

import com.kapai.core.card.AbstractCard;
import com.kapai.core.creature.AbstractCreature;

/**
 * 遗物监听器接口（观察者模式）。
 *
 * 设计思路：遗物作为观察者订阅战斗事件，由 {@code BattleManager} 在对应时机
 * 广播。所有方法提供默认空实现，遗物只需重写关心的钩子，避免强制实现全部方法。
 * 事件参数尽量传不可变快照或基本类型，防止遗物误改战斗内部状态。
 */
public interface RelicListener {

    /** 回合开始时触发（玩家回合）。 */
    default void onTurnStart(AbstractCreature player) {
    }

    /** 回合结束时触发。 */
    default void onTurnEnd(AbstractCreature player) {
    }

    /** 打出一张牌后触发。 */
    default void onCardPlayed(AbstractCard card, AbstractCreature user, AbstractCreature target) {
    }

    /** 造成伤害后触发（source→target，amount 为最终结算值）。 */
    default void onDamageDealt(AbstractCreature source, AbstractCreature target, int amount) {
    }

    /** 受到伤害后触发。 */
    default void onDamageTaken(AbstractCreature target, int amount) {
    }

    /** 战斗开始时触发。 */
    default void onBattleStart() {
    }

    /** 战斗结束时触发（playerVictory 标识胜负）。 */
    default void onBattleEnd(boolean playerVictory) {
    }
}
