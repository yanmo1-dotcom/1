package com.kapai.core.relic;

import com.kapai.core.card.AbstractCard;
import com.kapai.core.creature.AbstractCreature;
import com.kapai.core.enums.CardType;
import lombok.extern.slf4j.Slf4j;

/**
 * 示例遗物：钢笔尖——每打出 3 张攻击牌，下一张攻击牌额外造成伤害（演示计数器）。
 * 这里简化为：每打出 3 张攻击牌时立即回复 1 能量，演示 onCardPlayed 钩子。
 */
@Slf4j
public class PenNibRelic extends AbstractRelic {

    private static final int THRESHOLD = 3;

    public PenNibRelic() {
        super("PEN_NIB", "钢笔尖", "每打出 " + THRESHOLD + " 张攻击牌，回复 1 点能量");
    }

    @Override
    public void onCardPlayed(AbstractCard card, AbstractCreature user, AbstractCreature target) {
        if (card == null || user == null || card.getType() != CardType.ATTACK) {
            return;
        }
        counter++;
        if (counter >= THRESHOLD) {
            counter = 0;
            user.setEnergy(user.getEnergy() + 1);
            log.info("[遗物] {} 触发，{} 回复 1 能量", name, user.getName());
        }
    }
}
