package com.kapai.core.relic;

import com.kapai.core.creature.AbstractCreature;
import lombok.extern.slf4j.Slf4j;

/**
 * 示例遗物：燃烧之血——每回合开始时回复 2 点生命。
 * 演示观察者钩子的典型用法：在 onTurnStart 中修改玩家状态。
 */
@Slf4j
public class BurningBloodRelic extends AbstractRelic {

    private static final int HEAL_PER_TURN = 2;

    public BurningBloodRelic() {
        super("BURNING_BLOOD", "燃烧之血", "每回合开始时回复 " + HEAL_PER_TURN + " 点生命");
    }

    @Override
    public void onTurnStart(AbstractCreature player) {
        if (player == null) {
            return;
        }
        player.heal(HEAL_PER_TURN);
        log.info("[遗物] {} 触发，{} 回复 {} HP", name, player.getName(), HEAL_PER_TURN);
    }
}
