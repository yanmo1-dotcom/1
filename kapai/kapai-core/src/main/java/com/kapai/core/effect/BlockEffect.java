package com.kapai.core.effect;

import com.kapai.core.creature.AbstractCreature;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 格挡效果。受来源"敏捷"加成（+N）与"脆弱"减益（×0.75，由 gainBlock 内部处理）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class BlockEffect implements CardEffect {

    public static final String TYPE_ID = "BLOCK";

    private int amount;

    @Override
    public void apply(BattleContext ctx) {
        AbstractCreature self = ctx.getSelf();
        if (self == null) {
            return;
        }
        int base = ctx.getBaseValue() > 0 ? ctx.getBaseValue() : amount;
        self.gainBlock(base);
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }
}
